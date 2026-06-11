# FFmpeg License Files

MusicXCST public release jars do not bundle FFmpeg binaries.

Use this folder for research notes and license references for separately installed or managed FFmpeg builds. Do not put executable binaries, native libraries, archives, compressed blobs, or renamed payloads here.

Recommended FFmpeg selection:

- Prefer an LGPL build for redistribution.
- Do not use builds configured with `--enable-nonfree`.
- Avoid GPL builds unless you intentionally want to handle GPL distribution obligations.
- For current releases, FFmpeg 8.x is fine; MusicXCST only needs the `ffmpeg` executable with OGG Vorbis encode support.

Managed setup:

- Downloads only after explicit user/admin consent.
- Uses HTTPS.
- Verifies SHA-256 before extraction.
- Extracts only expected files.
- Rejects `--enable-nonfree` when reported by `ffmpeg -version`.
- Stores metadata under `config/musicxcst/ffmpeg/managed/<platform>/metadata.json`.

Current managed source:

- Windows x86_64: BtbN FFmpeg LGPL `win64` ZIP build selected from the latest GitHub release.
- Linux x86_64: BtbN FFmpeg LGPL `linux64` TAR.XZ build selected from the latest GitHub release.
- Linux aarch64: BtbN FFmpeg LGPL `linuxarm64` TAR.XZ build selected from the latest GitHub release.
- SHA-256: `8d68576f84043b3e2027ed020de9f814e39795007c64061bf40310e0d3f7fee6`

For unsupported managed platforms, install FFmpeg manually and use:

```text
ffmpegMode = system
```

or:

```text
ffmpegMode = path
ffmpegPath = /path/to/ffmpeg
```

Do not add copyrighted music files to this repository.
