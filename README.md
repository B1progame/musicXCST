# MusicXCST

MusicXCST is a Fabric mod for Minecraft `26.1.2` that adds writable custom music discs. Players can turn local audio files into Blueprint CDs, customize the disc artwork, and play the finished discs through jukeboxes without rebuilding resource packs or registering a new sound event for every song.

The mod is built for multiplayer servers, modpacks, and creative worlds that want custom music with server-side validation, storage limits, and client-side audio caching.

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

MusicXCST uses FFmpeg for audio probing and transcoding. The default config is:

```text
ffmpegMode = bundled
```

When a bundled binary is available for the platform, MusicXCST extracts it to:

```text
config/musicxcst/native/ffmpeg/<platform>/
```

Supported bundled resource locations are:

```text
native/ffmpeg/windows-x86_64/ffmpeg.exe
native/ffmpeg/linux-x86_64/ffmpeg
native/ffmpeg/linux-aarch64/ffmpeg
native/ffmpeg/macos-x86_64/ffmpeg
native/ffmpeg/macos-aarch64/ffmpeg
```

If no bundled binary is present, set `ffmpegMode` to `system` or `path`, or upload already-normalized `.ogg` files where possible.

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

Packagers should verify the exact FFmpeg build before distributing a bundled jar:

- Prefer stable LGPL builds.
- Do not distribute builds configured with `--enable-nonfree`.
- Avoid GPL builds unless the pack intentionally accepts GPL distribution obligations.
- Include FFmpeg license files, source/build URLs, configure flags, and checksums in `licenses/ffmpeg/`.

See:

- `THIRD_PARTY_NOTICES.md`
- `licenses/ffmpeg/README.md`
- `src/main/resources/musicxcst/ffmpeg/README.md`

## For Modpack Creators

- Confirm redistribution permission before publishing a pack that includes the MusicXCST jar.
- Use MusicXCST on both client and server.
- Include Fabric API and GeckoLib.
- Decide whether the pack ships bundled FFmpeg binaries or expects system/path FFmpeg.
- Review server defaults before publishing a public pack.
- Do not include copyrighted music unless the pack has permission to distribute it.
- Keep the license and third-party notice files with the mod distribution.

## Known Limitations

- MusicXCST targets Minecraft `26.1.2`; other versions are not guaranteed.
- FFmpeg availability depends on bundled binaries, system installation, or explicit config.
- Very large libraries can consume server disk space quickly without quotas.
- Playback uses a custom client audio path, so every listener needs the mod installed.
- Network latency and cache misses can delay full-track playback while audio chunks download.
- Advanced custom disc texture rendering is client-side and falls back to the Blueprint CD if item data is missing or corrupt.

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
- Uses FFmpeg as an optional/bundled external media executable.
- Uses Minecraft client audio facilities plus STB Vorbis/OpenAL for custom playback.

## License

MusicXCST source code, artwork, models, sounds, and original project files are all rights reserved unless a file states otherwise. See `LICENSE`.

Bundled third-party executables, including FFmpeg binaries, are covered by their own upstream licenses. See `THIRD_PARTY_NOTICES.md` and `licenses/ffmpeg/`.
