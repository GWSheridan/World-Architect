# World Architect 1.4.0

World Architect 1.4 connects terrain regions, generation rules and procedural assemblies, and adds tools for recovering drafts and testing modpacks. It targets Minecraft Java 26.3, NeoForge 26.3.0.103 and Java 25.

- **Draft history:** bounded Undo/Redo, duplication, ordering and explicit recovery after an interrupted editing session.
- **Shared regions:** named terrain masks in material, ore, decoration, template and spawn conditions, with matching Rule Inspector explanations.
- **Constrained pools:** required rooms, module count limits, branch depth and diagnostic seed samples. Incomplete layouts are rejected before placement.
- **Workbench authoring:** edit and export fingerprinted scenarios in game, then compare versions, providers and exact seeds in bounded source-tool batches with local HTML/JSON reports.

API 1 remains supported. Existing terrain algorithms, format-1 pools, saved templates, island assignments and End policies retain their behaviour. Named-region rules and constrained pools use explicit format 2 and require 1.4. Authoring networking requires matching 1.4 clients and servers. Changing a preset supplies defaults for future worlds; it does not replace saved generation.

Back up the complete world before updating. Retain its snapshots, archived bundle, settings, libraries and required provider mods. See [Draft History](wiki/Draft-History.md), [shared-region rules](wiki/World-Generation-Rules.md), [Procedural Pools](wiki/Procedural-Pools.md) and [Batch Testing](wiki/Batch-Testing.md).

The distribution includes the gameplay JAR, complete and buildable source ZIPs, eleven versioned example bundles and SHA-256 checksums. Windows/Linux validation passed 88 unit tests, 35 Node checks, public-mod compatibility, clean installs and corrected batch failure handling. Four real clients completed 63.8 minutes; eleven older-save cases and two revised-default cases matched 416 unexplored chunks. See [Testing](TESTING.md) for performance results, source reproducibility and the full fixture scope.
