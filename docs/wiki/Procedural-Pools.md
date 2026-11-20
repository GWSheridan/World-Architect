# Procedural assembly pools

Open `/wa_assembly`, add saved template modules, and choose **Pools**. Enable a pool, select its root module, and set the maximum generated modules, attempt limit and seed offset. The source modules become a catalog; their static offsets are not used by the pool. Source rotations and mirrors are baked into each catalog template before connection coordinates are interpreted.

Add named horizontal connections using local coordinates, facing, target pool and channel. A connection needs two clear cells on the matching module edge over a safe floor. Channels must match when pieces join. Add weighted regular choices for rooms, halls or branches, and end-cap choices with exactly one connection. Every outgoing pool/channel needs a compatible end cap. The module budget must leave room to cap the root's connections.

**Sample layout seed** updates the detached preview. Generated layouts also incorporate the world seed and candidate chunk coordinates. Regular choices are tried before end caps; weights select the order within each role. The layout reserves capacity for outstanding ends, checks occupied cells and marker/door clearance, and stops at its attempt budget. If it cannot close the layout, the entire candidate is skipped before placing any piece.

The existing limits apply: 16 source modules, at most 16 generated modules, 96 blocks per axis and 65,536 occupied cells. A pool has at most 128 connections, eight per module, 64 choices, 256 candidate attachment attempts and weights of 1–100. It uses right-angle horizontal rotations. Terrain adaptation must be `none`. Biome, dimension, slope, height, collision, reservation and generation-rule checks still apply to the selected layout.

Save the draft and export a datapack or preset as usual. The `worldarchitect:modular` structure definition gains optional `assembly_pool` format 1 with `root`, `max_modules`, `attempts`, `seed_salt`, `connections` and `choices`. Static definitions omit it. Spawn references may use the stable `root` module; generated child names are `module_1`, `module_2` and so on. A referenced child's presence depends on the selected layout, so prefer a root marker for required spawns.

Templates and pool definitions are embedded in preset archives. Generated pieces persist their chosen templates, positions, block states, metadata and markers. Later catalog edits supply future-world defaults and do not rewrite saved generation. See [Assembly Studio](Assembly-Studio.md), [Templates and Bundling](Templates-and-Bundling.md) and the [pool example](https://github.com/GWSheridan/World-Architect/tree/v1.6.0/examples/procedural-pools).

## Required rooms and depth

Use **Room constraints** to set a module's minimum and maximum count and the maximum branch depth. Counts include the root and caps. Root depth is zero; caps must fit the depth limit too. Constraints select pool **format 2**; an unconstrained format-1 pool keeps its original algorithm. Requirements that cannot fit the catalog or total module budget are refused. During planning, unmet requirements are preferred among eligible choices, but connections, collisions, clearance, bounds and budgets still apply. A valid definition can reject a particular seed; there is no partial placement or unbounded search.

Format 2 adds, for example:

```json
"constraints": {
  "max_depth": 2,
  "modules": [{"module": "hall", "min": 1, "max": 2}]
}
```

Counts range from 0–16; depth ranges from 1–15. Omitted module limits are 0–16. Module names must exist, and positive requirements outside the root need available choices. **Preview report** explains the detached layout's counts, attempts, depth and rejected choices. **Sample sixteen consecutive seeds** uses a bounded background worker and may truncate at its time budget. Samples are reproducible; generated candidates also incorporate their chunk coordinates. Editing or closing the session prevents an old background result replacing a newer view.

Use **History / organize** to duplicate/reorder catalog modules and reorder choices. List order can affect weighted choices in a future export. The stable root marker remains the recommended required spawn target; child ordinals depend on the selected layout. See [Draft History](Draft-History.md) and the [constrained example](https://github.com/GWSheridan/World-Architect/tree/v1.6.0/examples/constrained-pools).

## Vertical connections

In 1.5, connection facing also accepts `up` and `down`. Adding one selects **pool format 3**. Upward ports lie on the top template face; downward ports lie on the bottom. Each port and the cell immediately inside that face must be clear. Matched ports reserve their clear shaft cells across the join. Horizontal connections retain their two-cell doorway and safe-floor checks.

Modules remain upright and use horizontal right-angle rotations. A vertical connection aligns clearance; it does not build stairs, ladders or a route through the room. Supply those in the templates and keep them outside reserved shaft cells. Each pool/channel needs compatible end caps, including opposite vertical facings. Counts include every cap and the root; depth counts vertical branches too. Existing module, span, occupied-cell, port and attempt limits still apply.

Format 3 anchors the **root module**, preserving its entry height when a lower module is added. The layout's bounding origin may therefore lie below that anchor. Support checks can use solid cells in the complete planned assembly. A downward module still fails if it collides with terrain: the planner does not excavate a basement. Formats 1 and 2 retain their previous normalization and planning algorithms.

The [vertical tower example](https://github.com/GWSheridan/World-Architect/tree/v1.6.0/examples/vertical-pools) includes a continuous ladder between its floors. Inspect roots, ports, markers and terrain through the [site inspector](Structure-Site-Inspector.md).
