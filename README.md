# musicXCST

`musicXCST` is a Fabric mod for Minecraft `26.1.2` focused on custom writable music discs. The first working version adds a command-driven workflow for registering server-safe music metadata and writing that metadata onto custom CD items.

## Current Scope

This first version is intentionally limited to the custom CD system:

- blank writable CD item
- command-based CD creation with `/cstmusic`
- server-side metadata index and config
- ownership and admin management rules
- invalid or deleted disc handling

The future CD Writer block, GUI, local upload flow, and playback pipeline are not part of this initial implementation.

## Planned Command Root

All commands use a single root:

```text
/cstmusic
```

Planned subcommands for the first version:

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

## Blank CD Recipe

The blank disc recipe is planned around:

- iron nuggets
- blue glass pane

The exact recipe JSON is part of the first implementation stage.

## Ownership Rules

Each registered music entry is intended to store:

- a unique music ID
- owner UUID
- owner name when safely available
- safe relative import path or internal stored name

Normal players may manage only their own registered music. Admins may manage all entries.

## Storage and Safety

The mod is designed to keep server music imports constrained to configured safe folders. Absolute client paths are not intended to be exposed in public item metadata, tooltips, or logs.

Planned config includes:

- allowed file extensions
- max file size
- per-player quota
- total server quota
- import/storage folder paths
- deleted-entry behavior
- future playback ownership options

## Build

This repository currently contains the Fabric Loom project files for Minecraft `26.1.2`. A proper wrapper script and the first working gameplay implementation are being added as part of the initial setup pass.

Expected local build command once wrapper setup is restored:

```powershell
.\gradlew.bat build
```

## Repository Status

The local folder is already a Git repository, but GitHub publication is currently blocked because the installed `gh` client is present with an invalid authentication token. To publish later after re-authentication:

```powershell
gh auth login -h github.com
gh repo create musicXCST --public --source . --remote origin --push
gh repo edit --description "Fabric Minecraft mod for custom writable music CDs" --add-topic minecraft --add-topic fabric --add-topic minecraft-mod --add-topic music-disc --add-topic custom-audio --add-topic cstmusic
```

## License

Current project license metadata is `All-Rights-Reserved`, matching the existing repository files unless changed later.
