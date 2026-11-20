# Regional terrain

Open a dimension's **Single Block** or **Layered** editor, choose **Terrain Shape**, then **Regional terrain**. Add a rectangle or circle, enter world-coordinate bounds, and set elevation, relief, landform and an optional registered biome. The map shows coordinate masks: left-click selects a region and right-click moves its draft bounds. **Map view** changes the centre and scale. Use the terrain preview to inspect the resulting heights.

Regions remain in the editor draft until **Done**. **Cancel** discards changes to the region list. Applying them selects generator version 3 for that dimension. Existing worlds keep their saved generator and settings; version 1 and version 2 profiles retain their algorithms and encoding.

Higher priorities win. Equal priorities retain list order, with the earlier region taking precedence. Transition width feathers heights inward from the mask boundary using a smooth curve. A circle uses the midpoint and radius of square bounds. Biomes switch at half transition weight and are stored on Minecraft's four-block biome grid; the game's block-position biome lookup also applies its usual boundary fuzz.

Version 3 requires a solid base landform. Floating Islands cannot be a regional base or regional target. Each profile supports up to 32 uniquely named regions. Names use 1–32 lowercase letters, digits, underscores or hyphens; priority is −1024 to 1024. Bounds stay within ±29,999,984, with a span of at least two blocks. A transition is 0–4096 blocks and cannot exceed half the smaller span.

The JSON `profile` keeps its elevation, relief and `terrain` fields, sets `generator_version` to `3`, and adds a `terrain_regions` list. Each region contains `name`, `mask` (`rectangle` or `circle`), `min_x`, `min_z`, `max_x`, `max_z`, `elevation`, `relief` and `terrain`. Optional fields are `priority`, `transition` and `biome`. See the [regional example](https://github.com/GWSheridan/World-Architect/tree/v1.4.0/examples/regional-terrain) for a complete preset.

Region heights are sampled independently from coordinates and the world seed. They do not read neighbouring chunks. The preview shows base terrain and stored biome choices before features, structures and ticking changes. [World Generation Rules](World-Generation-Rules.md) can further change the generated result.

In 1.4, rule conditions can reference these region names as geometric masks or priority winners. Boundaries are inclusive and independent of feathered height/biome weights. Duplicate or reorder regions through **History / organize**; ties still use earlier list order. Removing or renaming a region referenced by a rule makes the draft invalid until its references are updated. Existing saved profiles retain their original names and generation. See [Draft History](Draft-History.md) and [shared-region rules](World-Generation-Rules.md).
