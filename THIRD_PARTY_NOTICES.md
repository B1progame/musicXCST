# Third-Party Notices

## FFmpeg

MusicXCST can use FFmpeg for audio probing and transcoding. FFmpeg binaries are third-party executables and are not owned by the MusicXCST copyright holder.

The bundled Windows binary is expected to come from GyanD FFmpeg builds. The bundled Linux x86_64 binary is expected to come from BtbN FFmpeg builds. Packagers should verify the exact binary, version, license, and configure flags before distributing a jar.

For redistribution, prefer stable release LGPL builds without `--enable-gpl` and without `--enable-nonfree`. GPL builds may impose GPL obligations on the distributed binary package, and `--enable-nonfree` builds must not be redistributed.

Place the matching FFmpeg license text, source/build URL, version, commit if available, configure flags, and checksums under `licenses/ffmpeg/` when bundling binaries.

MusicXCST invokes the `ffmpeg` executable as a separate process. It does not link FFmpeg libraries into the mod jar.

MusicXCST does not include copyrighted music. Users are responsible for uploading audio they have rights to use.
