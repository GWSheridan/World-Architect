# Testing World Architect

Use Java 25 and run `gradlew.bat build` or `bash ./gradlew build`. The suite covers terrain, profiles, rules, saved data, templates, locks and API contracts. Run `assembleRelease` to build and checksum the distribution.

Release testing uses the production JAR on clients and servers, with test instrumentation in a separate diagnostic mod. Original worlds are preserved; upgrades run against copies.

## Release checks

- Join a Skyblock server with two ordinary clients; verify separate islands, permissions, safe respawn and simultaneous exploration.
- Restart and verify positions, assignments and protected generation files.
- Exercise partial/full locks on clean clients and a dedicated server, including rejected edits.
- Reopen older managed, rule-engine, template and API saves; compare protected files and unexplored terrain.
- Remove or change required adapters, archives or generation files on copies; startup must stop without mutating the monitored save files.
- With End progression enabled, verify native arena terrain/biomes, custom outer terrain, boundary exclusions, entry/exit, first kill, gateway travel and crystal respawning. Restart during a fight and respawning; test an interrupted ritual.
- Rebuild a clean source extraction and compare the JAR with the tested binary. Check every ZIP and SHA-256 checksum.

The [End progression test tools](../examples/end-progression-test) include the separate native-fight and editor probes. The [API example](../examples/api-fixture) and [benchmark project](../examples/performance) include reproduction instructions. Supporting compatibility and performance data is retained under `maintainers/verification/`.

## Release results

The 1.0.0 release checks completed on Windows with Java 25, Minecraft 26.3 and NeoForge 26.3.0.103.

- All 51 unit tests passed. Documentation links were checked in 51 shipped Markdown files.
- Two ordinary clients joined a dedicated server, kept separate island assignments after restarting, and explored dimensions simultaneously. Permission, capacity and protection checks are retained in the supporting release data.
- The custom End completed two native dragon kills, exit travel, a paired gateway round trip, rewards, crystal respawning, a restart during respawning and recovery from an interrupted ritual. Unsafe gateway destinations were refused; a safe vertical fallback was found when supplied.
- Single Block, Layered, Void and Skyblock arena checks covered native blocks, biomes and preview queries, custom outer terrain and structure exclusions. The final JAR reopened and audited the saved arena. The new-world editor passed at GUI scales 1 and 2.
- Four older save types reopened and generated previously unexplored chunks. Saved profiles, templates, bundle archives and monitored island/generation files stayed intact. Block audits passed for version 1 terrain, generation rules, external adapters and saved Skyblock templates.
- Nine missing or mismatched dependency/file cases stopped loading with unchanged monitored save hashes. Partial and full locks rejected terrain, rule, seed and End-policy changes. Permitted structure-generation changes saved correctly while unrelated options stayed locked.
- The source ZIP rebuilt the same gameplay JAR. Archive integrity and all five asset checksums were checked independently before publication. Diagnostic classes are excluded from that JAR.

The final saved-chunk audits checked 393,216 terrain positions, 2,064,384 rule-filtered positions, 359,424 extension positions, 491,520 Skyblock positions and 393,216 partial-lock positions. They also checked 2,252 rule-template cells, eight markers, 254 bundled template cells and chest metadata.

Supporting [API and performance data](maintainers/verification/api-compatibility/) includes the same-host 0.9.0 comparison: 288 managed chunks matched exactly; native chunks were compared diagnostically because decoration and ticking can vary. [Release data](maintainers/verification/release-1.0/) records the earlier multiplayer and compatibility checks. These results cover the supplied fixtures and game version, not every possible modpack.

## Post-release stabilization

Milestone 21 rechecked the published 1.0.0 JAR on 19 November 2026. All 51 unit tests passed. Two-client Skyblock and external-provider packs passed permission, dimension, restart and persistence checks. A 321-second multiplayer run requested 320 additional chunks and passed another restart. Native End progression completed both kills, gateways, exit travel, crystal respawning across restart and interrupted-ritual recovery.

The dedicated matrix rejected nineteen dependency, missing-file, corruption and preset/policy cases. All 711 monitored files across twelve rejected existing saves retained their hashes and file sets; all fifteen pinned files across five original source worlds remained unchanged. Saved-region audits checked 36,370,432 positions, along with templates, markers and container metadata.

No World Architect defect was confirmed, so no 1.0.1 patch was prepared. The tested gameplay JAR and all ten released assets remain unchanged. See the [stabilization report and supporting results](maintainers/verification/stabilization-1.0/) for the fixture scope, checks and artifact hashes.

## World Architect 1.1

