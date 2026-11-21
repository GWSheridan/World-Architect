# Cell worlds and chunk progression

Cell World Studio builds a dimension from square or hexagonal cells. Each cell has a saved type, biome, terrain, resources and optional embedded structure. Gaps stay empty. The layout is World Architect's own implementation.

## Choose a starter

Open **Builder → Project → Choose starter**. The six cell recipes are:

| Recipe | Starting point |
| --- | --- |
| Hex Continents | Hexagonal terrain, oceans, biome types and stone borders |
| Hex Skyblock | Template islands separated by void |
| Expanding One Chunk | One 16 × 16 chunk, with advancements opening neighbouring chunks |
| Resource Archipelago | Islands with different ore deposits |
| Biome Mosaic | A square grid of different biomes and terrain types |
| Underground Start | A supplied starting chamber, with advancement-based expansion |

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

Objectives use an advancement registry ID or a named server signal. Configure prerequisite IDs and choose **all** or **any**. The editor rejects unknown prerequisites and dependency cycles. Rewards may target fixed cells, award expansion credits, or do both. Automatic mode spends credits on a deterministic frontier; choice mode lets players select a neighbouring cell in `/wa_cells panel`.

Signals received before prerequisites are remembered. A completed objective pays once per territory, including when two teammates complete it together. Revoking an advancement never removes terrain or refunds an objective. Advancements earned while visiting another dimension still count.

For an external quest mod, call `CellProgressionApi.signal(territoryLevel, player, signalId)` on the server thread after checking the quest reward. Datapack authors can use advancement objectives directly, or invoke the administrator signal command from a function with permission level 3. No quest mod is required or automatically assumed compatible.

## Player and administrator commands

| Command | Access |
| --- | --- |
| `/wa_cells` | Show territory progress |
| `/wa_cells panel` | Open objectives and frontier selection |
| `/wa_cells home` | Return to a safe starting marker |
| `/wa_cells choose <q> <r>` | Spend one earned credit in choice mode |
| `/wa_cells admin unlock <territory> <q> <r>` | Permission level 3 |
| `/wa_cells admin retry <territory> <q> <r>` | Permission level 3; retry a preserved conflict after inspecting it |
| `/wa_cells admin signal <player> <signal>` | Permission level 3; target the command's dimension |
| `/wa_cells admin reassign <territory> <owner>` | Permission level 3; owner is `player:UUID` or `team:NAME` |

Reassignment preserves the previous region. Automatic allocation never recycles a previously used territory. Players can edit their own open cells; other territories and the personal/team hub are protected. Locked cells reject ordinary block writes, piston movement and container access.

## Saves and activation

Create these worlds from a new preset. There is no conversion command for turning existing player chunks into reserved cell terrain.

Locked cells are actual void, even after their chunks have been loaded and saved. Unlocking queues a bounded activation job. The server checks the whole reserved volume before writing. Unexpected blocks preserve the cell as a conflict for an administrator to inspect. Interrupted jobs are checked again after restart. Containers remain inaccessible during activation, and their inventories are flushed before the cell is committed as ready.

Back up the entire world, including `data/worldarchitect-cells/`. This directory holds assignments, objectives, credits and activation states. The immutable generation snapshot separately preserves the cell definitions and embedded templates. A missing or mismatched journal stops loading rather than recreating progression.

Use matching mod versions on clients and servers. Keep the native End arena enabled for static cell terrain around the dragon fight. Progressive cells with that arena are rejected; put progression worlds in the Overworld or a separate custom dimension.
