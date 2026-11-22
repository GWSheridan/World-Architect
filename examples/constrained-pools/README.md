# Constrained procedural pool

`bundles/constrained-pool-1.4.0.zip` is an unlocked new-world example for Worlds Unwritten 1.4.0. Install it, still zipped, under `config/worldsunwritten/bundles/` and set:

```json
{"format":1,"bundle":"constrained-pool-1.4.0.zip","developer_mode":true}
```

The preset and structure IDs are `worldsunwritten_examples:constrained_pool`. It requires only Minecraft 26.3, NeoForge 26.3.0.103 and Worlds Unwritten. Start a new world with the manifest defaults. The adjacent source directory contains the readable definitions and embedded template snapshots.

The format-2 pool uses a junction, halls and two end caps. It requires one or two halls and at least one stone end cap, with maximum branch depth two. Root depth is zero; caps count toward depth and module totals. The existing eight-piece/128-attempt budgets apply. A candidate that cannot meet every requirement is rejected before any piece is placed.

Open **Assembly Studio → Pools → Room constraints** for count/depth authoring and **Preview report** for explanations and a bounded consecutive-seed sample. Generated pieces retain exact transformed block states, empty named chests and entry markers. Prefer the stable root marker for mandatory spawn targets; child ordinals vary by layout. Existing format-1 pools retain their original algorithm. See [Procedural Pools](../../docs/wiki/Procedural-Pools.md).
