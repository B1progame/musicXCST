# Server Admin Guide

FFmpeg is needed on the server if players will import `mp3`, `mp4`, `wav`, `flac`, `m4a`, `aac`, or `webm` files. musicXCST first uses an explicit `ffmpegPath`, then a bundled FFmpeg binary inside the mod jar, then `ffmpeg` on PATH.

Bundled binaries are extracted to `config/musicxcst/ffmpeg/<platform>/` when needed. Normal multiplayer clients do not need FFmpeg, because clients only download the normalized OGG cache files from the server.

Supported bundled paths inside the jar:

- `musicxcst/ffmpeg/windows-x86_64/ffmpeg.exe`
- `musicxcst/ffmpeg/linux-x86_64/ffmpeg`
- `musicxcst/ffmpeg/linux-aarch64/ffmpeg`
- `musicxcst/ffmpeg/macos-x86_64/ffmpeg`
- `musicxcst/ffmpeg/macos-aarch64/ffmpeg`

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
