#!/usr/bin/env python3
"""Fail if a MusicXCST release jar contains native executables or FFmpeg binaries."""

from __future__ import annotations

import sys
import zipfile
from pathlib import Path


EXECUTABLE_SUFFIXES = (".exe", ".dll", ".so", ".dylib", ".bat", ".cmd", ".sh")


def forbidden_entries(jar: Path) -> list[str]:
    hits: list[str] = []
    with zipfile.ZipFile(jar) as zf:
        for info in zf.infolist():
            if info.is_dir():
                continue
            name = info.filename.replace("\\", "/")
            lower = name.lower()
            file_name = lower.rsplit("/", 1)[-1]
            if lower.endswith(EXECUTABLE_SUFFIXES):
                hits.append(name)
            elif lower.startswith(("native/ffmpeg/", "musicxcst/ffmpeg/")):
                hits.append(name)
            elif file_name in {"ffmpeg", "ffprobe"} and ("/native/" in lower or "/ffmpeg/" in lower):
                hits.append(name)
    return hits


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: scan_release_jar.py <jar>", file=sys.stderr)
        return 2
    jar = Path(sys.argv[1])
    if not jar.is_file():
        print(f"Jar not found: {jar}", file=sys.stderr)
        return 2
    hits = forbidden_entries(jar)
    if hits:
        print("Forbidden native executable/FFmpeg entries found:", file=sys.stderr)
        for hit in hits:
            print(f" - {hit}", file=sys.stderr)
        return 1
    print(f"OK: {jar} contains no bundled executable/native FFmpeg entries.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
