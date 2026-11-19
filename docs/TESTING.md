# World Architect verification

Run `gradlew.bat build` for compilation, packaging, and JUnit tests. The terrain tests cover flat elevation, deterministic seed-dependent variation, and adjacent columns on both sides of the origin and across chunk boundaries.

## World Architect 1.0 release

The [Milestone 20 report](MILESTONE_20_VERIFICATION.md) records final packaged-JAR acceptance. Use two clean clients on the fully locked Skyblock example: verify distinct islands, server permissions, respawn, simultaneous dimension exploration, clean shutdown, reconnect positions/dimensions and unchanged assignments. Try a third new player at capacity. Test the partial/full bundles in clean clients and servers; rejected settings must not reach generation.

Copy older saves before upgrade testing. Record generation, template/archive and allocation hashes; generate absent regions, then use `auditTerrainWorld`, `auditVoidWorld`, `auditRuleWorld`, `auditExtensionWorld` and `auditStudioWorld` as appropriate. On separate marked-world copies, remove both generation files or the archived bundle and check startup refusal without replacement writes. Repeat the API fixture's missing/version cases with its provider still installed.

Run `gradlew.bat assembleRelease` for the production JAR, complete/source ZIPs, tested bundle assets and SHA-256 checksums. Check ZIP integrity, embedded JAR identity, absence of diagnostic classes/caches and a rebuild from the extracted source before preparing a draft release. Do not infer compatibility across Minecraft data versions from these fixtures.

## Public APIs, compatibility and performance

Build and install the [independent API fixture](../examples/api-fixture), then run its `verify` and `persisted` commands on a dedicated server across a restart. Test each missing adapter, incompatible adapter version and incompatible API contract in separate copied saves; compare all protected data and region hashes after rejection. Keep the provider installed when testing missing registrations. The [API contract](EXTENSION_API.md) defines threading, permissions, capabilities, locks and generation dependency guarantees.

```powershell
.\gradlew.bat auditExtensionWorld '-PterrainWorld=build/acceptance/milestone19/fixture/world' '-PextensionChunkOrigin=128'
.\gradlew.bat auditBenchmarkWorlds '-PbenchmarkBaseline=build/acceptance/milestone19/bench-baseline' '-PbenchmarkCandidate=build/acceptance/milestone19/bench-final-candidate'
```

The extension audit checks a six-by-six region in the lab, custom blocks and height-filtered markers, native structure pieces, original generation and dependency pins. Reopen 0.9.0 and 0.17.0 save copies and generate previously absent regions before running the relevant terrain, void and rule audits. Preserve generation files, archived bundles and island ledgers. Run the [same-host benchmark harness](../examples/performance) with the exact tested 0.9.0 JAR; repeat suspicious cases in fresh JVMs. See the [Milestone 19 evidence and results](MILESTONE_19_VERIFICATION.md).

## Preset Studio and modpack authoring

Enable developer mode and open [Preset Studio](PRESET_STUDIO.md) from the new-world dimension manager. At GUI scales 1 and 2, save a partial preset containing a selected modular structure, duplicate it, save a second release, load an earlier revision and import an exported ZIP. Verify that an attempted overwrite preserves the original version's bytes. Change world defaults, Apply to world and cancel the dimension manager: neither profiles nor seed should leak. Repeat and confirm Done applies the generation draft. Export a full Skyblock preset with embedded islands and the Structure Library disabled.

Install each exported ZIP in a clean client profile with no authoring datapack. Check automatic preset/default selection, world-option clamps and per-feature controls. Permit an ore edit under the partial policy. Forge a forbidden terrain/island edit in the new-world selection and invoke creation: the policy dialog must stop it before creating the world. A clean full profile should expose its locked summary and help.

Create clean dedicated instances from the same ZIPs. For the partial preset, allow seed 99, an Overworld ore rule and a complete End replacement through `world-selection.json`; preserve its terrain and rule engine. Test separate forbidden terrain, engine and full-lock seed edits; each must fail without a generation snapshot. After stopping the valid partial server, run:

```powershell
.\gradlew.bat auditStudioWorld '-PterrainWorld=build/acceptance/studio-partial/world'
.\gradlew.bat auditTerrainWorld '-PterrainWorld=build/acceptance/studio-partial/world' '-PterrainDimension=minecraft:the_end' '-PterrainBase=minecraft:end_stone' '-PterrainChunks=0,0'
```

