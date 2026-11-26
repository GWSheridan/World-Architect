# Cell worlds and chunk progression

Cell World Studio builds a dimension from square or hexagonal cells. Each cell has a saved type, biome, terrain, resources and optional embedded structure. Gaps stay empty. The layout is Worlds Unwritten's own implementation.

## Choose a starter

Open **Builder → Project → Choose starter**. The cell recipes are:

| Recipe | Starting point |
| --- | --- |
| Hex Continents | Hexagonal terrain, oceans, biome types and stone borders |
| Hex Skyblock | Template islands separated by void |
| Expanding One Chunk | One 16 × 16 chunk, with advancements opening neighbouring chunks |
| Resource Archipelago | Islands with different ore deposits |
| Biome Mosaic | A square grid of different biomes and terrain types |
| Underground Start | A supplied starting chamber, with advancement-based expansion |
| Hex Frontier | Resource islands, reserved paths, a procedural ruin and shared End progression |
| One Chunk to the End | One chunk, a timber turn-in, an End portal room and shared End progression |

The starting cell includes wood, saplings, dirt, water, lava, bone meal and torches. Edit these contents in Island Studio before exporting your pack. Other islands use their own saved templates and resource choices.

Use **Dimensions → Biomes, features and cells** to reopen Cell World Studio. To use cells in another supported dimension, choose **Terrain / generator → Cell worlds**. A modded dimension must advertise `CELLULAR` and return `true` from the dimension adapter method `supportsCellularTerrain()`.

## Geometry and cell types

Choose square, pointy hex or flat hex geometry. Square size 16 corresponds to one Minecraft chunk. Hex size measures from the centre to a corner. Sizes are multiples of four; gaps and rims are measured in blocks. **Border to floor** fills the rim down to the bottom of the dimension.

Each cell type has a biome, weight, layered terrain and optional island template. Terrain types support ores, simple decorations, caves and fluids. Island types contain only their embedded template. Cavern types include a starting chamber below the surface.

Weight zero keeps a type out of random selection: use it for the starting cell or an explicit assignment. Other cells use weights and the world seed. **Fixed cell assignments** accepts entries such as `1,0=desert;0,1=taiga`. The complete embedded template must fit inside its cell and void gap. Use embedded templates for cell structures; native structures and native feature groups are rejected because their footprints could cross locked terrain.

The preview shows the future terrain and biome layout without creating chunks. It excludes ore placement and decoration. Its native End arena view uses the vanilla terrain sampler.

## Advancement and quest objectives

Choose a progression scope before creating the world:

- **Static:** every cell generates normally.
- **Shared:** everyone expands one territory.
- **Player:** each player receives a physically separate territory.
- **Team:** scoreboard teammates share a territory; players without a team stay at the hub.

Territory capacity and radius are fixed in the preset. Progressive cells support sizes up to 64 and dimension heights up to 512 blocks. Separate territories are spaced 262,144 blocks apart, so players never see different terrain at the same coordinates.

Objectives can listen to an advancement, named server signal, dimension visit, or site discovery. For a visit, use a dimension ID such as `minecraft:the_nether`. For discovery, use `worldsunwritten:<marker>` and put that marker in a cell template or procedural site. A player discovers the marker by coming within three blocks of it in a ready cell belonging to their territory. Configure prerequisite IDs and choose **all** or **any**. The editor rejects unknown prerequisites and dependency cycles. Rewards may target fixed cells, award expansion credits, or do both. Automatic mode spends credits on a deterministic frontier. Choice mode lets players select a neighbouring cell in `/worldsunwritten cells panel`.

Signals received before prerequisites are remembered. A completed objective pays once per territory, including when two teammates complete it together. Revoking an advancement never removes terrain or refunds an objective. Advancements earned while visiting another dimension still count.

For an external quest mod, call `CellProgressionApi.signal(territoryLevel, player, signalId)` on the server thread after checking the quest reward. Datapack authors can use advancement objectives directly, or invoke the administrator signal command from a function with permission level 3. No quest mod is required or automatically assumed compatible.

