# Creator tools

**Modpack Builder** guides a detached project through dimensions, island types, server policies, locks and export. **Island Studio** provides native block-model and slice previews, capture/import, markers, transforms and starter contents. Both share draft history and interrupted-draft recovery. See the [modpack workflow](Modpack-Quick-Start.md) and [island guide](Void-and-Skyblock.md).

Builder and Island Studio were introduced in World Architect 1.8.0. Use matching mod versions on clients and dedicated servers. Existing saved worlds keep their generation settings.

World Architect 1.9 adds [Cell World Studio](Cell-Worlds.md): original hex and square layouts, per-cell terrain and islands, and advancement or quest objectives that open reserved void cells. The six Builder recipes include Hex Continents, Hex Skyblock, Expanding One Chunk, Resource Archipelago, Biome Mosaic and Underground Start.

## Start a world

Open **Create New World → World → World Architect · Dimensions → Customize → Starters**. Choose a recipe, then customize its dimension:

| Starter | Result |
| --- | --- |
| Single Block | Rolling stone terrain in the Overworld |
| Layered | Dirt over andesite and stone in the Overworld |
| Skyblock | A classic island and 64 personal island slots in the Overworld |
| Custom End | Custom outer End terrain with the native central arena and dragon progression |

Single Block, Layered and Skyblock starters replace the Overworld draft. Custom End replaces the End draft. Other dimension drafts remain as configured. An installed bundle's locks take precedence; the gallery cannot replace a locked selection.

Child editors apply to the dimension-manager draft. **Done** in the manager applies that session to the new world. **Cancel** or Escape asks before discarding a changed session. Saved library versions remain on disk when a world draft is discarded.

## Find a saved preset

In Preset Studio, choose **Browse saved presets**. Search by library name, title, version, author or lock status. Select a row, then choose **Load selected**. Browsing or cancelling the library leaves the current draft in place. Loading a different revision or importing asks before replacing a changed draft.

**Validate draft** checks the current settings. **Dependencies** shows the detailed report. Missing resource or adapter errors must be resolved in the authoring instance before exporting. Save a new version before exporting changed settings; existing versions remain immutable. See [Preset Studio](Preset-Studio.md) for bundling and locks.

## Preview terrain

**Preview terrain** samples the selected dimension with the draft seed. Choose a height map, biome map, rotatable 3D surface or vertical cross-section. Use the orbit buttons for rotation, arrows for panning, zoom buttons for scale, **Go to** for coordinates and Home to return to the origin.

Gold marks island spawns. Cyan marks the native End arena boundary when that policy is enabled. The cross-section follows X at the displayed centre Z and covers the dimension's build height; its colours distinguish materials and fluids.

Views show base terrain. They exclude ores, decoration, generated structures and other later generation steps. Cross-sections include base caves, fluids, strata and floating terrain. The native End arena uses its own base-terrain sampler. Original and unsupported modded generators show an unavailable explanation.

Sampling advances over client ticks. Moving or zooming discards the unfinished request. Closing the screen stops its work. It never loads chunks, writes a save or changes a generation snapshot.

## Stage a structure

Choose **3D viewer** from a Structure Library preview to orbit a template and inspect transformed spawn markers. Choose **Stage here**, or use:

```text
/wa_structure stage builds:workshop 64 100 -32 cw90 left_right
```

The placement screen offers rotation, mirroring and origin coordinates. Each change requests a fresh server check. Gold labels show spawn names and facing. **World ghost** shows a temporary particle silhouette at the destination for ten seconds. It places no blocks. Reopen staging at the same origin to change or confirm the placement.

**Confirm** is available after a successful preview check. The server then checks permissions, the active bundle policy, template revision, current dimension, loaded chunks, border, terrain and biome restrictions, collisions, protected islands, entities and marker clearance again. A preview expires after sixty seconds. A changed template or destination can therefore cause confirmation to be refused.

Direct `/wa_structure place` remains available. It uses the same placement checks. The 32-block-per-axis limit and metadata restrictions remain in force. See [Structure Library](Structure-Library.md) for imports, rules and marker commands.

## Undo a manual placement

```text
/wa_structure undo
```

Undo applies to your most recent retained manual library placement. Return to its dimension and keep every affected chunk loaded. If any placed block or block-entity data differs from the recorded result, undo refuses the whole operation. This includes items added to a placed chest. Clear players and mobs from the affected blocks before trying again.

History retains at most ten operations per author with a shared 32 MiB accounting budget. Older entries are evicted as necessary. History is kept only for the current server session and disappears after restart. Automatic world-generation structures and console placements have no player undo entry. Undo suppresses drops and does not restore inventories, entities or generated terrain.

## Local diagnostics

Level 3 administrators can run `/wa_diagnose report`. It writes a local JSON file under `config/worldarchitect/diagnostics/`. Reports contain platform/mod versions, dimension and adapter capabilities, live dependency matches and SHA-256 hashes of generation files. Player identities, addresses, credentials, absolute paths and raw seeds are excluded. No report is uploaded automatically. A world that fails protection during loading must be diagnosed from its startup error before this command becomes available.

Use [Assembly Studio](Assembly-Studio.md) to combine templates into complete modular structures, [Rule Inspector](Rule-Inspector.md) for condition maps and explanations, and [Administration Panel](Administration-Panel.md) for confirmed server changes.

## Draft authoring history

Preset Studio, regional terrain, rules, Assembly Studio and workbench scenarios now have bounded history and explicit interrupted-draft recovery. See [Draft History](Draft-History.md) for the difference between editor Undo/Redo and the manual placement undo described above. Neither changes immutable world generation.

## Terrain and structure tools in 1.5

Use the regional editor for polygon outlines and vertex history. Assembly settings now include placement anchors and per-module water collision rules; pool connections also accept up/down shafts. The [site inspector](Structure-Site-Inspector.md) explains a saved preset's base-terrain placement decisions on an authoring server without creating chunks. It does not certify later generation effects.
