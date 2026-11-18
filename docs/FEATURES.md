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

The current native structure IDs are `minecraft:desert_pyramid`, `minecraft:jungle_pyramid`, and `minecraft:swamp_hut`. They retain native generation, pieces, biome checks, and `/locate structure` support. Choose a compatible biome and suitable terrain in the destination dimension, and keep Minecraft's **Generate Structures** option enabled.

To generate a desert pyramid in the End, choose a flat sandstone Single Block profile for the End. In **Features & Structures → Biomes**, enter `minecraft:desert`. Under **Structures**, add `minecraft:desert_pyramid` and choose its spacing and separation. Custom End terrain disables the dragon fight and the exit portal that fight creates.

The [dedicated-server demo](../examples/features-and-structures) includes a desert pyramid in the End, a Nether lava sea, and an Overworld with gold in forests, amethyst decorations at Y 57 in plains, and dry caves.

## Save format

The optional `dimension_profile.content` contains this dimension's rules. Empty content retains the earlier generator encoding and behavior. Version 1 content stores biome IDs and the layout seed, rule materials and limits, and native structure IDs. The example datapack contains a complete profile.

World Architect records the registered structure definitions, effective structure settings, and resolved permitted biomes in the save's generation snapshot. Startup blocks when those dependencies change. Existing saves use their saved rules after an update to the starting preset. The world seed continues to control terrain, cave, ore, decoration, and structure placement. The separate biome layout seed is initialized from the world seed when the biome editor first opens.
