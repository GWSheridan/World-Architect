# World Architect 1.3.0

World Architect 1.3 adds saved-world inspection, regional terrain, procedural assembly pools and a modpack test workbench. It targets Minecraft Java 26.3, NeoForge 26.3.0.103 and Java 25.

- **World Inspector:** inspect original dimension profiles and pinned resources, compare installed or saved presets, and export bounded local reports.
- **Regional terrain:** draw rectangles and circles, choose priorities and biomes, and feather height transitions with opt-in generator version 3.
- **Procedural pools:** connect weighted rooms, halls, branches and end caps with seeded previews and bounded generation.
- **Test workbench:** export fingerprinted scenarios and run isolated dedicated servers with explicit probes, startup expectations, restart checks and local reports.

Assembly exports now include live mod-added dimensions that are absent from the selected starting preset.

API 1 remains supported. Existing version 1/2 terrain, static assemblies, saved templates, island assignments and native End policies retain their formats and behaviour. New regional profiles and pool definitions require 1.3. Use matching 1.3 clients and servers for authoring networking. Changed defaults affect new worlds; they do not migrate existing saves.

Back up the complete world and keep its original snapshot, settings, archived bundle and provider mods. Restore missing dependencies instead of replacing saved generation files. Reports and test results stay local unless deliberately shared.

The distribution includes the JAR, complete ZIP, buildable source ZIP, nine versioned example bundles and SHA-256 checksums. See [World Inspector](wiki/World-Inspector.md), [Regional Terrain](wiki/Regional-Terrain.md), [Procedural Pools](wiki/Procedural-Pools.md) and [Test Workbench](wiki/Test-Workbench.md) for usage.

Release preparation is in progress. This branch and its wiki changes are staged; 1.2.0 remains the public latest release until the 1.3 draft is published. Earlier release files are retained.
