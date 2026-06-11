# Changelog

## 0.1.8

Release for Minecraft `26.1.2` on Fabric.

### Added

- Added high-resolution custom disc design support with new `MXC2` Design IDs for larger square canvases.
- Added editor feedback after successful import with a blocking success overlay, progress bar, and auto-close countdown.
- Added editor bootstrap loading so large custom designs reopen in the browser editor without hitting Windows URL length limits.
- Added drag-and-drop reference-image loading in the Reference tab of the web editor.

### Changed

- Custom disc data now stores design width, height, format version, and editor/import metadata instead of assuming every design is fixed `16x16`.
- CD Writer and custom item rendering now recognize larger design sizes, and the in-block preview can display normalized high-resolution designs.
- Final PNG export in the web editor now follows the actual exported design size instead of always forcing `16x16`.
- Blueprint CD renderer placement is now tunable per canvas size in code, making it easier to hand-adjust `16x16`, `32x32`, `64x64`, and `128x128` pixel scale/offset profiles.

### Fixed

- Fixed imported and editor-created custom disc designs falling back to inconsistent rendering paths.
- Fixed the redesigned browser editor failing to reopen large designs because the full Design ID was being passed through the browser launch URL.
- Fixed oversized `MXC2` Design IDs crashing item sync/world save NBT serialization by avoiding unsafe large Design ID strings in item stack data.
- Fixed CD Writer status checks showing the wrong “place a Blueprint CD” message when the real blocker was the occupied output slot.
- Fixed the Reference tab lacking the same image drag-and-drop workflow available in the import/convert area.

## 0.1.7

Release for Minecraft `26.1.2` on Fabric.

### Added

- Added colorful command help formatting so `/cstmusic help` and the admin help entries are easier to scan in chat without changing the command wording.

### Changed

- Styled more command output, warnings, and status updates through the shared chat feedback formatter, including upload start/finish messages and FFmpeg/admin status feedback.
- Upload actionbar progress now highlights the percentage and ETA with richer colors while keeping the existing wording.
- Added local fake-platform `runClient` helper scripts to `.gitignore`.

### Fixed

- Command errors triggered from `/cstmusic` subcommands now route through MusicXCST's styled error output instead of falling back to plainer raw exception text.

## 0.1.6

Release for Minecraft `26.1.2` on Fabric.

### Added

- Server is now operable without FFmpeg installed. When FFmpeg is not available the server accepts and operates on OGG Vorbis files only: duration probing, previews, and playback use a pure-Java OGG container probe and simple file-copy previews.

### Changed

- FFmpeg is now optional. Server-side transcoding will only run when an FFmpeg executable is available and configured. If server-side transcoding is requested but FFmpeg is missing, uploads requiring conversion are rejected with a clear message instructing to convert on the client or install FFmpeg.

### Fixed

- Added a robust OGG-only duration probe fallback to determine track duration when FFmpeg is absent.


## 0.1.5

Release for Minecraft `26.1.2` on Fabric.

### Fixed

- Fixed managed FFmpeg download using outdated SHA-256: The managed downloader was downloading the latest FFmpeg release but verifying it against a hardcoded old SHA value, causing verification failures. Now dynamically fetches the newest BtbN/FFmpeg-Builds release and verifies the archive using the checksum from that exact same release.
- Fixed managed FFmpeg detection after installation: Installed FFmpeg was extracted to a `bin/` subdirectory containing ffmpeg.exe and all required runtime DLLs (avcodec, swscale, etc.), but the detection logic was looking for ffmpeg.exe in the root directory. This caused the CD Writer to always show the FFmpeg setup screen even after successful installation. Now correctly detects managed FFmpeg in the `bin/` subdirectory.

### Changed

- Improved managed FFmpeg installer: Extracts complete `bin/` directory with all runtime dependencies instead of just the executable, ensuring FFmpeg runs without missing DLL errors.
- Managed FFmpeg now queries GitHub API for latest verified release instead of using a hardcoded moving URL.
- Enhanced connection handling with timeouts and proper resource cleanup.
- SHA-256 verification remains mandatory and is now tied to the actual downloaded release, not a stale hardcoded value.

## 0.1.4

Release for Minecraft `26.1.2` on Fabric.

### Changed

- `/cstmusic storage` now shows whether the per-player music file limit is enabled, the active file count, and the configured limit mode.
- Per-player music file limits now count active tracks only, so missing, invalid, or deleted entries no longer block new uploads.
- `/cstmusic list` now shows only currently available music.
- Player music commands now use uploaded file names where possible instead of forcing players to copy music IDs.
- Command output is now color-coded for easier reading.
- CD Writer uploads now keep the original imported file name for uploaded music suggestions.

### Fixed

- Fixed `/cstmusic info` and `/cstmusic delete` without a music ID showing Minecraft's raw incomplete-command error instead of useful usage and the player's music list.
- Fixed the CD Writer so previously uploaded music can be selected from suggestions and written again without re-uploading it.
- Fixed uploaded music suggestions to show up to 6 matching files.
- Fixed deleting music so it removes the stored playback files and the reusable uploaded file.
- Fixed delete messages and suggestions so they show uploaded names instead of player UUID folders or raw IDs.
- Fixed deleting duplicate uploaded names by selecting the newest matching active entry instead of failing.
- Fixed uploaded-disc creation creating an extra duplicate file in the `created` folder.
- Fixed invalid Blueprint CDs rendering with extra custom color or texture overlays.

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
