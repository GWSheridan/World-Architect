# World Architect 1.2

Milestones 27–31 focus on visual authoring and server administration on Minecraft 26.3, NeoForge 26.3.0.103 and Java 25.

| Milestone | Deliverable | Acceptance |
| --- | --- | --- |
| 27 · Stabilization | Published 1.1.0 validation, reproducible modpack fixtures and confirmed fixes | Four real clients for one hour; exploration, islands, structures, native End progression and restart persistence; patch 1.1 only for a confirmed product defect |
| 28 · Assembly Studio | Visual module editing, complete assembly previews, saved drafts, datapack and preset exports | Create an assembly in-game, export and generate it in supported dimensions; reject overlap, bounds, invalid metadata and missing dependencies |
| 29 · Rule Inspector | Condition overlays, coordinate explanations, dependency/order view and conservative warnings | Compare visual decisions with the existing evaluator over seeds and coordinates; bounded sampling without chunk creation |
| 30 · Administration panel | Permissioned island, team, spawn and protection controls with staged confirmation | Server authority, stale-state refusal, simultaneous administrators, team changes, container revocation and restart persistence |
| 31 · Integration and release | Exact-JAR Windows/Linux acceptance, upgrades, performance, wiki, examples and reproducible artifacts | Compare against 1.1.0 on the same host; verify prior saves and releases; rebuild the exact JAR from source; prepare the 1.2 GitHub release |

API 1, immutable saved generation, native End policies and existing preset/template formats remain supported. Assemblies retain the existing limits: 16 modules, 96 blocks per axis and 65,536 occupied cells; each source template remains limited to 32 blocks per axis. Authoring drafts and administration controls do not replace generation snapshots. Upgrade runs use copies of saved worlds.

Earlier release downloads and tags remain unchanged. Publication of the prepared 1.2 release is a separate step.

Implementation and validation are recorded in the [1.2 verification report](maintainers/verification/release-1.2/README.md), including exact-JAR platform checks, both four-client hour-long runs, upgrade copies and the performance investigation. Release artifacts and the wiki are staged for review before publication.