`auditStudioWorld` targets the included Studio partial example with its allowed gold-ore selection (Y 60–70, eight attempts, size six), seed 99, and force-loaded block region `0 0 64 64`. It checks locked base terrain, allowed ores, all 254 persisted modular-template states, chest metadata and safe marker air. The End audit checks the permitted flat End Stone replacement at Y 64.

On a developer-mode authoring server, exercise `/wa_preset capture`, duplicate, import, validate, list and export. Capture an existing release twice and check overwrite refusal. Import a modular bundle with an unknown block: the error must identify the template resource and block, and no revision may be written. A library containing a revision for an unavailable mod must remain listable; loading/validating that revision still requires its dependencies.

Connect two ordinary clients to the installed full Skyblock server. Verify distinct personal islands, forbidden authoring commands, respawn assignments, simultaneous dimension exploration and reconnect positions after restart. Hash the generation files, bundle archive and island ledger; exclude the intentional initial allocation change when first assigning players. Audit the islands and empty distant chunks with `auditVoidWorld`, then repeat after restarting.

For an upgrade check, copy a 0.16.0 rule-engine world and preserve both generation-file hashes. Generate a region absent from the old world, then run `auditRuleWorld` with an extra chunk list, for example `-PruleExtraChunks=-256,256`. Saved rules, rather than changed installed preset defaults, must govern the new chunks.

The 0.17.0 acceptance run passed the authoring UI and both clean consumer profiles at GUI scales 1 and 2. The final packaged JAR passed fresh partial/full dedicated installations, three forbidden-selection startups, native authoring commands and actionable missing-block import rejection. The dedicated bundle audits checked 851,968 positions, including 147 allowed ore cells, all 254 modular cells, chest metadata, a safe marker and 435 island cells. Two simultaneous clients kept distinct islands, respawn policies, saved positions and dimensions after restart; generation files, the bundle archive and island assignments retained their hashes. Post-multiplayer audits checked another 163,840 empty-void positions and the two-owner ledger. A copied 0.16.0 rule world passed 1,769,472 position checks, including a previously absent region, while both generation files retained their hashes. All 45 JUnit tests passed. The distributable matched all final dedicated fixtures by SHA-256 and excluded authoring/test probes. The two example ZIPs matched the exact clean-instance bundles.

## World Generation Rule Engine

Use the [floating Sky City example](../examples/worldgen-rules) on a disposable dedicated server with seed 18. Force-load block region `-64 -32 64 64` and block `1536 1536`, wait for generation, then stop cleanly. Audit the actual saved region files:

```powershell
.\gradlew.bat auditRuleWorld '-PterrainWorld=build/acceptance/rules/world'
```

The audit checks 1,671,168 positions against independent material/feature boundaries, negative dimension/biome/height/seed/mod filters, gold-over-lapis priority, redstone target/dependency checks, the decoration radius and coordinate partition, preserved void air, 2,252 template cells with states and chest metadata, eight safe markers, suppressed lower-priority templates and snapshot equality. It prints a deterministic block-sample digest excluding the decoration region.

For a strict assembly test, reduce the primary city rule's `region.max_x` to 20 in a fresh world. Its origin remains eligible while eastern modules cross the boundary. Run the same audit with `-PruleCityExpected=false`: the entire city must be absent, including the lower-priority fallback. For a budget rejection, replace the rules in another fresh world with 17 ore actions, each with 32 attempts and size 64. Startup must report the per-chunk budget and must not create a generation snapshot.

Join two real clients without operator permissions. Both should start at the main marker `(1.5, 98, 1.5)`. Inspect `/wa_rules minecraft:overworld explain 1 94 1`; a mutation suffix must be rejected. Kill both and verify marker respawns. Move one to unexplored Overworld terrain and the other to `wa_example:annex`, save and stop. Change the installed preset's material, region and elevation, restart, reconnect and verify saved positions/dimensions, then kill both and check marker policies again. Generate the distant chunk after the preset change and repeat the audit. Hash both generation files before/after.

On a disposable stopped-world copy, install a fixture mod with ID `wa_missing_dependency`, pinned as absent. Startup must reject the `rule_mods` change before writing level, generation, template or region files. Compare hashes.

