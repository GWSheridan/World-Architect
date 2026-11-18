# Structure Library

Open the world’s library with `/wa_structure`. Select a template to see its title, category, author, size, required block namespaces, placement rules, and spawn markers. The top view shows the highest block in each column. Use the slice buttons to inspect individual Y levels. Gold cells mark spawn points.

Library commands require the same permission as Minecraft’s `/place` command. On a dedicated server, the console can run capture, import, export, editing, and placement commands. An in-game player can open the preview and travel to a marker.

## Capture a build

Set both corners of a loaded build in the same dimension. Supply coordinates or stand at a corner and omit them. The second corner displays the selection edges. Run `/wa_structure outline` to show the edges again.

```text
/wa_structure pos1 15 100 14
/wa_structure pos2 18 103 16
/wa_structure capture builds:workshop
/wa_structure describe builds:workshop "Houses" "Workshop"
/wa_structure preview builds:workshop
```

The selection includes both corners and supports up to 32 blocks per axis. You can also pass both corners to `capture` directly. Library IDs use Minecraft’s `namespace:path` form. Use `duplicate` to copy a template under a new ID and `rename` to move it to an unused ID.

## Import and export

```text
/wa_structure import examples:workshop builds:imported_workshop
/wa_structure export builds:workshop examples:workshop_copy
```

The importer reads single-palette Minecraft structure files from `generated/<namespace>/structure/<path>.nbt` in the world or `data/<namespace>/structure/<path>.nbt` in an enabled datapack. It reports unknown blocks and invalid states. The archive stores a snapshot in `data/worldarchitect/structures/<namespace>/<path>.nbt` within the save. Export writes a native file under `generated/` and includes World Architect’s category, author, revision, rules, and markers.

Captures and imports preserve registered block states, sign text and its dye, glow, and wax settings, banner patterns, and chest or banner display names. Sign lines and display names are copied as plain text. Chests are placed empty. Entity data, inventories, loot assignments, and unsupported block-entity fields are discarded. Privileged or world-management blocks such as command blocks, jigsaws, and spawners are rejected.

Block-only exports that meet the [Skyblock template limits](SKYBLOCK.md) can be imported into the island editor. Enter the local coordinates of the library marker you want to use.

## Place a template

```text
/wa_structure rules set builds:workshop supported air 80 100 minecraft:the_nether minecraft:nether_wastes
/execute in minecraft:the_nether run wa_structure place builds:workshop 64 90 -32 cw90 left_right
```

`rules set <id> <terrain> <collision> <min-y> <max-y> [dimension] [biome]` saves the rules to the template. Use `any` for an unrestricted dimension when specifying a biome. `rules clear <id>` restores the defaults.

- `free` allows floating placement. `supported` requires a safe full block below the lowest placed block in each occupied column. `buried` requires the placed blocks to fit under an existing surface.
- `air` requires air at every occupied destination cell. `matching` also accepts existing identical blocks without block entities and leaves them in place. Air spaces in a template leave the destination as it is.
- The server validates loaded chunks, build height, biome, world border, collisions, and spawn clearance before placement.
- Rotation options are `none`, `cw90`, `cw180`, and `ccw90`. Mirror options are `none`, `left_right` for the Z axis, and `front_back` for the X axis. Omit both for the saved orientation. The origin is the lower corner of the transformed bounds. Use absolute coordinates or Minecraft’s relative coordinates.

Library placement is command-driven. Placed blocks and supported metadata save as ordinary chunk data, independent of later template edits.

## Spawn markers

```text
/wa_structure marker set builds:workshop entry 2 1 2 north
/execute in minecraft:the_nether run wa_structure spawn builds:workshop entry 64 90 -32 cw90 left_right
```

A marker stores the player’s feet position and facing relative to the template. Place it inside the selection with two air cells over a safe full block. Use the same origin, rotation, and mirror for both `place` and `spawn`. The server transforms the location and facing together and checks the live blocks before teleporting the player. Use `marker remove <id> <name>` to remove a marker.
