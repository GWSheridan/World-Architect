# Modpack Developer Tools

World Architect bundles package a world preset, developer policy, and native structure files in one ZIP. A bundle declares the preset ID, title, author, release, and required mod IDs. The packaged starter island is embedded in the generator and the original native template is included as a datapack structure.

## Author and export

1. In an authoring instance, create `config/worldarchitect/developer.json` with `{"format":1,"developer_mode":true}`. Restart the instance. The server console and administrators can now use `/wa_modpack export`.
2. Copy [the locked Skyblock draft](../examples/locked-skyblock) into `config/worldarchitect/drafts/locked-skyblock`. Edit its three JSON files and place any additional native templates under `data/<namespace>/structure/<path>.nbt` in the draft.
3. Run `/wa_modpack export locked-skyblock`. The bundle appears in `config/worldarchitect/exports/locked-skyblock.zip`. Choose a new draft name for each export.

| Draft file | Purpose |
| --- | --- |
| `manifest.json` | Metadata, dependency IDs, default generation options, and lock policy. |
| `preset.json` | Minecraft world preset with complete dimension settings. Omit this file to export the running authoring world's dimension profiles. |
| `islands.json` | Optional assignments from dimension IDs to bundled native structures and local spawn markers. |
| `data/<namespace>/structure/<path>.nbt` | Native structure files, including exports from the [Structure Library](STRUCTURE_LIBRARY.md). |

For a starter assignment, give the dimension a Skyblock generator and island layout in `preset.json`. Set its structure ID and local feet position in `islands.json`. Export validates the palette and marker, then writes the embedded block-state template into the generated preset. Starter islands use the [Skyblock template limits](SKYBLOCK.md). Other native files ship as datapack structures for use by the Structure Library or Minecraft's template commands.

## Configure locks

Set `lock` to one of the following values in the manifest.

| Mode | What players can edit |
| --- | --- |
| `unlocked` | The bundle supplies defaults. Presets and generation controls remain editable. |
| `partial` | Generation edits are allowed only where the manifest lists them. |
| `full` | The bundle fixes all dimension profiles and generation options. World name, difficulty, game mode, and gameplay rules remain available. |

A partial bundle can permit entire dimensions, individual generation features on solid profiles, and selected world-generation options. For example:

```json
{
  "lock": "partial",
  "editable_dimensions": ["minecraft:the_end"],
  "editable_features": {"minecraft:the_nether": ["ores"]},
  "editable_options": ["seed"]
}
```

Feature groups are `biomes`, `ores`, `decorations`, `caves`, `fluids`, and `structures`. Give a dimension a whole-dimension entry or a feature list. World-generation options are `seed`, `generate_structures`, and `bonus_chest`. Put the default values for those options under `options` in the manifest. Set `allow_structure_library` to `false` to disable `/wa_structure` in the installed instance.

## Install a bundle

In a fresh client or dedicated-server instance with World Architect and the required mods, place the ZIP in `config/worldarchitect/bundles/` and create `config/worldarchitect/developer.json`:

```json
{"format":1,"bundle":"locked-skyblock.zip"}
```

The client selects the bundled preset when creating a new world. A dedicated server uses the bundle defaults for omitted `level-type`, `level-seed`, and `generate-structures` properties when creating a world. A conflicting value fails startup if that setting is locked. Use `/wa_modpack` to see the running world's release and lock mode.

To use permitted dimension edits on a dedicated server, extract the packaged world preset JSON from the ZIP and save the edited copy as `config/worldarchitect/world-selection.json` before first startup. The server validates that selection, including the final generators after datapacks are applied. Set an editable seed or structure toggle through the normal server properties. Use a separate instance for unrelated worlds.

Locked instances accept the bundle, vanilla, and the installed mods' required datapacks. Attempts to add a world datapack, change a locked generation setting, or move a locked Overworld spawn are rejected. The locks apply to the client controls and the server's loaded settings. Instance owners control the installed mods and files.

A bundled world created in 0.7.0 or later pins the bundle ID, release, and SHA-256 digest in its generation snapshot. The original ZIP lives in the world's `data/worldarchitect/generation/` directory under its SHA-256 digest. The server loads the archived templates and bundle policy from there even after the instance bundle is updated. Create a new world to use a revised bundle. Back up the world directory, including the archive. If the archived ZIP is missing or corrupt, restore the exact original ZIP at the location reported by the server.

When upgrading a locked world created with 0.6.0, keep the matching original ZIP selected on its first successful launch. World Architect copies that ZIP into the world without rewriting the original snapshot. You can then change the installed bundle for future worlds.

For a 0.6.0 world created with an unlocked bundle, keep 0.6.0 and the original ZIP. Those saves predate bundle fingerprints for unlocked worlds. Starting with 0.7.0, the server stops before loading a save whose original bundle cannot be verified.
