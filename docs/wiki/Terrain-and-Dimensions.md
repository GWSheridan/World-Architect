# Terrain and dimensions

Each registered dimension has an independent profile. **Original Generation** keeps the native or modded generator. **Single Block** fills the terrain with one chosen block state. **Layered** adds surface-relative layers over a base material. **Void** leaves chunks empty; **Skyblock** adds template islands.

The dimension type still controls build limits and coordinate scaling. Specialised modded generators need a compatible adapter before replacement modes become available. Choose Original Generation when no replacement is offered.

The block picker searches names and registry IDs, supports mod filters and block-state properties, and keeps local favourites and recent choices. Falling materials cannot support caves or floating islands.


Open **Terrain Shape** in Single Block or Layered terrain and choose a version 2 landform. Set its elevation, height variation, scale in blocks, and an optional terrain seed offset. The seed offset changes the terrain within that dimension while retaining the world seed for other generation. Choose **Classic (version 1)** for an original heightfield.

Mountains and valleys use broad ridges and cuts. Plateaus and canyons use steeper elevation transitions. Basins and craters form depressions, with raised rims around craters. Continents and archipelagos use large-scale land and lower terrain. Add water under **Features → Fluids** to flood their lower areas. Floating islands generate suspended shelves with air beneath and between them. Use a stable block such as stone for their material.

For a new floating-island Overworld, the server looks for a safe surface near the origin and uses it as the initial spawn.

In Layered terrain, **Strata Rules** can override the normal surface layers and base. Add a block state, an anchor, inclusive bounds, and optionally a comma-separated list of biomes enabled in that dimension. The first matching rule wins. A `height` anchor uses absolute world Y. A `depth` anchor counts down from the highest terrain block, with zero at the surface. Set a transition from 0 to 32 blocks to shift that band’s bounds smoothly using the terrain seed.

The [advanced terrain example](https://github.com/GWSheridan/Worlds-Unwritten/tree/v2.0.0/examples/advanced-terrain) includes forest-specific moss caps, a wavering calcite band, an absolute deepslate layer, Nether plateaus, and floating islands in the End. Install it as a datapack in a new test world and set `level-type=worldsunwritten\:advanced_terrain`, `level-seed=194723`, and `initial-enabled-packs=vanilla,file/advanced-terrain` in `server.properties`. Set `generate-structures=false` for the terrain audit.

In datapack JSON, set `generator_version` to `2` and add a `terrain` object with `landform`, optional `scale` (32–2048), and optional `seed_salt`. Add Layered `strata` to the same profile. Existing saves retain their stored generator version, materials, and seed.

For coordinate masks, priority and feathered height transitions, see [Regional Terrain](Regional-Terrain.md). It is an opt-in version 3 profile for new worlds.
