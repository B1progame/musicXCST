# Server Admin Guide

This guide covers MusicXCST setup for public and private Fabric servers.

## Required Mods

Install these on the server and on every client:

- MusicXCST
- Fabric API
- GeckoLib

MusicXCST targets Minecraft `26.1.2`.

## FFmpeg

MusicXCST uses FFmpeg for conversion and probing.

- CD Writer uploads are converted on the uploading client before transfer.
- The server still validates, stores, hashes, probes, and distributes the uploaded normalized audio.
- Admin server-side imports may require server-side transcoding if the source file is not already playable OGG.

The public MusicXCST jar does not bundle FFmpeg executables. The default config is:

```text
ffmpegMode = system
```

Modes:

1. `ffmpegMode = disabled`: never use FFmpeg.
2. `ffmpegMode = path`: use `ffmpegPath`.
3. `ffmpegMode = system`: use `ffmpeg` from PATH.
4. `ffmpegMode = managed`: use an admin-approved verified download from `config/musicxcst/ffmpeg/managed/<platform>/`.

Old `ffmpegMode = bundled` configs are migrated to `system`.

Install examples:

```powershell
winget install Gyan.FFmpeg
choco install ffmpeg
```

```bash
sudo apt update
sudo apt install ffmpeg
sudo dnf install ffmpeg
brew install ffmpeg
```

Verify:

```bash
ffmpeg -version
```

Admin FFmpeg commands:

```text
/cstmusic admin ffmpeg status
/cstmusic admin ffmpeg path <path>
/cstmusic admin ffmpeg download confirm
/cstmusic admin ffmpeg reset
```

Dedicated servers never open FFmpeg setup screens and never download FFmpeg automatically. Managed download only starts after `/cstmusic admin ffmpeg download confirm`. Managed download supports Windows x86_64 and Linux x86_64/aarch64. If the current platform has no managed source, install FFmpeg manually and use `system` or `path`.

## Storage

Default server storage paths:

```text
<world>/music-import/
<world>/music-normalized/
<world>/data/musicxcst/music-index.json
```

`music-index.json` stores ownership, file metadata, checksums, status, color, and creation timestamps. Do not edit it while the server is running.

## Limits

Important storage and upload controls:

```text
maxFileSizeBytes
maxStoragePerPlayerBytes
maxTotalServerStorageBytes
maxUploadMb
maxMusicDurationEnabled
maxMusicDurationSeconds
maxMusicFilesPerPlayerEnabled
maxMusicFilesPerPlayer
playerLimitMode
```

`playerLimitMode` accepts:

```text
confirm_delete_oldest
auto_delete_oldest
block_new_upload
```

When deleting an oldest upload, MusicXCST only targets entries owned by that player. It updates metadata and removes stored audio files for that entry.

## Permissions

Normal players can:

- create discs through the CD Writer
- list their own music
- inspect their own music IDs
- delete their own entries
- manage their local download cache

Admins can:

- import from server paths
- inspect all entries
- delete any entry
- reload config/index
- repair the index
- test-play entries

Dedicated servers should keep `allowAdminAbsoluteServerPaths` disabled unless trusted admins need direct server filesystem imports.

## Admin Commands

```text
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

Admin creation/import commands:

```text
/cstmusic upload <name> <localFilePath>
/cstmusic createupload <name> <hexColor> <uploadedFile>
/cstmusic create <name> <hexColor> <serverFilePath>
```

## Operational Notes

- Back up `music-index.json` and the audio folders before major updates.
- Use quotas on public servers.
- Keep `allowedFileExtensions` narrow if the server has strict upload policies.
- Disable duration limiting only if storage and moderation policies are ready for long tracks.
- Players and server operators are responsible for audio rights.
