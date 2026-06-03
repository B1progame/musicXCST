#!/usr/bin/env python3
"""Generate obvious 16x16 test designs in MusicXCST Design ID formats."""

from __future__ import annotations

import base64
import struct


SIZE = 16
PIXELS = SIZE * SIZE
PREFIX = "MXC1:"
BASE64_PREFIX = "MXCST1."
PALETTE = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz-_"


def sanitize(pixel: int) -> int:
    return 0 if (pixel >> 24) == 0 else 0xFF000000 | (pixel & 0x00FFFFFF)


def encode(pixels: list[int]) -> str:
    sanitized = [sanitize(p) for p in pixels]
    colors: list[int] = []
    body: list[str] = []
    for pixel in sanitized:
        if (pixel >> 24) == 0:
            body.append(".")
            continue
        rgb = pixel & 0x00FFFFFF
        if rgb not in colors:
            if len(colors) >= len(PALETTE):
                payload = bytes([SIZE]) + b"".join(struct.pack(">I", p) for p in sanitized)
                return BASE64_PREFIX + base64.urlsafe_b64encode(payload).decode("ascii").rstrip("=")
            colors.append(rgb)
        body.append(PALETTE[colors.index(rgb)])
    return PREFIX + ",".join(f"{c:06X}" for c in colors) + ";" + "".join(body)


def average(pixels: list[int]) -> int | None:
    opaque = [p for p in map(sanitize, pixels) if (p >> 24) != 0]
    if not opaque:
        return None
    r = sum((p >> 16) & 0xFF for p in opaque) // len(opaque)
    g = sum((p >> 8) & 0xFF for p in opaque) // len(opaque)
    b = sum(p & 0xFF for p in opaque) // len(opaque)
    return (r << 16) | (g << 8) | b


def literal(pixels: list[int]) -> str:
    chunks = []
    for y in range(SIZE):
        row = ", ".join(f"0x{sanitize(pixels[y * SIZE + x]):08X}" for x in range(SIZE))
        chunks.append("    " + row)
    return "new int[] {\n" + ",\n".join(chunks) + "\n};"


def diagonal() -> list[int]:
    return [0xFFFF0000 if x >= y else 0xFF005DFF for y in range(SIZE) for x in range(SIZE)]


def checker() -> list[int]:
    return [0xFFFFFFFF if (x + y) % 2 == 0 else 0xFF000000 for y in range(SIZE) for x in range(SIZE)]


def transparent_border() -> list[int]:
    return [0 if x in (0, 15) or y in (0, 15) else 0xFF22CC66 for y in range(SIZE) for x in range(SIZE)]


def solid() -> list[int]:
    return [0xFFAA33CC] * PIXELS


def many_color() -> list[int]:
    return [0xFF000000 | ((i * 65793) & 0x00FFFFFF) for i in range(PIXELS)]


def main() -> None:
    designs = {
        "red_blue_diagonal": diagonal(),
        "checkerboard": checker(),
        "transparent_border_colored_center": transparent_border(),
        "all_one_color": solid(),
        "many_color_palette_fallback": many_color(),
    }
    for name, pixels in designs.items():
        avg = average(pixels)
        opaque = sum(1 for p in pixels if (sanitize(p) >> 24) != 0)
        design_id = encode(pixels)
        print(f"\n== {name} ==")
        print(f"opaque_pixels={opaque}")
        print(f"expected_average_color={'none' if avg is None else f'#{avg:06X}'}")
        print(f"design_id={design_id}")
        print("java_literal=")
        print(literal(pixels))


if __name__ == "__main__":
    main()
