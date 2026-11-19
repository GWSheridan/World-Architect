# Backups and upgrades

Stop the server and copy the **complete world directory** before changing mods. Replace the World Architect JAR while keeping compatible Minecraft, NeoForge, provider mods and datapacks. Reopen the copied world first. Existing saves retain their original generation profiles; editing a starting preset changes future worlds.

Keep these files with the world:

| File or directory | Purpose |
| --- | --- |
| `level.dat` and its backup | Native world metadata and the protected-world marker |
| `data/minecraft/world_gen_settings.dat` | Saved seed and dimension generation settings |
| `data/worldarchitect-generation-v1.dat` | Immutable generation snapshot and dependency pins |
| `data/worldarchitect/generation/` | Archived original bundle and its embedded resources |
| `data/worldarchitect-islands-v1.dat` | Personal/team island assignments |
| `data/worldarchitect-administration-v1.dat` | Spawn, protection and environment overrides, when configured |
| The world's generated structures/library files | Captured templates, rules and markers |

Missing generation files, changed pinned resources, unsupported data versions or missing/mismatched adapters stop loading. Restore the matching files or compatible provider version; do not delete or edit the snapshot to force a load. Keep the exact original bundle available for the first upgrade of a 0.6.0 locked world, as described in [Modpack Developer Tools](Templates-and-Bundling.md).

World Architect does not convert between Minecraft data formats. Keep compatible providers and datapacks with the world.
