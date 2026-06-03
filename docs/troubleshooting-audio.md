# Troubleshooting Audio

## FFmpeg Is Missing

If conversion or duration probing fails, make sure the relevant side has FFmpeg:

- The uploading client needs FFmpeg for CD Writer file conversion.
- The server needs FFmpeg for duration checks and admin/server-side imports.

Options:

- ship a compatible bundled FFmpeg binary
- set `ffmpegMode = system` and install FFmpeg on PATH
- set `ffmpegMode = path` and configure `ffmpegPath`
- upload already-normalized `.ogg` where possible

Install examples:

```powershell
winget install Gyan.FFmpeg
```

```bash
sudo apt install ffmpeg
```

## Track Is Rejected As Too Long

Check:

```text
maxMusicDurationEnabled
maxMusicDurationSeconds
```

If duration limiting is enabled and the server cannot determine duration, the upload is rejected so the limit cannot be bypassed.

## Player Hit The File Limit

Check:

```text
maxMusicFilesPerPlayerEnabled
maxMusicFilesPerPlayer
playerLimitMode
```

Use `/cstmusic list` and `/cstmusic delete <musicId>` to clean up old entries.

## Jukebox Disc Is Silent

Check that the entry is active:

```text
/cstmusic info <musicId>
```

If the normalized file is missing, an admin can run:

```text
/cstmusic admin repairindex
```

If the file cannot be repaired, re-import the track.

## Client Download Fails

MusicXCST validates server audio chunks with SHA-256. If an interrupted download left a bad cache file, delete:

```text
<minecraft directory>/musicxcst-cache/
```

Then reconnect or run:

```text
/cstmusic download all
```

## Dedicated Server Cannot Read A Player's File Path

Server commands cannot read files from a player's private computer. For normal players, use the CD Writer GUI upload flow. For admin server-side imports, copy the file into the server import folder or enable trusted absolute server paths in config.
