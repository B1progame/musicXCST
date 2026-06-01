# musicXCST

musicXCST is a Fabric mod for Minecraft `26.1.2` that adds writable custom music CDs. Players craft a blank Blueprint CD, write audio with the CD Writer block, and play the finished disc through the mod's custom audio backend.

## Versions

This repository has two distribution branches:

- `master`: includes bundled FFmpeg binaries for Windows x86_64 and Linux x86_64 through Git LFS.
- `no-bundled-ffmpeg`: contains the same mod code without bundled FFmpeg binaries. Use this branch when a pack or server wants a smaller jar or wants clients to provide FFmpeg themselves.

This branch is `no-bundled-ffmpeg`, so it does not include FFmpeg binaries in `src/main/resources/native/ffmpeg/`.

## CD Writer Workflow

1. Place the CD Writer block.
2. Put a blank Blueprint CD in the input slot.
3. Enter a display name for the disc.
4. Use the file button to choose an audio file.
5. Pick a disc color in the texture editor.
6. Press Print and keep the GUI open until the upload/conversion finishes.
7. Take the written Blueprint CD from the output slot.

Supported source file types are `mp3`, `mp4`, `wav`, `ogg`, `flac`, `m4a`, `aac`, `webm`, and `avi`. The client converts the chosen file to normalized OGG Vorbis before upload.

## FFmpeg

musicXCST is designed so normal server hosters do not need to install FFmpeg globally.

The default flow is client-side transcoding:

1. The client selects a local file in the CD Writer GUI.
2. The client runs FFmpeg locally and converts the audio to OGG Vorbis.
3. The client uploads only the converted `.ogg` file to the server.
4. The server validates, stores, and distributes the converted audio.

Server-side FFmpeg is only an optional fallback for admin/server-side imports. It is disabled by default with `allowServerSideTranscoding = false`.

If a distributor wants to add bundled FFmpeg binaries back to this branch, they should be placed at:

```text
src/main/resources/native/ffmpeg/windows-x86_64/ffmpeg.exe
src/main/resources/native/ffmpeg/linux-x86_64/ffmpeg
src/main/resources/native/ffmpeg/linux-aarch64/ffmpeg
src/main/resources/native/ffmpeg/macos-x86_64/ffmpeg
src/main/resources/native/ffmpeg/macos-aarch64/ffmpeg
```

At runtime a bundled binary would be extracted to:

```text
config/musicxcst/native/ffmpeg/
```

Recommended FFmpeg builds:

- Use stable release builds.
- Prefer LGPL builds for redistribution.
- Do not bundle builds made with `--enable-nonfree`.
- Avoid GPL builds unless the distribution intentionally accepts GPL obligations.
- The mod only needs the `ffmpeg` executable with OGG Vorbis encoding support.

Because this branch does not bundle binaries, clients should use `ffmpegMode = system` or `ffmpegMode = path`, and each client needs FFmpeg available locally for CD Writer uploads.

## Config

Important config keys in `config/musicxcst.json`:

- `ffmpegMode`: `bundled`, `system`, `path`, or `disabled`
- `ffmpegPath`: explicit executable path when `ffmpegMode = path`
- `audioBitrateKbps`: normalized output bitrate, usually `128` or `160`
- `maxUploadMb`
- `maxDurationSeconds`
- `maxServerStorageMb`
- `allowServerSideTranscoding`: disabled by default
- `clientUploadBytesPerSecond`
- `previewCacheSeconds`

## Commands

Normal player commands:

- `/cstmusic help`
- `/cstmusic list`
- `/cstmusic info <musicId>`
- `/cstmusic delete <musicId>`
- `/cstmusic storage`
- `/cstmusic download all`
- `/cstmusic download auto <30m|1h|1h30m>`
- `/cstmusic download off`

Admin-only creation/import commands:

- `/cstmusic upload <name> <localFilePath>`
- `/cstmusic create <name> <hexColor> <location>`
- `/cstmusic createupload <name> <hexColor> <uploadedFile>`

Admin management commands:

- `/cstmusic admin storage`
- `/cstmusic admin list [page]`
- `/cstmusic admin info <musicId>`
- `/cstmusic admin delete <musicId>`
- `/cstmusic admin play <musicId>`
- `/cstmusic admin reload`
- `/cstmusic admin repairindex`

## Audio Playback

The mod does not create one Minecraft sound event per uploaded song and does not require resource-pack rebuilds. Imported audio is stored as normalized OGG Vorbis and sent to clients on demand in chunks.

Playback flow:

1. A jukebox or admin command starts playback.
2. The server sends metadata, including checksums and playback start time.
3. The client checks its local musicXCST cache.
4. If the full song is missing, the client can play the preview while downloading the full audio.
5. The client verifies SHA-256 before playback.
6. The client decodes OGG with STB Vorbis and plays through OpenAL.

Use `/cstmusic download all` before long sessions to pre-cache active server songs. Automatic cache warming can be enabled with `/cstmusic download auto 30m`, `/cstmusic download auto 1h`, or `/cstmusic download auto 1h30m`.

## Licensing And Audio Rights

musicXCST does not include copyrighted music. Users are responsible for uploading audio they have rights to use.

The MusicXCST source code and assets are covered by `LICENSE.txt`. Bundled FFmpeg binaries are separate third-party executables and are covered by their own licenses and notices in `THIRD_PARTY_NOTICES.md` and `licenses/ffmpeg/`.
