# Development changelog

## 0.9.0 · Final Integration and Release

- Fixed intermittent startup failures in presets that referenced a structure before its registry had finished loading. The server checks placement support after the registry is ready.
- Added a protected-world flag to `level.dat` so the server stops safely even when both generation files are missing. A missing bundle archive now shows its recovery path in the single-player menu.
- Added release and source packaging tasks. The release contains the JAR, buildable sources, docs, example presets, and the exported locked Skyblock bundle.

## 0.8.0-alpha · Multiplayer and Dedicated Server Support

- Skyblock joins now explain when personal islands are full or no safe initial spawn is available.
- Extended the void world audit to verify exact player UUID and island slot pairs.
- Dedicated server tests verified player and operator permissions, full-lock enforcement, concurrent exploration in the Overworld and Nether, and original island assignments after upgrading a multiplayer save from 0.7.0.

## 0.7.0-alpha · Permanent Save Protection

- Made newly written generation snapshots immutable and added compatibility checks for effective dimensions, biome definitions, feature flags, and native world-generation resources. Existing generation snapshots remain readable.
- Archived the original bundle inside the world, including its native structures and any lock policy. Older locked saves archive the matching original on first successful launch.
- Tested existing and newly explored chunks on a dedicated server after changing starting presets and bundle templates. A 0.6.0 Skyblock save reopened with its generation files and island allocations byte-identical.

## 0.6.0-alpha · Modpack Developer Tools

- Added developer-mode export for versioned preset ZIPs with required mods, world options, native structures, and embedded starting islands.
- Added unlocked, partial, and full modes. Partial policies can permit whole dimensions, individual feature groups, or selected generation options.
- Added default preset selection in the creation screen and dedicated-server validation before generation. Locked saves pin their original bundle and validate the active datapacks.
- Verified a fresh dedicated instance using the release JAR, the bundled island spawn, accepted and rejected partial edits, datapack reload enforcement, and rejection of missing or revised bundles on saved-world copies.

## 0.5.0-alpha · Structure Library

- Added two-corner selection, capture, and a world-local library with top-down and layer previews. Native Minecraft templates import from world files or datapacks and export with library metadata.
- Added placement rules for dimension, biome, height, support, and collisions. Rotation and mirroring transform block states and the position and facing of named spawn markers.
- Added safe decorative metadata for signs, banners, and display names. Imports and captures omit inventories, entities, and active sign actions.
- Dedicated-server tests checked a cross-chunk capture, native round-trip, transformed placement, and reload of the placed blocks and block entities. A client verified the preview and tested a blocked marker followed by a successful arrival.

## 0.4.0-alpha · Void and Skyblock

- Added per-dimension Void and Skyblock profiles. Pure void suppresses natural terrain, the bonus chest, and automatic portal arrivals that would place blocks. An empty Overworld requires explicit consent.
- Skyblock embeds a built-in or imported structure with a local spawn marker in the world settings. Players without a valid personal respawn point use a checked location on their assigned island or the shared island.
- Personal Overworld slots are assigned by UUID and saved atomically. A missing allocation file stops startup for an existing personal-island world.
- A dedicated server verified air-only chunks in all three vanilla dimensions. Two clients verified separate island assignment, fallback from a blocked marker, and persisted assignments and player positions after restart. A copy of the 0.3.1 feature save reopened with byte-identical generation files and passed its strict generation audit on fresh chunks.

## 0.3.1-alpha · Feature filters

- Ore and decoration rules accept per-rule biome IDs. Decorations also accept optional surface Y bounds.
- The client accepted valid filters and rejected unknown biome IDs and inverted decoration bounds.
- Dedicated server audits verified gold only in forests and amethyst only in plains at Y 57. Fresh chunks still followed the saved filters after those filters were removed from the starting preset. The settings and snapshot checksums matched across restart.
- A 0.3.0 fixture reopened and generated new chunks with its original unfiltered rules and unchanged generation files. The three audits covered 1,376,256 terrain positions.

## 0.3.0-alpha · Features and Structures

