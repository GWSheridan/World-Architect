# World Architect examples

These examples target Minecraft 26.3 and NeoForge 26.3.0.103. Use a **new world** when trying a generation preset. Copy a datapack folder with its `pack.mcmeta` to `<world>/datapacks/` before first generation; configure `initial-enabled-packs=vanilla,file/<folder-name>` on a dedicated server. A bundle uses the separate installation workflow below.

| Example | Purpose |
| --- | --- |
| [Pure Sand](pure-sand/server.properties) | Built-in Single Block server configuration |
| [Universal dimensions](universal-dimensions) | Independent Overworld, Nether and End profiles; `worldarchitect:dimension_demo` |
| [Features and structures](features-and-structures) | Biomes, ores, caves, decorations, fluids and a cross-dimension pyramid; `worldarchitect:features_demo` |
| [Void and Skyblock](skyblock) | `worldarchitect:void_demo` and `worldarchitect:skyblock_demo` |
| [Advanced terrain](advanced-terrain/README.md) | Version 2 landforms, floating terrain and strata |
| [Cross-dimension structures](cross-dimension-structures/README.md) | Overworld End Cities and modular custom-dimension assemblies |
| [World generation rules](worldgen-rules/README.md) | Ordered, filtered material, feature, template and spawn rules |
| [Environment controls](dimension-environment/README.md) | Dimension time, weather, spawning, portals and visuals |
| [Preset Studio](preset-studio/README.md) | Ready-to-install partial and fully locked Skyblock bundles, plus their authoring sources |
| [Locked Skyblock draft](locked-skyblock) | Earlier JSON authoring example and native starting structure |
| [API fixture](api-fixture/README.md) | Separate developer mod demonstrating API 1; not needed for normal play |
| [Performance harness](performance/README.md) | Same-host regression comparison against the tested 0.9.0 binary |

To install a ready-to-use bundle, put **one** of `studio-partial-1.0.0.zip` or `studio-skyblock-1.0.0.zip` in `config/worldarchitect/bundles/`, then write:

```json
{"format":1,"bundle":"studio-skyblock-1.0.0.zip"}
```

to `config/worldarchitect/developer.json`. Restart the instance. Omit `level-type`, `level-seed` and `generate-structures` from a new dedicated server's properties to take the bundle defaults. The partial bundle permits seed changes, Overworld ore edits and the whole End profile; the full Skyblock bundle fixes generation and provides two personal island slots.

The bundle's `1.0.0` is its **preset revision**. It is separate from the mod version. Existing bundle bytes remain unchanged so archived saves retain their fingerprints. See [installation](../docs/INSTALLING.md), [modpack tools](../docs/MODPACKS.md) and [release verification](../docs/MILESTONE_20_VERIFICATION.md).
