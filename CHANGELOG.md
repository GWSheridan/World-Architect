# Changelog

## 1.2.0

- Added Assembly Studio for visual modular structures, transformed spawn markers, saved drafts and validated datapack or preset exports.
- Added a Rule Inspector with condition overlays, coordinate explanations, dependency order and conservative budget and shadow warnings.
- Added a server administration panel for island assignments, teams, spawn policies and protection, with staged confirmations and current-state checks.
- Cached complete assembly previews and retained the existing module, cell and session budgets.
- Fixed overlapping rule-editor navigation controls at 320 × 240 and kept the new confirmation screens usable after resizing.
- Fixed the Windows benchmark launcher’s toolchain argument handling.
- Added a versioned assembly example and expanded dedicated-server fixtures and combined public-mod comparisons against 1.1.0.
- Retained API 1 and existing generation, preset, template, island and administration formats. Matching 1.2 clients and servers are required for authoring networking.

## 1.1.0

- Added starter recipes and a searchable preset library, with explicit draft-discard prompts.
- Added rotatable base-terrain previews, vertical cross-sections and coordinate navigation.
- Added a 3D template viewer, staged structure placement, temporary world ghosts and guarded session undo.
- Added administrator-only local diagnostics and portable dedicated-server validation fixtures.
- Fixed Unix wrapper line endings so clean source checkouts run on Linux.
- Retained API 1, preset and template formats, saved generation settings and native End policies.

Release checks cover Windows/Linux dedicated servers, two-client placement, a thirty-minute multiplayer soak, native End progression, older-save protection, six clean example installations and three same-host performance pairs. Existing 0.9.0 and 1.0.0 releases remain unchanged.

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
