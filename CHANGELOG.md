# Changelog

## 0.1.1b

Patch release for Minecraft `26.1.2` on Fabric.

### Changed

- The client FFmpeg title-screen notice now respects a working configured `path` or `system` FFmpeg setup.
- The setup screen disables **Use system FFmpeg** when no PATH FFmpeg is detected.

## 0.1.1

Release for Minecraft `26.1.2` on Fabric.

### Security / Distribution

- Removed bundled FFmpeg executables from the public release jar.
- Added system/path/managed/disabled FFmpeg modes with old `bundled` configs migrated to `system`.
- Added explicit client/admin FFmpeg setup flows and release jar checks that fail on bundled native executables.

### Fixed

- Fixed the advanced disc texture editor workflow so custom disc designs render correctly on written Blueprint CDs.

### Changed

- Bumped the mod version to `0.1.1`.

## 0.1.0

Initial public development release for Minecraft `26.1.2` on Fabric.

### Added

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

### Notes

- MusicXCST requires the mod on both client and server.
- FFmpeg binaries are not included in the public mod jar; use system/path setup or explicit managed setup where supported.
- Users and server operators are responsible for uploaded audio rights.
