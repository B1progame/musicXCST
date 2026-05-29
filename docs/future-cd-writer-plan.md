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

## Planned Disc Designs

The CD Writer should eventually let players choose or upload a disc design.

Design rules to evaluate:

- vanilla-style default remains `16x16`
- custom designs may scale up to `128x128`
- custom designs should be stored as a design ID or server-managed asset reference, not as raw private client paths
- server owners should be able to cap custom design size and storage usage
- designs should use the same ownership and moderation rules as music entries
- the written disc item remains `blueprint_cd`; it should not become a separate written item

Color behavior:

- the written disc uses the stored hex color as real RGB metadata
- tinting should preserve brightness so darker tones stay dark and lighter tones stay light
- invalid or deleted discs may use a red warning visual/state

Future PNG mockups can be added later once the command-based first version is stable.

## Planned Pipeline

Later versions should add:

- client upload flow for integrated and dedicated servers
- direct client-to-server upload for friends on multiplayer servers, because server commands cannot read files from a player's private computer
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