## Player and administrator commands

| Command | Access |
| --- | --- |
| `/worldsunwritten cells` | Show territory progress |
| `/worldsunwritten cells panel` | Open objectives and frontier selection |
| `/worldsunwritten cells home` | Return to a safe starting marker |
| `/worldsunwritten cells submit <objective>` | Submit the objective’s required items from your main inventory |
| `/worldsunwritten cells choose <q> <r>` | Spend the saved cell cost in choice mode |
| `/worldsunwritten cells admin connections <territory>` | Permission level 3; show placement states and conflicts |
| `/worldsunwritten cells admin retry_connection <territory> <id>` | Permission level 3; recheck a repaired connection |
| `/worldsunwritten cells admin unlock <territory> <q> <r>` | Permission level 3 |
| `/worldsunwritten cells admin retry <territory> <q> <r>` | Permission level 3; retry a preserved conflict after inspecting it |
| `/worldsunwritten cells admin signal <player> <signal>` | Permission level 3; target the command's dimension |
| `/worldsunwritten cells admin reassign <territory> <owner>` | Permission level 3; owner is `player:UUID` or `team:NAME` |

Reassignment preserves the previous region. Automatic allocation never recycles a previously used territory. Players can edit their own open cells; other territories and the personal/team hub are protected. Locked cells reject ordinary block writes, piston movement and container access.

## Saves and activation

Create these worlds from a new preset. There is no conversion command for turning existing player chunks into reserved cell terrain.

Locked cells are actual void, even after their chunks have been loaded and saved. Unlocking queues a bounded activation job. The server checks the whole reserved volume before writing. Unexpected blocks preserve the cell as a conflict for an administrator to inspect. Interrupted jobs are checked again after restart. Containers remain inaccessible during activation, and their inventories are flushed before the cell is committed as ready.

Back up the entire world, including `data/worldsunwritten-cells/`. This directory holds assignments, objectives, credits and activation states. The immutable generation snapshot separately preserves the cell definitions and embedded templates. A missing or mismatched journal stops loading rather than recreating progression.

Use matching 2.1 clients and servers. Version-one worlds retain their original static/progressive End restrictions. For a new progressive native End, choose **Shared End progression** or one of the two campaign starters; this explicitly creates a version-two campaign.

## Visual layouts

Open **World Layout Designer** from Cell World Studio. Choose a brush, switch between Select and Paint, then click or drag over the map. **Go to** selects a coordinate and centres the view; the last clicked or entered coordinate is the clipboard cursor. **Tools** supports rectangular selections, copy/paste, named groups and up to 32 undo steps. Square rotations turn 90 degrees and hex rotations turn 60 degrees. These operations move cell assignments; edit individual template orientations in Island Studio.

Copy resolves the selected cells using the draft's seed, so pasting preserves the types shown in the preview. Selections and fixed assignments are limited to 1,024 cells. A named group contains explicit coordinates and can supply a progression reward. Apply the layout to its parent project, then save or export the project. All previews are detached from the live world.

## Progression graphs, costs and turn-ins

**Progression Designer** shows objectives in dependency layers. Links join prerequisite nodes to their dependants; page through larger layers or open an objective to inspect every prerequisite. Set advancement or server-signal triggers, require all or any prerequisites, choose fixed cells or an `@group`, and award credits. Simulation checks a set of observed objective IDs without changing a world. Keep objective IDs stable between draft revisions.

Set cell-type costs from 1–64 credits. Fixed cell rewards remain direct grants; manual and automatic frontier choices spend the saved cost. Automatic expansion chooses the first affordable cell in its stable order. If none is affordable, credits remain available. The player panel shows costs and requires confirmation; the server checks the current dimension, territory and cost again.

