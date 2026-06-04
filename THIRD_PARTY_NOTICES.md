# Third-Party Notices

## FFmpeg

MusicXCST can use FFmpeg for audio probing and transcoding. FFmpeg binaries are third-party executables and are not owned by the MusicXCST copyright holder.

MusicXCST invokes FFmpeg as a separate executable process. It does not link FFmpeg libraries into the mod jar.

MusicXCST public release jars do not ship FFmpeg binaries, native libraries, archives, base64 blobs, or renamed executable payloads. Users and server owners may install FFmpeg separately, configure an explicit path, or use a managed setup flow when their platform has a pinned verified download.

Managed setup downloads only after explicit user/admin action, verifies SHA-256 before extraction, extracts only expected files, checks `ffmpeg -version`, rejects `--enable-nonfree`, and stores source URL, version, hash, and license metadata under `config/musicxcst/ffmpeg/managed/<platform>/`.

For any separately installed or managed FFmpeg binary, prefer LGPL builds without `--enable-nonfree`. GPL builds may impose GPL obligations on the binary package, and `--enable-nonfree` builds must not be redistributed.

MusicXCST does not include copyrighted music. Users are responsible for uploading audio they have rights to use.

## GeckoLib

MusicXCST uses GeckoLib for animated block rendering. GeckoLib is a third-party library distributed under its own license by its upstream authors.

## Fabric API

MusicXCST is built on Fabric Loader and Fabric API. Fabric components are third-party dependencies distributed under their own upstream licenses.
