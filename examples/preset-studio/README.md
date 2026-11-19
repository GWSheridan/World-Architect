# Preset Studio examples

These ready-to-install bundles demonstrate partial and full locks for World Architect **1.0.0**, Minecraft **26.3** and NeoForge **26.3.0.103**. Their source resources are included. Keep the original ZIP bytes when recovering an existing bundled world.

| Bundle | Policy |
| --- | --- |
| `bundles/studio-partial-1.0.0.zip` | Permits the seed, Overworld ore settings and the complete End profile. Locks other profiles and the Overworld rule engine. Includes a registered modular build with a named chest and a safe entry marker. |
| `bundles/studio-skyblock-1.0.0.zip` | Fixes every generation option and dimension profile, including a classic starting island with two personal multiplayer slots. Disables the Structure Library. Includes the embedded island and a native `.nbt` copy. |

Install one ZIP in `config/worldarchitect/bundles` and create `config/worldarchitect/developer.json` with `{"format":1,"bundle":"<filename.zip>"}`. Restart. Leave `level-type`, `level-seed` and `generate-structures` out of new dedicated-server properties to use the bundle defaults (seed 18). These are new-world examples; back up existing worlds before changing an instance's configuration.

For authoring, enable developer mode, put a bundle in `config/worldarchitect/imports`, then use **Import ZIP** in Studio. Choose a new release or **Duplicate** before saving changed versions. The source directories beside the bundles contain their manifest, preset and embedded template resources for inspection.

See the [Studio guide](../../docs/PRESET_STUDIO.md) and [verification procedure](../../docs/TESTING.md).

## 1.1.0 bundles

`bundles/studio-partial-1.1.0.zip` and `bundles/studio-skyblock-1.1.0.zip` retain the same generation and lock policies under new release identities. Their expanded resources are in `partial-1.1.0-source/` and `skyblock-1.1.0-source/`. Earlier ZIPs remain unchanged for existing saves.
