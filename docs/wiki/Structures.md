# Structures

Add structures under a solid dimension's **Features → Structures** controls. Keep Minecraft's **Generate Structures** option enabled. Placements still need compatible terrain and an allowed biome.

Built-in support covers `minecraft:desert_pyramid`, `minecraft:jungle_pyramid`, `minecraft:swamp_hut` and `minecraft:end_city`. Other structure types need a compatible adapter. Supported structures retain native pieces and `/locate structure` support.

For an Overworld End City, select suitable solid terrain and a biome accepted by its definition, then add `minecraft:end_city`. For a desert pyramid in the End, use stable sandstone terrain and a desert biome outside the dragon arena.

Spacing and separation are measured in chunks. Spacing must be 4–128; separation must be 3–127 and smaller than spacing. Biome, height and slope checks still decide whether each candidate can generate.

Modular structures combine named templates at offsets with optional seeded rotation. Assemblies support up to 16 modules, 96 blocks per axis and 65,536 occupied cells, with `terrain_adaptation: none`. The entire assembly must pass its placement conditions. A rule filter or End arena overlap rejects the assembly as a whole.

Read [Structure Library](Structure-Library.md) for capture, imports, markers and generation exports. [Example Presets](Example-Presets.md) includes complete modular datapacks. Saved worlds check their original structure definitions and required native templates when opened.

## Structure districts in 1.7

Add the modular assemblies under **Features → Structures**, then open **Structure districts**. Select an assembly and edit its district name, exclusion group, weight, minimum distance and optional named region. Members of one district share spacing, separation, region and exclusion settings; changing those shared fields updates its other members together. Weights remain individual. Remove membership to return an assembly to its individual placement grid.

Each native grid site selects one weighted member before checking terrain. If that member cannot fit, the site remains empty. Districts in the same exclusion group reserve higher-ranked potential sites within the larger of their two minimum distances. This calculation uses only the seed and coordinates, so generation order and server restarts cannot change ownership. Reservations are conservative: a candidate that later fails its terrain checks can still exclude a neighbour. These distances apply to candidate origins, rather than the outer bounds of a large building. Use a suitable distance for the size of your assemblies.

A region restriction applies to every occupied template, support and clearance cell. Districts respect template placement rules, markers, structure-generation settings and the native End arena. They support World Architect modular structures, up to eight districts and sixteen selected structures per dimension. Weights are 1–100; minimum distances are 0–32 chunks. Names use 1–32 lowercase letters, digits, underscores or hyphens. Ordinary spacing/separation limits still apply.

District rules require `content.version: 2`. A structure entry adds `"district": {"group": "village", "exclusion": "settlements", "weight": 3, "min_distance": 12, "region": "mainland"}`. Omit `region` for unrestricted terrain. District membership belongs in the dimension's selected structures, rather than a rule-engine template action. Content without districts retains version 1 and its old encoding; older releases refuse version 2.

Use [Site Inspector](Structure-Site-Inspector.md) and bounded site searches to check candidates before installing the pack. See the [district example](https://github.com/GWSheridan/World-Architect/tree/v1.7.0/examples/structure-districts). New settings affect new worlds; existing saves retain their original placement definitions.
