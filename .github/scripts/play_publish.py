#!/usr/bin/env python3
"""Upload an Android App Bundle to a Google Play track, or promote what is on one track to another.

Ported from neteinstein/sms-redirect. Play requires the `changesNotSentForReview` query parameter in
some publishing states (after a rejected update, with managed publishing) and refuses it in others
("Changes are sent for review automatically. The query parameter changesNotSentForReview must not be
set."). The state changes in Play Console without any change here, so this script commits the edit,
and when Play rejects the commit because of that parameter it commits again with the opposite
setting. It also falls back to a draft release while the app itself is still a draft, and explains
the errors that only a change in Play Console can fix.

Release notes ("What's new") come from store-listing/text/<locale>/release_notes.txt.

Needs `google-auth` and `requests`. Usage:
  play_publish.py --key key.json --package org.goodcompany.app --aab app.aab --track internal \
      [--mapping mapping.txt] [--release-notes store-listing/text]
  play_publish.py --key key.json --package org.goodcompany.app --promote-from internal --track beta
"""

from __future__ import annotations

import argparse
import os
import pathlib
import sys

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{package}"
UPLOAD_API = "https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{package}"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"
REVIEW_FLAG = "changesNotSentForReview"
RELEASE_NOTES_LIMIT = 500

# Errors no retry can fix: what the maintainer has to do, keyed by a fragment of Play's message.
CONSOLE_ONLY_ERRORS = [
    (
        "has already been used",
        "Play already has this versionCode. Run the 'Test APK' workflow again (each run is a new "
        "versionCode), or merge another change.",
    ),
    (
        "does not have permission",
        "The service account in PLAY_STORE_SERVICE_ACCOUNT_JSON lacks access to this app. "
        "Grant it release permissions under Users and permissions in Play Console.",
    ),
    (
        "package not found",
        "Play doesn't know org.goodcompany.app yet. Create the app in Play Console and upload the "
        "first bundle by hand (see CI_CD.md, 'Google Play').",
    ),
    (
        "data safety",
        "Play needs the Data safety form. Fill it in under Policy and programs > App content "
        "(store-listing/data-safety.md has the answers).",
    ),
]


class PlayError(Exception):
    def __init__(self, status: int, message: str):
        super().__init__(f"HTTP {status}: {message}")
        self.status = status
        self.message = message


def flipped_review_flag(message: str, current: bool) -> bool | None:
    """The other `changesNotSentForReview` setting when Play rejected [current], else None."""
    return (not current) if REVIEW_FLAG in message else None


def is_draft_app_error(message: str) -> bool:
    """"Only releases with status draft may be created on draft app." (app never published)."""
    return "draft app" in message.lower()


def is_edit_gone(error: PlayError) -> bool:
    message = error.message.lower()
    return error.status == 404 or ("edit" in message and any(w in message for w in ("deleted", "expired", "not found")))


def console_fix_for(message: str) -> str | None:
    lowered = message.lower()
    for fragment, fix in CONSOLE_ONLY_ERRORS:
        if fragment in lowered:
            return fix
    return None


def warn(message: str) -> None:
    print(f"::warning::{message}", flush=True)


def read_release_notes(directory: str | None) -> list[dict]:
    """[{language, text}] from <directory>/<locale>/release_notes.txt, one per locale folder."""
    if not directory:
        return []
    notes = []
    for locale in sorted(pathlib.Path(directory).iterdir()):
        path = locale / "release_notes.txt"
        if not path.is_file():
            continue
        text = path.read_text(encoding="utf-8").strip()
        if len(text) > RELEASE_NOTES_LIMIT:
            raise ValueError(f"{path} has {len(text)} characters; Play allows {RELEASE_NOTES_LIMIT}.")
        if text:
            notes.append({"language": locale.name, "text": text})
    return notes


def upload_stage(aab: str, mapping: str | None = None):
    """Puts [aab] (and its R8 [mapping]) into the edit; returns its versionCode."""

    def stage(client, edit: str) -> list[int]:
        version_code = client.upload_bundle(edit, aab)
        if mapping:
            client.upload_mapping(edit, version_code, mapping)
        return [version_code]

    return stage


def promote_stage(from_track: str):
    """The versionCodes of the newest release on [from_track]."""

    def stage(client, edit: str) -> list[int]:
        releases = [r for r in client.get_track(edit, from_track).get("releases", []) if r.get("versionCodes")]
        if not releases:
            raise PlayError(404, f"The '{from_track}' track has no release to promote.")
        newest = max(releases, key=lambda r: max(int(c) for c in r["versionCodes"]))
        return sorted(int(c) for c in newest["versionCodes"])

    return stage


def prepare_edit(client, stage, track: str, status: str, notes: list[dict]) -> tuple[str, list[int], str]:
    """Opens an edit holding [stage]'s bundles on [track]. Returns the edit, versionCodes and status used."""
    edit = client.insert_edit()
    version_codes = stage(client, edit)
    try:
        client.update_track(edit, track, version_codes, status, notes)
    except PlayError as error:
        if status == "draft" or not is_draft_app_error(error.message):
            raise
        warn(f"The app is still a draft in Play Console, so this release is created as a draft instead of '{status}'.")
        status = "draft"
        client.update_track(edit, track, version_codes, status, notes)
    return edit, version_codes, status


