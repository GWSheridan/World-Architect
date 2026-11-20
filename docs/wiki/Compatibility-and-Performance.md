# Compatibility and performance

World Architect 1.4.0 targets Minecraft 26.3, NeoForge 26.3.0.103 and Java 25. Use matching mod/provider versions on clients and servers. Specialised dimensions, structures and native features need compatible adapters; support does not certify every configuration of another mod.

Saved profiles pin the versions and definitions used for generation. A missing or mismatched dependency stops loading with an error. Earlier profiles keep their original algorithms, encoding and End policy. Native generation still has decoration and ticking variability; compare managed base terrain separately when testing changes.

Native feature adapters are trusted provider code. Their filters constrain placement origins; fixed rule-engine actions enforce their own per-block filters. Providers that bypass normal world-generation regions also need to honour the End reservation.

For pack performance, measure a fixed seed and chunk region on the same host, then add structures, rules and features in stages. Respect work budgets and assembly bounds. The central arena uses native generation within a fixed area; custom chunks outside it retain the existing path.

The source includes a separate API consumer and same-host benchmark project under `examples/`. See the [maintainer testing guide](https://github.com/GWSheridan/World-Architect/blob/v1.3.0/docs/TESTING.md) for reproduction instructions and release results. Diagnostic probes are not part of the gameplay JAR.

The 1.2 checks use the published 1.1.0 baseline. Windows and Linux pass the Terralith/Lithostitched pack, the Biomes O' Plenty/GlitchCore/TerraBlender pack and their combined five-mod pack, each with Original and Single Block terrain. Seven example bundles install in fresh dedicated servers. Four real clients complete more than an hour of exploration, End progression and restart checks with the exact release JAR.

Short Void and Single Block timing flags did not reproduce in three fresh alternating pairs. A native desert pyramid's randomized burial depth varied between runs of the same binary; terrain outside its footprint and all biomes matched. Existing-save comparisons retain exact equality. The [verification report](https://github.com/GWSheridan/World-Architect/blob/v1.2.0/docs/maintainers/verification/release-1.2/README.md) preserves the initial flags, repeat data and test limits.

The 1.3 checks use the published 1.2.0 baseline. Both Windows and Linux pass 70 unit tests, six public-mod combinations, six exported assembly cases and nine clean example installs with the exact gameplay JAR. Four real clients completed 62.7 minutes, native End progression and restart checks. Pools also passed persisted-piece checks in the separate API fixture's custom dimension.

The longer benchmark uses 64 warmup chunks and five measured batches of 64 chunks per profile in three alternating pairs. Initial timing flags in Original, Void and structure-heavy, plus Original resident memory, did not reproduce in three fresh pairs of those profiles. Repeat generation changes were −3.23%, +5.40% and +5.15%; sampled memory stayed below the 15% threshold. Native pyramid burial variation also occurred with unchanged 1.2.0; terrain outside its footprint and all biomes matched. Strict older-save comparisons remained exact. See [Testing](https://github.com/GWSheridan/World-Architect/blob/v1.3.0/docs/TESTING.md) for the original and repeat figures and their limits.

Use the [Test Workbench](Test-Workbench.md) to validate a pack in isolated instances. These results cover fixed fixtures and pinned providers; they do not certify every configuration. The 1.2 results above remain historical.


## Version 1.4 checks

Windows and Linux passed 88 unit tests, 35 Node checks, six pinned public-mod combinations against 1.3.0, eleven clean examples and the corrected workbench/batch suites. Static, legacy and constrained assemblies also passed in the separate API laboratory. Four real clients completed 63.8 minutes with native End progression and restart persistence.

Eleven older-save cases matched 352 unexplored chunks and left 1,019 original files unchanged. Two new format-2 worlds retained archived settings/templates after revised defaults were installed, with another 64 exact unexplored chunks and valid fresh installs of those revised packs. Missing/mismatched dependencies and corrupt generation data stopped safely.

All profiles passed the initial three-pair comparison. The comparison used three alternating pairs, 64 warmup chunks and five measured batches of 64 chunks per profile. The [testing guide](https://github.com/GWSheridan/World-Architect/blob/v1.4.0/docs/TESTING.md) records percentages, initial flags, repeats and strict/native diagnostic limits. Existing-save comparisons remain exact. API 1 is retained; authoring networking requires matching 1.4 clients and servers.
