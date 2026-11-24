# Backups and upgrades

Stop the server and copy the **complete world directory** before changing mods. Replace the Worlds Unwritten JAR while keeping compatible Minecraft, NeoForge, provider mods and datapacks. Reopen the copied world first. Existing saves retain their original generation profiles; editing a starting preset changes future worlds.

Keep these files with the world:

| File or directory | Purpose |
| --- | --- |
| `level.dat` and its backup | Native world metadata and the protected-world marker |
| `data/minecraft/world_gen_settings.dat` | Saved seed and dimension generation settings |
| `data/worldsunwritten-generation-v1.dat` | Immutable generation snapshot and dependency pins |
| `data/worldsunwritten/generation/` | Archived original bundle and its embedded resources |
| `data/worldsunwritten-islands-v1.dat` | Personal/team island assignments |
| `data/worldsunwritten-administration-v1.dat` | Spawn, protection and environment overrides, when configured |
| `data/worldsunwritten/structures/` | Captured templates, placement rules and markers |
| `data/worldsunwritten/assemblies/` | Saved Assembly Studio drafts and embedded module snapshots |
| `generated/` | Imported structures, schematics and assembly datapack exports |

Missing generation files, changed pinned resources, unsupported data versions or missing/mismatched adapters stop loading. Restore the matching files or compatible provider version; do not delete or edit the snapshot to force a load. Keep the exact original bundle available for the first upgrade of a 0.6.0 locked world, as described in [Modpack Developer Tools](Templates-and-Bundling.md).

Worlds Unwritten does not convert between Minecraft data formats. Keep compatible providers and datapacks with the world.

Pack authors should also back up the instance’s `config/worldsunwritten/` directory, including the Preset Studio library, exported bundles and developer configuration. These authoring files are separate from the saved world’s pinned generation archive.

## Upgrading to Worlds Unwritten 2.0

Stop the server, make a complete backup, remove the previous mod JAR and install `Worlds-Unwritten-2.1.0.jar`. Install the same version on every client. Do not install the old and new JAR together.

The primary mod ID, commands and new-world data namespace are `worldsunwritten`. The included legacy support module retains `worldarchitect` and its 1.9 API dependency contract for existing saves and API 1 add-ons. It is part of the same JAR.

Existing worlds keep their original `data/worldarchitect-generation-v1.dat`, `data/worldarchitect/`, `data/worldarchitect-cells/`, island and administration files. Keep those names and every archived bundle intact. New worlds use the corresponding `worldsunwritten` names. The upgrade does not rewrite generation snapshots, template metadata or saved resource IDs. Never combine the two data naming families in one world.

When `config/worldsunwritten/` is absent, the mod imports `config/worldarchitect/` once, verifies the copied bytes and retains the original directory. An existing current configuration takes precedence and is never overwritten. Back up both directories during the upgrade. Older preset archives remain readable without extracting or renaming their manifest. Re-exporting an edited project creates a new archive; it does not replace the archive pinned to an existing world.

Commands now start with `/worldsunwritten`, for example `/worldsunwritten island`. Existing `/wa_*` commands remain aliases with the same permissions and lock checks.

Version 2.1 adds opt-in cell campaigns. Existing version-one cell profiles and journals keep their original format, terrain, inventories and objectives. Do not add a `campaign` field to a saved world or replace its journal with one from another world. New version-two journals also hold delivery receipts, connection states and permanent End reservations; back up the complete world and player data together.
