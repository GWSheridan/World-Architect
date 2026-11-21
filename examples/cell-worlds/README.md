# Cell world starters

Six editable presets for World Architect 1.9.0, Minecraft 26.3 and NeoForge 26.3.0.103:

| Bundle | Preset ID |
| --- | --- |
| Hex Continents | `worldarchitect_examples:hex_continents` |
| Hex Skyblock | `worldarchitect_examples:hex_skyblock` |
| Expanding One Chunk | `worldarchitect_examples:expanding_one_chunk` |
| Resource Archipelago | `worldarchitect_examples:resource_archipelago` |
| Biome Mosaic | `worldarchitect_examples:biome_mosaic` |
| Underground Start | `worldarchitect_examples:underground_start` |

Copy one ZIP from `bundles/` to `config/worldarchitect/bundles/`. In `config/worldarchitect/developer.json`, select its filename:

```json
{"format":1,"developer_mode":false,"bundle":"expanding-one-chunk-1.9.0.zip"}
```

Use matching files on clients and dedicated servers, then create a new world. These examples are unlocked so pack authors can customize them. Enable authoring in a separate instance, use Builder to edit cell types, islands and objectives, then choose locks before exporting a distribution for players.

The expanded `datapacks/` folders expose each preset and its embedded templates for editing. Install one folder as a datapack when testing raw resources; choose its preset ID through `level-type`. Generation defaults for a bundled server are supplied by the bundle, so omit conflicting generation properties when installing it.

The starting cell contains wood, saplings and basic supplies. Expanding One Chunk and Underground Start use advancement objectives in a shared territory. The other examples generate all cells normally. All retain the vanilla Nether and End.

See [Cell worlds and chunk progression](../../docs/wiki/Cell-Worlds.md) for team/player territories, quest signals, placement limits, administration and save files.
