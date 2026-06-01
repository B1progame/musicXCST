# FFmpeg License Files

If you bundle FFmpeg binaries with MusicXCST, put the matching FFmpeg license notices and build information in this folder.

Recommended bundled binary:

- Use a stable FFmpeg release build, not a random nightly, unless you need a specific fix.
- Prefer an LGPL build for redistribution.
- Do not use builds configured with `--enable-nonfree`.
- Avoid GPL builds for the mod jar unless you intentionally want to handle GPL distribution obligations.
- For current releases, FFmpeg 8.x is fine; MusicXCST only needs the `ffmpeg` executable with OGG Vorbis encode support.

Expected binary resource locations:

- `src/main/resources/native/ffmpeg/windows-x86_64/ffmpeg.exe`
- `src/main/resources/native/ffmpeg/linux-x86_64/ffmpeg`
- `src/main/resources/native/ffmpeg/linux-aarch64/ffmpeg`
- `src/main/resources/native/ffmpeg/macos-x86_64/ffmpeg`
- `src/main/resources/native/ffmpeg/macos-aarch64/ffmpeg`

At runtime the selected binary is extracted to `config/musicxcst/native/ffmpeg/`.

When adding binaries, also add:

- the FFmpeg version
- the download/source URL
- the license file from that build
- configure flags if the provider publishes them
- checksums if available
