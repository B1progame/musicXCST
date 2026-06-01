# Third-Party Notices

## FFmpeg

MusicXCST can use FFmpeg for audio probing and transcoding. FFmpeg binaries are not music files and must be distributed under the FFmpeg project's license terms for the exact build you ship.

For bundling with this repository, prefer a stable release LGPL build of FFmpeg, without `--enable-gpl` and without `--enable-nonfree`. GPL builds may impose GPL obligations on the distributed binary package, and `--enable-nonfree` builds must not be redistributed.

Place the matching FFmpeg license text, source/build URL, version, commit if available, and configure flags under `licenses/ffmpeg/` when bundling binaries. Server owners and pack distributors are responsible for ensuring their FFmpeg binary source, license, and enabled codecs are compatible with their distribution.

MusicXCST invokes the `ffmpeg` executable as a separate process. It does not link FFmpeg libraries into the mod jar.

MusicXCST does not include copyrighted music. Users are responsible for uploading audio they have rights to use.
