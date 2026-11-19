# World Architect 1.2 verification

Milestones 27–31 were verified on Minecraft 26.3, NeoForge 26.3.0.103 and Java 25. The release adds Assembly Studio, Rule Inspector and the multiplayer administration panel. API 1 and existing saved generation, preset, template, island and administration formats remain supported.

## Exact binary and platforms

The gameplay JAR has SHA-256 **`4d4732da16802e1d85e1d821e287b78bf3b2ea70cf4f6acb28cb42f4f8bc64b1`**. All 1.2 acceptance runs described here use that binary, including the long multiplayer runs, native GUI, upgrade, public-mod and performance checks. Test instrumentation is a separate mod and is excluded from the gameplay JAR.

The baseline is the published 1.1.0 JAR, SHA-256 `2bb93f40d6c9d03a1f461cb62c888ec650132ef44ba5f18052da5cc7d8e79efa`. A [binary comparison](generation-binary-relationship.json) found 168 existing generation, configuration, adapter, rule, template and protection classes byte-for-byte identical. That comparison excludes the read-only preview family and does not claim that the entire client or mod registration is unchanged.

All 61 unit tests passed. Both jobs in [CI run 44578290062](https://github.com/GWSheridan/World-Architect/actions/runs/44578290062) passed. Windows and Linux independently built the same gameplay JAR and ran authoring/restart, exported assemblies, the six-case public-mod matrix and seven fresh example installations. Retained results: [Windows dedicated](dedicated-windows.json), [Linux dedicated](dedicated-linux.json), [Windows assemblies](assemblies-windows.json), [Linux assemblies](assemblies-linux.json), [Windows bundles](bundles-windows.json) and [Linux bundles](bundles-linux.json).

## Stabilization and multiplayer

The published 1.1.0 JAR passed a [four-real-client run](four-player-1.1.json) lasting **3,772.652 seconds**. The final 1.2 JAR passed the [same workload](four-player-1.2.json) for **3,827.725 seconds**. Each run requested 1,280 chunks over twenty exploration rounds, split across the Overworld and End. Clients use synthetic offline identities on loopback; these are actual Minecraft clients connected to a dedicated server.

Each run checks four independent island homes, ordinary-player denial, transformed structure placement, client synchronization and undo. Native End checks cover portal entry, first kill, gateway travel, rewards, crystal respawning across restart, second kill and exit. A final restart verifies all four positions, safe homes, island assignments and generation pins. In each run the 640 requested Overworld chunks were audited after shutdown: **62,914,560 positions contained no terrain or natural structures**. No blocking gameplay or save defect in 1.1 was confirmed; a separate 1.1.1 was not prepared. Minor compact-window navigation defects are fixed in 1.2.

The locked two-client integration pack also passed a **1,825.430-second** run requesting 320 additional chunks and a further restart. A separately compiled API consumer passed two-real-client dimension, permission, protection and restart checks.

## Authoring and administration

[Dedicated authoring](authoring.json) checks transformed block states, named chest metadata, safe markers, embedded snapshots after source deletion, saved revision conflicts, overlap, bounds, sixteen-module and 65,536-cell limits, invalid metadata and missing dependencies. Exports remain inert until installed. [Exported assemblies](assemblies-four-dimensions.json) generated complete two-module structures in the Overworld, Nether, End and the external adapter's laboratory, then generated again in unexplored regions after restart. The shipped [assembly example](assembly-example.json) passed its own installation and generation check.

Rule tests compare condition coverage with the actual evaluator over 4,335 contexts. Dedicated assertions cover material targets, dependency order, conservative shadow warnings and a 34,816-work estimate exceeding the 32,768 warning budget. Preview assertions cover 25 cases, 2,700 columns and 1,009,152 block positions, without generating chunks.

The [two-client Studio and administration workflow](native-studio-multiplayer.json) exercises the actual native GUI at scales 1 and 2 and 320 × 240. It creates, transforms, saves and exports a two-module assembly, refuses overlapping exports, and reopens the saved draft. The creator workflow separately checks all four starters, exports, keyboard selection, dirty-draft prompts, every terrain view, all five rule overlays and coordinate explanations.

Two administrators stage competing actions: stale confirmations and permission revocation are refused without ledger changes. Team creation, reservations and membership changes persist. A real protected chest closes when the second player's team access is revoked; its three diamonds remain. Spawn set/clear, ledger, settings, teams and assembly drafts survive restart. Ordinary players receive only their own island information and cannot perform administrative mutations.

## Save protection and dependencies

The [integration results](integration.json) retain 31 dedicated matrix cases, plus multiplayer and native End phases. Earlier 0.9-origin, rule-engine, Studio, API and 1.1 saves are copied before loading, reopened and asked to generate distant chunks. Generation settings, snapshots, embedded templates, archived presets and island files retain their pins.

Twelve rejected existing-save cases cover missing or mismatched adapters/API, missing archives, corrupted or unknown snapshots and tampered bundles. **All 711 monitored files retain both contents and file sets**, excluding `session.lock`. Partial and full locks refuse prohibited terrain, rule, seed, dimension, progression and bundle-version edits. Native End boundaries pass for Single Block, Layered, Void and Skyblock.

The original saves are never live test worlds. A [complete source audit](source-saves.json), beyond the twenty generation pins, verifies **593 files across seven original source saves** unchanged. All twenty downloads from 0.9.0, 1.0.0 and 1.1.0 retain their hashes.

An [existing custom End save](existing-end.json) preserves completed dragon history, exit portal and gateway state across restart. Separate [published-baseline](end-comparison-baseline.json) and [candidate](end-comparison-candidate.json) copies generate the same 32 distant End chunks: full block states, biomes, heightmaps and configured End City structures match strictly. This checks the actual purpur-and-End-Cities preset, rather than assuming an empty outer End. Selected [acceptance markers](acceptance-summary.txt) retain the saved-region counts and strict comparison result.

## Public mods

The matrix checks Terralith with Lithostitched, Biomes O' Plenty with GlitchCore and TerraBlender, and the combined five-mod pack. Each is tested with Original and Single Block generation against the published 1.1.0 baseline, with fresh worlds, restart and distant generation. All six cases pass locally and on both CI platforms: [local](compatibility-local.json), [Windows](compatibility-windows.json), [Linux](compatibility-linux.json).

Exact official files and hashes are pinned in [`mods.lock.json`](../../../../tools/validation/mods.lock.json). Third-party JARs are not redistributed. These checks establish coexistence for those supplied packs, not universal support for every mod's terrain or structures.

## Performance investigation

The same-host smoke benchmark uses the historical five profiles, four active processors per server, a 1,300 MiB heap, sixteen warmup chunks and two measured 36-chunk regions. Three fresh pairs alternate baseline/candidate order. Other game and build workloads are stopped. [Host settings](performance/host.json), [JAR inputs](performance/inputs.json), raw CSVs and [saved-chunk audit markers](performance/saved-chunk-audits.txt) are retained.

The [initial comparison](performance/initial/comparison.json) flagged Void (+39.73%) and Single Block (+61.96%) against the 10% timing threshold. All five memory comparisons stayed within 15%. Original, Layered and structure-heavy timing remained within threshold. These initial flags are retained; they are not replaced by the repeat results.

Three fresh alternating pairs repeated only the flagged profiles, with the same warmup, regions, heap, processor count, polling and JARs. All 432 repeated chunk comparisons matched strictly. The [focused comparison](performance/repeats/comparison.json) did not reproduce a slowdown:

| Profile | Initial median pair seconds, 1.1 → 1.2 | Initial time change | Fresh-repeat median seconds, 1.1 → 1.2 | Repeat time change | Repeat memory change |
| --- | --- | --- | --- | --- | --- |
| Void | 2.19 → 3.06 | +39.73% | 3.26 → 2.22 | −31.90% | +4.23% |
| Single Block | 2.55 → 4.13 | +61.96% | 3.95 → 3.29 | −16.71% | −7.36% |

Individual baseline pair times vary from 2.84–8.94 seconds for Void and 2.63–7.82 for Single Block in the repeats. These short wall-clock measurements include tick scheduling, neighbor work and I/O, with 200 ms polling. The investigation did not establish a reproducible product regression; it also does not establish a universal speed improvement. Both batches remain available for comparison.

The initial exact saved-chunk audit passed 792 full managed chunk comparisons, then refused the final structure-heavy pair because one vanilla desert pyramid was buried one block higher. A comparison of two worlds generated by the **same 1.2 binary** reproduced the same difference: one start record, 1,956 blocks and 1,532 heightmap entries inside native pyramid footprints. Three earlier 1.1 worlds and an additional fresh 1.1 control matched each other.

Minecraft 26.3's `DesertPyramidPiece.postProcess` calls `updateHeightPositionToLowestGroundHeight(level, -random.nextInt(3))` only when a piece is first processed. The dedicated `auditNativePyramidPair` diagnostic normalizes only that allowed 0–2 burial offset, checks all other start fields and references, and compares all biomes. **All 5,723,136 block positions and heightmap columns outside the exact native footprints match.** The native interior remains diagnostic; the original strict refusal remains recorded. The three Original Generation comparisons likewise retain their diagnostic native decoration scope. Existing-save and custom End comparisons continue to require exact equality.

## Distribution and scope

The prepared release includes the gameplay JAR, buildable source ZIP, complete ZIP, four starters, two locked bundles, the assembly example and a checksum file. Packaging independently checks nested JAR/source hashes, ZIP paths, fixture exclusion and all earlier release assets. A clean extraction of the final source ZIP must rebuild the exact gameplay JAR before the GitHub draft is prepared. Artifact hashes belong in the checksum download, avoiding self-referential archive hashes in this report.

The release branch and wiki branch stage 1.2 documentation. Main, the live wiki and latest stable release remain on 1.1 until publication is requested. These tests cover the supplied fixtures and pinned platform, not arbitrary production capacity or every possible modpack. Use matching 1.2 clients and servers, preserve complete backups and keep required providers installed.
