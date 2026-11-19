# Structures

Add structures under a solid dimension's **Features → Structures** controls. Keep Minecraft's **Generate Structures** option enabled. Placements still need compatible terrain and an allowed biome.

Built-in support covers `minecraft:desert_pyramid`, `minecraft:jungle_pyramid`, `minecraft:swamp_hut` and `minecraft:end_city`. Other structure types need a compatible adapter. Supported structures retain native pieces and `/locate structure` support.

For an Overworld End City, select suitable solid terrain and a biome accepted by its definition, then add `minecraft:end_city`. For a desert pyramid in the End, use stable sandstone terrain and a desert biome outside the dragon arena.

Spacing and separation are measured in chunks. Spacing must be 4–128; separation must be 3–127 and smaller than spacing. Biome, height and slope checks still decide whether each candidate can generate.

Modular structures combine named templates at offsets with optional seeded rotation. Assemblies support up to 16 modules, 96 blocks per axis and 65,536 occupied cells, with `terrain_adaptation: none`. The entire assembly must pass its placement conditions. A rule filter or End arena overlap rejects the assembly as a whole.

Read [Structure Library](Structure-Library.md) for capture, imports, markers and generation exports. [Example Presets](Example-Presets.md) includes complete modular datapacks. Saved worlds check their original structure definitions and required native templates when opened.
