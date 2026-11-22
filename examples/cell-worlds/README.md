# Cell world starters

Six editable presets for Worlds Unwritten 2.0.0, Minecraft 26.3 and NeoForge 26.3.0.103:

| Bundle | Preset ID |
| --- | --- |
| Hex Continents | `worldsunwritten_examples:hex_continents` |
| Hex Skyblock | `worldsunwritten_examples:hex_skyblock` |
| Expanding One Chunk | `worldsunwritten_examples:expanding_one_chunk` |
| Resource Archipelago | `worldsunwritten_examples:resource_archipelago` |
| Biome Mosaic | `worldsunwritten_examples:biome_mosaic` |
| Underground Start | `worldsunwritten_examples:underground_start` |

Copy one ZIP from `bundles/` to `config/worldsunwritten/bundles/`. In `config/worldsunwritten/developer.json`, select its filename:

```json
{"format":1,"developer_mode":false,"bundle":"expanding-one-chunk-2.0.0.zip"}
```

Use matching files on clients and dedicated servers, then create a new world. These examples are unlocked so pack authors can customize them. Enable authoring in a separate instance, use Builder to edit cell types, islands and objectives, then choose locks before exporting a distribution for players.

The expanded `datapacks/` folders expose each preset and its embedded templates for editing. Install one folder as a datapack when testing raw resources; choose its preset ID through `level-type`. Generation defaults for a bundled server are supplied by the bundle, so omit conflicting generation properties when installing it.

The starting cell contains wood, saplings and basic supplies. Expanding One Chunk and Underground Start use advancement objectives in a shared territory. The other examples generate all cells normally. All retain the vanilla Nether and End.

See [Cell worlds and chunk progression](../../docs/wiki/Cell-Worlds.md) for team/player territories, quest signals, placement limits, administration and save files.