- Added per-dimension editors for fixed or regional biomes, ore veins with replacement targets, surface decorations, caves, water and lava, and native structures. The solid generators apply explicit rules while keeping unselected generation disabled.
- Added configurable spacing and separation for native desert pyramids, jungle temples, and swamp huts. Dedicated server tests located a desert pyramid in the End, checked its pieces and trap blocks, and located the jungle temple and swamp hut in custom dimensions. A separate test confirmed water in a sea and inside a flooded cave.
- The client acceptance run exercised all six editors and passed their settings to the create-world callback. It also checked invalid ore height, a structure without a compatible biome, and sand selected with cave generation.
- Dedicated-server block audits checked 917,504 terrain positions across the initial feature world and newly explored chunks after removing its feature rules from the global preset. Both saved generation files retained their SHA-256 checksums.
- Copies of the 0.1.0 Pure Sand and 0.2.0 multidimension saves reopened with unchanged generation files. Audits checked 786,432 positions across existing and newly generated legacy chunks.
- Saved native structures pin their registered and effective definitions and resolved biome sets. A changed pyramid definition caused startup to reject the test copy.

## 0.2.0-alpha · Universal Dimensions

- Added a searchable dimension manager backed by the world preset and active dimension registry. The Overworld, Nether, End, and compatible custom dimensions can independently retain Original Generation or use Single Block or Layered terrain.
- Published a NeoForge dimension adapter registry. Adapters declare the modes and preferred biome for an exact registry ID. Saved profiles pin the selected adapter version and the dimension's build range.
- Custom terrain in a dragon-fight dimension suppresses the fight and its generated portal. Original Generation retains the vanilla behavior.
- The client acceptance test exercised search, independent edits, Cancel, persistence through a preset refresh, and the creation callback. A separate test mod supplied two dimensions. Its adapter enabled Single Block for one dimension, while the other kept its original flat generator and obsidian terrain.
- The dedicated server generated independent Overworld, Nether, End, and adapted profiles. After a datapack preset change, fresh chunks still matched the saved profiles. The settings and snapshot files kept their SHA-256 checksums.
- Copies of the 0.1.0 sand and layered worlds reopened and generated new chunks using the original profiles, with byte-identical generation settings and snapshots. Across both old-world copies and the restarted demo, the audits compared 983,040 block positions.
- A missing required adapter and an unsupported generator replacement each blocked startup. The rejected existing save's generation files and sampled region kept their checksums.

## 0.1.0-alpha

- Pinned Minecraft 26.3, NeoForge 26.3.0.103, ModDevGradle 2.0.148, Java 25, and Gradle 9.2.1. The development asset downloader also uses Java 21.
- Added the World Architect world type, a Single Block editor, and a strict server-side generator. Its version 1 codec stores the terrain material, elevation, relief, and fixed biome per configured dimension. The seed is recorded in Minecraft's world options.
- Added a world-specific generation snapshot and a pre-load check at the vanilla settings fallback. Its test scope is the 26.3 save format. World Architect has no automatic migration.
- The build and two JUnit terrain tests passed. A dedicated 26.3 server audit verified 294,912 positions across three Pure Sand chunks, including negative coordinates and a previously unexplored chunk after changing the preset default to stone. The saved settings and snapshot kept their SHA-256 checksums across the restart.
- A copied save missing its generation settings was refused before startup. The original snapshot and sampled region file kept their checksums, and the settings file was not recreated.
- The development client acceptance run verified the World Type entry, material validation, Cancel, Done, editor reopening, and delivery of the selected generator to the create-world callback. It rendered the editor and checked widget bounds at 320 × 180 logical pixels.
- Added Flat, Hills, and Highlands with editable elevation and height variation. The new Layered Overworld generator shares the version 1 terrain algorithm and adds editable fixed-depth layers and a column preview. A server audit verified 196,608 positions across two layered chunks on opposite sides of the origin.
- A separate fixture mod supplied the base material for a layered world. The audit verified 196,608 positions, including those modded blocks. Reopening a copy without the fixture mod named the missing block and stopped before startup. The generation files and sampled region kept their checksums.
- The layered client acceptance run verified thickness validation, layer addition, removal and reordering, applying Flat terrain, and the creation callback. A preset-list refresh retained an edited World Architect profile.

The [specification](docs/SPECIFICATION.md) tracks the remaining terrain and layer controls, universal dimensions, feature and structure systems, Skyblock, modpack locks, multiplayer, and release testing.
