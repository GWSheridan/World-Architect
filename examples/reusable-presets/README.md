# Reusable Skyblock presets

These three self-contained bundles demonstrate inheritance in Worlds Unwritten 1.7.0:

| Bundle | Policy | Personal islands |
| --- | --- | --- |
| `reusable-skyblock-1.7.0.zip` | Base: only the Overworld is editable | 64 |
| `inherited-partial-1.7.0.zip` | Child: only the Overworld is editable | 96 |
| `inherited-skyblock-1.7.0.zip` | Child: full generation lock | 128 |

Copy the chosen archive into `config/worldsunwritten/bundles` and select its filename in `developer.json`. Install matching archives on clients and servers. Children do not require the base ZIP at runtime. Each child embeds its parent's exact preset ID, release, archive SHA-256, lock baseline and template fingerprints. Both children retain the original captured island resource. All three disable Structure Library placement, keep seed 18 and native Nether/End generation, and preserve vanilla dragon progression.

Each archive has an unpacked source directory beside `bundles`. To author another child, enable developer mode, import and save the base, then choose **Preset Studio → Tools / help → Derive from saved preset**. The new child pins the saved revision you actually select; reimporting and saving an equivalent archive can give a different archive hash. Validate, save under a new version, then export. Never edit a published parent or its recorded fingerprints to update a child.

These bundles configure generation and island allocation. Runtime island administration and optional block/container protection belong to the destination world's administration settings. Existing worlds retain their snapshots and multiplayer assignments. See [Preset Studio](../../docs/wiki/Preset-Studio.md), [locks](../../docs/wiki/Locks-and-Server-Enforcement.md) and [multiplayer islands](../../docs/wiki/Multiplayer-and-Islands.md).