def publish(client, stage, track: str, status: str = "completed", review_flag: bool = False, notes=()) -> list[int]:
    """Stages and commits, switching `changesNotSentForReview` if Play asks for the other one."""
    notes = list(notes)
    edit, version_codes, status = prepare_edit(client, stage, track, status, notes)
    try:
        client.commit(edit, review_flag)
        return version_codes
    except PlayError as error:
        other = flipped_review_flag(error.message, review_flag)
        if other is None:
            raise
        warn(f"Play rejected {REVIEW_FLAG}={str(review_flag).lower()} ({error.message}); committing again with {str(other).lower()}.")

    try:
        client.commit(edit, other)
        return version_codes
    except PlayError as error:
        if flipped_review_flag(error.message, other) is not None or not is_edit_gone(error):
            raise
    # The failed commit discarded the edit; nothing was published, so start over once.
    edit, version_codes, _ = prepare_edit(client, stage, track, status, notes)
    client.commit(edit, other)
    return version_codes


class PlayClient:
    """The few Android Publisher API v3 calls this needs."""

    def __init__(self, key_file: str, package: str):
        from google.auth.transport.requests import AuthorizedSession
        from google.oauth2 import service_account

        credentials = service_account.Credentials.from_service_account_file(key_file, scopes=[SCOPE])
        self.session = AuthorizedSession(credentials)
        self.api = API.format(package=package)
        self.upload_api = UPLOAD_API.format(package=package)

    def _call(self, method: str, url: str, **kwargs) -> dict:
        response = self.session.request(method, url, timeout=600, **kwargs)
        if response.status_code >= 400:
            try:
                message = response.json()["error"]["message"]
            except (ValueError, KeyError, TypeError):
                message = response.text
            raise PlayError(response.status_code, message)
        return response.json() if response.content else {}

    def insert_edit(self) -> str:
        return self._call("POST", f"{self.api}/edits")["id"]

    def upload_bundle(self, edit: str, aab: str) -> int:
        with open(aab, "rb") as bundle:
            result = self._call(
                "POST",
                f"{self.upload_api}/edits/{edit}/bundles",
                params={"uploadType": "media"},
                headers={"Content-Type": "application/octet-stream"},
                data=bundle,
            )
        return int(result["versionCode"])

    def upload_mapping(self, edit: str, version_code: int, mapping: str) -> None:
        with open(mapping, "rb") as file:
            self._call(
                "POST",
                f"{self.upload_api}/edits/{edit}/apks/{version_code}/deobfuscationFiles/proguard",
                params={"uploadType": "media"},
                headers={"Content-Type": "application/octet-stream"},
                data=file,
            )

    def get_track(self, edit: str, track: str) -> dict:
        return self._call("GET", f"{self.api}/edits/{edit}/tracks/{track}")

    def update_track(self, edit: str, track: str, version_codes: list[int], status: str, notes: list[dict]) -> None:
        release = {"versionCodes": [str(c) for c in version_codes], "status": status}
        if notes:
            release["releaseNotes"] = notes
        self._call("PUT", f"{self.api}/edits/{edit}/tracks/{track}", json={"track": track, "releases": [release]})

    def commit(self, edit: str, changes_not_sent_for_review: bool) -> None:
        params = {REVIEW_FLAG: "true"} if changes_not_sent_for_review else None
        self._call("POST", f"{self.api}/edits/{edit}:commit", params=params)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--key", required=True, help="service account JSON file")
    parser.add_argument("--package", required=True)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--aab", help="bundle to upload")
    source.add_argument("--promote-from", help="track whose newest release goes to --track")
    parser.add_argument("--mapping", help="R8 mapping.txt for the uploaded bundle")
    parser.add_argument("--track", required=True)
    parser.add_argument("--status", default="completed")
    parser.add_argument("--release-notes", help="folder with <locale>/release_notes.txt")
    args = parser.parse_args(argv)

    stage = upload_stage(args.aab, args.mapping) if args.aab else promote_stage(args.promote_from)
    try:
        notes = read_release_notes(args.release_notes)
        codes = publish(PlayClient(args.key, args.package), stage, args.track, args.status, notes=notes)
    except ValueError as error:
        print(f"::error::{error}", flush=True)
        return 1
    except PlayError as error:
        fix = console_fix_for(error.message)
        print(f"::error::Google Play rejected the upload: {error.message}", flush=True)
        if fix:
            print(f"::error::This needs a change in Play Console, not in the code. {fix}", flush=True)
        return 1

    what = f"versionCode {', '.join(map(str, codes))}"
    verb = f"Promoted {what} from '{args.promote_from}'" if args.promote_from else f"Uploaded {what}"
    summary = f"{verb} to the Play '{args.track}' track ({args.status})."
    print(summary)
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as out:
            out.write(summary + "\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
