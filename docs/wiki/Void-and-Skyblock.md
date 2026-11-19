# Void and Skyblock

In **Create New World → World**, select **World Architect · Dimensions** and open **Customize**. Select a dimension and choose **Void / Skyblock**. Apply the profile and choose **Done** in the dimension manager.

**Use Empty Void** generates air throughout the dimension's build range. An empty Overworld requires explicit consent because it has no safe spawn. Vanilla Nether and End portals refuse arrival into pure void, which prevents their automatic exit frame or obsidian platform. An administrator can enter with a teleport command.

## Starting islands

**Use Skyblock** generates the selected island template. Enter its bottom Y and a local spawn marker in `X Y Z` order. The marker is the player's feet position, measured from the template's low corner. It needs two cells of air over a safe full block. The built-in Classic island uses marker `4 4 4`.

For a custom island, save a vanilla structure `.nbt` with a structure block in a separate build world. Include both cells of spawn headroom in the selected bounds. Enter the file path and its local marker in the editor, then choose **Import .nbt**. The template may be up to 32 blocks on each axis and can use registered block states, including modded blocks. This import supports one palette and block states only. Fluids, block entities and their contents, entities, falling blocks, and active Nether or End portal blocks are outside this template format.

The saved dimension embeds the template, so moving or editing the original `.nbt` has no effect. If players obstruct or remove the spawn marker, the server searches for a clear spot over a safe floor within the same template footprint, then tries the shared island. If both are unusable, the server refuses the affected spawn. Ordinary saved logout positions and valid beds or respawn anchors continue to apply. Vanilla portal arrivals into Skyblock use an island marker or a valid personal return point from the End.

## Multiplayer islands

Set **Personal slots** in the Overworld to a number between 1 and 4095. Use `0` for one shared island. Each first-time player claims one slot by UUID. The shared island is slot 0. Personal slots are distinct origins arranged in rows of 16. Spacing is measured between origins, in multiples of 16 from 64 to 4096 blocks. The generator reserves the grid, so sections explored before allocation already have the same island layout. When all slots are assigned, the server tells unassigned players that the islands are full. Existing players can rejoin.

Include `worldarchitect-islands-v1.dat` in any world backup or move. If it is missing or unreadable, the server stops before opening the saved world. Slot ownership controls spawn assignment and access when island protection is enabled.

## Spawn policies and island administration

Operators with permission level 3 can configure initial joins, death respawns, and portal arrivals with `wa_spawn`. Policies use safe fixed positions, assigned islands, scoreboard teams, library markers, or saved markers in generated modular structures.

```text
/wa_spawn set initial team minecraft:overworld
/wa_spawn set respawn team minecraft:overworld
/wa_spawn set portal team minecraft:overworld
/wa_spawn set portal fixed minecraft:the_nether 0 67 0
/wa_spawn set respawn marker minecraft:overworld builds:hall entry 64 90 -32 cw90 left_right
/wa_spawn set initial generated wa_example:workshop wa_example:village 1 81 1 main entry
/wa_spawn clear respawn
```

Fixed positions are feet coordinates. Place a library structure before selecting its marker. Generated marker policies identify a structure, a position inside its saved bounding box, the module name, and its marker. Set commands validate the requested destination before saving it. The server checks live support, headroom, build limits, and the world border at arrival. It searches up to four blocks from a fixed or structure marker and can fall back to an island or terrain spawn. It refuses an arrival when it cannot find a safe destination.

Initial policies apply when a player has no saved position. A valid personal respawn point takes priority over the configured death-respawn policy. Portal policies apply to players entering the selected dimension. The bundled spawn lock also governs policy edits.

Team policies use Minecraft scoreboard membership. Create a team and add players with the vanilla `team` commands. The first arriving member reserves one team slot, and members share that island. Team reservations and player assignments use the same capacity. Existing individual assignments remain allocated when a player joins a team.

```text
/team add builders
/team join builders PlayerName
/wa_island
/wa_island home
/wa_island list
/wa_island inspect 2
/wa_island reassign PlayerName 3
/wa_island swap FirstPlayer SecondPlayer
/wa_island team assign builders 4
```

All players can inspect their own assignment and use `home`. Other island commands require permission level 3. Reassign and swap commit the updated ownership before reporting success. Reassign requires an available slot, while swap exchanges two player assignments atomically. Use online player names or saved player UUIDs. Changes take effect on the next island home or policy-based respawn. The commands preserve the island's blocks and player-built contents.

## Optional island protection

Enable player block breaking, placement, and selected container protection independently. Protection covers each reserved island's entire horizontal spacing cell through the dimension's build height. Owners and members of its assigned scoreboard team have access. Operators at permission level 2 or higher can bypass World Architect protection.

```text
/wa_island protection breaking true
/wa_island protection placement true
/wa_island protection containers true
/wa_island protection shared true
/wa_island protection container add minecraft:hopper
/wa_island protection container remove minecraft:barrel
```

Chests, trapped chests, and barrels are the default protected container types. Container protection also covers breaking those blocks. Both halves of a connected double chest must be accessible. The server checks open-container clicks again so a change of ownership or team membership can revoke an existing session, including a session opened before container protection was enabled. Set any flag to `false` to disable it. The `shared` flag applies the enabled protections to island 0. These controls govern player actions; they do not claim islands against automation or environmental damage.

Administration settings live in `data/worldarchitect-administration-v1.dat`. Include that file and the island ledger in backups. The normal save records whether administration settings are required and rejects a missing file on reopening. Team reservations use allocation format 2 in the existing `worldarchitect-islands-v1.dat` file. Versions without team-allocation support cannot open worlds using teams. The server continues to read earlier allocation files.

## Dedicated server presets

Copy [the Skyblock example datapack](https://github.com/GWSheridan/World-Architect/tree/v1.0.0/examples/skyblock) into the new world's `datapacks` directory. Enable `file/skyblock` with `initial-enabled-packs=vanilla,file/skyblock`. Set `level-type=worldarchitect\:skyblock_demo` for a custom Overworld island with two personal slots, a shared Nether island, and an empty End. Set `level-type=worldarchitect\:void_demo` for pure void in all three dimensions with unsafe Overworld spawn explicitly accepted.

Both presets use `type: "worldarchitect:void"`. Omit `island` for pure void. On the Overworld, `allow_unsafe_spawn` must then be `true`. With Skyblock, `island.template` stores the bounds, marker, and blocks. `island.origin_y` sets the bottom of the template. `island.spacing` defaults to 128 and `island.personal_slots` defaults to 0. Secondary dimensions support a shared island. Create a new world to change the generator, template, or allocation grid.
