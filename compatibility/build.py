#!/usr/bin/env python3
"""Checks compatibility/results.json and writes COMPATIBILITY.md from it. Standard library only.

    python3 compatibility/build.py          # validate, then rewrite COMPATIBILITY.md
    python3 compatibility/build.py --check  # validate only (used on pull requests)

COMPATIBILITY.md is generated; change results.json instead.
"""
import json
import os
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
DATA = HERE / "results.json"
OUT = HERE.parent / "COMPATIBILITY.md"

CATEGORIES = {
    "aosp": ("Close to stock Android", "Stock or close to AOSP: no manufacturer skin."),
    "manufacturer-skin": ("Manufacturer skins", "A phone maker's version of Android, such as One UI, MIUI or HyperOS."),
    "emulator": ("Emulators", "Emulators that run the arm64 game through a translator (the native bridge)."),
    "bluestacks": ("BlueStacks", "Listed on its own: it runs the game through libhoudini like other emulators, but "
                                 "its anti-detection checks make it behave differently."),
}
BRIDGES = {"none": "none (native arm64)", "libhoudini": "libhoudini", "libndk_translation": "libndk_translation"}
RESULTS = {"works": "Works", "partial": "Partly works", "crashes": "Crashes"}
ID_RE = re.compile(r"^[a-z0-9][a-z0-9.-]{1,63}$")
VERSION_RE = re.compile(r"^[0-9]+(\.[0-9]+)*[a-z]?$")
ANDROID_RE = re.compile(r"^[0-9]{1,2}(\.[0-9])?$")
ALLOWED = {"id", "category", "android", "device", "bridge", "loader", "game", "result", "notes"}
REQUIRED = ["id", "category", "android", "loader", "game", "result"]
MAX_NOTES = 300

problems = []


def annotate(level, msg):
    print(f"{level}: {msg}")
    if os.environ.get("GITHUB_ACTIONS") == "true":
        print(f"::{level} file=compatibility/results.json::{msg}")


def err(where, msg):
    problems.append((where, msg))
    annotate("error", f"{where}: {msg}")


def check(results):
    seen = set()
    for i, r in enumerate(results):
        where = r.get("id", f"entry {i + 1}") if isinstance(r, dict) else f"entry {i + 1}"
        if not isinstance(r, dict):
            err(where, "must be an object")
            continue
        for k in sorted(set(r) - ALLOWED):
            err(where, f"unknown field '{k}'")
        for k in REQUIRED:
            if not isinstance(r.get(k), str) or not r[k]:
                err(where, f"'{k}' is required and must be text")
        if isinstance(r.get("id"), str):
            if not ID_RE.match(r["id"]):
                err(where, "id must be lowercase letters, digits, '.' and '-'")
            if r["id"] in seen:
                err(where, "duplicate id")
            seen.add(r["id"])
        cat = r.get("category")
        if cat not in CATEGORIES:
            err(where, f"category must be one of {sorted(CATEGORIES)}")
        if isinstance(r.get("android"), str) and not ANDROID_RE.match(r["android"]):
            err(where, "android must be a version such as 13 or 16")
        for k in ("loader", "game"):
            if isinstance(r.get(k), str) and not VERSION_RE.match(r[k]):
                err(where, f"{k} must be a version such as 0.0.4.2a")
        if r.get("result") not in RESULTS:
            err(where, f"result must be one of {sorted(RESULTS)}")
        if cat in ("emulator", "bluestacks"):
            if r.get("bridge") not in BRIDGES or r.get("bridge") == "none":
                err(where, "an emulator needs a bridge: libhoudini or libndk_translation")
        elif "bridge" in r and r["bridge"] != "none":
            err(where, "only emulators have a native bridge; a real device runs the game natively")
        if "notes" in r and (not isinstance(r["notes"], str) or len(r["notes"]) > MAX_NOTES):
            err(where, f"notes must be text of at most {MAX_NOTES} characters")


def version_key(v):
    m = re.match(r"^([0-9.]+)([a-z]?)$", v)
    return [int(x) for x in m.group(1).split(".")], m.group(2) or "~"


def cell(text):
    return (text or "").replace("|", "\\|").replace("\n", " ")


def render(results):
    lines = ["# Compatibility", "",
             "Which loader versions were tried on which Android versions and kinds of device, and how it went.",
             "This page is generated from [`compatibility/results.json`](compatibility/results.json); to add or "
             "correct a result, change that file (see [compatibility/README.md](compatibility/README.md)).", ""]
    for cat, (title, blurb) in CATEGORIES.items():
        rows = [r for r in results if r["category"] == cat]
        if not rows:
            continue
        rows.sort(key=lambda r: (-int(r["android"].split(".")[0]), r.get("bridge", ""),
                                 [-x for x in version_key(r["loader"])[0]]))
        emulated = cat in ("emulator", "bluestacks")
        lines += [f"## {title}", "", blurb, ""]
        head = ["Android", "Device"] + (["Native bridge"] if emulated else []) + ["Loader", "Game", "Result", "Notes"]
        lines += ["| " + " | ".join(head) + " |", "|" + "---|" * len(head)]
        for r in rows:
            row = [r["android"], r.get("device", "")]
            if emulated:
                row.append(BRIDGES[r["bridge"]])
            row += [r["loader"], r["game"], RESULTS[r["result"]], r.get("notes", "")]
            lines.append("| " + " | ".join(cell(c) for c in row) + " |")
        lines.append("")
    return "\n".join(lines).rstrip() + "\n"


def summary(rows):
    path = os.environ.get("GITHUB_STEP_SUMMARY")
    if not path:
        return
    with open(path, "a") as f:
        f.write("### Compatibility results\n\n")
        if not rows:
            f.write("All entries are valid.\n")
        else:
            f.write("| Entry | Problem |\n|---|---|\n")
            for where, msg in rows:
                f.write(f"| `{where}` | {msg.replace('|', chr(92) + '|')} |\n")


def main(argv):
    try:
        data = json.loads(DATA.read_text())
    except (OSError, json.JSONDecodeError) as e:
        err("results.json", f"cannot be read: {e}")
        summary(problems)
        sys.exit(1)
    if data.get("schemaVersion") != 1 or not isinstance(data.get("results"), list):
        err("results.json", "needs schemaVersion 1 and a 'results' list")
    else:
        check(data["results"])
    summary(problems)
    if problems:
        print(f"{len(problems)} problem(s)")
        sys.exit(1)
    if "--check" not in argv:
        OUT.write_text(render(data["results"]))
        print(f"wrote {OUT.name} ({len(data['results'])} results)")
    else:
        print(f"ok ({len(data['results'])} results)")


if __name__ == "__main__":
    main(sys.argv[1:])
