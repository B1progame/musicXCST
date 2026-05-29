# musicXCST

`musicXCST` is a Fabric mod for Minecraft `26.1.2` that adds custom writable music CDs. The current first version is deliberately small: players craft a blank `Blueprint CD`, register server-safe music metadata with a command, and write that metadata onto the disc item.

## First-Version Scope

Included now:

- blank writable `Blueprint CD` item
- crafting recipe for the blank disc
- `/cstmusic` command tree
- server-side metadata index
- ownership rules
- admin management commands
- jukebox insertion/ejection support with placeholder vanilla playback
- invalid / deleted disc handling
- config file for quotas, extensions, and import folder rules

Not included yet:

- CD Writer block
- CD Writer GUI
- client upload flow
- custom imported-audio playback engine
- directional or synchronized multiplayer audio

## Crafting the Blank CD

Recipe shape:

```text
  iron nugget
iron nugget + blue stained glass pane + iron nugget
  iron nugget
```

Crafting result:

- `Blueprint CD`

## Command Usage

All commands use a single root:

```text
/cstmusic
```

Available commands:

- `/cstmusic help`
- `/cstmusic create <name> <hexColor> <location>`
- `/cstmusic list`
- `/cstmusic info <musicId>`
- `/cstmusic delete <musicId>`
- `/cstmusic storage`
- `/cstmusic admin storage`
- `/cstmusic admin list [page]`
- `/cstmusic admin info <musicId>`
- `/cstmusic admin delete <musicId>`
- `/cstmusic admin reload`
- `/cstmusic admin repairindex`

## Creating a Written CD

Current workflow:

1. Craft a blank `Blueprint CD`.
2. Hold the blank disc in the selected hotbar slot.
3. Put the music file inside the configured server import folder.
4. Run:

```text
/cstmusic create "Song Name" #00AAFF folder/song.ogg
```

In singleplayer or an integrated server, local absolute paths can also be imported:

```text
/cstmusic create "Song Name" #00AAFF "C:\Users\Name\Music\song.mp3"
```

The file is copied into the world import folder and the private absolute path is not stored on the disc or in the metadata index.

Behavior notes:

- the command requires a blank `Blueprint CD`
- a stack of blank discs will consume one and create one written disc
- the written disc stores a music ID, owner, color, display name, status, design placeholder, and schema version

## Ownership Rules

Each music entry stores:

- unique music ID
- owner UUID
- owner name
- safe relative import path
- file metadata

Rules:

- normal players can list, inspect, create, and delete only their own entries
- admins can manage all entries
- written discs preserve the original owner metadata
- obtaining another player's disc does not transfer ownership of the underlying music entry

## Storage and Config

The mod writes:

- config: `config/musicxcst.json`
- metadata index: `<world>/data/musicxcst/music-index.json`
- import folder: `<world>/music-import/` by default
- absolute import copy folder: `<world>/music-import/imported/` by default

Config fields include:

- max file size per entry
- max storage per player
- max total server storage
- allowed file extensions
- server import folder
- singleplayer absolute path import toggle
- admin absolute server path import toggle
- soft delete toggle
- future playback ownership toggles
- debug logging

Allowed extensions in the first version:

- `mp3`
- `mp4`
- `wav`
- `ogg`
- `flac`
- `m4a`
- `aac`
- `webm`

## Deleted and Missing Music

If an entry is deleted or the file goes missing:

- metadata stays in the index
- discs do not crash
- the disc name becomes invalid/red
- tooltips warn that the audio is missing, deleted, or invalid
- future playback can fail safely against the stored status

## Jukebox Behavior

`Blueprint CD` items can be inserted into and removed from vanilla jukeboxes. In this first version, jukebox playback uses a vanilla placeholder song because the custom imported-audio playback engine is not implemented yet.

## Safety Rules

The first version accepts server-side import paths inside the configured import folder. It can also import quoted absolute paths in singleplayer or integrated-server worlds.

Current safety behavior:

- singleplayer absolute paths are copied into the world import folder
- dedicated-server absolute paths are rejected by default
- dedicated servers can enable admin-only absolute server paths with `allowAdminAbsoluteServerPaths`
- path traversal is rejected
- only configured extensions are accepted
- file size quotas are enforced
- only safe relative paths are stored
- private local client paths are not written onto the item

On a dedicated multiplayer server, a command cannot read a file from a player's computer. The file must already exist on the server, be placed into the import folder, or be uploaded later through the planned CD Writer upload flow.

## Known Limitations

- no client upload flow yet
- no local singleplayer file picker yet
- no actual audio playback yet
- jukebox playback uses a vanilla placeholder song
- only one disc item model is used right now; written discs are still `Blueprint CD` items with metadata
- custom disc color is stored as real RGB metadata and reflected in name/item bar color for now
- invalid state is communicated through name, tooltip, item bar color, and the red invalid placeholder texture reserved for future model overrides
- runtime behavior was build-verified, but not exercised through a live in-game command session in this repository

## Future CD Writer

The future workstation plan is documented in [docs/future-cd-writer-plan.md](docs/future-cd-writer-plan.md).

Planned later:

- CD Writer block
- note-block based recipe
- DJ-style write GUI
- disc design selector with default `16x16` designs and possible custom designs up to `128x128`
- client upload and conversion pipeline
- playback and synchronization features

## Building From Source

Requirements:

- Java `25`
- Minecraft `26.1.2`

Build:

```powershell
.\gradlew.bat build
```

Output jars are written to `build/libs/`.

## GitHub Notes

The local folder is already a Git repository. GitHub publication is currently blocked because the installed `gh` client has an invalid authentication token. After re-authentication, the repository can be created and pushed with:

```powershell
gh auth login -h github.com
gh repo create musicXCST --public --source . --remote origin --push
gh repo edit --description "Fabric Minecraft mod for custom writable music CDs" --add-topic minecraft --add-topic fabric --add-topic minecraft-mod --add-topic music-disc --add-topic custom-audio --add-topic cstmusic
```

## License

Current project license metadata is `All-Rights-Reserved`, matching the existing repository files.
