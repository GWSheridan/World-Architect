# Modpack Developer Tools

World Architect bundles package a world preset, developer policy, and native structure files in one ZIP. A bundle declares the preset ID, title, author, release, and required mod IDs. The packaged starter island is embedded in the generator and the original native template is included as a datapack structure.

## Preset Studio

Use [Preset Studio](Preset-Studio.md) to save immutable releases, duplicate, import/export and validate presets, edit locks, inspect dependencies and read help in game. Ready-to-install partial and full examples are included in [the Studio examples](https://github.com/GWSheridan/World-Architect/tree/v1.2.0/examples/preset-studio). The original JSON authoring workflow remains available below.

Bundles can also embed selected `worldarchitect:modular` definitions under `data/<namespace>/worldgen/structure/`. Studio captures those definitions and native starting islands automatically. The dedicated authoring export shares Studio validation.

## Author and export

1. In an authoring instance, create `config/worldarchitect/developer.json` with `{"format":1,"developer_mode":true}`. Restart the instance. The server console and administrators can now use `/wa_modpack export`.
2. Copy [the locked Skyblock draft](https://github.com/GWSheridan/World-Architect/tree/v1.2.0/examples/locked-skyblock) into `config/worldarchitect/drafts/locked-skyblock`. Edit its three JSON files and place any additional native templates under `data/<namespace>/structure/<path>.nbt` in the draft.
3. Run `/wa_modpack export locked-skyblock`. The bundle appears in `config/worldarchitect/exports/locked-skyblock.zip`. Choose a new draft name for each export.

| Draft file | Purpose |
| --- | --- |
| `manifest.json` | Metadata, dependency IDs, default generation options, and lock policy. |
| `preset.json` | Minecraft world preset with complete dimension settings. Omit this file to export the running authoring world's dimension profiles. |
| `islands.json` | Optional assignments from dimension IDs to bundled native structures and local spawn markers. |
| `data/<namespace>/structure/<path>.nbt` | Native structure files, including exports from the [Structure Library](Structure-Library.md). |

For a starter assignment, give the dimension a Skyblock generator and island layout in `preset.json`. Set its structure ID and local feet position in `islands.json`. Export validates the palette and marker, then writes the embedded block-state template into the generated preset. Starter islands use the [Skyblock template limits](Void-and-Skyblock.md). Other native files ship as datapack structures for use by the Structure Library or Minecraft's template commands.


## Releasing a bundle

Validate the bundle in an instance with the required blocks and registries. Give every changed bundle a new release. Keep its source revision and the exact original ZIP for recovery. Existing worlds load their original archived bundle; replacing the installed bundle supplies defaults for future worlds.

Template metadata is limited to supported decorative data. See [Structure Library](Structure-Library.md) for imports, transforms, markers, size limits and supported block entities.
