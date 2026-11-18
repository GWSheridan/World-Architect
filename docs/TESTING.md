# World Architect verification

Run `gradlew.bat build` for compilation, packaging, and JUnit tests. The terrain tests cover flat elevation, deterministic seed-dependent variation, and adjacent columns on both sides of the origin and across chunk boundaries.

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

## Release regression checks

- After successfully opening and saving a protected world with 0.9.0, copy it and remove both `world_gen_settings.dat` and `worldarchitect-generation-v1.dat` from the copy. The dedicated server should identify the protected save and stop before Minecraft reads fallback settings. Compare `level.dat` and a sampled region file by SHA-256.
- In a copied locked world, move the archived ZIP aside and open the world from the single-player screen. Check for the World Architect dialog with the missing archive path. Close it, restore the ZIP, and verify the save opens.
- Start a fresh dedicated world from the features example. Check that the End pyramid can be located and run `auditFeatureWorld` with `-PfeatureFiltersExpected=true`. In a separate copy of the starting datapack, replace the pyramid rule with `minecraft:end_city`. The server should report that there is no compatible placement implementation before writing new world data.

### Performance comparison

Run one dedicated server at a time with the same Java version, heap limit, active processor count, seed, and view distances. Use fresh worlds for Original Generation, pure Void, Pure Sand, Layered, and a structure-heavy control. The structure-heavy sample uses stone with caves, ores, decorations, and a desert pyramid grid with spacing 4 and separation 3.

Warm each server with a 4 × 4 force-loaded Overworld batch. Then measure two separate 6 × 6 batches in unexplored regions. Start the timer when sending `forceload add` and stop when `execute if loaded` confirms all 36 chunks in the batch. Sample the server JVM working set during the wait. Record elapsed time and the highest sampled working set for each batch. These are end-to-end local measurements that include server tick scheduling and neighboring chunk work. [Release results](RELEASE_NOTES.md)
