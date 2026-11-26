"""Unit tests for play_publish.py, against a fake Play client. Run: python3 -m unittest (in .github/scripts)."""

import pathlib
import tempfile
import unittest
from unittest import mock

import play_publish
from play_publish import PlayError, console_fix_for, promote_stage, publish, read_release_notes, upload_stage

MUST_NOT_SET = (
    "Changes are sent for review automatically. The query parameter changesNotSentForReview must not be set."
)
MUST_SET = (
    "Changes cannot be sent for review automatically. Please set the query parameter "
    "changesNotSentForReview to true. Once all changes are ready to be reviewed, send them for review "
    "manually."
)


class FakePlay:
    """Accepts a commit only with [accepted_flag]; optionally discards the edit on a failed commit."""

    def __init__(self, accepted_flag, discard_edit_on_failure=False, draft_app=False, other_error=None, tracks=None):
        self.accepted_flag = accepted_flag
        self.discard_edit_on_failure = discard_edit_on_failure
        self.draft_app = draft_app
        self.other_error = other_error
        self.tracks = tracks or {}
        self.calls = []
        self.edits = 0
        self.live_edits = set()
        self.committed = []

    def insert_edit(self):
        self.edits += 1
        edit = f"e{self.edits}"
        self.live_edits.add(edit)
        self.calls.append(("insert", edit))
        return edit

    def upload_bundle(self, edit, aab):
        self.calls.append(("upload", edit))
        return 151

    def upload_mapping(self, edit, version_code, mapping):
        self.calls.append(("mapping", edit, version_code))

    def get_track(self, edit, track):
        self.calls.append(("get", edit, track))
        return self.tracks.get(track, {})

    def update_track(self, edit, track, version_codes, status, notes):
        self.calls.append(("track", edit, track, tuple(version_codes), status, len(notes)))
        if self.draft_app and status != "draft":
            raise PlayError(400, "Only releases with status draft may be created on draft app.")

    def commit(self, edit, flag):
        self.calls.append(("commit", edit, flag))
        if edit not in self.live_edits:
            raise PlayError(404, f"Edit {edit} not found.")
        if self.other_error:
            raise PlayError(403, self.other_error)
        if flag != self.accepted_flag:
            if self.discard_edit_on_failure:
                self.live_edits.discard(edit)
            raise PlayError(400, MUST_SET if self.accepted_flag else MUST_NOT_SET)
        self.committed.append((edit, flag))


UPLOAD = upload_stage("app.aab", "mapping.txt")


class PublishTest(unittest.TestCase):
    def setUp(self):
        # Collect warnings instead of printing them: on GitHub Actions a printed "::warning::" is a
        # workflow annotation, and these simulated rejections would show up on every run.
        self.warnings = []
        patcher = mock.patch.object(play_publish, "warn", self.warnings.append)
        patcher.start()
        self.addCleanup(patcher.stop)

    def test_commits_without_the_flag_when_play_sends_changes_for_review_automatically(self):
        play = FakePlay(accepted_flag=False)

        self.assertEqual([151], publish(play, UPLOAD, "internal"))
        self.assertEqual([("e1", False)], play.committed)
        self.assertIn(("mapping", "e1", 151), play.calls)
        self.assertEqual([], self.warnings)

    def test_retries_with_the_flag_when_play_requires_it(self):
        play = FakePlay(accepted_flag=True)

        publish(play, UPLOAD, "internal")

        self.assertEqual([("e1", True)], play.committed)
        self.assertEqual(1, sum(1 for c in play.calls if c[0] == "upload"), "bundle uploaded once")
        self.assertEqual(1, len(self.warnings))
        self.assertIn("changesNotSentForReview=false", self.warnings[0])

    def test_retries_without_the_flag_when_play_refuses_it(self):
        play = FakePlay(accepted_flag=False)

        publish(play, UPLOAD, "internal", review_flag=True)

        self.assertEqual([("e1", False)], play.committed)

    def test_starts_a_new_edit_when_the_failed_commit_discarded_it(self):
        play = FakePlay(accepted_flag=True, discard_edit_on_failure=True)

        publish(play, UPLOAD, "internal")

        self.assertEqual([("e2", True)], play.committed)

    def test_creates_a_draft_release_while_the_app_is_a_draft(self):
        play = FakePlay(accepted_flag=False, draft_app=True)

        publish(play, UPLOAD, "internal")

        self.assertIn(("track", "e1", "internal", (151,), "draft", 0), play.calls)
        self.assertIn("draft", self.warnings[0])
        self.assertEqual([("e1", False)], play.committed)

    def test_other_errors_are_not_retried(self):
        play = FakePlay(accepted_flag=False, other_error="The caller does not have permission")

        with self.assertRaises(PlayError):
            publish(play, UPLOAD, "internal")
        self.assertEqual(1, sum(1 for c in play.calls if c[0] == "commit"))

    def test_promotes_the_newest_release_of_the_source_track_without_uploading(self):
        play = FakePlay(
            accepted_flag=False,
            tracks={"internal": {"releases": [{"versionCodes": ["150"]}, {"versionCodes": ["152"]}, {"status": "halted"}]}},
        )

        self.assertEqual([152], publish(play, promote_stage("internal"), "beta", notes=[{"language": "en-US", "text": "x"}]))
        self.assertIn(("track", "e1", "beta", (152,), "completed", 1), play.calls)
        self.assertFalse(any(c[0] == "upload" for c in play.calls))

    def test_promoting_an_empty_track_fails(self):
        with self.assertRaises(PlayError):
            publish(FakePlay(accepted_flag=False), promote_stage("internal"), "beta")

    def test_console_only_errors_get_an_explanation(self):
        self.assertIn("Test APK", console_fix_for("APK specifies a version code that has already been used."))
        self.assertIn("Users and permissions", console_fix_for("The caller does not have permission"))
        self.assertIsNone(console_fix_for(MUST_NOT_SET))


class ReleaseNotesTest(unittest.TestCase):
    def test_reads_one_note_per_locale_folder(self):
        with tempfile.TemporaryDirectory() as tmp:
            for locale, text in [("en-US", "Hello\n"), ("pt-PT", "Olá"), ("empty", "")]:
                (pathlib.Path(tmp) / locale).mkdir()
                (pathlib.Path(tmp) / locale / "release_notes.txt").write_text(text, encoding="utf-8")
            (pathlib.Path(tmp) / "no-notes").mkdir()

            self.assertEqual(
                [{"language": "en-US", "text": "Hello"}, {"language": "pt-PT", "text": "Olá"}],
                read_release_notes(tmp),
            )

    def test_too_long_notes_fail_before_anything_is_uploaded(self):
        with tempfile.TemporaryDirectory() as tmp:
            (pathlib.Path(tmp) / "en-US").mkdir()
            (pathlib.Path(tmp) / "en-US" / "release_notes.txt").write_text("x" * 501, encoding="utf-8")
            with self.assertRaises(ValueError):
                read_release_notes(tmp)

    def test_the_repo_notes_fit(self):
        repo_notes = pathlib.Path(__file__).resolve().parents[2] / "store-listing" / "text"
        self.assertEqual(5, len(read_release_notes(str(repo_notes))))


if __name__ == "__main__":
    unittest.main()
