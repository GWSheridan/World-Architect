# World Architect 1.1 verification

This release targets Minecraft 26.3, NeoForge 26.3.0.103 and Java 25. Milestones 22–26 add creator workflows, base-terrain previews, staged structure placement and repeatable compatibility checks. API 1 signatures and the saved preset, template and generation formats remain compatible.

## Tested binaries

The release JAR has SHA-256 `2bb93f40d6c9d03a1f461cb62c888ec650132ef44ba5f18052da5cc7d8e79efa`. The final two-client placement check and clean locked-client checks use this exact binary. Windows and Linux CI independently build the same source and run the dedicated fixtures, public-mod comparisons and six example installations.

The long integration suite froze its input before final client layout refinements. Its JAR has SHA-256 `53641bd74dba319893e145b1445eacee80029d6cdaae9c078fa9af1ee41acaf7`. All 242 classes were compared with the release JAR: only eight client classes differ. Server, generation, placement, protection, API and diagnostic classes are byte-for-byte identical. Version metadata also changes from the development identifier to 1.1.0. [Binary comparison](binary-relationship.json) records the changed classes; the results do not imply that every historical test used the final client binary.

Test instrumentation lives in separate fixture mods. It is excluded from the gameplay JAR. Servers bind to loopback and clients use synthetic offline identities. Older saves are copied before running; original saves never become live test worlds.

## Creator and placement checks

The [creator workflow](creator-workflow.json) created and exported all four independent starters at GUI scales 1 and 2. It exercised validation, versioned saves, search, keyboard selection, explicit loading, dirty-draft prompts, apply/cancel, coordinate navigation and all preview views. Screens were resized to 320 × 240 and restored. Clean partial and fully locked packs verified native defaults, embedded templates, permitted edits and rejected tampered creation.

The dedicated preview fixture compares 2,700 columns and 1,009,152 block positions across 25 cases. Those cases cover nine landforms, layered terrain, caves, fluids, strata, floating terrain, Void, Skyblock and the native End arena. It includes multiple seeds and negative chunk boundaries. Sampling uses generator queries without creating chunks. Client sampling is incremental and bounded; moving or closing the view discards pending work.

Dedicated placement checks cover transformed block states, chest metadata, collisions, marker clearance, failed-application rollback, exact-state undo refusal, the ten-operation author limit and shared 32 MiB accounting. Five large-template authors exercise eviction under that shared budget. Undo checks refuse an edited block or a chest receiving items without changing the world. Restart removes session history.

[Final placement multiplayer](placement-multiplayer.json) uses two real clients. It verifies ordinary-player command denial, rotation/mirroring, a changed template, a changed destination, world ghosts, confirmed placement, synchronized blocks, undo and restart persistence. The second client observes both the placed blocks and their removal.

## Integration and save protection

[Structured integration results](integration.json) and the [acceptance summary](acceptance-summary.txt) retain the dedicated cases and saved-region audit counts.

- Two Skyblock clients keep independent assignments, safe respawns and saved positions across restarts. A third player is refused without changing the two-owner ledger.
- Both clients stay connected for 1,821 seconds through ten rounds requesting 320 chunks across the Overworld and End. Another restart verifies persistence. Flushed stress regions are audited after shutdown.
- A separate API consumer compiles against the production API. Its provider pack passes with two clients in the adapter-managed lab and an original dimension, before and after restart.
- Native End checks complete an active-fight restart, first kill, gateway round trip, exit/re-entry, crystal respawning across restart, second kill, one egg and interrupted-ritual recovery. Unsafe gateway arrival is refused; a safe vertical fallback is exercised. All four custom-terrain modes retain their native arena boundaries.
- Four older-save/provider cases load and generate distant unexplored chunks. Saved settings, snapshots, templates, archived bundles and the existing island ledger retain their pins.
- Nineteen dependency, file, corruption and preset/policy cases are refused. All 711 monitored files across twelve rejected existing saves retain their hashes and file sets, excluding `session.lock`.
- All fifteen pinned files across five original save sources remain unchanged. All ten published 0.9.0 and 1.0.0 artifacts retain their original hashes.

