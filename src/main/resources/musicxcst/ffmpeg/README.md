Bundled FFmpeg binaries can be placed in these resource folders before packaging:

- `musicxcst/ffmpeg/windows-x86_64/ffmpeg.exe`
- `musicxcst/ffmpeg/linux-x86_64/ffmpeg`
- `musicxcst/ffmpeg/linux-aarch64/ffmpeg`
- `musicxcst/ffmpeg/macos-x86_64/ffmpeg`
- `musicxcst/ffmpeg/macos-aarch64/ffmpeg`

The server extracts the matching binary to `config/musicxcst/ffmpeg/<platform>/` and runs it from there.

Only distribute FFmpeg builds whose license terms are compatible with this mod's distribution.
