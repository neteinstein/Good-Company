#!/usr/bin/env python3
"""List missing or extra translations against English.

Usage: python3 scripts/check-strings.py [--todo]

Checks every values/strings.xml (Compose resources and Android res) against values-pt|es|fr|ro, and every
iOS en.lproj/*.strings against pt|es|fr|ro.lproj. For each key it checks that the key exists, that the
placeholders (%1$s, %d, ...) match, and that plurals have the right quantities (ro: one/few/other).
--todo prints the English text of each missing key, ready to translate.
Exits 1 when anything is wrong.
"""
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

LANGS = ["pt", "es", "fr", "ro"]
PLURAL_QUANTITIES = {"pt": {"one", "other"}, "es": {"one", "other"}, "fr": {"one", "other"}, "ro": {"one", "few", "other"}}
PLACEHOLDER = re.compile(r"%(?:\d+\$)?[sdf]")
IOS_LINE = re.compile(r'^\s*"((?:[^"\\]|\\.)*)"\s*=\s*"((?:[^"\\]|\\.)*)"\s*;', re.M)

root = Path(subprocess.check_output(["git", "rev-parse", "--show-toplevel"], text=True).strip())
show_todo = "--todo" in sys.argv
problems = 0


def report(path, msg):
    global problems
    problems += 1
    print(f"{path.relative_to(root)}: {msg}")


def text_of(el):
    return "".join(el.itertext())


def parse_xml(path):
    """key -> ("string", text) | ("plurals", {quantity: text}) | ("array", [text])"""
    out = {}
    for el in ET.parse(path).getroot():
        name = el.get("name")
        if name is None or el.get("translatable") == "false":
            continue
        if el.tag == "string":
            out[name] = ("string", text_of(el))
        elif el.tag == "plurals":
            out[name] = ("plurals", {i.get("quantity"): text_of(i) for i in el.findall("item")})
        elif el.tag == "string-array":
            out[name] = ("array", [text_of(i) for i in el.findall("item")])
    return out


def placeholders(value):
    kind, v = value
    texts = [v] if kind == "string" else list(v.values()) if kind == "plurals" else v
    return sorted(set(p for t in texts for p in PLACEHOLDER.findall(t)))


def english_text(value):
    kind, v = value
    return v if kind == "string" else " | ".join(f"{k}: {t}" for k, t in v.items()) if kind == "plurals" else " | ".join(v)


def compare(en_path, en, lang, path, other):
    if other is None:
        report(path, "file missing")
        return
    for key in en.keys() - other.keys():
        report(path, f"missing {key}" + (f"  EN: {english_text(en[key])}" if show_todo else ""))
    for key in other.keys() - en.keys():
        report(path, f"extra {key} (not in English)")
    for key in en.keys() & other.keys():
        if en[key][0] != other[key][0]:
            report(path, f"{key}: is a {other[key][0]}, English is a {en[key][0]}")
            continue
        if placeholders(en[key]) != placeholders(other[key]):
            report(path, f"{key}: placeholders {placeholders(other[key])}, English has {placeholders(en[key])}")
        if en[key][0] == "plurals":
            want, got = PLURAL_QUANTITIES[lang], set(other[key][1])
            if not want <= got:
                report(path, f"{key}: plural needs {sorted(want - got)}")
        if en[key][0] == "array" and len(en[key][1]) != len(other[key][1]):
            report(path, f"{key}: {len(other[key][1])} items, English has {len(en[key][1])}")


tracked = subprocess.check_output(["git", "ls-files", "-co", "--exclude-standard"], cwd=root, text=True).split()

for rel in tracked:
    p = root / rel
    if p.name == "strings.xml" and p.parent.name == "values":
        en = parse_xml(p)
        for lang in LANGS:
            lp = p.parent.parent / f"values-{lang}" / "strings.xml"
            compare(p, en, lang, lp, parse_xml(lp) if lp.exists() else None)
    elif p.suffix == ".strings" and p.parent.name == "en.lproj":
        en = {k: ("string", v) for k, v in IOS_LINE.findall(p.read_text(encoding="utf-8"))}
        for lang in LANGS:
            lp = p.parent.parent / f"{lang}.lproj" / p.name
            other = {k: ("string", v) for k, v in IOS_LINE.findall(lp.read_text(encoding="utf-8"))} if lp.exists() else None
            compare(p, en, lang, lp, other)

if problems:
    print(f"\n{problems} problem(s). Run with --todo to see the English text of each missing key.")
    sys.exit(1)
print("All translations complete.")
