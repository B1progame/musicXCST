# Server Admin Guide

Install FFmpeg on the server if players will import `mp3`, `mp4`, `wav`, `flac`, `m4a`, `aac`, or `webm` files. Set `ffmpegPath` in `config/musicxcst.json` when the executable is not named `ffmpeg` or is not on PATH.

musicXCST does not install FFmpeg automatically. Server owners install it once on the server machine. Normal multiplayer clients do not need FFmpeg, because clients only download the normalized OGG cache files from the server.

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

Important folders:

- `<world>/music-import/`: safe server import folder
- `<world>/music-normalized/`: normalized OGG playback storage
- `<world>/data/musicxcst/music-index.json`: metadata index

Admin commands:

- `/cstmusic admin storage`
- `/cstmusic admin list [page]`
- `/cstmusic admin info <musicId>`
- `/cstmusic admin delete <musicId>`
- `/cstmusic admin play <musicId>`
- `/cstmusic admin reload`
- `/cstmusic admin repairindex`

Dedicated servers should keep `allowAdminAbsoluteServerPaths` disabled unless trusted admins need to import files already present on the server machine.
