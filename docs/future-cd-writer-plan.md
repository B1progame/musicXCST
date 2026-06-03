# CD Writer And Roadmap Notes

The CD Writer is the main player-facing workstation for MusicXCST.

## Current Workflow

- Place a Blueprint CD in the input slot.
- Enter a disc name.
- Select a local audio file.
- Customize the disc color or texture.
- Print the disc and wait for upload/conversion to finish.
- Take the written Blueprint CD from the output slot.

## Current Design Storage

Written discs remain the `blueprint_cd` item. Music and design metadata are stored on the `ItemStack`, while audio files and ownership metadata are stored in the server music index.

Custom disc designs use a compact 16x16 pixel format and fall back to the default Blueprint CD design if data is missing or invalid.

## Roadmap

Future work may include:

- More polished CD Writer layout and visual feedback.
- Better custom disc texture tooling.
- Optional richer design import/export workflows.
- Improved playback seek/sync for late listeners.
- Expanded moderation and storage controls for public servers.
- Optional permission-mod integrations.

## Compatibility Goal

MusicXCST should keep one written-disc item type and evolve metadata/rendering around it. That keeps discs simple for inventories, modpacks, and server migration.
