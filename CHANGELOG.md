# Changelog

## 0.1.4

Release for Minecraft `26.1.2` on Fabric.

### Changed

- `/cstmusic storage` now shows whether the per-player music file limit is enabled, the active file count, and the configured limit mode.
- Per-player music file limits now count active tracks only, so missing, invalid, or deleted entries no longer block new uploads.

### Fixed

- Fixed `/cstmusic info` and `/cstmusic delete` without a music ID showing Minecraft's raw incomplete-command error instead of useful usage and the player's music list.

## 0.1.3

Release for Minecraft `26.1.2` on Fabric.

### Added

- Added a server-backed FFmpeg setup request for the dedicated setup GUI, so the GUI download uses the same managed FFmpeg install logic as the admin command.
- Added FFmpeg setup GUI progress details including download percentage, received size, speed, ETA, and the current install step.
- Added per-jukebox volume control support for normal Minecraft music discs as well as MusicXCST Blueprint CDs.
- Added loop support for normal Minecraft music discs through the MusicXCST jukebox controls.

### Changed

- The FFmpeg setup GUI now keeps server download status across screen reopenings instead of tying the task to the temporary screen.
- The title-screen FFmpeg setup notice now remains available until the player explicitly acknowledges a setup option, even if system FFmpeg exists locally.
- The advanced disc texture editor now opens from Minecraft through a local `127.0.0.1` editor page instead of a `file://` page, making the browser-to-Minecraft import callback more reliable.

### Fixed

- Fixed the dedicated FFmpeg setup GUI downloading/configuring FFmpeg only on the client instead of configuring the server.
- Fixed managed FFmpeg setup progress/result messages being unavailable to the GUI.
- Fixed normal Minecraft discs being blocked from the MusicXCST jukebox controls.
- Fixed normal Minecraft disc volume changes not applying through the MusicXCST jukebox slider.
- Fixed MusicXCST jukebox loop and volume settings not being saved across server reloads.
- Fixed the CD Writer texture preview not loading the stored design from an inserted written Blueprint CD.
- Removed a noisy Blueprint CD renderer bounds log line from normal runtime output.
- Removed the crashing sound interface mixin and replaced it with a sound-engine volume hook.

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
