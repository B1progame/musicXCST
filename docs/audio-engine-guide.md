# Audio Engine Guide

musicXCST uses a mod-controlled audio path instead of dynamic Minecraft sound events.

Server responsibilities:

- validate import paths and extensions
- hash original files
- normalize playable audio into OGG Vorbis
- store only safe relative paths in metadata
- stream normalized audio to clients in bounded chunks
- start and stop playback sessions from jukeboxes or admin commands

Client responsibilities:

- receive playback metadata
- check the local `musicxcst-cache`
- request missing chunks
- verify SHA-256 checksums
- decode OGG with STB Vorbis
- play stereo or positional OpenAL sources

Current limitations:

- playback start time is sent, but exact seek into the middle of long tracks is still a TODO
- full range enter/leave session tracking is represented by service classes and initial radius sends, but the continuous tracker still needs to be connected
- fade controller exists as an extension point and needs deeper OpenAL gain integration