Milestones 22–26 add separate creator, preview and placement fixtures under `src/acceptance/`, with portable runners in [`tools/validation`](../tools/validation/README.md). Build `acceptanceJar` to produce the separate validation mod. It is not included in the gameplay JAR. CI builds and runs the dedicated suite on Windows and Linux, compares pinned public-mod packs with 1.0.0, and installs all six versioned examples in fresh server directories.

The creator fixture exports all four starters at GUI scales 1 and 2, tests keyboard loading and dirty-draft prompts, exercises all preview views, and resizes screens to 320 × 240. Clean locked clients verify permitted partial edits, full locks and rejected tampering. Dedicated preview assertions compare 2,700 columns and 1,009,152 blocks across 25 cases.

The placement fixture checks transformed states, block-entity metadata, collisions, rollback, exact-state undo refusal, history limits and shared-budget eviction. A final two-real-client run uses the release JAR for staging, stale-template and stale-destination refusal, world ghosts, confirmed placement, synchronized undo and restart persistence.

The release integration run repeats End progression, older-save generation, dependency rejection, multiplayer islands and the separate API provider. Both Skyblock clients stay connected for 1,821 seconds through 320 requested chunks, then pass another restart. All 711 files in twelve rejected existing saves and all fifteen pins in five original source saves remain unchanged. Saved-region audits check 36,370,432 positions. The old 0.9.0 and 1.0.0 downloads remain unchanged.

Run three alternating fresh-world performance pairs after stopping other game/build workloads. Compare median pair generation time and median sampled peak resident memory against 1.0.0; investigate increases over 10% and 15%, respectively, and repeat flagged cases before classifying a regression. Rebuild the final source ZIP in a new directory and compare its JAR hash with the tested binary before preparing the release.

All five 1.1 profiles stayed within the performance thresholds. Across the three pairs, 864 managed chunk comparisons matched exactly; 216 native comparisons remained diagnostic. Raw measurements, input hashes and host settings are retained with the verification report.

The [1.1 verification report](maintainers/verification/release-1.1/) distinguishes the frozen integration binary from the final client refinements and records the exact release-JAR checks. API 1 and saved generation formats remain compatible; placement networking requires matching 1.1 clients and servers.

## World Architect 1.2

Milestones 27–31 use the published 1.1.0 JAR as the stabilization and performance baseline. The release candidate adds real-client Assembly Studio and administration workflows, condition/evaluator comparisons, bounded no-chunk preview checks, exported generation in vanilla and API dimensions, the combined pinned public-mod pack and seven clean example installations. The four-real-client acceptance workload runs for one hour before a final restart. Reuse a new output directory for every run; all upgrade fixtures copy their source worlds before loading.

Use the portable commands in [validation runners](../tools/validation/README.md). Keep the gameplay JAR hash fixed across the dedicated, multiplayer, upgrade, compatibility and performance suites. Build from the final extracted source archive and compare its JAR hash before preparing the release. API 1 remains stable; authoring and placement networking requires matching 1.2 clients and servers.

The [1.2 verification report](maintainers/verification/release-1.2/README.md) records the final binary, both four-client hour-long runs, native GUI workflows, four-dimension assembly exports, Windows/Linux results and complete original-save audits. Initial performance flags and their fresh repeats are preserved separately. The report also records a same-binary vanilla pyramid burial difference and its strict outside-footprint audit; existing-save comparisons retain exact equality.

## 1.3 validation

The 1.3 fixture adds immutable Inspector comparisons, region preview/generation checks, pool determinism and bounded failures, and workbench probes. Run `node --test tools/workbench/scenario.test.mjs` for scenario validation. See [Test Workbench](wiki/Test-Workbench.md) for disposable jobs and longer benchmark settings. The published 1.2.0 JAR is the new same-host baseline; earlier verification records remain historical.

