# World Architect 1.1

This cycle targets Minecraft 26.3, NeoForge 26.3.0.103 and Java 25. The release candidate is packaged as `1.1.0`; publication waits for the release checks. Existing 0.9.0 and 1.0.0 releases remain available.

Milestones 22–26 are implemented. Dedicated, client, multiplayer, save-protection and performance checks passed; [supporting results](maintainers/verification/release-1.1/) record their scope. The release assets and wiki are prepared for publication. The GitHub release remains a draft until it is published.

| Milestone | Scope | Release checks |
| --- | --- | --- |
| 22 · Creator workflow | Starter gallery, searchable preset library, draft cancellation and validation | Create, export and install each starter; keyboard navigation, resizing, scales 1 and 2, bundle locks |
| 23 · Terrain preview | Rotatable surface view, cross-sections, coordinate navigation, markers and End arena boundary | Compare base columns and heights; cancel stale sampling; ensure previews never create chunks |
| 24 · Structure placement | 3D template viewer, staged placement, world ghost and guarded undo | Two-client synchronization, server permissions, stale revisions, destination edits, metadata, bounds and restart |
| 25 · Compatibility lab | Portable fixtures, Windows/Linux CI, public-mod matrix, local diagnostics and performance comparisons | Thirty-minute two-client soak; three alternating benchmark pairs; investigate time regressions over 10% or memory regressions over 15% |
| 26 · Integration and release | Final regression suite, upgrade copies, wiki, examples and reproducible artifacts | Preserve saved generation and unexplored chunks; rebuild the tested JAR from its source ZIP; prepare the GitHub release |

API 1 signatures and preset/template formats stay compatible. Placement networking requires matching 1.1 clients and servers. Editor, preview and placement-history classes are internal, including classes that are Java-public for loader access.

Worlds retain their original algorithms, snapshots, templates, archive fingerprints and End policies. Starters configure future worlds. Manual structure placement writes ordinary blocks without changing generation settings.

Larger templates, new terrain algorithms, Minecraft ports and breaking API changes remain outside this cycle. The template limit stays at 32 blocks per axis.

Milestone 21's [stabilization evidence](maintainers/verification/stabilization-1.0/README.md) is retained separately. It did not identify a defect requiring a 1.0.1 patch.
