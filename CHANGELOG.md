# Changelog

## 0.1.2

Release for Minecraft `26.1.2` on Fabric.

- Removed bundled FFmpeg executables from the mod jar.
- Added explicit FFmpeg setup options: system, path, managed download, or OGG-only mode.
- Added a title-screen FFmpeg setup notice and server admin FFmpeg commands.
- Added release jar checks to prevent native executable binaries from being packaged.
- Fixed FFmpeg setup prompts so valid system/path configs stop asking.

## 0.1.1

Release for Minecraft `26.1.2` on Fabric.

- Added the advanced custom disc texture editor workflow.
- Fixed custom disc designs so they render correctly on written Blueprint CDs.

## 0.1.0

Initial public development release for Minecraft `26.1.2` on Fabric.

- Writable Blueprint CDs.
- CD Writer block and GUI.
- Local client upload/conversion workflow.
- Custom disc names, colors, and 16x16 design data.
- Jukebox playback through MusicXCST's custom audio engine.
- Server music index with ownership metadata.
- Normalized OGG Vorbis storage.
- Client audio cache with SHA-256 validation.
- Admin commands for importing, inspecting, deleting, playing, reloading, and repairing entries.
- Configurable upload, storage, duration, and per-player file limits.
- Optional system/path FFmpeg support.