The saved-region audits check 36,370,432 positions. They also verify 2,252 rule-template cells, eight markers, embedded template cells and container metadata. The 39 selected positive-case client/server logs contain no assertion, unexpected tick failure, packet-handling failure, chunk-save failure or memory failure. Setup retries are kept separately from the final successful runs.

## Platform, public mods and performance

The portable runners and pinned inputs are under [`tools/validation`](../../../../tools/validation/README.md). CI runs on Windows and Linux. Terralith 2.6.5 with Lithostitched 2.0.6, and Biomes O' Plenty 26.3.0.0.16 with GlitchCore 26.3.0.0.5 and TerraBlender 26.3.0.0.9, are tested on both original and managed terrain against the published 1.0.0 baseline. Matching files and SHA-256/SHA-512 values are pinned; third-party binaries are not included in the release.

Both jobs in the [release-JAR CI run](https://github.com/GWSheridan/World-Architect/actions/runs/44529656774) passed. [Run identity](ci-run.json), [Windows dedicated results](ci-windows.json), [Linux dedicated results](ci-linux.json), [Windows public-mod comparisons](compatibility-windows.json), [Linux public-mod comparisons](compatibility-linux.json), [Windows bundles](bundles-windows.json) and [Linux bundles](bundles-linux.json) preserve the results independently of temporary CI artifacts. Both platforms produced the release JAR hash above.

The same-host benchmark runs three fresh-world pairs in alternating order against 1.0.0, with five profiles, two measured regions per profile and other game/build workloads stopped. Each region contains 36 chunks after a 16-chunk warmup. Four active processors and a 1,300 MiB heap match the baseline settings. Median pair generation time and median sampled peak resident memory are compared. A time increase over 10% or memory increase over 15% requires investigation and a fresh repeat before being classified as a reproducible regression. Native decoration and ticking variability are recorded separately from strict managed-block comparisons.

All five profiles stayed within those thresholds. The [comparison](performance/comparison.json), [input hashes](performance/inputs.json), [host settings](performance/host.json) and [saved-chunk audits](performance/saved-chunk-audits.txt) retain the measurements. Raw CSVs are included beside them. Each version generated 1,080 measured chunks; 864 managed chunk comparisons matched exactly across the three pairs. The 216 native comparisons retained their diagnostic scope.

| Profile | Median pair time, 1.0.0 → 1.1.0 | Time change | Median peak resident MiB, 1.0.0 → 1.1.0 | Memory change |
| --- | --- | --- | --- | --- |
| Original | 6.99 → 7.61 s | +8.87% | 877.5 → 906 | +3.25% |
| Void | 3.06 → 2.19 s | −28.43% | 756 → 728 | −3.70% |
| Single Block | 3.71 → 2.41 s | −35.04% | 751 → 695.5 | −7.39% |
| Layered | 3.29 → 3.52 s | +6.99% | 747.5 → 778 | +4.08% |
| Structure-heavy | 3.28 → 2.64 s | −19.51% | 710 → 795.5 | +12.04% |

These short workloads have a 200 ms polling interval and visible run-to-run variation. The figures are release thresholds for these fixtures, not claims of universal speed improvements. No threshold flag required another repeat or a production performance fix.

## Artifacts and scope

The release contains the gameplay JAR, buildable source ZIP, complete ZIP, four unlocked starters, two locked Studio bundles and SHA-256 checksums. A clean extraction of the source ZIP is rebuilt before publication and must produce the same gameplay JAR hash recorded above. ZIP entries, checksums, excluded fixture classes and earlier released artifact hashes are checked independently. Artifact-specific hashes are recorded in the checksum download rather than embedded inside the archives they identify.

These results cover the supplied fixtures and pinned platform. They establish neither compatibility with every modpack nor production capacity for an arbitrary server. Previews show base terrain; later ores, decoration and generated structures remain outside that view. Placement history is bounded and session-only. Use matching 1.1 clients and servers for placement networking.
