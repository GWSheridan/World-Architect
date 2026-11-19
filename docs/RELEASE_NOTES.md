# World Architect 1.0.0

World Architect 1.0.0 targets **Minecraft Java Edition 26.3**, **NeoForge 26.3.0.103** and **Java 25**. Install the same World Architect/provider versions on the server and clients. Author, original creator and developer: **GWSHERIDAN**.

## What's included

- Independent registry-discovered dimension profiles: Original Generation, Single Block, Layered, Void and Skyblock.
- Searchable block/state selection, seed-based height/biome previews, version 2 landforms, floating terrain and strata.
- Per-dimension features, fluids and structures; Overworld End Cities and bounded modular template assemblies in custom dimensions.
- Structure capture, native/Sponge import, transforms, previews, placement rules, supported decorative metadata and safe spawn markers.
- Persistent multiplayer islands, safe spawn policies, island administration, optional protection and dimension environment controls.
- Ordered generation rules, Preset Studio, versioned bundles, partial/full locks and dedicated-server enforcement.
- Public API 1 for provider integrations, with missing/version checks and pinned native extension dependencies.
- Immutable generation snapshots, preserved templates/bundle archives and pre-generation compatibility checks.

## Downloads

| Artifact | Use |
| --- | --- |
| `worldarchitect-1.0.0.jar` | Production mod; install in `mods/` |
| `worldarchitect-1.0.0-release.zip` | JAR, complete buildable source ZIP, documentation and examples |
| `worldarchitect-1.0.0-source.zip` | Gradle wrapper, all implementation/test sources, docs and example projects |
| `studio-partial-1.0.0.zip` | Ready-to-install partially locked preset with an embedded modular structure |
| `studio-skyblock-1.0.0.zip` | Fully locked Skyblock preset with two personal slots and embedded islands |
| `SHA256SUMS-1.0.0.txt` | SHA-256 checksums for the five distributable assets |

The bundle revision is independent of the mod version. Their original bytes are preserved for archived-world compatibility. The API fixture and acceptance probes are diagnostic tools; they are not included in the production JAR.

## Installation and upgrades

See [installation](INSTALLING.md) for clean instances, checksum verification, building and existing-save upgrades. The [example index](../examples/README.md) gives the available presets and bundle installation workflow.

Existing saves retain their original seed, generator versions, profiles, rule order, templates and adapter requirements. Keep the complete world folder, compatible providers and datapacks. Do not edit or discard the immutable snapshot to bypass a rejected load. The 0.6.0 bundle upgrade caveat remains documented in [Modpack Developer Tools](MODPACKS.md).

This release does not migrate between Minecraft data formats. Native Original Generation can vary with native decoration overlap and ticking even between runs of the same binary; managed generation is checked separately. Custom End terrain disables vanilla dragon-fight progression; retain Original Generation there when that progression is needed. Native feature adapters are trusted provider code: their filters constrain origins, while fixed rule-engine actions enforce per-block filters. See [API 1](EXTENSION_API.md) and [Milestone 19 evidence](MILESTONE_19_VERIFICATION.md).

## Verification and source

[Milestone 20 verification](MILESTONE_20_VERIFICATION.md) records the final 1.0.0 multiplayer, clean client/server lock, compatibility, save-protection and artifact checks. [Testing](TESTING.md) describes the audits and reproduction steps. The earlier [0.9.0 release notes](RELEASE_NOTES_0.9.0.md) and milestone changelog remain available as historical evidence.

Build with `gradlew.bat assembleRelease` or `bash ./gradlew assembleRelease`. This runs the required checks, builds the production/source/distribution archives, stages both example bundles and generates checksums in `build/release/`.
