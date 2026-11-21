# Validation runners

## Cell worlds

`cells.mjs --jar JAR --fixture VALIDATION_JAR --output build/NEW_FOLDER --crash true` validates the six recipe exports, true voids, activation of saved void chunks, protected conflicts and recovery after forcibly terminating its disposable server. Omitting `--crash true` uses an orderly interrupted-activation restart. The fixture is separate from the gameplay JAR.

`island-multiplayer.mjs --cells true` uses the same four real clients to check separate player/team territories, duplicate quest signals, frontier selection, command permissions, consumed supplies and restart persistence. Use a cell fixture bundle, `--teams true` for scoreboard teams, and `--end true` for the native arena/dragon sequence. The optional pack test kit also activates reserved cells and audits gaps and template inventories.

## Modpack Builder and island catalogs

`run.mjs --islands true --bundle examples/modpack-builder/bundles/modpack-choice-1.8.0.zip --suite compatibility` checks actual template states, safe markers, explicit container contents, consumed items across restart, newly generated supplies, empty void chunks and locked runtime writes. The authoring suite also checks 64-block capture/native/schematic imports, transforms, immutable revisions, inventory inclusion and rejected metadata/worker operations.

`island-multiplayer.mjs` starts four real clients. Supply `--jar`, `--fixture`, `--bundle`, a new `--output` and an existing client `--options` file. Use `--teams true` for the team example and `--end true` for native entry, kills, gateway travel and a ritual interrupted by restart. Release validation uses the default twenty three-minute exploration rounds; shorter runs are smoke tests. All evidence stays in the disposable output folder.

`packageTestKit` builds the optional Java-only user kit. Test it against a separately installed NeoForge runtime and a GUI-exported project. Its fixture remains separate from the gameplay JAR.

The direct benchmark runner validates all identical-JAR controls before starting candidate comparisons. Noisy or missing-resident-memory controls produce an `inconclusive` report with zero candidate comparisons. A sampler smoke can verify completed controls without claiming a performance comparison passed.

Use Node 22 or newer and Java 25. The asset tools also require Java 21. Set `JAVA21_HOME` if Gradle cannot discover it. Build with `gradlew.bat -I tools/validation/server.init.gradle build acceptanceJar prepareValidationServerRun` on Windows or `sh ./gradlew -I tools/validation/server.init.gradle build acceptanceJar prepareValidationServerRun` on Linux. Add `-Porg.gradle.java.installations.paths=<Java 21 directory>` when needed. Preparing the server run online populates runtime and asset dependencies before the runner uses offline Gradle.

The `validation` JAR is a separate test mod. It is excluded from the gameplay JAR. The portable runner installs the packaged gameplay JAR and fixture into a new disposable directory under `build/`, starts a loopback dedicated server, runs assertions and restarts it. It writes results and local logs to that directory. It refuses to reuse an output directory. Local runs default to offline Gradle. Add `--network online` for a clean host that still needs runtime dependencies such as NeoForge DevLaunch; CI uses that option.

```text
node tools/validation/run.mjs --jar build/libs/worldarchitect-1.9.0.jar --fixture build/validation-tools/worldarchitect-1.9.0-validation.jar --output build/validation-new
```

Use `--world <source-world>` to copy an existing save before testing. Source files are hashed before and after; the runner writes only to its disposable copy. Provider mods, bundles and datapacks required by that save must also be installed in the copy. The current basic runner accepts extra JARs through `--mods <directory>`; use the complete pack-specific harness for saves with bundled configuration. For a fresh bundled world, pass `--bundle <export.zip>`; omitted server generation properties then come from its manifest.

`--suite compatibility` checks startup, distant chunks and restart without using new authoring commands. It can compare a compatible baseline JAR with the candidate. Choose `--profile normal`, `single-block`, `layered`, `void` or `structure-heavy`. External-mod coexistence is narrower than support for every feature in those mods.

`mods.lock.json` records official matching public test builds and hashes. `node tools/validation/fetch-mods.mjs` downloads and verifies those inputs into `build/compatibility-inputs/`. Install Terralith with Lithostitched, or Biomes O' Plenty with GlitchCore and TerraBlender. Required dependencies inside a JAR must be checked as well as hosting-site metadata. Third-party binaries are not bundled in the release.

## Client and multiplayer checks

Install the gameplay and validation JARs in a clean client directory, enable `developer_mode` in that instance, and run `runCreatorValidation` with `-I tools/validation/client.init.gradle -PvalidationClientDirectory=<directory>`. The fixture checks starter exports, draft cancellation, bounds and preview views at GUI scales 1 and 2. It prints `WA_CREATOR_WORKFLOW_PASS`; Gradle exit status alone does not establish that the assertions passed. Screenshots remain in the instance's `screenshots/` directory.

