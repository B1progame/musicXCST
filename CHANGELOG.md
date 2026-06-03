# Changelog

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
- Optional bundled/system/path FFmpeg support.

### Notes

- MusicXCST requires the mod on both client and server.
- FFmpeg packaging and third-party notices must be reviewed before distributing bundled binaries.
- Users and server operators are responsible for uploaded audio rights.
