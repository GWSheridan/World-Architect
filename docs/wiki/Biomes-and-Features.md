# Features and structures

From **Create New World → World → World Architect · Dimensions → Customize**, select a dimension, choose **Single Block** or **Layered**, and open **Features & Structures**. Add rules for that dimension and apply the changes in both the feature editor and the dimension manager. Original Generation uses the original generator's own rules.

| Control | What it configures |
| --- | --- |
| Biomes | A registered biome ID for the whole dimension, or up to 16 IDs for seeded regions. Set the region scale and layout seed when using multiple biomes. An empty list uses the terrain profile's default biome. |
| Ores | Separate vein rules with a registered output block, an optional terrain replacement target, attempts per chunk, vein size, a Y range, and optional biome IDs. Veins replace matching terrain and stop at chunk boundaries. |
| Decorations | Registered stable solid blocks or compatible single-block flowers, with attempts per chunk, placement chance, optional biome IDs, and optional minimum and maximum Y. Each attempt checks for air and a suitable supporting block. |
| Caves | A winding tunnel network with a Y range and radius. The generator keeps a roof and floor. Cave rules require stable terrain materials. |
| Fluids | A water or lava sea with a configurable level and optional flooding of carved cavities below that level. |
| Structures | Independent placement rules for compatible native structures. Each rule sets its spacing and separation in chunks. |

With an empty rule list, the generator skips the corresponding feature. Assigning a biome changes the biome data and associated environment or gameplay rules.

Enter comma-separated biome IDs on an ore or decoration rule to limit its placement. Leave the field empty to allow every biome. Placement checks the stored generation biome at each block position, including across biome borders.

These editors configure World Architect's generation pipeline. Native tree features, jigsaw structures, and generation rules supplied by the original generator have separate compatibility requirements.

## Cross-dimension placement

The supported native structure IDs are `minecraft:desert_pyramid`, `minecraft:jungle_pyramid`, `minecraft:swamp_hut`, and `minecraft:end_city`. They retain native generation, pieces, biome checks, and `/locate structure` support. Choose a compatible biome and suitable terrain in the destination dimension, and keep Minecraft's **Generate Structures** option enabled.

To generate a desert pyramid in the End, choose a flat sandstone Single Block profile for the End. In **Features & Structures → Biomes**, enter `minecraft:desert`. Under **Structures**, add `minecraft:desert_pyramid` and choose its spacing and separation. Enable **Dragon fight** to keep a native central arena; see [End progression](End-Progression.md).

The [dedicated-server demo](https://github.com/GWSheridan/World-Architect/tree/v1.5.0/examples/features-and-structures) includes a desert pyramid in the End, a Nether lava sea, and an Overworld with gold in forests, amethyst decorations at Y 57 in plains, and dry caves.

For an End City in the Overworld, choose solid terrain with its surface at least Y 60 and enable `minecraft:end_highlands` or `minecraft:end_midlands`. Add `minecraft:end_city` under **Structures**. The [cross-dimension example](https://github.com/GWSheridan/World-Architect/tree/v1.5.0/examples/cross-dimension-structures) includes an Overworld End City and modular templates in two custom dimensions.

## Modular template generation

Export saved library templates into a generation datapack, then register its structure ID under the destination dimension's **Structures** rules. Spacing and separation use chunks. The assembly's bounding span must fit between neighbouring candidates. The native pipeline handles chunk references, `/locate structure`, and piece persistence.

```text
/wa_structure export_generation builds:house pack:village minecraft:plains
/wa_structure append_generation builds:house pack:village east 16 0 0
/wa_structure append_generation builds:tower pack:village south 0 0 24
```

These commands write `world/datapacks/worldarchitect-generated/data/<namespace>/worldgen/structure/<path>.json`. Install that datapack in a new world and enable `file/worldarchitect-generated`. Templates are embedded in the definition. Editing the source library entry leaves those copies intact.

Each module has a unique name, a nonnegative local offset, and a copied template with its saved placement rules and spawn markers. Saved rotations and mirrors carry through to the copy. The whole assembly can receive a seeded random rotation. Assemblies support up to 16 modules, 65,536 occupied cells, and 96 blocks per axis. Occupied cells and marker clearance must remain free of overlap.

The structure JSON supports `y_offset` from −64 to 64, `max_slope` from 0 to 32, and `random_rotation`. The origin follows the sampled solid surface at the candidate chunk. Every module retains its dimension, biome, height, support, and collision rules. **Supported** requires a safe block below each occupied column and honours the assembly slope limit. **Free** allows suspended placement. **Buried** requires terrain above the occupied cells. **Air** requires empty destination cells. **Matching** also allows identical terrain blocks without block entities.

Placement validates the complete assembly against the destination generator's base terrain before creating a start. It checks the allowed biomes throughout the occupied footprint. Rejected candidates create no module pieces. Native structures and decorations run through Minecraft's generation stages.

Inspect saved generated markers or travel to one with an operator account. Supply a location inside the generated structure's bounding box. Marker travel checks the current floor, headroom, loaded chunks, and world border.

```text
/execute in pack:dimension run wa_structure generated_markers pack:village 1 81 1
/execute in pack:dimension run wa_structure generated_spawn pack:village 1 81 1 east entry
```
## Save format

The optional `dimension_profile.content` contains this dimension's rules. Empty content retains the earlier generator encoding and behavior. Version 1 content stores biome IDs and the layout seed, rule materials and limits, and native structure IDs. The example datapack contains a complete profile.

World Architect records the registered structure definitions, effective structure settings, and resolved permitted biomes in the save's generation snapshot. Startup blocks when those dependencies change. Existing saves use their saved rules after an update to the starting preset. The world seed continues to control terrain, cave, ore, decoration, and structure placement. The separate biome layout seed is initialized from the world seed when the biome editor first opens.

Modular definitions pin the embedded templates, rules, and markers. Generated pieces also save their own transformed template and marker data. End City worlds pin native generation resources, including the native template files. A changed registered definition or pinned resource stops startup before chunk generation.

## Native features and the End arena

Native feature selections need a registered feature adapter. Their origins obey the selected biome and height filters; the provider controls the feature's writes. Selected adapters and definitions are pinned with the world. Fixed rule-engine actions provide per-block spatial filtering.

With End progression enabled, the central reservation uses native generation. Custom material, biome, cave, fluid, ore and decoration rules apply outside it. Custom structures cannot overlap it. Native gateway travel can create a landing island outside the reservation.
