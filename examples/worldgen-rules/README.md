# Floating Sky City rule example

Copy this directory to `world/datapacks/worldgen-rules` before creating a **new** dedicated-server world. Install Worlds Unwritten 0.16.0 and NeoForge 26.3.0.103 for Minecraft 26.3. Set:

```properties
level-seed=18
level-type=wa_example\:rule_city
initial-enabled-packs=vanilla,file/worldgen-rules
```

The seed is significant: the example requires seed 18. The Overworld has floating stone terrain, gold/lapis regions, filtered redstone, amethyst decorations and an eight-module city at chunk 0,0. Its 2,252 template cells include chest metadata and eight entry markers. New players and death respawns use the main entry marker. Nether and End are empty; `wa_example:annex` is a managed flat dimension.

`lower_city` is a competing quartz variant with lower priority. Negative rules demonstrate rejected mod, dimension, biome, height and seed conditions. Amethyst requires a 12-block radius and a seeded coordinate partition. Ore writes depend on the blue material rule's conditions.

Inspect with `/worldsunwritten rules minecraft:overworld explain 1 94 1` and, as an operator, `/worldsunwritten structure generated_markers wa_example:rule_city 1 98 1`.

See the [rule engine guide](../../docs/WORLDGEN_RULES.md) for ordering, dependency semantics, budgets and save protection. Changing this preset affects new worlds. Existing worlds retain their saved rules and require unchanged registered structure definitions.
