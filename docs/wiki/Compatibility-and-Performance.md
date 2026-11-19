# Compatibility and performance

World Architect 1.2.0 targets Minecraft 26.3, NeoForge 26.3.0.103 and Java 25. Use matching mod/provider versions on clients and servers. Specialised dimensions, structures and native features need compatible adapters; support does not certify every configuration of another mod.

Saved profiles pin the versions and definitions used for generation. A missing or mismatched dependency stops loading with an error. Earlier profiles keep their original algorithms, encoding and End policy. Native generation still has decoration and ticking variability; compare managed base terrain separately when testing changes.

Native feature adapters are trusted provider code. Their filters constrain placement origins; fixed rule-engine actions enforce their own per-block filters. Providers that bypass normal world-generation regions also need to honour the End reservation.

For pack performance, measure a fixed seed and chunk region on the same host, then add structures, rules and features in stages. Respect work budgets and assembly bounds. The central arena uses native generation within a fixed area; custom chunks outside it retain the existing path.

The source includes a separate API consumer and same-host benchmark project under `examples/`. See the [maintainer testing guide](https://github.com/GWSheridan/World-Architect/blob/v1.2.0/docs/TESTING.md) for reproduction instructions and release results. Diagnostic probes are not part of the gameplay JAR.

The 1.2 checks use the published 1.1.0 baseline. Windows and Linux pass the Terralith/Lithostitched pack, the Biomes O' Plenty/GlitchCore/TerraBlender pack and their combined five-mod pack, each with Original and Single Block terrain. Seven example bundles install in fresh dedicated servers. Four real clients complete more than an hour of exploration, End progression and restart checks with the exact release JAR.

Short Void and Single Block timing flags did not reproduce in three fresh alternating pairs. A native desert pyramid's randomized burial depth varied between runs of the same binary; terrain outside its footprint and all biomes matched. Existing-save comparisons retain exact equality. The [verification report](https://github.com/GWSheridan/World-Architect/blob/v1.2.0/docs/maintainers/verification/release-1.2/README.md) preserves the initial flags, repeat data and test limits.