```text
node tools/validation/multiplayer.mjs --jar build/libs/worldarchitect-1.9.0.jar --fixture build/validation-tools/worldarchitect-1.9.0-validation.jar --output build/multiplayer-new
```

This starts two real clients and a dedicated server for staged placement, ordinary-player denial, stale revision/destination rejection, ghost previews, undo, client block synchronization and restart checks. Use `--options <test-instance-options.txt>` to reuse accessibility and graphics settings. It uses synthetic offline identities on loopback. It must not be pointed at a production server.

The GitHub workflow repeats the dedicated fixture and pinned public-mod matrix on Windows and Linux. `node tools/validation/compatibility.mjs` compares original and managed terrain with the frozen 1.4.0 baseline. Candidate-only failures fail the job; matching identified upstream failures are recorded as blocked. The workflow uploads diagnostics, structured results, JUnit XML and CI server logs. A scheduled workflow is not created; runs follow source changes or a manual dispatch.

The release additionally requires the pack, End progression, dependency rejection, older-save audit, one-hour multiplayer soak and alternating same-host performance suites described by the runners below. A passing smoke test does not substitute for those checks.

`node tools/validation/bundles.mjs` checks all eighteen versioned examples in clean dedicated-server instances. Each successful case compares pinned generation files across restart. For Windows resident-memory comparisons, use `benchmark-pairs.ps1 -BaselineJar <1.1.0.jar> -CandidateJar <candidate.jar> -OutputRoot build/<new-directory>` with other game/build workloads stopped. It runs three alternating pairs and writes a threshold report. A threshold flag requires investigation and a fresh repeat before being classified as a reproducible regression.

## 1.2 authoring and administration

`assemblies.mjs` installs the exported assembly bundles from a completed authoring fixture, generates complete two-module structures in distant regions, checks states, empty named chests and safe markers, restarts, and repeats in unexplored regions. CI runs the Overworld, Nether and End cases. Local release acceptance also installs the separate API provider and repeats all four supported dimensions. `run.mjs --preset <ID>` selects a custom registered preset for a fresh fixture.

`studio.mjs` needs `--jar`, `--fixture`, `--bundle`, `--options` and a new `--output` directory. It runs two real clients against a loopback server for the Assembly Studio workflow, both GUI scales and 320 × 240 bounds, ordinary-player privacy, simultaneous administrators, stale confirmations, revoked permissions, team reservations, real container access revocation, inventory retention, spawn changes and restart persistence. Use the unlocked 64-slot Skyblock starter.

`soak.mjs` takes the same inputs and runs four real clients for twenty three-minute exploration rounds. It checks four island homes, ordinary-player denial, placement and synchronized undo, native portal entry, dragon kills, gateway travel and a crystal ritual across restart. It then splits players across the Overworld and End for 1,280 requested chunks and verifies all four clients remain active. A final restart compares positions, safe homes and pinned generation and island files. The default workload exceeds one hour. Shorter `--rounds` and `--round-seconds` values are rehearsal options and do not meet release acceptance.

The public-mod matrix now includes the combined five-mod pack. It accepts `--output` and `--network`, and distinguishes candidate failures from matching upstream linkage failures. Pass `--baseline-fixture` with a fixture built for the published baseline when the current fixture uses newer APIs. Do not run another `runValidationServer` fixture concurrently in the same checkout, because generated launch arguments share a task name. The Studio, soak and older integration tasks have distinct names. Stop all game and build workloads before measuring performance.

For an existing custom End save, use `--world <source-world> --suite compatibility --dimension minecraft:the_end --check-end true`. This copies the source, audits the native arena, records persistent dragon/portal/gateway state across restart, and waits for every requested outer-End chunk. Compare published and candidate copies with `auditSavedChunkPair`, passing `-PbaselineWorld`, `-PcandidateWorld`, `-PterrainDimension` and a semicolon-separated `-PterrainChunks` list. The comparison includes full block states, biomes, heightmaps and configured structures; it does not assume that an End preset has no End Cities.

The [performance instructions](../../examples/performance/README.md) distinguish strict saved-chunk audits from the explicit native-pyramid diagnostic. Keep initial failures, same-binary controls and fresh timing repeats in the release report. A diagnostic exception must not turn a mismatched existing save into a passing strict comparison.

## 1.4 history, masks and constraints

The authoring suite checks draft history/recovery across restart, immutable workbench bindings and server permissions, shared named-region rules and format-2 pool constraints. It compares 256 legacy pool layouts directly with the published 1.3 JAR. Use `--baseline JAR` to supply that exact baseline offline; online runs verify and cache its pinned download under `build/validation-inputs/`.

