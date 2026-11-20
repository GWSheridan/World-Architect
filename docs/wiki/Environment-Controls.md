# Dimension Environment Designer

Environment settings are stored separately for each world. Each dimension can use its own supported clock, weather, spawn restrictions and portal-entry policy. The server sends fog, sky and cloud settings to each player's active dimension.

Use `/wa_environment <dimension>` to inspect settings, current time and weather, and capability diagnostics. Administrators at permission level 3 can open `/wa_environment <dimension> edit`. The designer has pages for time and weather, mob spawning, fog, sky and clouds, and portal entry. Choose an action to save that setting. The server checks permissions, preset locks, capability support and input bounds for every edit.

## Time and weather

```text
/wa_environment minecraft:overworld set time fixed 6000
/wa_environment wa_example:workshop set time fixed 18000
/wa_environment wa_example:workshop set weather thunder
/wa_environment minecraft:overworld set weather clear
/wa_environment minecraft:overworld set time offset 6000
/wa_environment minecraft:overworld set time vanilla
/wa_environment wa_example:workshop set weather vanilla
```

The workshop example dimension is supplied by the [cross-dimension structures datapack](https://github.com/GWSheridan/World-Architect/tree/v1.5.0/examples/cross-dimension-structures). Replace its ID with an installed compatible dimension.

Fixed time holds the dimension's default clock at the selected tick. Offset time adds 0–23999 ticks to the underlying clock and follows its normal rate and pause state. These are read-only views: shared clocks, game time, scheduled ticks and saved generation data remain untouched. Native timeline attributes use the same clock view, including sun position and skylight. Normal `/time` commands still change Minecraft's underlying clocks.

Weather choices are `vanilla`, `clear`, `rain` and `thunder`. Overrides use a dimension-local view and rain/thunder levels without changing the shared weather record. Clearing an override returns control to vanilla weather. Rain and snow still follow the biome's precipitation and temperature rules.

Dimensions without a changing default clock report time overrides as unsupported. The Nether and dimensions without surface weather reject weather overrides. Use the capability report before editing a modded dimension.

## Mob spawning and portals

```text
/wa_environment minecraft:overworld set mobs monsters false
/wa_environment minecraft:overworld set mobs creatures false
/wa_environment minecraft:overworld set mobs spawners false
/wa_environment minecraft:the_nether set portal operators
```

Monster and creature flags restrict native natural, chunk-generation, structure, patrol, event, reinforcement and jockey spawn checks. The spawner flag independently controls ordinary and trial spawners. An enabled flag permits the normal checks; it does not force a spawn. Existing mobs, breeding, player-created mobs, spawn eggs, commands and dimension travel retain their normal behaviour. Mods that bypass the native spawn hooks need their own integration.

Portal entry accepts `all`, `players`, `operators` or `none`. Operators need permission level 2 to enter an operator-only destination. The policy applies to the destination of native Nether and End portals, including non-player entities. Console teleports and administrator commands retain their normal behaviour. Custom portal destinations are reported as unsupported by the built-in hooks; a mod integration must call `WorldEnvironment.canEnterPortal` and supply its own configuration support.

## Fog, sky and clouds

```text
/wa_environment minecraft:overworld set fog_color 223344
/wa_environment minecraft:overworld set fog 4 96
/wa_environment minecraft:overworld set sky_color 33aaff
/wa_environment minecraft:overworld set clouds hidden
/wa_environment wa_example:workshop set clouds shown
/wa_environment wa_example:workshop set cloud_color 00ff00
/wa_environment wa_example:workshop set cloud_height 110
```

Colours are six hexadecimal RGB digits without `#`. Use `vanilla` in place of a colour or cloud height to remove that override. Cloud modes are `vanilla`, `hidden` and `shown`. Use `set fog vanilla` to restore normal fog distances. Fog start must be below fog end; both distances are bounded to 4096 blocks.

These settings use Minecraft's native environment attributes. Air fog retains water, lava and status-effect fog rules; client render distance still limits the view. Sky colours and clouds require the native Overworld skybox; other skyboxes report those settings as unsupported. Custom renderers can replace native attributes, and the player's cloud preference still controls whether clouds render.

The server synchronizes settings after edits, joining, respawning and changing dimensions. Disconnecting clears the client cache. Use matching versions on clients and servers.

## Persistence and locks

`/wa_environment <dimension> reset` removes that dimension's overrides. Unconfigured dimensions retain vanilla behaviour. Settings are stored under `environments` in the existing world-local `data/worldarchitect-administration-v1.dat` file. Include it in backups with island allocations and generation snapshots. Missing required administration data stops loading before Minecraft rewrites the level metadata.

The server reads earlier administration files without changing them until an administrator saves an edit. Environment edits preserve spawn policies and island protection settings. Generation snapshots, embedded templates and allocation files remain separate. Existing bundle dimension locks also govern environment changes; a full lock rejects them.

Keep the original world files when testing an update. Versions without environment support cannot open an environment-enabled world. See [Compatibility and Performance](Compatibility-and-Performance.md).
