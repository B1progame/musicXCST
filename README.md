# MusicXCST

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/musicxcst?logo=modrinth&label=Modrinth%20Downloads)](https://modrinth.com/mod/musicxcst)
[![CurseForge Downloads](https://cf.way2muchnoise.eu/full_1563663_downloads.svg)](https://www.curseforge.com/minecraft/mc-mods/musicxcst)
[![GitHub Source](https://img.shields.io/badge/GitHub-Source-181717?logo=github)](https://github.com/B1progame/musicXCST)

MusicXCST is a Fabric mod for Minecraft `26.1.2` that adds writable custom music discs. Players can turn local audio files into Blueprint CDs, customize the disc artwork, and play the finished discs through jukeboxes without rebuilding resource packs or registering a new sound event for every song.

The mod is built for singleplayer worlds, LAN worlds, multiplayer servers, modpacks, and creative worlds that want custom music with validation, storage limits, and client-side audio caching.

## Highlights

- Writable Blueprint CDs using the CD Writer block.
- Custom disc names, colors, and pixel-art disc designs.
- Local file upload workflow from the CD Writer GUI.
- Supported input formats: `mp3`, `mp4`, `wav`, `ogg`, `flac`, `m4a`, `aac`, `webm`, and `avi`.
- Normalized OGG Vorbis storage for consistent playback.
- Jukebox playback through MusicXCST's custom audio engine.
- Client audio cache with checksum validation and optional pre-download commands.
- Server-side file size, duration, per-player file count, and storage quota controls.
- Admin commands for importing, inspecting, deleting, repairing, and test-playing tracks.
- No generated resource packs and no permanent registration of thousands of item textures.

## Requirements

### Required On Servers And Clients

- Minecraft `26.1.2`
- Fabric Loader `0.19.2` or newer
- Fabric API for `26.1.2`
- GeckoLib `5.5.1` or newer

### FFmpeg

MusicXCST uses FFmpeg for audio probing and transcoding, but the public mod jar does not bundle FFmpeg binaries. This avoids shipping third-party native executables in CurseForge/Modrinth release jars and keeps FFmpeg installation under the user or server owner's control.

The default config is:

```text
ffmpegMode = system
```

Supported modes:

```text
system   Use ffmpeg from PATH.
path     Use the executable configured in ffmpegPath.
managed  Use a user-approved, verified download stored outside the jar.
disabled Do not use FFmpeg; already-compatible OGG files may still work.
```

Client managed files are stored under:

```text
<minecraft directory>/config/musicxcst/ffmpeg/managed/<platform>/
```

Server managed files are stored under the server directory at the same relative path. The mod never writes FFmpeg into the jar or system folders.

On the Minecraft title screen, the client shows a top-right MusicXCST banner when MusicXCST does not have its own managed FFmpeg installed, even if system FFmpeg exists on PATH. Click the banner to open setup. The setup screen also appears when a non-OGG file needs conversion and FFmpeg is missing. It offers **Download FFmpeg**, **Use system FFmpeg**, **Choose FFmpeg path**, **Use OGG only**, and **Cancel**. No executable is downloaded unless the user clicks **Download FFmpeg**.

Managed download currently supports Windows x86_64 and Linux x86_64/aarch64 using BtbN LGPL FFmpeg builds verified against the checksum published with the selected release. Other platforms should install FFmpeg manually and use `system` or `path`.

## Installation

1. Install Fabric Loader for Minecraft `26.1.2`.
2. Install Fabric API and GeckoLib.
3. Put the MusicXCST jar in the `mods` folder on the server and on every client.
4. Start the game/server once to generate `config/musicxcst.json`.
5. Review server limits, FFmpeg settings, and storage settings before opening a public server.

For modpacks, use the same MusicXCST jar and dependency versions on both sides where redistribution is permitted by the project license or written permission. The mod has client-only rendering/audio code, but the jar is intended to be installed on both client and server.

## Basic Usage

1. Craft or obtain a blank Blueprint CD.
2. Place a CD Writer block.
3. Put the blank Blueprint CD in the CD Writer input slot.
4. Enter a disc name.
5. Choose a local audio file with the file button.
6. Edit the disc color or texture.
7. Press **Print** and keep the GUI open until upload/conversion finishes.
8. Take the written Blueprint CD from the output slot.
9. Play it in a jukebox.

Useful player commands:

```text
/cstmusic help
/cstmusic list
/cstmusic info <musicId>
/cstmusic delete <musicId>
/cstmusic storage
/cstmusic download all
/cstmusic download auto <30m|1h|1h30m>
/cstmusic download off
```

Admin-only commands:

```text
/cstmusic upload <name> <localFilePath>
/cstmusic createupload <name> <hexColor> <uploadedFile>
/cstmusic create <name> <hexColor> <serverFilePath>
/cstmusic admin storage
/cstmusic admin list [page]
/cstmusic admin info <musicId>
/cstmusic admin delete <musicId>
/cstmusic admin play <musicId>
/cstmusic admin ffmpeg status
/cstmusic admin ffmpeg path <path>
/cstmusic admin ffmpeg download confirm
/cstmusic admin ffmpeg reset
/cstmusic admin reload
/cstmusic admin repairindex
```

## Server Configuration

Important keys in `config/musicxcst.json`:

```text
maxFileSizeBytes
maxStoragePerPlayerBytes
maxTotalServerStorageBytes
allowedFileExtensions
ffmpegMode
ffmpegPath
ffmpegManagedDownloadAllowed
ffmpegManagedSourceUrl
ffmpegManagedVersion
ffmpegManagedSha256
allowServerSideTranscoding
audioBitrate
sampleRate
maxMusicDurationEnabled
maxMusicDurationSeconds
maxMusicFilesPerPlayerEnabled
maxMusicFilesPerPlayer
playerLimitMode
clientUploadBytesPerSecond
playbackRadiusBlocks
clientCacheSizeBytes
allowSingleplayerAbsolutePaths
allowAdminAbsoluteServerPaths
softDeleteEnabled
allowFoundDiscsPlayback
ownerOnlyPlayback
adminBypass
```

Duration limit examples:

```text
150 seconds  = 2:30
600 seconds  = 10:00
3600 seconds = 1:00:00
```

Set `maxMusicDurationEnabled` to `false` for no duration limit.

Per-player file limit modes:

```text
confirm_delete_oldest
auto_delete_oldest
block_new_upload
```

Set `maxMusicFilesPerPlayerEnabled` to `true` before `maxMusicFilesPerPlayer` is enforced.

## Storage And Security

MusicXCST stores audio metadata and normalized playback files on the server. Default storage locations are:

```text
<world>/music-import/
<world>/music-normalized/
<world>/data/musicxcst/music-index.json
```

Client downloads are cached locally in:

```text
<minecraft directory>/musicxcst-cache/
```

Security and safety behavior:

- Players can manage only music entries they own.
- Admin commands require server admin permission level.
- The server validates extensions, file size, duration, quotas, checksums, and safe paths.
- Dedicated servers cannot read arbitrary files from a player's computer through commands.
- Absolute server paths are disabled by default on dedicated servers.
- Audio chunks sent to clients are bounded and verified with SHA-256.
- Users and server operators are responsible for uploaded audio rights.

## FFmpeg And Media Notes

MusicXCST invokes FFmpeg as a separate executable process. It does not link FFmpeg libraries into the mod jar.

MusicXCST public release jars do not contain FFmpeg `.exe` files, native libraries, or hidden binary archives. Build checks fail if the release jar contains executable/native FFmpeg entries.

Manual install examples:

```powershell
winget install BtbN.FFmpeg.LGPL
winget install Gyan.FFmpeg
```

```bash
sudo apt install ffmpeg
sudo dnf install ffmpeg
brew install ffmpeg
```

Managed download rules:

- HTTPS only.
- Exact SHA-256 verification before extraction.
- Extract only the archive `bin/` folder needed by the selected `ffmpeg` executable.
- Reject builds reporting `--enable-nonfree`.
- Save source URL, version, SHA-256, and license metadata in the managed folder.
- Never run downloaded FFmpeg until the archive hash has passed.

See:

- `THIRD_PARTY_NOTICES.md`
- `licenses/ffmpeg/README.md`

## For Modpack Creators

- Confirm redistribution permission before publishing a pack that includes the MusicXCST jar.
- Use MusicXCST on both client and server.
- Include Fabric API and GeckoLib.
- Do not include FFmpeg executables in the MusicXCST jar. Expect system/path FFmpeg or the explicit managed setup flow.
- Review server defaults before publishing a public pack.
- Do not include copyrighted music unless the pack has permission to distribute it.
- Keep the license and third-party notice files with the mod distribution.

## Known Limitations

- MusicXCST targets Minecraft `26.1.2`; other versions are not guaranteed.
- FFmpeg availability depends on system installation, explicit path config, or user-approved managed setup.
- Very large libraries can consume server disk space quickly without quotas.
- Playback uses a custom client audio path, so every listener needs the mod installed.
- Network latency and cache misses can delay full-track playback while audio chunks download.
- Custom disc designs fall back to the Blueprint CD appearance if item design data is missing or corrupt.

## Roadmap

Planned or likely future improvements:

- More polished CD Writer and jukebox UX.
- Better continuous range tracking for listeners entering or leaving playback radius.
- Stronger audio preview and seek handling for long tracks.
- Expanded server moderation tools for public music libraries.
- More robust custom disc texture tooling and import/export workflows.
- Optional integration points for permission mods or server dashboards.

## Credits

- Created by B1progame.
- Built for Fabric with Fabric API.
- Uses GeckoLib for animated block rendering.
- Uses FFmpeg as an optional external media executable.
- Uses Minecraft client audio facilities plus STB Vorbis/OpenAL for custom playback.

## License

MusicXCST source code, artwork, models, sounds, and original project files are all rights reserved unless a file states otherwise. See `LICENSE`.

MusicXCST does not bundle FFmpeg executables in the public release jar. FFmpeg, when installed separately or through managed setup, is covered by its own upstream licenses. See `THIRD_PARTY_NOTICES.md` and `licenses/ffmpeg/`.
