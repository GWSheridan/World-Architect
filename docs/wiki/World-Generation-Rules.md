# World Generation Rule Engine

Generation rules control managed terrain independently in each dimension and are saved with the world. Open **Create New World → Customize → select a managed dimension → World Generation Rules**. Add an action, edit its conditions, and choose **Done** in both editors. Cancel discards the draft.

Separate pages cover identity and priority; dimensions, biomes and height; region and distance; seed and dependencies; and the action. Move up/down controls ties. Invalid rule sets disable Done and explain the problem. Saved-world generation rules cannot be edited with commands.

## Conditions

Omitted conditions match every candidate. All supplied conditions must pass.

| Field | Meaning |
| --- | --- |
| `dimensions`, `biomes` | Up to 16 registry IDs each. The candidate must match an ID in each supplied list. |
| `min_y`, `max_y` | Inclusive absolute block heights. |
| `region` | Inclusive `min_x`, `max_x`, `min_z`, `max_z`; supply all four. |
| `named_region` | Version 2: existing terrain-region `name`, optional `mode` (`mask` or `winner`) and `exclude` boolean. |
| `distance` | Inclusive horizontal radius from `center_x`, `center_z` (default 0). Requires `max`; `min` defaults to 0. |
| `seed` | Exact world seed. |
| `seed_modulus`, `seed_remainder`, `seed_salt` | A deterministic hash of seed and candidate X/Z must have the specified remainder. Defaults: 1, 0, 0. |
| `mods` | All listed mod IDs must be installed. Declared IDs' presence, absence and versions are pinned in the world snapshot. |
| `requires` | IDs of earlier rules whose conditions must match at the same candidate. This rule-level field belongs outside `when`. |

Dependencies describe condition matches, not successful placement. A target mismatch, collision or terrain check can still prevent an earlier action from writing anything. Unknown, cyclic and forward dependencies are rejected. Generated-marker spawning independently verifies that the referenced structure actually exists.

## Actions and order

Fixed phases run **material → feature → template → spawn**. Within each phase, higher priority comes first; ties retain editor order. Priorities range from -1000 to 1000.

- **Material:** output block state and optional exact target state. The first matching rule accepting the original terrain material wins. Rules feed the shared terrain sampler, so generated columns, heightmaps, previews and structure validation agree. They replace existing solid cells and preserve terrain geometry and void air.
- **Ore:** bounded ore placement with material, target, attempts, vein size, Y range and biome filters. Rule conditions are checked at every proposed write.
- **Decoration:** surface placement with material, attempts, chance, height and biome filters. Every proposed write also needs rule conditions, support and free space.
- **Template:** registered `worldarchitect:modular` structure with spacing/separation in chunks. The highest-priority eligible template whose seeded grid selects the candidate wins. Every assembly cell must pass the rule's spatial/height filters and the template's own placement rules. A rejected winner does not fall back to a lower-priority template. Use existing Features controls for supported native structures.
- **Spawn:** event `initial`, `respawn` or `portal`, with a safe `fixed`, `generated`, `island` or `team` policy. Generated policies reference an enabled modular structure, module, marker and position inside its start. Portal rules target their own dimension. Personal respawn points and explicit administrative policies retain their existing precedence. See [spawn policies](Void-and-Skyblock.md).

Initial join rules are selected from the Overworld profile and may target another dimension. Respawns check the player's current dimension and then the Overworld; portal rules use the destination dimension. Conditions for a spawn action are evaluated at its target reference position.

Actions add to existing Features selections. Ore actions run during terrain filling; decorations run before template placement. Existing actions retain their support, collision and target checks. Do not select a structure in both Features and a template action. Stable rule IDs seed feature randomness; moving a rule preserves its random stream. Decisions do not depend on players, time, request order or a shared mutable random sequence.

Each dimension accepts at most 32 rules, eight template candidates and 65,536 combined template cells. Engine ore `attempts × size` plus decoration attempts must total at most 32,768 per chunk. Existing action-specific bounds still apply. The editor and startup reject excessive profiles.

Managed solid terrain supports every action. True Void/Skyblock supports spawn actions; remove terrain/template actions before switching to Void. Specialized generators retain adapter requirements. Falling material outputs are rejected where floating terrain or caves would make terrain unstable.

## Datapacks

Put `rule_engine` inside the managed generator's `dimension_profile`. Bind its `dimension` to that profile's dimension. Example engine value:

```json
{
  "version": 1,
  "dimension": "minecraft:overworld",
  "rules": [
    {
      "id": "gold_cap",
      "priority": 100,
      "when": {
        "dimensions": ["minecraft:overworld"],
        "biomes": ["minecraft:plains"],
        "min_y": 92,
        "max_y": 96,
        "region": {"min_x": -64, "max_x": -1, "min_z": -32, "max_z": 64},
        "seed": 18
      },
      "action": {"type": "material", "material": "minecraft:gold_block"}
    }
  ]
}
```

The [floating Sky City example](https://github.com/GWSheridan/World-Architect/tree/v1.6.0/examples/worldgen-rules) includes every action type, competing templates, rejected filters and generated-marker join/respawn policies.

## Inspection and protection

`/wa_rules <dimension>` reports execution order. `/wa_rules <dimension> explain <x> <y> <z>` reports condition matches without generating chunks or changing settings. All players may inspect; there is no mutation command. Explanations describe conditions rather than successful placement.

Rules, engine version, template definitions and declared mod dependencies are covered by immutable generation snapshots. Changing an installed world preset does not change an existing world's inline rules or unexplored chunks. A changed declared mod presence/version stops startup. Keep required datapacks installed; changes to registered templates used by a protected save are rejected.

Profiles without `rule_engine` keep the original generation path and serialize without an added engine field, preserving older snapshots. Back up the complete world, including generation files, template archives, administration settings and island ledger, before upgrading.

The [Rule Inspector](Rule-Inspector.md) visualizes condition coverage, explains coordinates and reports order and conservative conflicts without creating chunks.

## Shared terrain regions

Choose **Named terrain region** in the rule editor to reuse a region from that dimension's terrain profile. `mask` tests its inclusive geometric bounds. `winner` also requires that it wins over other containing regions by priority and then list order. Height feathering and biome transition weights do not shrink these masks. `exclude: true` inverts a known region's result; unknown or missing regions still fail closed. A named spawn condition must target its own dimension. Void has no regional terrain and refuses named references.

For example, `"when": {"named_region": {"name": "forest", "mode": "winner", "exclude": false}}` requires a root engine `"version": 2`. Version 1 keeps its previous encoding and evaluator. Material, ore and decoration writes use the same condition; every template cell must satisfy its spatial filters. [Rule Inspector](Rule-Inspector.md) uses that evaluator for region overlays and coordinate explanations. See the complete [shared-regions example](https://github.com/GWSheridan/World-Architect/tree/v1.6.0/examples/shared-regions).