An item turn-in belongs to a signal objective. Players use **Item turn-ins** in the expansion panel or `cells submit`. Only an explicit submission consumes the saved item ID and count from the player's main inventory. Equipment and offhand slots are excluded. Supply a count that fits a normal main inventory. Prerequisites, ownership, inventory contents and previous completion are checked on the server. Concurrent teammates cannot pay the same territory objective twice.

The server persists a player inventory receipt before awarding the objective. After an interruption it either aborts an unconsumed intent or completes the recorded consumption once. Missing or inconsistent evidence stops recovery. Keep player data and cell journals from the same backup. Item-delivery signals and the native dragon-kill signal cannot be completed through quest APIs or administrator signal commands.

## Rehearse a campaign

In Builder, select a progressive cell dimension and choose **Rehearse campaign**. Enter the objective IDs whose events should already have happened, separated by spaces. Leave the field empty to start from the beginning. **Inspect route** shows completed and waiting objectives, cell costs and gaps between awarded cells. **Create rehearsal world** opens a new save with those observations applied through the progression journal. Checkpoints stay in the rehearsal save. Exports use the original draft.

## Connections and procedural sites

In **Connections and adventure sites**, define a connection between neighbouring cells. Select a path, bridge or tunnel, an odd width of 1, 3 or 5, a safe solid material and either a floor height or saved template ports. Tunnels require width 3 or 5. Port names refer to standing positions above safe floors. Inspect the placement report before exporting.

The full corridor is reserved before activation. Both endpoints must be ready; the server then checks entrances, entities and every proposed position before writing. It preserves unexpected blocks as a conflict. Matching junction blocks can be shared; disagreeing materials or headroom are rejected. Each territory is limited to 65,536 reserved positions, with at most 64 connections and a 256-block route length. A saved fingerprint binds recovery to the same plan.

The administration report lists waiting, queued, applying, ready and conflicting routes. Inspect conflicts and restore or repair the affected saved blocks before retrying. A retry rechecks the footprint and preserves any remaining obstruction. It never authorizes excavation. The player panel's **Generation status** also shows queued cells and route states.

Procedural sites embed an Assembly Studio modular definition for a cell type. Load its exported datapack, select the registered assembly and set the chance. The production pool planner chooses its modules; the complete result must fit one cell, the dimension height, 64 blocks per axis and 65,536 occupied positions. Rejected layouts leave the type's ordinary template or terrain. The review samples eight seeds and explains rejected layouts.

Cell sites use their cell's elevation, biome and collision contract. Use free module templates with default placement rules, no global placement anchor or foundation, and a zero global height offset. Module rotations and optional assembly rotation preserve block states and markers. Decorative metadata is retained; procedural containers start empty. Marker names use the selected module name as a prefix, such as `root_west` or `module_1_entry`. These definitions and their algorithm version are saved with the world.

Version-two campaign jobs share a 16,384-position server limit per tick and each dimension's lower saved limit. Connections and cell activation share that allowance. Planning, chunk loading and durable save flushes also take time; lower the saved per-tick limit for busy packs and validate on the intended server hardware.

## Shared progressive End

The native square arena remains permanently open, including its dragon, fountain, crystals and inner gateways. Arena cells never cost credits. Twenty predeclared End-stone landing islands and reserved arrival volumes keep native outer gateways usable without spending a cell reward. They are travel reservations, not extra unlocked territories.

For an End objective, use the signal source `worldsunwritten:dragon_defeated`; the two campaign starters configure it already. Use advancement `minecraft:story/enter_the_end` for arrival rewards. The verified native dragon kill can complete its saved End objective once. Repeated kills continue normal gateway progression without repeating that objective. A blocked new arrival preserves the build and falls back to a safe position in the native arena; a matching partial gateway frame can recover. Shared progression is required for this native End mode.

The [campaign examples](https://github.com/GWSheridan/Worlds-Unwritten/tree/v2.1.0/examples/campaigns) contain complete survival routes. Hex Frontier is fully locked; One Chunk to the End allows a new-world seed and runtime protection changes. Both retain the normal Nether and an unfilled End portal room, so players still need Eyes of Ender.