Test the editor at GUI scales 1 and 2: all form pages, incomplete-region rejection, dependency reordering, Cancel and Apply. Reopen earlier version 1 Layered, multiplayer Skyblock with team reservations, and locked 0.9.0 saves separately; generate unexplored chunks and run terrain/void audits. Compare generation, island ledger, administration, library and archive hashes.

The 0.16.0 dedicated acceptance run passed with two concurrent clients, marker-based joins/death respawns, independent dimension exploration, reconnect persistence and read-only command permissions. Changing the installed preset's material, region and elevation left saved rules and newly explored distant terrain intact. Three rule fixtures each passed 1,671,168 position checks: the original city, the competing-template/radius example and whole-assembly rejection. Original/fresh city block-sample hashes matched outside the deliberately changed decoration region. All 2,252 city cells, six named chests and eight markers were verified. Oversized rules failed before snapshot creation; changed mod presence failed before writing existing world data. Earlier Layered, multiplayer Skyblock/End and locked 0.9.0 saves passed 720,896 block checks and preserved 11 protected files, including island assignments, administration, a library template and the bundle archive. Editor validation, ordering, Cancel and Apply passed at GUI scales 1 and 2.

The final build passed all 40 JUnit tests. The distributable JAR matched the packaged artifact used by all six successful dedicated-server fixtures by SHA-256 and contained no acceptance probes. Its checksum is emitted next to `build/libs/worldarchitect-0.16.0.jar`.

A repeat acceptance run on 2026-11-19 passed the two-client permission, respawn and restart scenarios on a separate world copy. Upgrade servers generated chunks in regions confirmed absent from the earlier saves: Layered `(-900,3100)` and Skyblock/locked `(-1100,1100)`, including the Skyblock End. Existing and new chunks passed 720,896 position checks, and all 11 protected-file hashes matched. The original, competing-template, strict-rejection and multiplayer-copy rule worlds each passed their 1,671,168-position audit. All 40 unit tests were forced to run again and passed. The rebuilt JAR retained SHA-256 `6b1076581dca475453dd8bcc2d19371eb8e3122559d553d81684678e5d5a4912` and contained no acceptance probes.

## Block picker and generation preview

Open the World Architect editor at GUI scales 1 and 2. Use Tab to move focus from block search, then filter a modded block by namespace. Search for Oak Log by display name and by registry ID, change its axis, and add it to Favourites. Apply the state and reopen the picker to check Recents. Cancel a different selection and confirm that the material is unchanged. Check the Layered base and an individual layer through the same picker.

Enter a numeric world seed, then open the preview in Dimension Manager. Compare height and biome readings with the server at the same seed and coordinates. For exact surface-height checks, disable decorations and avoid columns altered by placed structures. Check negative coordinates, chunk edges, a Nether sea, and empty columns and reserved spawn markers in Skyblock. Pan and zoom, switch the map view, then verify that Cancel discards dimension edits and Done applies them.

## Dedicated Pure Sand fixture

1. Make a new disposable directory under `build/acceptance`. Copy [the server example](../examples/pure-sand/server.properties) into that directory. Use the normal Minecraft EULA process for the test server.
2. Run `.\gradlew.bat runServer -PtestWorldDirectory=build/acceptance/sand` and wait for `Done` in the console.
3. Run `forceload add 0 0` and `forceload add -16 -16`. These are block coordinates for chunks `(0, 0)` and `(-1, -1)`. Wait for generation and run `stop` to flush the region files.
4. Audit every block position and the saved generator profile with the following command.

```powershell
.\gradlew.bat auditTerrainWorld '-PterrainWorld=build/acceptance/sand/world' '-PterrainChunks=-1,-1;0,0'
```

The audit reads the real region files. It verifies that the columns contain sand down to Y −64 with air above, each surface matches the saved seed and profile, inspected chunks are fully generated, and structure starts are empty. It also checks equality between the saved settings and the original World Architect snapshot.

For the layered preset, create a fresh disposable server with `level-type=worldarchitect\:layered` and repeat the force-load and stop steps. Run `auditTerrainWorld` with that world's path and `-PterrainPreset=layered`. The audit compares every block with the configured thickness and placement of each surface-relative layer.

## Version 2 terrain

