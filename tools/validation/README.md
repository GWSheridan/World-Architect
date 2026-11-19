# Validation runners

Use Node 22 or newer and Java 25. The asset tools also require Java 21. Set `JAVA21_HOME` if Gradle cannot discover it. Build with `gradlew.bat -I tools/validation/server.init.gradle build acceptanceJar prepareValidationServerRun` on Windows or `sh ./gradlew -I tools/validation/server.init.gradle build acceptanceJar prepareValidationServerRun` on Linux. Add `-Porg.gradle.java.installations.paths=<Java 21 directory>` when needed. Preparing the server run online populates runtime and asset dependencies before the runner uses offline Gradle.

The `validation` JAR is a separate test mod. It is excluded from the gameplay JAR. The portable runner installs the packaged gameplay JAR and fixture into a new disposable directory under `build/`, starts a loopback dedicated server, runs assertions and restarts it. It writes results and local logs to that directory. It refuses to reuse an output directory. Local runs default to offline Gradle. Add `--network online` for a clean host that still needs runtime dependencies such as NeoForge DevLaunch; CI uses that option.

```text
node tools/validation/run.mjs --jar build/libs/worldarchitect-1.2.0.jar --fixture build/validation-tools/worldarchitect-1.2.0-validation.jar --output build/validation-new
```

Use `--world <source-world>` to copy an existing save before testing. Source files are hashed before and after; the runner writes only to its disposable copy. Provider mods, bundles and datapacks required by that save must also be installed in the copy. The current basic runner accepts extra JARs through `--mods <directory>`; use the complete pack-specific harness for saves with bundled configuration. For a fresh bundled world, pass `--bundle <export.zip>`; omitted server generation properties then come from its manifest.

`--suite compatibility` checks startup, distant chunks and restart without using new authoring commands. It can compare the published 1.1.0 JAR with the candidate. Choose `--profile normal`, `single-block`, `layered`, `void` or `structure-heavy`. External-mod coexistence is narrower than support for every feature in those mods.

`mods.lock.json` records official matching public test builds and hashes. `node tools/validation/fetch-mods.mjs` downloads and verifies those inputs into `build/compatibility-inputs/`. Install Terralith with Lithostitched, or Biomes O' Plenty with GlitchCore and TerraBlender. Required dependencies inside a JAR must be checked as well as hosting-site metadata. Third-party binaries are not bundled in the release.

## Client and multiplayer checks

Install the gameplay and validation JARs in a clean client directory, enable `developer_mode` in that instance, and run `runCreatorValidation` with `-I tools/validation/client.init.gradle -PvalidationClientDirectory=<directory>`. The fixture checks starter exports, draft cancellation, bounds and preview views at GUI scales 1 and 2. It prints `WA_CREATOR_WORKFLOW_PASS`; Gradle exit status alone does not establish that the assertions passed. Screenshots remain in the instance's `screenshots/` directory.

```text
node tools/validation/multiplayer.mjs --jar build/libs/worldarchitect-1.2.0.jar --fixture build/validation-tools/worldarchitect-1.2.0-validation.jar --output build/multiplayer-new
```

This starts two real clients and a dedicated server for staged placement, ordinary-player denial, stale revision/destination rejection, ghost previews, undo, client block synchronization and restart checks. Use `--options <test-instance-options.txt>` to reuse accessibility and graphics settings. It uses synthetic offline identities on loopback. It must not be pointed at a production server.

The GitHub workflow repeats the dedicated fixture and pinned public-mod matrix on Windows and Linux. `node tools/validation/compatibility.mjs` compares original and managed terrain with the verified 1.1.0 baseline. Candidate-only failures fail the job; matching identified upstream failures are recorded as blocked. The workflow uploads diagnostics, structured results, JUnit XML and CI server logs. A scheduled workflow is not created; runs follow source changes or a manual dispatch.

The release additionally requires the pack, End progression, dependency rejection, older-save audit, thirty-minute multiplayer soak and alternating same-host performance suites described in [the roadmap](../../docs/ROADMAP-1.1.md). A passing smoke test does not substitute for those checks.

`node tools/validation/bundles.mjs` checks all seven versioned examples in clean dedicated-server instances. Each successful case compares pinned generation files across restart. For Windows resident-memory comparisons, use `benchmark-pairs.ps1 -BaselineJar <1.1.0.jar> -CandidateJar <candidate.jar> -OutputRoot build/<new-directory>` with other game/build workloads stopped. It runs three alternating pairs and writes a threshold report. A threshold flag requires investigation and a fresh repeat before being classified as a reproducible regression.

## 1.2 authoring and administration

`assemblies.mjs` installs the exported assembly bundles from a completed authoring fixture, generates complete two-module structures in distant regions, checks states, empty named chests and safe markers, restarts, and repeats in unexplored regions. CI runs the Overworld, Nether and End cases. Local release acceptance also installs the separate API provider and repeats all four supported dimensions. `run.mjs --preset <ID>` selects a custom registered preset for a fresh fixture.

`studio.mjs` needs `--jar`, `--fixture`, `--bundle`, `--options` and a new `--output` directory. It runs two real clients against a loopback server for the Assembly Studio workflow, both GUI scales and 320 × 240 bounds, ordinary-player privacy, simultaneous administrators, stale confirmations, revoked permissions, team reservations, real container access revocation, inventory retention, spawn changes and restart persistence. Use the unlocked 64-slot Skyblock starter.

`soak.mjs` takes the same inputs and runs four real clients for twenty three-minute exploration rounds. It checks four island homes, ordinary-player denial, placement and synchronized undo, native portal entry, dragon kills, gateway travel and a crystal ritual across restart. It then splits players across the Overworld and End for 1,280 requested chunks and verifies all four clients remain active. A final restart compares positions, safe homes and pinned generation and island files. The default workload exceeds one hour. Shorter `--rounds` and `--round-seconds` values are rehearsal options and do not meet release acceptance.

The public-mod matrix now includes the combined five-mod pack. It pins the 1.1.0 baseline hash, accepts `--output` and `--network`, and distinguishes candidate failures from matching upstream linkage failures. Do not run another `runValidationServer` fixture concurrently in the same checkout, because generated launch arguments share a task name. The Studio, soak and older integration tasks have distinct names. Stop all game and build workloads before measuring performance.
