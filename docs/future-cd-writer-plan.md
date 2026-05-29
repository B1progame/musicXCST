# Future CD Writer Plan

## Planned Block

The future workstation is a dedicated CD Writer block for `musicXCST`.

Recipe idea:

- crafting table in the center
- four note blocks around it

## Planned GUI

The future GUI should feel like a compact DJ / CD writer workstation.

Planned controls:

- CD slot
- song name field
- file selector
- color picker
- design selector
- write button

Future PNG mockups can be added later once the command-based first version is stable.

## Planned Pipeline

Later versions should add:

- client upload flow for integrated and dedicated servers
- server-side import validation
- background conversion pipeline where needed
- stereo playback support
- positional and directional audio support
- multiplayer synchronization
- client-side caching

## Migration Notes

The first version already separates:

- item metadata
- server-side music index
- config
- ownership checks
- command workflow

That structure is intended to let the future CD Writer GUI call into the same core write and validation services without rewriting the mod.
