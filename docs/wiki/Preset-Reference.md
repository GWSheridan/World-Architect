# Preset reference

A preset's `dimensions` object is keyed by dimension ID. Each entry contains a dimension `type` and `generator`. A datapack stores it at `data/<namespace>/worldgen/world_preset/<name>.json`; bundles use `preset.json` beside a manifest.

| Generator | Fields |
| --- | --- |
| `worldarchitect:single_block` | `biome`, `profile`, optional `dimension_profile` |
| `worldarchitect:layered` | `biome`, `profile`, optional `dimension_profile` |
| `worldarchitect:void` | `biome`, `dimension`, optional `island`, `allow_unsafe_spawn` (default false) |

Solid profiles include `generator_version`, `elevation` and `relief`. Single Block uses `material`; Layered uses `base_material`, `layers` and optional `strata`. Layers specify `material` and `depth`. Version 2 adds a `terrain` object with `landform`, optional `scale` (32–2048) and `seed_salt`. Read [Terrain and Dimensions](Terrain-and-Dimensions.md) for landforms and strata.

A dimension profile contains `min_y`, `height`, optional `adapter`, `content`, `rule_engine` and `end_progression`. Its range must match the dimension type and use 16-block alignment. Adapter bindings specify the exact dimension ID and version.

`content` supports `biomes`, `biome_scale` (16–1024, default 128), `biome_seed` (default 0), `ores`, `decorations`, `caves`, `sea`, `structures` and `native_features`. Lists default to empty. Height ranges must fit the dimension, and feature biomes must be enabled. Read [Biomes and Features](Biomes-and-Features.md) for behaviour and the [content codecs](https://github.com/GWSheridan/World-Architect/blob/v1.2.0/src/main/java/com/gwsheridan/worldarchitect/config/ContentProfile.java) for field-level bounds/defaults.

An island specifies `template`, `origin_y`, `spacing` (64–4096, multiple of 16, default 128) and `personal_slots` (0–4095, default 0). Personal allocations require Overworld Skyblock. An empty Overworld requires a safe island or explicit unsafe spawning. See [Void and Skyblock](Void-and-Skyblock.md).

`end_progression: {"version":1,"mode":"vanilla_arena"}` enables the native central arena. Omit the field for the previous disabled policy. [End Progression](End-Progression.md) describes the reservation and requirements.

The manifest declares format, preset ID, title, description, author, release, lock, world options, editable dimensions/features/options, required mods and Structure Library permission. Use the complete [example manifests](Example-Presets.md) as a starting point, then validate them in Studio. [World Generation Rules](World-Generation-Rules.md) documents ordered actions and filters.

## Field details

All Y bounds are inclusive and must fit the selected dimension. Biome filters default to every enabled biome when omitted. Biome lists contain at most 16 registered IDs.

| Content rule | Required fields | Optional fields and limits |
| --- | --- | --- |
| Ore | `material`, `attempts` (1–32), `size` (1–64), `min_y`, `max_y` | `target` (base terrain), `biomes` (empty) |
| Decoration | `material`, `attempts` (1–32), `chance` (1–100 percent) | `biomes` (empty), `min_y`, `max_y` (dimension bounds) |
| Caves | `min_y`, `max_y`, `radius` (1–12) | Stable terrain required |
| Sea | `fluid` (`water` or `lava`), `level` | `flood_caves` (false); flooding requires caves |
| Structure | `structure`, `spacing` (4–128), `separation` (3–127) | Separation must be smaller than spacing |
| Native feature | `feature`, `attempts` (1–8), `min_y`, `max_y` | `surface` (false), `biomes` (empty); compatible adapter required |

Each feature list has at most 16 entries. Native features have a combined per-chunk work budget of 65,536 adapter units. A sea level must leave three blocks of build headroom. Decoration materials must be stable supported solids or compatible single-block flowers.

Solid terrain elevation is −2032–2028 and relief is 0–80, further constrained by the dimension. Layered profiles allow 32 layers with a combined depth of at most 383. Strata require generator version 2 and allow 16 ordered entries. Each entry specifies `material`, `anchor` (`height` or `depth`), `from`, `to`, optional `biomes` and `transition` (0–32, default 0). Depth bounds are nonnegative; first matching entries win.

## Bundle manifest

Required keys are `format` (1), `preset`, `title`, `description`, `author`, `release`, `lock` and `options`. The preset ID uses a non-Minecraft namespace. Title is 1–80 characters, description up to 512, and author 1–64. Release names are 1–40 characters using letters, digits, dots, underscores or hyphens, beginning with a letter or digit.

Optional keys are `editable_dimensions` (empty, up to 128), `editable_features` (empty), `editable_options` (empty), `required_mods` (empty, up to 256) and `allow_structure_library` (true). Edit lists belong only to partial locks; a partial lock must allow at least one edit. Give a dimension either whole-dimension permission or feature permissions.

Feature categories are `biomes`, `ores`, `decorations`, `caves`, `fluids`, `structures` and `native_features`. The Minecraft `options` object and editable world-option names use `seed`, `generate_structures` and `bonus_chest`. Use Studio to produce a validated manifest and review its dependency report before release.
