# musicXCST

`musicXCST` is a Fabric mod for Minecraft `26.1.2` that adds writable custom music CDs. The current version is command-based: players craft a blank `Blueprint CD`, import a server-safe music file with `/cstmusic`, and play the written disc through the mod's custom audio backend.

## Current Scope

Implemented now:

- blank and written `Blueprint CD` item metadata
- crafting recipe using iron nuggets and a blue stained glass pane
- `/cstmusic` player and admin commands
- server metadata index with ownership, status, quotas, hashes, and safe relative paths
- FFmpeg-backed normalization to OGG Vorbis for real songs, speech, vocals, and long audio
- client chunk download, SHA-256 validation, cache, STB Vorbis decode, and OpenAL playback
- jukebox insertion/ejection hooks for custom CDs
- invalid/deleted disc state

Not implemented yet:

- CD Writer block behavior
- CD Writer GUI
- client upload/file picker workflow
- exact late-join seek into already-playing songs
- bundled audio codec binaries

## Commands

All commands use one root:

```text
/cstmusic
```

Player commands:

- `/cstmusic help`
- `/cstmusic create <name> <hexColor> <location>`
- `/cstmusic list`
- `/cstmusic info <musicId>`
- `/cstmusic delete <musicId>`
- `/cstmusic storage`
- `/cstmusic download all`
- `/cstmusic download auto <30m|1h|1h30m>`
- `/cstmusic download off`

Admin commands:

- `/cstmusic admin storage`
- `/cstmusic admin list [page]`
- `/cstmusic admin info <musicId>`
- `/cstmusic admin delete <musicId>`
- `/cstmusic admin play <musicId>`
- `/cstmusic admin reload`
- `/cstmusic admin repairindex`

## Creating a CD

1. Craft a blank `Blueprint CD`.
2. Hold it in the selected hotbar slot.
3. Put the audio file in the configured server import folder, or use an absolute path only in allowed singleplayer/admin cases.
4. Run:

```text
/cstmusic create "Song Name" #00AAFF folder/song.mp3
```

In singleplayer, quoted Windows paths are supported when `allowSingleplayerAbsolutePaths` is enabled:

```text
/cstmusic create "Song Name" #4d1111 "C:\Users\Name\Music\song.mp3"
```

Dedicated servers cannot read a friend's private computer path from chat. The file must exist on the server import folder, or a later CD Writer upload flow must send it to the server.

## Audio Engine

The mod does not create one Minecraft sound event per uploaded song and does not require resource pack rebuilds. Imported audio is normalized server-side into OGG Vorbis, stored under the world audio storage folder, and sent to clients on demand in chunks.

Playback flow:

1. A jukebox or admin command starts a playback session.
2. The server sends metadata: music ID, checksum, size, preview checksum, source position, radius, and start time.
3. The client checks `musicxcst-cache`.
4. If the full song is not cached yet, the client can immediately play the cached preview while downloading the full song.
5. Missing audio is requested from the server in chunks. Playback requests start with a small first chunk, then continue with larger chunks for better responsiveness.
6. The client verifies SHA-256 before playback.
7. The client decodes OGG with STB Vorbis and plays through OpenAL.

Players can run `/cstmusic download all` to pre-cache every active server song. `/cstmusic download auto 30m`, `/cstmusic download auto 1h`, and `/cstmusic download auto 1h30m` keep the local cache warm at that interval and prune deleted or missing songs from the local cache. `/cstmusic download off` disables automatic downloads for that player.

Jukebox playback is positional. Admin test playback uses non-positional stereo playback for the requesting admin.

## FFmpeg

Supported input extensions:

- `mp3`
- `mp4`
- `wav`
- `ogg`
- `flac`
- `m4a`
- `aac`
- `webm`

Non-OGG files require FFmpeg. The server looks for FFmpeg in this order:

1. explicit `ffmpegPath` in `config/musicxcst.json`, when it is not blank and not the default `ffmpeg`
2. a bundled FFmpeg binary inside the mod jar
3. `ffmpeg` on the server PATH

Bundled binaries should be packaged at one of these resource paths:

- `musicxcst/ffmpeg/windows-x86_64/ffmpeg.exe`
- `musicxcst/ffmpeg/linux-x86_64/ffmpeg`
- `musicxcst/ffmpeg/linux-aarch64/ffmpeg`
- `musicxcst/ffmpeg/macos-x86_64/ffmpeg`
- `musicxcst/ffmpeg/macos-aarch64/ffmpeg`

At runtime, musicXCST extracts the matching bundled binary to `config/musicxcst/ffmpeg/<platform>/` on the server and runs it from there. OGG files can still be copied into normalized storage without conversion.

Normal multiplayer clients do not need FFmpeg for playback.

Common install commands:

```powershell
# Windows, with winget
winget install Gyan.FFmpeg

# Windows, with Chocolatey
choco install ffmpeg
```

```bash
# Debian/Ubuntu server
sudo apt update
sudo apt install ffmpeg

# Fedora server
sudo dnf install ffmpeg

# macOS server/integrated host
brew install ffmpeg
```

Check the server can see it:

```bash
ffmpeg -version
```

If the command is not globally available and the mod jar does not include a bundled binary for the server platform, set `ffmpegPath` to the full executable path, for example `C:\ffmpeg\bin\ffmpeg.exe`.

Default normalization:

- format: OGG Vorbis
- bitrate: `128k`
- sample rate: `44100`
- stereo: enabled unless mono downmix is configured

## Storage and Config

Files:

- config: `config/musicxcst.json`
- metadata index: `<world>/data/musicxcst/music-index.json`
- import folder: `<world>/music-import/`
- normalized audio: `<world>/music-normalized/`
- client cache: `<game directory>/musicxcst-cache/`

Important config fields include max file size, per-player quota, total server quota, allowed extensions, import folder, normalized output format, bitrate, sample rate, stereo/mono settings, preview cache seconds, playback radius, range interval, fade timings, FFmpeg path, client cache warming, found-disc playback rules, soft delete, and debug logging.

## Ownership

Every music entry stores an owner UUID. Normal players can only list, inspect, delete, and create discs from their own entries. Admins can manage all entries. A physical written disc can move between players, but ownership of the stored music entry does not change.

Config keeps future behavior options for found/stolen discs: allow anyone to play found discs, owner-only playback, and admin bypass.

## Deleted or Missing Audio

Deleted and missing entries do not crash existing discs. Metadata is marked deleted, missing, or invalid; discs update their status and tooltip; playback fails safely.

## Future CD Writer

The CD Writer is planned but not implemented in this patch. See [docs/future-cd-writer-plan.md](docs/future-cd-writer-plan.md) for the block, GUI, upload flow, color picker, design selector, and custom design size plan up to `128x128`.

## Building

Requirements:

- Java `25` or the project-compatible JDK used by the current Minecraft `26.1.2` toolchain
- Fabric Loom `1.16.2`

Build:

```powershell
.\gradlew.bat build
```

Output jars are written to `build/libs/`.

## License

Current project license metadata is `All-Rights-Reserved`, matching the existing repository files.
