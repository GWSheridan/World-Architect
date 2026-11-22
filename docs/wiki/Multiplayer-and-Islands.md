# Multiplayer and islands

## Multiplayer islands

Set **Personal slots** in the Overworld to a number between 1 and 4095. Use `0` for one shared island. Each first-time player claims one slot by UUID. The shared island is slot 0. Personal slots are distinct origins arranged in rows of 16. Spacing is measured between origins, in multiples of 16 from 64 to 4096 blocks. The generator reserves the grid, so sections explored before allocation already have the same island layout. When all slots are assigned, the server tells unassigned players that the islands are full. Existing players can rejoin.

Include `worldsunwritten-islands-v1.dat` in any world backup or move. If it is missing or unreadable, the server stops before opening the saved world. Slot ownership controls spawn assignment and access when island protection is enabled.

## Spawn policies and island administration

Operators with permission level 3 can configure initial joins, death respawns, and portal arrivals with `worldsunwritten spawn`. Policies use safe fixed positions, assigned islands, scoreboard teams, library markers, or saved markers in generated modular structures.

```text
/worldsunwritten spawn set initial team minecraft:overworld
/worldsunwritten spawn set respawn team minecraft:overworld
/worldsunwritten spawn set portal team minecraft:overworld
/worldsunwritten spawn set portal fixed minecraft:the_nether 0 67 0
/worldsunwritten spawn set respawn marker minecraft:overworld builds:hall entry 64 90 -32 cw90 left_right
/worldsunwritten spawn set initial generated wa_example:workshop wa_example:village 1 81 1 main entry
/worldsunwritten spawn clear respawn
```

Fixed positions are feet coordinates. Place a library structure before selecting its marker. Generated marker policies identify a structure, a position inside its saved bounding box, the module name, and its marker. Set commands validate the requested destination before saving it. The server checks live support, headroom, build limits, and the world border at arrival. It searches up to four blocks from a fixed or structure marker and can fall back to an island or terrain spawn. It refuses an arrival when it cannot find a safe destination.

Initial policies apply when a player has no saved position. A valid personal respawn point takes priority over the configured death-respawn policy. Portal policies apply to players entering the selected dimension. The bundled spawn lock also governs policy edits.

Team policies use Minecraft scoreboard membership. Create a team and add players with the vanilla `team` commands. The first arriving member reserves one team slot, and members share that island. Team reservations and player assignments use the same capacity. Existing individual assignments remain allocated when a player joins a team.

```text
/team add builders
/team join builders PlayerName
/worldsunwritten island
/worldsunwritten island home
/worldsunwritten island list
/worldsunwritten island inspect 2
/worldsunwritten island reassign PlayerName 3
/worldsunwritten island swap FirstPlayer SecondPlayer
/worldsunwritten island team assign builders 4
```

All players can inspect their own assignment and use `home`. Other island commands require permission level 3. Reassign and swap commit the updated ownership before reporting success. Reassign requires an available slot, while swap exchanges two player assignments atomically. Use online player names or saved player UUIDs. Changes take effect on the next island home or policy-based respawn. The commands preserve the island's blocks and player-built contents.

## Optional island protection

Enable player block breaking, placement, and selected container protection independently. Protection covers each reserved island's entire horizontal spacing cell through the dimension's build height. Owners and members of its assigned scoreboard team have access. Operators at permission level 2 or higher can bypass Worlds Unwritten protection.

```text
/worldsunwritten island protection breaking true
/worldsunwritten island protection placement true
/worldsunwritten island protection containers true
/worldsunwritten island protection shared true
/worldsunwritten island protection container add minecraft:hopper
/worldsunwritten island protection container remove minecraft:barrel
```

Chests, trapped chests, and barrels are the default protected container types. Container protection also covers breaking those blocks. Both halves of a connected double chest must be accessible. The server checks open-container clicks again so a change of ownership or team membership can revoke an existing session, including a session opened before container protection was enabled. Set any flag to `false` to disable it. The `shared` flag applies the enabled protections to island 0. These controls govern player actions; they do not claim islands against automation or environmental damage.

Administration settings live in `data/worldsunwritten-administration-v1.dat`. Include that file and the island ledger in backups. The normal save records whether administration settings are required and rejects a missing file on reopening. Team reservations use allocation format 2 in the existing `worldsunwritten-islands-v1.dat` file. Versions without team-allocation support cannot open worlds using teams. The server continues to read earlier allocation files.


Keep the same authentication mode throughout a world's life so player UUIDs remain consistent. The fully locked Studio example has two personal slots; a third unassigned player receives a capacity message. Choose a larger capacity before creating a world when your pack needs more players.

Use [Administration Panel](Administration-Panel.md) for the permissioned GUI over these commands. Changes require confirmation and are refused when the reviewed server state is stale.
