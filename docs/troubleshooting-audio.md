# Troubleshooting Audio

## FFmpeg Missing

If importing an MP3 or other non-OGG file fails with an FFmpeg message, make sure the server has one of these:

- a working explicit `ffmpegPath` in `config/musicxcst.json`
- a bundled FFmpeg binary inside the mod jar for the server platform
- `ffmpeg` available on PATH

Install it on the server or the singleplayer/integrated host, not on every multiplayer client:

```powershell
winget install Gyan.FFmpeg
```

```bash
sudo apt install ffmpeg
```

## Jukebox Disc Is Silent

Check that the entry is active with `/cstmusic info <musicId>`. If the normalized file is missing, run `/cstmusic admin repairindex` and re-import the song if needed.

## Client Download Fails

The client validates the server-provided SHA-256 checksum. Delete the local `musicxcst-cache` folder and try again if a partial download was interrupted.

## Dedicated Server Cannot Read a Friend's Path

A chat command on a server cannot access files on a player's private PC. Put the file in the server import folder first. The future CD Writer GUI will add a proper upload workflow.
