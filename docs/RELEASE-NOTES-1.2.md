# World Architect 1.2.0

World Architect 1.2 adds visual tools for modular structures and generation rules, plus an administration panel for multiplayer servers.

- Build an assembly from saved templates. Edit module offsets, rotations and mirrors, orbit the complete preview, review transformed markers, and save the draft for later.
- Export validated datapacks and new-world preset bundles with embedded modules. Conflicting saved revisions, occupied cells, blocked markers and oversized assemblies are refused.
- Inspect generation conditions on a map. Switch between biome, height, region, distance and combined coverage, explain a coordinate, and review rule order, dependencies and conservative warnings without creating chunks.
- Manage islands, scoreboard teams, spawn policies and protection through `/wa_admin`. Changes require confirmation and a fresh server check; stale views and revoked permissions cannot commit them.
- Close protected containers when ownership or team access changes, while retaining their contents.
- Keep small-window navigation and confirmation controls usable after resizing, and cache complete assembly previews between edits.

Requires **Minecraft Java 26.3, NeoForge 26.3.0.103 and Java 25**. Install matching 1.2 versions on clients and servers. API 1 and existing preset, template, generation, island and administration formats remain supported.

Existing worlds retain their saved algorithms, templates, archived presets and End policies, including unexplored terrain. Assembly drafts are separate authoring documents. Exports affect new worlds; they do not replace saved generation. Back up the complete world and keep its required providers installed before updating.

## Downloads

| File | Contents |
| --- | --- |
| `worldarchitect-1.2.0.jar` | Gameplay mod for clients and dedicated servers |
| `worldarchitect-1.2.0-release.zip` | Mod, buildable source, documentation, offline wiki and examples |
| `worldarchitect-1.2.0-source.zip` | Buildable source, tests, validation tools, documentation and examples |
| `single-block-1.2.0.zip`, `layered-1.2.0.zip`, `skyblock-1.2.0.zip`, `custom-end-1.2.0.zip` | Four unlocked starter bundles |
| `studio-partial-1.2.0.zip`, `studio-skyblock-1.2.0.zip` | Partially locked preset and fully locked multiplayer Skyblock pack |
| `assembly-demo-1.2.0.zip` | Assembly Studio example with two transformed modules and safe markers |
| `SHA256SUMS-1.2.0.txt` | SHA-256 checksums for all ten downloads |

Place the JAR in `mods/`. Keep preset bundles zipped in `config/worldarchitect/bundles/` and select the required bundle in `developer.json`. Test fixtures and third-party compatibility mods are excluded from the gameplay distribution.

See [Assembly Studio](wiki/Assembly-Studio.md), [Rule Inspector](wiki/Rule-Inspector.md), [Administration Panel](wiki/Administration-Panel.md) and [Backups and Upgrades](wiki/Backups-and-Upgrades.md).