Start the [advanced terrain example](ADVANCED_TERRAIN.md) on a disposable dedicated server. Force-load chunks `(-4,-4)`, `(-3,-4)`, `(-1,0)`, `(0,0)`, `(1,0)`, `(0,-1)`, `(5,5)`, and `(6,5)` in each dimension. Pass block coordinates to `forceload` by multiplying each chunk coordinate by 16. Stop the server and audit the region files.

```powershell
.\gradlew.bat auditAdvancedTerrainWorld '-PterrainWorld=build/acceptance/advanced-terrain/world'
```

Record SHA-256 hashes for the generation settings and snapshot. In the disposable datapack, change the starting elevation, strata, and the End landform. Restart, add 256 to both chunk coordinates for the entire test set, and generate those chunks in all three dimensions. Run the audit with `-PadvancedTerrainChunkOrigin=256` and compare the recorded hashes.

To verify a version 1 upgrade, make two copies of the same 0.9.0 Layered save. Generate identical new chunks with 0.9.0 and the candidate JAR, then run `auditTerrainWorld` against existing and new chunks in both copies. Compare the candidate save's generation files before and after.
For the floating Overworld spawn check, switch the example Overworld landform to `floating_islands` in a fresh copy and use seed `194725`. The default column at `(8, 8)` is empty for that profile. Check the saved spawn for a full floor and two air blocks.

## Preset changes and missing data

For a restart regression, record the SHA-256 checksums of `world/data/minecraft/world_gen_settings.dat` and `world/data/worldarchitect-generation-v1.dat`. Change the bundled preset's default material to stone in a test build, then reopen the same world on the test server. Generate an unexplored chunk with `forceload add 1024 1024`, stop, and add `64,64` to the audit's semicolon-delimited list. Compare the original checksums after the restart.

On a disposable copy, move the original `world_gen_settings.dat` aside while keeping the World Architect snapshot in place. Verify that startup stops with a World Architect error and leaves the snapshot and region data intact.

## Universal dimensions

1. Copy the [universal dimensions datapack](../examples/universal-dimensions) under the disposable world's `datapacks` directory before its first launch. Set `level-type=worldarchitect\:dimension_demo` and `initial-enabled-packs=vanilla,file/universal-dimensions` in `server.properties`.
2. Start the dedicated server. Use `forceload add 0 0`, `execute in minecraft:the_nether run forceload add 0 0`, and `execute in minecraft:the_end run forceload add 0 0`. Stop the server after those chunks generate.
3. Run `auditTerrainWorld` for each dimension. `-PterrainDimension` defaults to `minecraft:overworld`. The Nether example uses `-PterrainPreset=layered -PterrainBase=minecraft:netherrack`. The End example uses `-PterrainBase=minecraft:end_stone`.

```powershell
.\gradlew.bat auditTerrainWorld '-PterrainWorld=build/acceptance/dimensions/world' '-PterrainChunks=0,0' '-PterrainDimension=minecraft:the_nether' '-PterrainPreset=layered' '-PterrainBase=minecraft:netherrack'
```

For the restart regression, change the preset materials in a copy of the test world and generate unexplored chunks in each dimension. Audit them against the original saved materials and compare the two generation file hashes across the restart. Follow the [adapter example](DIMENSION_ADAPTERS.md) to create a separate mod fixture. Exercise a supported replacement, a dimension kept in Original Generation, and a restart with the required adapter omitted.

## Features and structures

Copy [the features demo datapack](../examples/features-and-structures) to a disposable world's `datapacks` directory. Before starting the dedicated server, set `level-type=worldarchitect\:features_demo`, `level-seed=194723`, `generate-structures=true`, and `initial-enabled-packs=vanilla,file/features-and-structures`.

Run these commands on the test server and allow the chunks to generate before stopping it.

```text
forceload add 0 0
forceload add -96 -96
forceload add 96 96
execute in minecraft:the_nether run forceload add 0 0
execute in minecraft:the_end run forceload add 0 0
execute in minecraft:the_end run locate structure minecraft:desert_pyramid
```

```powershell
.\gradlew.bat auditFeatureWorld '-PterrainWorld=build/acceptance/features/world' '-PfeatureFiltersExpected=true'
```

The audit checks the stored biome at each gold and amethyst position, the gold replacement target and Y range, the lack of diamond ore where its target does not occur, dry cave and decoration positions, the Nether fluid layers, and native pyramid pieces and TNT in the End. Omit `-PfeatureFiltersExpected=true` for a fixture from 0.3.0.

