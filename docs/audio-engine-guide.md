# Audio Engine Guide

MusicXCST uses a mod-controlled audio path instead of creating dynamic Minecraft sound events or rebuilding resource packs.

## Server Responsibilities

- Validate upload paths, extensions, size, duration, and quotas.
- Normalize playable audio to OGG Vorbis.
- Store safe relative paths in the music index.
- Track ownership and creation timestamps.
- Send playback metadata, checksums, preview data, and audio chunks to clients.
- Start and stop jukebox/admin playback sessions.
- Send live jukebox volume updates to listeners.

## Client Responsibilities

- Convert selected CD Writer files to normalized OGG before upload.
- Receive playback metadata from the server.
- Check the local `musicxcst-cache` folder.
- Request missing audio chunks.
- Verify SHA-256 checksums before full playback.
- Decode OGG with STB Vorbis.
- Play audio through OpenAL with positional gain and live volume updates.

## Cache Behavior

Players can pre-download active tracks:

```text
/cstmusic download all
/cstmusic download auto 30m
/cstmusic download auto 1h
/cstmusic download auto 1h30m
/cstmusic download off
```

The cache is stored in:

```text
<minecraft directory>/musicxcst-cache/
```

## Current Limitations

- Cache misses can delay full-track playback while chunks download.
- Precise mid-track seeking is still an area for improvement.
- Large public libraries should use server quotas and moderation.
- Every listener needs the client mod installed because playback uses a custom audio path.
