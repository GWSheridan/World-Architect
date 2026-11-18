# Void and Skyblock

In **Create New World → World**, select **World Architect · Dimensions** and open **Customize**. Select a dimension and choose **Void / Skyblock**. Apply the profile and choose **Done** in the dimension manager.

**Use Empty Void** generates air throughout the dimension's build range. An empty Overworld requires explicit consent because it has no safe spawn. Vanilla Nether and End portals refuse arrival into pure void, which prevents their automatic exit frame or obsidian platform. An administrator can enter with a teleport command.

## Starting islands

**Use Skyblock** generates the selected island template. Enter its bottom Y and a local spawn marker in `X Y Z` order. The marker is the player's feet position, measured from the template's low corner. It needs two cells of air over a safe full block. The built-in Classic island uses marker `4 4 4`.

For a custom island, save a vanilla structure `.nbt` with a structure block in a separate build world. Include both cells of spawn headroom in the selected bounds. Enter the file path and its local marker in the editor, then choose **Import .nbt**. The template may be up to 32 blocks on each axis and can use registered block states, including modded blocks. This import supports one palette and block states only. Fluids, block entities and their contents, entities, falling blocks, and active Nether or End portal blocks are outside this template format.

The saved dimension embeds the template, so moving or editing the original `.nbt` has no effect. If players obstruct or remove the spawn marker, the server searches for a clear spot over a safe floor within the same template footprint, then tries the shared island. If both are unusable, the server refuses the affected spawn. Ordinary saved logout positions and valid beds or respawn anchors continue to apply. Vanilla portal arrivals into Skyblock use an island marker or a valid personal return point from the End.

## Multiplayer islands

Set **Personal slots** in the Overworld to a number between 1 and 4095. Use `0` for one shared island. Each first-time player claims one slot by UUID. The shared island is slot 0. Personal slots are distinct origins arranged in rows of 16. Spacing is measured between origins, in multiples of 16 from 64 to 4096 blocks. The generator reserves the grid, so sections explored before allocation already have the same island layout. When all slots are assigned, the server tells unassigned players that the islands are full. Existing players can rejoin.

Include `worldarchitect-islands-v1.dat` in any world backup or move. If it is missing or unreadable, the server stops before opening the saved world. Slot ownership controls spawn assignment. Players can still travel to and modify other islands under the server's ordinary rules.

## Dedicated server presets

Copy [the Skyblock example datapack](../examples/skyblock) into the new world's `datapacks` directory. Enable `file/skyblock` with `initial-enabled-packs=vanilla,file/skyblock`. Set `level-type=worldarchitect\:skyblock_demo` for a custom Overworld island with two personal slots, a shared Nether island, and an empty End. Set `level-type=worldarchitect\:void_demo` for pure void in all three dimensions with unsafe Overworld spawn explicitly accepted.

Both presets use `type: "worldarchitect:void"`. Omit `island` for pure void. On the Overworld, `allow_unsafe_spawn` must then be `true`. With Skyblock, `island.template` stores the bounds, marker, and blocks. `island.origin_y` sets the bottom of the template. `island.spacing` defaults to 128 and `island.personal_slots` defaults to 0. Secondary dimensions support a shared island. Create a new world to change the generator, template, or allocation grid.