For the restart regression, remove the content rules from the starting preset in a copy of the test save. Force-load Overworld chunks `(54, 54)`, `(60, 60)`, and `(66, 66)` using block coordinates `(864, 864)`, `(960, 960)`, and `(1056, 1056)`. Force-load chunk `(60, 60)` in both the Nether and the End. Stop and audit the copy with `-PfeatureChunkOrigin=60`. Compare its world-generation settings and snapshot checksums before and after.

In a separate copy, override the registered `minecraft:desert_pyramid` definition while keeping the same ID. Reopening should identify the changed structure and stop before chunk generation. Compare checksums for the generation files and an existing End region.

## Cross-dimension structure generation

Create a fresh dedicated world using the [cross-dimension datapack](../examples/cross-dimension-structures). Generate the origin area with the commands in its README, then stop and run `auditCrossDimensionWorld`. Check the Overworld's native End City pieces and blocks, and all three modules, transformed states, metadata, and markers in each custom dimension.

Copy the test save, record SHA-256 hashes for `data/worldarchitect-generation-v1.dat` and `data/minecraft/world_gen_settings.dat`, then change the starting world preset's materials, elevation, and structure lists. Retain the registered structure definitions. Reopen the copy and generate the same test areas around chunk `(-24,-24)`. Audit with `-PstructureChunkOrigin=-24`. Compare the generation hashes and inspect the original markers again.

For rejection tests, use separate registered copies of the modular definition. Try a different required dimension, a biome absent from the destination, Y bounds above the structure, unsupported elevated placement, and colliding base terrain. Verify that rejected candidates have no pieces and leave the base terrain intact. A template with matching terrain blocks and the `matching` collision rule should generate.

On a dedicated client, try marker travel without operator permission. Grant operator permission, obstruct the destination, and check that the player stays at their original position. Clear the obstruction and verify arrival coordinates and facing from the persisted module.

In disposable save copies, modify an embedded template in its registered structure definition and override a native End City template file. Each copy should stop during compatibility validation. Compare the generation files and existing region hashes after each rejection.

Reopen copies of earlier Layered and library/Skyblock saves using the candidate JAR. Audit existing and unexplored Layered chunks, and compare generation files, the island ledger, and unrelated saved library entries.

The 0.13.0 acceptance run verified 99 modular cells with exact block states and supported metadata at each audited location, all three markers in each assembly, five rejected placement rules, and 196,608 legacy Layered block positions. Generation hashes matched after the preset-change and upgrade runs. Dedicated-client tests verified permission denial, blocked-marker rejection, and arrival at the saved coordinates and facing.

## Void and Skyblock

Use the two presets in the [Skyblock datapack](../examples/skyblock) on separate disposable servers. For `void_demo`, force-load chunks at the origin and far away in each dimension. Stop the server and use `auditVoidWorld` with the chunk coordinates. Repeat with the vanilla `--bonusChest` startup argument to verify the same air-only output. Test the consent guard on a fresh copy of the preset with `allow_unsafe_spawn` removed.

```powershell
.\gradlew.bat auditVoidWorld '-PterrainWorld=build/acceptance/void/world' '-PterrainDimension=minecraft:the_end' '-PterrainChunks=6,0;-30,-30'
```

For `skyblock_demo`, join with two test profiles and check that they start at `(112.5, 82, 1.5)` and `(208.5, 82, 1.5)`. Place a block at the first player's marker, respawn, and verify that they arrive on a clear block of the same island. Remove the obstruction, move the first player to a safe position off the marker, and disconnect both players. On restart, verify their saved positions and use death respawn to confirm the assignment still points to the original island.

Change the datapack's example template, height, and slot count between starts. The saved template and allocation file should keep their hashes. Audit the chunks containing the shared and personal islands, both sides of the template's chunk boundary, and empty chunks between and beyond the slots. Use `-PislandAllocations=2` to verify the number of persisted owners. On a disposable copy, move `worldarchitect-islands-v1.dat` out of the save's `data` directory and check that startup stops without recreating it.

## Structure Library

Use a dedicated server with a disposable copy of a 0.4.0-alpha Skyblock save. Record the hashes of the generation settings, generation snapshot, and island allocation file before opening it.

