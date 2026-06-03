#!/usr/bin/env python3
"""Diagnostic tests for MusicXCST Design ID formats."""

from __future__ import annotations

import base64
import struct


SIZE = 16
PIXELS = SIZE * SIZE
PREFIX = "MXC1:"
BASE64_PREFIX = "MXCST1."
LEGACY_PREFIX = "MXC16."
CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_"


def sanitize_pixel(pixel: int) -> int:
    return 0 if (pixel >> 24) == 0 else 0xFF000000 | (pixel & 0x00FFFFFF)


def sanitize(pixels: list[int]) -> list[int]:
    return [sanitize_pixel(p) for p in pixels] if len(pixels) == PIXELS else default_design()


def default_design() -> list[int]:
    return [0xFF616161] * PIXELS


def encode_palette(pixels: list[int]) -> str:
    pixels = sanitize(pixels)
    palette: list[int] = []
    body: list[str] = []
    for pixel in pixels:
        if (pixel >> 24) == 0:
            body.append(".")
            continue
        rgb = pixel & 0x00FFFFFF
        if rgb not in palette:
            if len(palette) >= len(CHARS):
                return encode_base64(pixels)
            palette.append(rgb)
        body.append(CHARS[palette.index(rgb)])
    return PREFIX + ",".join(f"{c:06X}" for c in palette) + ";" + "".join(body)


def encode_base64(pixels: list[int]) -> str:
    payload = bytes([SIZE]) + b"".join(struct.pack(">I", p) for p in sanitize(pixels))
    return BASE64_PREFIX + base64.urlsafe_b64encode(payload).decode("ascii").rstrip("=")


def encode_legacy(pixels: list[int]) -> str:
    payload = b"".join(struct.pack(">I", p) for p in sanitize(pixels))
    return LEGACY_PREFIX + base64.urlsafe_b64encode(payload).decode("ascii").rstrip("=")


def decode(design_id: str) -> list[int] | None:
    design_id = design_id.strip()
    if design_id.startswith(PREFIX):
        sep = design_id.find(";", len(PREFIX))
        if sep < 0:
            return None
        palette_text = design_id[len(PREFIX):sep]
        body = design_id[sep + 1:]
        if len(body) != PIXELS:
            return None
        colors = [] if not palette_text else [int(part, 16) for part in palette_text.split(",")]
        pixels = []
        for code in body:
            if code in ". ":
                pixels.append(0)
            else:
                index = CHARS.find(code)
                if index < 0 or index >= len(colors):
                    return None
                pixels.append(0xFF000000 | colors[index])
        return pixels
    if design_id.startswith(BASE64_PREFIX):
        raw = padded_b64decode(design_id[len(BASE64_PREFIX):])
        if len(raw) != 1 + PIXELS * 4 or raw[0] != SIZE:
            return None
        return [sanitize_pixel(struct.unpack_from(">I", raw, 1 + i * 4)[0]) for i in range(PIXELS)]
    if design_id.startswith(LEGACY_PREFIX):
        raw = padded_b64decode(design_id[len(LEGACY_PREFIX):])
        if len(raw) != PIXELS * 4:
            return None
        return [sanitize_pixel(struct.unpack_from(">I", raw, i * 4)[0]) for i in range(PIXELS)]
    return None


def padded_b64decode(text: str) -> bytes:
    return base64.urlsafe_b64decode(text + "=" * (-len(text) % 4))


def diagonal() -> list[int]:
    return [0xFFFF0000 if x >= y else 0xFF005DFF for y in range(SIZE) for x in range(SIZE)]


def transparent_border() -> list[int]:
    return [0 if x in (0, 15) or y in (0, 15) else 0xFF22CC66 for y in range(SIZE) for x in range(SIZE)]


def many_color() -> list[int]:
    return [0xFF000000 | ((i * 65793) & 0x00FFFFFF) for i in range(PIXELS)]


def assert_round_trip(name: str, pixels: list[int], encoder) -> None:
    expected = sanitize(pixels)
    encoded = encoder(expected)
    decoded = decode(encoded)
    if decoded != expected:
        raise AssertionError(f"{name} failed round trip")
    print(f"ok {name}: len={len(encoded)} opaque={sum(1 for p in expected if p)}")


def main() -> None:
    assert_round_trip("palette diagonal", diagonal(), encode_palette)
    assert_round_trip("palette transparent border", transparent_border(), encode_palette)
    assert_round_trip("base64 many color fallback", many_color(), encode_palette)
    assert_round_trip("explicit base64", diagonal(), encode_base64)
    assert_round_trip("legacy", diagonal(), encode_legacy)
    if decode("MXC1:FF0000;bad") is not None:
        raise AssertionError("invalid short palette body decoded")
    print("all design id diagnostics passed")


if __name__ == "__main__":
    main()
