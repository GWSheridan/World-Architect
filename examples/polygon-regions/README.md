# Polygon regions

Install `bundles/polygon-regions-1.5.0.zip` in `config/worldarchitect/bundles/` and select it through `developer.json` as described in the [modpack guide](../../docs/MODPACKS.md). Create a new world with matching World Architect 1.5 clients and server.

Preset ID: `worldarchitect_examples:polygon_regions`. The Overworld uses solid terrain version 4 with a concave polygon inside a larger meadow region. Materials, ores, decorations, a template and spawn conditions share those names. The other dimensions retain their original profiles.

The readable source beside the bundle shows the vertex order, derived bounds and rule conditions. Use [Regional Terrain](../../docs/wiki/Regional-Terrain.md) to edit the outline. Polygons have no holes and do not support floating landforms. Revised exports supply defaults for new worlds; keep an existing world's original archive.