1. In the Overworld, build a structure across a chunk boundary with an X-axis log, top-half stairs, a dyed glowing sign, a patterned banner, and a named chest containing items. Set selection corners in reverse order and capture. Open the library and inspect the top view and a layer slice.
2. Add a local marker over a full block, then export and import the template. Set a Nether biome and height rule, and require support below the footprint. Try the wrong dimension, biome, height, missing support, and an occupied destination. For the collision test, check the blocker and other destination cells after rejection.
3. Place using `cw90 left_right` on the correct support. Use `execute if block` to check the transformed log, stairs, and wall sign states. Use `data get block` to check sign text, styling, banner patterns, and the chest name. Check that the copied inventory is empty and sign text has no click action.
4. Restart after moving the source export aside. Check the saved template and placed blocks again. Try its spawn marker with the destination obstructed, then clear it and verify the player's transformed coordinates and facing. Generate a distant void chunk and audit it with `auditVoidWorld`. Compare the template and original world-data file hashes.

### Structure Builder and Sponge import

On a dedicated server, open the [template editor](STRUCTURE_LIBRARY.md#use-the-in-game-editor) with an operator account. Use the dialogs to set and outline a selection, capture a template, open a slice preview, replace a palette state, rotate the saved template, and set a spawn marker.

Import a Sponge version 3 schematic with a log axis, stairs, a coloured sign, a patterned banner, and a named empty chest. Export it as native NBT and re-import it. After placement, inspect the saved block states and block-entity data. Move the original source files aside, restart, and check that the library entry, placed blocks, and marker remain usable. Compare hashes for the earlier generation snapshot, settings, island assignments, and unrelated library entries.

Test separate files containing a missing block, greater than 32 blocks on one axis, an unsupported schematic version, incomplete block indices, mismatched block-entity metadata, and entity data. Each should report the offending part and leave the target library ID unused.

## Modpack bundles

Run `.\gradlew.bat test --tests "*BundleManifestTest"` to check the edit policy for each lock mode and rejection of contradictory or unknown permissions.

Export the [locked Skyblock draft](../examples/locked-skyblock) from an authoring server. Install the ZIP and release JAR in a separate instance with a new world directory. Leave the preset, seed, and structure-toggle properties absent on first launch. Check that the server selects `wa_example:locked_skyblock` and a joining player arrives at `(112.5, 87, 1.5)`. Audit the shared and personal islands and chunks far from the island grid.

Use separate empty test directories for these startup checks:

- Full lock rejects a different seed, an alternate world type, a substituted island marker, and an additional world datapack.
- Partial lock accepts an allowlisted seed, Nether ore rules, and End terrain. The same setup rejects a changed Nether elevation.
- The installed lock prevents `/setworldspawn`. A normal `/reload` succeeds, and enabling a new world datapack fails while the active selection stays the same.

## Permanent save protection

1. Create a disposable locked Skyblock world with the release JAR. In a second directory, start a copy of a 0.6.0 locked world with its exact original ZIP selected. Stop both servers and check that the archived ZIP in each world matches the original bundle by SHA-256. Record the hashes of the generation snapshot, world-generation settings, island allocations, and archived ZIP. Compare the 0.6.0 copy's generation files and allocations with the original save as well.
2. Replace the instance bundle with a new release of the same preset using a different island height and native starter template. Remove the original ZIP from the instance configuration. Reopen both saves, generate chunks beyond the explored area, and use `auditVoidWorld` to check the original island layout and saved allocations. On the dedicated server, place the starter via `/place template` in a test area and check blocks unique to the archived template. Confirm the hashes from the first step still match. In a fresh world, verify that the same installation generates the revised layout.
3. In a separate world with a Single Block Overworld and vanilla Nether and End, change the starting Overworld preset from sand to stone. Reopen it, generate a far chunk, and check the existing and new chunks with `auditTerrainWorld`. Compare the two saved generation file hashes.
4. Use independent copies to test missing settings, a missing snapshot, a missing archived ZIP, a changed Overworld dimension type, and a changed Nether noise settings resource under its existing ID. Each copy should fail before the world starts. Compare hashes for the remaining protected files, `level.dat`, and a sample existing region.
5. In a disposable copy of the protected save, set the `DataVersion` under `Data` in `level.dat` to an earlier value. Record the file's hash after the edit and start the server. Confirm that World Architect skips file conversion and that the level file, generation files, and sampled region keep their hashes.

## Dedicated multiplayer

Use a disposable dedicated server with the packaged JAR and the two-slot `skyblock_demo` preset. For a cross-dimension test, use the layered Nether profile from the [universal dimensions example](../examples/universal-dimensions). Enable developer mode and install a valid draft as described in the [bundle guide](MODPACKS.md).

1. Connect two separate client profiles at the same time. Record their UUIDs and spawn coordinates. Verify that each appears on a different personal island. Attempt a third first-time join while both slots are assigned. Check that the client receives the island capacity message and that both existing players can continue playing.
2. From a regular player, try `/wa_structure capture test:marker <from> <to>` for a loaded block and `/wa_modpack export <draft>` for the installed draft. Verify that the server rejects both and leaves the library and export directory as they were. Repeat as a gamemaster and check that capture succeeds and export is denied. Grant admin permissions and confirm that export succeeds when developer mode is enabled.
3. From a single server function, teleport both players into separate, unexplored regions during the same tick. Use spectator mode for the void destination. Verify that both clients receive their chunks and that the Overworld contains air and the Nether contains its saved layers. Use `auditVoidWorld` and `auditTerrainWorld` on the saved regions after stopping the server.
4. Restart, reconnect both clients, and verify that each player returns to the saved location and dimension. Trigger a death respawn for each player and check that each returns to their original personal island. Check the ledger SHA-256 before and after the restart. Pass `-PislandAllocations=2` and `'-PislandAssignments=<first-uuid>=<slot>;<second-uuid>=<slot>'` to `auditVoidWorld` to audit each UUID directly.
5. In a separate instance with the full-lock Skyblock example and developer mode disabled, submit `/wa_structure capture`, `/setworldspawn`, and `/wa_modpack export` from an operator client. Verify that the server rejects all three and that `/wa_modpack` reports the running bundle and its full lock.

## Spawn policies, island administration and protection

Use a disposable dedicated server running the packaged JAR with a six-slot copy of `skyblock_demo`. Set `spawn-protection=0` to test World Architect's controls independently. Connect two regular clients whose scoreboard team was created before their first joins.

1. Configure initial and respawn team policies. Verify both players share one reserved team island. Test island home, individual reassignment, a player swap, and team reassignment. An occupied-slot reassignment must fail without changing the ledger hash. Regular clients must be unable to change assignments or spawn policies.
2. Enable breaking, placement, and container protection. Verify owner/team building, foreign-island denial, operator bypass, and independent flag disabling. Remove a player from a team while their chest is open, then send container input: the menu must close and its items remain in the chest. Send an unrelated block-interaction packet while the chest is open and repeat the revocation check; the active menu's ownership check must remain bound to its container. Open a chest while its type is unprotected, enable protection, and verify that session closes too. A double chest crossing an ownership boundary must be inaccessible through either half unless both halves are permitted.
3. Set a personal respawn point and trigger death. Verify it takes precedence over the policy. For a player without a personal point, verify policy respawn. Stop the server and reconnect both clients. Check saved positions, team reservations, foreign-island denial, and byte-identical administration and allocation files.
4. Configure a safe Nether fixed initial policy and a different fixed portal policy. A new client must join in the Nether at the initial position, then arrive at the portal destination after entering a real portal from the Overworld. Test a transformed library marker with another new client, including facing.
5. Reopen a copied modular-structure world from 0.13.0. Configure initial and respawn policies using a saved generated marker in its custom dimension. Check coordinates and facing, then obstruct the marker and verify a clear supported alternative. Restore the obstruction afterward.
6. Reopen earlier Layered and library/Skyblock saves, generate unexplored chunks, and audit their saved terrain. Compare generation settings, snapshots, island allocations, and existing library files by SHA-256. In a stopped copy of an administered world, move the administration file aside. Startup must reject the copy without changing its protected generation files or existing regions.

The 0.14.0 acceptance run passed these scenarios with two simultaneous regular clients and separate new-player profiles. The saved-world audits checked 294,912 Layered positions, 196,608 Overworld void positions, and 65,536 End void positions. All 18 sampled generation, allocation, and library files across the three upgraded fixtures retained their hashes. Missing-administration rejection preserved the sampled level data, generation files, allocation file, and region. The requirement is checked during level-data validation, before Minecraft can rewrite custom level flags. The build passed 28 JUnit tests.

## Dimension environment designer

Use the packaged JAR with a copied cross-dimension structure world. Record the saved generation settings, snapshot, existing administration settings and any island allocation or library files for hash comparisons. Run the [environment example](../examples/dimension-environment) from the server console.

1. Hold the Overworld at tick 6000 with clear weather and the workshop at tick 18000 with thunder. Check each level's default clock, rain/thunder flags, and native sun-angle timeline. Verify the shared clock and weather records retain normal control. Request weather/time in the Nether and a native portal restriction in a custom dimension: each unsupported setting must fail with a capability explanation.
2. Disable Overworld monsters, creatures and spawners while leaving the workshop enabled. Exercise native entity spawning with natural, chunk-generation, ordinary-spawner, trial-spawner and command reasons in both dimensions. Confirm the enabled dimension retains its normal behaviour and command spawning remains available.
3. Connect two regular clients and place one in each dimension. Check synchronized clock, weather, fog/sky colours, cloud alpha and cloud height. Move a client between dimensions and change its active fog colour on the server. The other dimension must keep its own values. Direct regular-player edit requests must leave the administration file unchanged.
4. Grant administrator permission, open the designer, navigate to time/weather, edit a field and apply the action. Inspect the rendered dialog and the resulting server/client value. Revoke permission and test a real Nether portal with entry disabled and then restricted to operators. The player must remain in the source dimension until operator access is granted. In a strict void, explicitly build and configure a safe arrival platform first.
5. Stop and restart the dedicated server, reconnect both clients and verify saved positions, the same environment settings, mob restrictions and portal permission checks. The administration file must retain its SHA-256 hash when no settings are edited.
6. Reopen copied 0.14.0 and 0.9.0 saves. Verify generation, allocation, archive and library hashes and audit new void chunks. Attempt environment edits in a fully locked bundle; each must fail without writing an environment override. Keep acceptance-only probe mods and client scripts outside the release JAR.

The 0.15.0 acceptance run passed with two simultaneous clients, dimension switching, live visual edits, designer navigation/input, and operator-only portal entry to an explicitly configured safe void destination. Reconnecting after restart preserved player locations and the administration hash. Native spawn tests covered all five reasons listed above in both dimensions. A separate server copy verified offset clocks and reset-to-vanilla behaviour without changing the other dimension. Capability rejections and three full-lock rejections left configuration intact. Dedicated save audits checked 622,592 block positions across legacy Layered, Skyblock and End void chunks; protected generation, allocation, archive, administration and library hashes matched. The build passed 33 JUnit tests, and the final JAR matched the dedicated-server artifact by SHA-256 with no acceptance probes included.

## Release regression checks

- After successfully opening and saving a protected world with 0.9.0, copy it and remove both `world_gen_settings.dat` and `worldarchitect-generation-v1.dat` from the copy. The dedicated server should identify the protected save and stop before Minecraft reads fallback settings. Compare `level.dat` and a sampled region file by SHA-256.
- In a copied locked world, move the archived ZIP aside and open the world from the single-player screen. Check for the World Architect dialog with the missing archive path. Close it, restore the ZIP, and verify the save opens.
- Start a fresh dedicated world from the features example. Check that the End pyramid can be located and run `auditFeatureWorld` with `-PfeatureFiltersExpected=true`. In a separate copy of the starting datapack, replace the pyramid rule with `minecraft:end_city`. The server should report that there is no compatible placement implementation before writing new world data.

### Performance comparison

Run one dedicated server at a time with the same Java version, heap limit, active processor count, seed, and view distances. Use fresh worlds for Original Generation, pure Void, Pure Sand, Layered, and a structure-heavy control. The structure-heavy sample uses stone with caves, ores, decorations, and a desert pyramid grid with spacing 4 and separation 3.

Warm each server with a 4 × 4 force-loaded Overworld batch. Then measure two separate 6 × 6 batches in unexplored regions. Start the timer when sending `forceload add` and stop when `execute if loaded` confirms all 36 chunks in the batch. Sample the server JVM working set during the wait. Record elapsed time and the highest sampled working set for each batch. These are end-to-end local measurements that include server tick scheduling and neighboring chunk work. [Release results](RELEASE_NOTES.md)