`assemblies.mjs` now covers static, format-1 and constrained format-2 exports in each available fixture dimension. `run.mjs --named-regions true --bundle examples/shared-regions/bundles/shared-regions-1.4.0.zip --suite compatibility` checks actual filtered material/ore/decor/template writes and spawn decisions, then repeats the block/biome fingerprint after restart. `bundles.mjs` installs all eighteen current examples.

The four-client soak also exercises connected history, pool constraints, background seed sampling, scenario editing, exact seeds, server acknowledgements and permission revocation before its native End and exploration workload. Its default twenty rounds remain the release requirement.

Run `node --test tools/workbench/scenario.test.mjs tools/workbench/batch.test.mjs` for the shared Java/Node scenario contract and matrix validation. `node tools/workbench/batch-cases.mjs --network online` runs real version/provider comparisons, preflight rejections, a total deadline and active-server cancellation. See [Batch Testing](../../docs/wiki/Batch-Testing.md). Only one workbench/batch or runValidationServer process may use the checkout at once.

## 1.5 polygons, anchors and sites

The authoring suite exports polygon, coastal, vertical and maximum-bound stress presets. Run `node tools/validation/sites.mjs --source build/validation-new --output build/sites-new --network online` after a successful authoring run. Native generation and restart checks cover strict polygon writes, seabed water opt-in, root height, persisted pieces, metadata and markers. The stress case uses 512 vertices and sixteen vertical modules; an incomplete preview is allowed only when the cooperative budget expires, while actual native generation must pass. Installing the independent API fixture adds its registered laboratory to the vertical cases.

Authoring compares 256 format-1 layouts against the pinned published 1.3 input and 256 format-2 layouts against frozen 1.4. Supply `--baseline14 <JAR>` or place the exact 1.4 JAR under `build/validation-inputs/worldarchitect-1.4.0.jar`. Its SHA-256 is `3684963461df2331c7f0fa4968d12b972fceff6c73c3568c77a19dcc66474fe4`. CI builds that input from source commit `c062da90c909e9883a54007651abfd37536fa14a` before testing the candidate.

The four-client soak accepts `--explore-dimension` and `--site-structure` to exercise the integration pack's custom highlands and persisted tower. It also checks the connected site screen at both GUI scales, closed-session late-result suppression and a fresh request after permission revocation.

## Composite regions, preparation, themes and searches

The 1.6 authoring suite validates shared composite masks, bounded supports and clearance, captured weighted variants and palettes, native-candidate searches, permission revocation, cancellation and immutable report export. It verifies that detached work leaves loaded chunk counts and generation files unchanged. The site suite then generates prepared structures in each available fixture dimension and compares their embedded pieces, block states, metadata and markers after restart. Clean bundle installation covers all eighteen current examples.

Format-3 pool layouts are compared exactly against 256 seeds from the published 1.5 JAR. Supply `--baseline15 <JAR>` or place that input at `build/validation-inputs/worldarchitect-1.5.0.jar`, with SHA-256 `27a6a5e55567e04db4c93818daa2a866bd9bc0d0313780b08f17dcba1e20be42`. CI builds it from tag `v1.5.0`. The frozen 1.3 and 1.4 comparisons remain in place.

The four-client soak also exercises foundation/theme editing, captured variant history, search results, report export and cancellation through real screens. Use `--site-kind themed` with `--site-structure` for a prepared themed integration fixture. Keep its default twenty 180-second rounds for a release endurance run; a shortened rehearsal does not replace that run. Reports and disposable worlds stay under `build/`.

## Corridors, districts and inherited presets

The 1.7 authoring fixture exports connected corridors and weighted districts, compares detached planning across worker threads and reordered member lists, and checks bounded search agreement. It tests parent archive pins, inherited lock scopes and resource fingerprints, self-contained children, immutable revisions, command permissions and API thread guards. The site runner generates actual corridor blocks/biomes and district structures, checks every occupied district cell and safe marker, then repeats after restart. District starts are requested in reverse order on restart.

`node tools/validation/bundles.mjs --output build/bundles-new --network offline` installs all 23 current examples in clean instances. The output option keeps local repeats separate from CI. Real creator fixtures also exercise corridor point history, shared district settings and child derivation/override/report controls at both GUI scales.

Build `benchmarkProbeJar` separately and follow the [direct-generation instructions](../../examples/performance/README.md). Windows and Linux CI run a small probe/RSS smoke matrix against the fingerprinted published 1.6 JAR; full same-host performance remains a separate quiet-host release check. Run the seven analysis checks with `node --test tools/validation/benchmark-analysis.test.mjs` alongside the existing workbench tests. Validation fixtures, measurement mods, raw logs and worlds are excluded from the gameplay JAR.
