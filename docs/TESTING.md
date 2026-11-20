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
