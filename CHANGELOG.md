# Changelog

## 1.0.0

- Added a native End arena option so new custom End worlds can keep the dragon fight, exit portal, gateways and crystal respawning.
- Added advanced landforms, floating terrain, biome-aware strata and seeded previews.
- Added block-state search, saved templates, schematic imports, modular structures and cross-dimension End Cities.
- Added multiplayer island administration, safe spawn policies, optional protection and per-dimension environment controls.
- Added ordered generation rules and Preset Studio, with versioned exports, embedded templates, dependency reports and partial/full locks.
- Added public extension APIs for dimensions, structures, features, blocks, spawning, environments and presets.
- Fixed partial-lock validation when all seven feature editors are enabled.
- Improved layered generation performance and retained existing saved-world generation contracts.
- Added the player, server and modpack developer wiki, new examples, and source/distribution downloads with SHA-256 checksums.

Existing worlds retain their saved profiles and previous End behaviour. Create a new world to use changed defaults or enable the native End arena.

## 0.9.0

- Fixed intermittent startup failures in presets referencing structures before their registries were ready.
- Added a protected-world marker so missing generation files stop loading safely.
- Added a recovery message for missing archived bundles in single-player worlds.
- Added production JAR, buildable source and complete release downloads.
