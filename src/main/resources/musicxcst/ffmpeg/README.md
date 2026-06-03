# Bundled FFmpeg Resource Notes

Bundled FFmpeg binaries can be placed in these resource folders before packaging:

- `native/ffmpeg/windows-x86_64/ffmpeg.exe`
- `native/ffmpeg/linux-x86_64/ffmpeg`
- `native/ffmpeg/linux-aarch64/ffmpeg`
- `native/ffmpeg/macos-x86_64/ffmpeg`
- `native/ffmpeg/macos-aarch64/ffmpeg`

MusicXCST extracts the matching binary to `config/musicxcst/native/ffmpeg/<platform>/` and runs it from there.

The CD Writer upload flow converts audio on the uploading client. Server-side FFmpeg is still useful for duration probing and admin/server-side imports.

Only distribute FFmpeg builds whose license terms are compatible with this mod's distribution.