The corrected 1.3 gameplay JAR passed [Windows and Linux CI](https://github.com/GWSheridan/World-Architect/actions/runs/44660328512): 70 JUnit tests, six exported assembly cases, six pinned public-mod comparisons against 1.2.0, nine clean example installations and four workbench integration cases on each platform. The additional empty-void safe-spawn rejection passed locally, bringing the workbench integration set to five cases.

Seven copied older saves produced 224 unexplored chunks identical to the published 1.2.0 baseline; all 732 original source files remained unchanged. The later export fix changed only `AssemblyExports.class`; generation and save-protection classes remained byte-identical. A separate 31-case matrix and three corrupted/unknown/tampered-save cases checked adapter/API versions, locks, native arenas and refusal without overwriting protected data.

Pool starts were reloaded at the same recorded coordinates in the Overworld, Nether, End and the separate API fixture's laboratory dimension. Piece names, templates, transforms and marker fingerprints matched across restart; generated block states, empty named chests and safe marker floors were checked again. The API fixture was also built independently against the shipped API 1 JAR.

Four real clients completed 3,764.968 seconds (62.7 minutes) of exploration and 1,280 requested chunks. Native End entry, first kill/rewards, gateway travel, crystal respawning across restart, the second kill, final player positions, island homes, assignments and generation hashes passed.

Three alternating same-host performance pairs used 64 warmup chunks and five measured batches of 64 chunks per profile. Initial timing flags in Original, Void and structure-heavy, plus Original resident memory, did not reproduce in three fresh pairs of those profiles. The table preserves both sets. Percentages compare medians of complete per-pair generation times and sampled peak resident memory against the published 1.2.0 JAR.

| Profile | Initial time | Initial memory | Repeat time | Repeat memory |
| --- | ---: | ---: | ---: | ---: |
| Original | +12.31% | +15.84% | −3.23% | +4.39% |
| Void | +12.88% | −2.84% | +5.40% | +0.49% |
| Single Block | −9.16% | +6.98% | — | — |
| Layered | +9.79% | −10.94% | — | — |
| Structure-heavy | +11.78% | −3.37% | +5.15% | −9.69% |

The original three-pair managed-terrain checks matched 320 measured chunks per profile and pair. Native pyramid burial offsets of 0–2 blocks varied in two candidate comparisons and in unchanged-1.2.0 controls. Structure references, terrain outside the pyramid footprints and all biomes matched; the repeat pyramid comparisons were identical. Strict old-save comparisons were not relaxed. This benchmark covers the fixed legacy fixtures, not every regional/pool configuration or other modpack.

Raw logs, failed setup attempts, superseded candidates and all timing samples remain in local verification output and CI artifacts; they are not additional release assets. World Architect 1.3.0 is published with the verified gameplay JAR and updated guides.

## World Architect 1.4

Milestones 38–42 passed with the frozen gameplay JAR. [Windows and Linux CI](https://github.com/GWSheridan/World-Architect/actions/runs/44727155213) each checked 88 JUnit tests, 35 Node tests, nine exported assembly cases, six pinned public-mod combinations against 1.3.0, eleven clean example installs, five workbench cases and the corrected eight-job batch matrix. Batch checks cover input fingerprints, provider contents, existing outputs, redirected parents, total deadlines, interruption and released server ports. Fixture assertions are reported immediately; timeouts cannot satisfy an expected-failure case.

The native creator workflow passed at GUI scales 1 and 2 and 320 × 240 bounds, including history, explicit interrupted-draft recovery, named-region editing and scenario exports. Clean partial/full locked clients kept forbidden editors disabled; permitted ore edits and tamper refusal passed. A connected level-4 author edited constraints, sampled sixteen seeds, used scenario Undo/Redo, exported the exact minimum signed 64-bit seed and received the server acknowledgement. Revoking permission blocked another export. Server session, stale revision and source-fingerprint checks passed.

Dedicated generation checks kept 1,387 ore blocks, 44 decorations, 8,715 material replacements and a structure inside their named masks; spawn selection and the block/biome fingerprint matched after restart. Format-2 layouts passed required-room, count, depth, collision, marker, budget and concurrency checks. All 256 compared format-1 layouts matched the published 1.3.0 implementation. Static, legacy-pool and constrained-pool exports passed in the Overworld, Nether, End and independent API laboratory, including stored piece fingerprints, transformed states, empty named chests and safe markers. Independent consumers compiled against 1.3.0 and 1.4.0 produced identical binaries; the separate prebuilt older consumer also passed runtime checks.

Eleven copied older-save cases matched 352 unexplored chunks against 1.3.0. All 1,019 original files across ten source worlds and all 80 protected-copy file checks stayed intact. Twelve rejected saves retained their complete file sets and 711 hashes; a separate omitted-provider pool copy retained another 41 files. Two new format-2 saves also kept their archived profiles and templates after installing revised same-ID defaults, terrain materials, template blocks and metadata. Their 64 additional unexplored chunks matched, stored pieces and markers stayed original, and both revised packs passed fresh-world installs. Existing-save comparisons kept exact equality throughout. Initial fixture assumptions about a vanilla End and an omitted pool provider were corrected and repeated on fresh copies; the failed evidence was retained.

Four real clients completed 63.8 minutes and 1,280 requested chunks, with simultaneous exploration, separate islands, permissions, synchronized placement/undo, native End entry, first kill/rewards, gateway travel, crystal respawning across restart, the second kill and final player/home/ledger/generation persistence. The additional dedicated matrix covered native arena modes, adapter/API mismatches, locks and corrupted/unknown/tampered generation data.

Performance used three alternating 1.3.0/1.4.0 pairs on the same host after other local game/build workloads stopped: 64 warmup chunks and five measured batches of 64 chunks per profile, with 200 ms resident-memory sampling. All profiles passed the initial three-pair comparison. Percentages compare medians of complete per-pair generation times and sampled peak resident memory.

| Profile | Generation time | Peak resident memory |
| --- | ---: | ---: |
| Original | −11.64% | +0.92% |
| Void | −3.18% | −10.01% |
| Single Block | +1.72% | −12.78% |
| Layered | −5.35% | +7.52% |
| Structure-heavy | +1.02% | +4.20% |

Managed terrain comparisons remained strict. The established vanilla-pyramid diagnostic is confined to its burial footprint, with strict outside terrain/biomes and same-binary controls; it is not used to relax older-save checks. Raw inputs, timing samples and audit logs are retained locally. These results cover the supplied fixtures and pinned game/provider versions.

## World Architect 1.5

Milestones 43–47 passed with the frozen gameplay JAR. [Windows and Linux CI](https://github.com/GWSheridan/World-Architect/actions/runs/44755729256) each checked 98 JUnit tests, 35 Node tests, nine assembly exports, six native-generation site cases, six pinned public-mod combinations against frozen 1.4, fifteen clean example installs, five workbench cases and the eight-job batch matrix. Expected failures require explicit assertions or input errors; timeouts cannot count as successful rejection.

Native creator checks passed GUI scales 1 and 2 and 320 × 240 bounds, including polygon coordinates, map editing, history and recovery. A partially locked polygon pack allowed its declared ore edits and rejected locked terrain changes. The fully locked Skyblock integration pack kept its editors disabled. Connected clients checked the site screen at both scales, stale/closed results and fresh permission revocation. Server-only checks covered source tampering, mismatched fingerprints, current policy, session/revision guards and cancellation without loading chunks or modifying saved generation.

Native site generation and restart passed coastal water placement, strict polygon filtering and vertical pools in the Overworld, Nether, End and independent adapter laboratory. Block states, named empty chests, root height, markers and stored piece fingerprints stayed intact. Water opt-in refused lava, waterlogged solids and containers; air-only seabed placement was rejected. A stress fixture reached 512 polygon vertices and sixteen vertical modules, including actual native generation and the bounded read-only preview. This fixture is a limit check, not a whole-modpack performance guarantee.

All 256 compared format-1 layouts matched 1.3, and all 256 format-2 layouts matched frozen 1.4. Static, format-1 and format-2 assemblies also passed twelve generation/restart cases across four dimensions. Independent API consumers compiled against 1.4 and 1.5 produced identical binaries; the older prebuilt provider passed runtime and saved-world checks. API 1 remains supported.

Thirteen copied older-save cases matched 416 unexplored chunks against frozen 1.4, covering older releases, shared terrain regions, constrained pools, the adapter laboratory, Skyblock and both managed and native End progression. All 1095 original files stayed intact. Twelve rejected saves retained their complete file sets and 711 hashes. Polygon, vertical and coastal worlds retained original snapshots, settings, archives, template states, metadata and markers after changed same-ID future defaults were installed. Their 96 additional unexplored chunks matched exactly; the three revised packs also started fresh worlds and generated their new base material. Existing-save comparisons remain strict.

Four real clients completed 64.3 connected minutes and 1280 requested chunks, including simultaneous custom-highlands/End exploration, persistent separate islands, ordinary-player denial, synchronized placement/undo, native End entry, first kill and rewards, gateway travel, a crystal ritual across restart, the second kill and final player/home/ledger/generation persistence. A separate real client completed the custom-terrain End arena, first kill/rewards, gateway round trip, crystal respawn across restart, second kill and interrupted-ritual recovery with generation pins intact. The dedicated matrix additionally checked native arena modes, partial/full locks, missing or mismatched providers and corrupt/unknown/tampered generation data.

Performance compared three alternating 1.4/1.5 pairs on the same host after other local game/build workloads stopped, with 64 warmup chunks, five measured batches of 64 chunks per profile and 200 ms resident-memory sampling. All profiles passed the initial three-pair comparison. Percentages compare medians of complete per-pair generation times and sampled peak resident memory.

| Profile | Generation time | Peak resident memory |
| --- | ---: | ---: |
| Original | +6.20% | +5.59% |
| Void | −18.04% | +2.85% |
| Single Block | −9.92% | −3.92% |
| Layered | −1.68% | +2.69% |
| Structure-heavy | −22.35% | +3.46% |

Managed terrain audits remain exact. The established vanilla-pyramid diagnostic is confined to its burial footprint, retaining strict outside terrain/biomes and same-binary controls. It does not relax existing-save checks. Initial fixture failures, raw results and audit logs remain local. These checks cover the supplied fixtures and pinned game/provider versions; they do not establish compatibility with every modpack. Source packaging is rebuilt independently and checked against the accepted JAR before the draft release is prepared.
