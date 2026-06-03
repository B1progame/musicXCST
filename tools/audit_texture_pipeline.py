#!/usr/bin/env python3
"""Audit the MusicXCST custom disc texture pipeline.

This is a static diagnostic helper. It does not modify project files.
"""

from __future__ import annotations

import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def read(rel: str) -> str:
    path = ROOT / rel
    return path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""


def find_all(pattern: str) -> list[str]:
    hits: list[str] = []
    regex = re.compile(pattern)
    for path in ROOT.rglob("*"):
        if not path.is_file() or ".gradle" in path.parts or "build" in path.parts:
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        if regex.search(text):
            hits.append(str(path.relative_to(ROOT)))
    return hits


def section(title: str) -> None:
    print(f"\n== {title} ==")


def yes_no(value: bool) -> str:
    return "YES" if value else "NO"


def main() -> None:
    disc_data = read("src/main/java/de/coulees/B1progame/musicxcst/data/DiscData.java")
    payload = read("src/main/java/de/coulees/B1progame/musicxcst/network/CdWriterWritePayload.java")
    renderer = read("src/client/java/de/coulees/B1progame/musicxcst/client/render/CustomDiscRenderCache.java")
    decoration = read("src/client/java/de/coulees/B1progame/musicxcst/client/render/CustomDiscItemDecoration.java")
    item_json = read("src/main/resources/assets/musicxcst/items/blueprint_cd.json")
    model_json = read("src/main/resources/assets/musicxcst/models/item/blueprint_cd.json")
    entry = read("src/main/java/de/coulees/B1progame/musicxcst/data/MusicEntry.java")
    menu = read("src/main/java/de/coulees/B1progame/musicxcst/menu/CdWriterMenu.java")
    screen = read("src/client/java/de/coulees/B1progame/musicxcst/client/screen/CdWriterScreen.java")
    html = read("src/main/resources/assets/musicxcst/editor/disc_texture_editor.html")

    section("designPixels writes")
    for path in find_all(r"designPixels\s*=|putIntArray\(\"designPixels\"|writeDesignPixels"):
        print(path)

    section("designPixels reads")
    for path in find_all(r"designPixels\)|designPixels\.|getIntArray\(\"designPixels\"|fromStack\(.*design"):
        print(path)

    section("networking")
    print(f"CdWriterWritePayload has int[] designPixels: {yes_no('int[] designPixels' in payload)}")
    print(f"Payload writes 256 pixels: {yes_no('writeDesignPixels' in payload and 'DESIGN_PIXELS' in payload)}")
    print(f"Payload sanitizes pixels: {yes_no('sanitizeDesign' in payload)}")

    section("client renderer")
    print(f"CustomDiscItemDecoration.register() empty: {yes_no('public static void register()' in decoration and 'Custom disc pixels are rendered through ItemStackRenderState' in decoration)}")
    print(f"Creates DynamicTexture/NativeImage: {yes_no('DynamicTexture' in renderer or 'NativeImage' in renderer)}")
    print(f"Appends ItemStackRenderState layer: {yes_no('newLayer()' in renderer and 'prepareQuadList()' in renderer)}")
    print(f"Reads DiscData.fromStack in renderer: {yes_no('DiscData.fromStack(stack)' in renderer)}")
    print(f"Renders per pixel quads: {yes_no('pixelQuad' in renderer and 'DESIGN_SIZE' in renderer)}")
    print(f"Uses tint layers for pixel colors: {yes_no('tintLayers()' in renderer)}")

    section("item model JSON")
    item_static = '"model"' in item_json and "blueprint_cd" in item_json and "special" not in item_json
    model_static = '"layer0"' in model_json and "custom" not in model_json
    print(f"Item declaration static model only: {yes_no(item_static)}")
    print(f"Model static layer0 only: {yes_no(model_static)}")

    section("music index persistence")
    print(f"MusicEntry has designId/designPixels field: {yes_no('designId' in entry or 'designPixels' in entry)}")
    print(f"DiscData.fromEntry resets to default design: {yes_no('data.designPixels = defaultDesign()' in disc_data)}")

    section("CD writer menu sync")
    print(f"moveInputToOutput exists: {yes_no('void moveInputToOutput()' in menu)}")
    print(f"moveInputToOutput calls setChanged: {yes_no('moveInputToOutput' in menu and 'setChanged()' in menu)}")
    print(f"moveInputToOutput calls broadcastChanges: {yes_no('moveInputToOutput' in menu and 'broadcastChanges()' in menu)}")

    section("HTML editor callback")
    uses_callback_port = 'callbackPort=params.get("port")' in html
    print(f"Has MXC1 encoder/decoder: {yes_no('MXC1:' in html and 'decodeDesignId' in html and 'encodeDesignId' in html)}")
    print(f"Apply theme mutates pixels: {yes_no('function applyTheme' in html and 'pixels' in html[html.find('function applyTheme'):html.find('function applyTheme') + 2000])}")
    print(f"Finish posts to /finish: {yes_no('/finish' in html and 'fetch(' in html)}")
    print(f"Finish sends token/design: {yes_no('token' in html and 'design' in html and 'URLSearchParams' in html)}")
    print(f"Uses callback port query parameter: {yes_no(uses_callback_port)}")
    print(f"Has visible callback status/debug: {yes_no('callbackStatus' in html or 'Last callback' in html or 'debugInfo' in html or 'lastCallbackStatus' in html)}")

    section("screen data flow")
    starts_callback_server = "HttpServer.create" in screen and 'createContext("/finish"' in screen
    print(f"openAdvancedEditor starts localhost callback server: {yes_no(starts_callback_server)}")
    print(f"handleEditorFinish decodes Design ID: {yes_no('handleEditorFinish' in screen and 'DiscData.decodeDesignId' in screen)}")
    print(f"designForWrite returns discPixels: {yes_no('int[] designForWrite()' in screen and 'sanitizeDesign(discPixels)' in screen)}")
    print(f"write sends designForWrite: {yes_no('new CdWriterWritePayload' in screen and 'designForWrite()' in screen)}")


if __name__ == "__main__":
    main()
