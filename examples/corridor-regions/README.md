# Corridor regions

Install `bundles/corridor-regions-1.7.0.zip` through `config/worldsunwritten/developer.json`:

```json
{"format":1,"bundle":"corridor-regions-1.7.0.zip"}
```

The Overworld uses generator version 6. A rounded path bends through a flat meadow. Its named region selects diamond surface material and a desert biome, while meadow-only ore and decoration rules exclude the corridor. Modular markers use the same geometric filter. Nether and End generation remain native.

The `corridor-regions-1.7.0-source` directory contains the complete unpacked bundle. Open its preset in Studio to change points, radius, transitions and priorities. See [Regional Terrain](../../docs/wiki/Regional-Terrain.md) for limits and editing controls. Install the same archive on clients and servers. Existing worlds retain their archived generation.
