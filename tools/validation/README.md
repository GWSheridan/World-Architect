# Validation runners

Use Node 22 or newer and Java 25. The asset tools also require Java 21. Set `JAVA21_HOME` if Gradle cannot discover it. Build with `gradlew.bat build acceptanceJar prepareServerRun` on Windows or `sh ./gradlew build acceptanceJar prepareServerRun` on Linux. Add `-Porg.gradle.java.installations.paths=<Java 21 directory>` when needed. Preparing the server run online populates runtime and asset dependencies before the runner uses offline Gradle.

The `validation` JAR is a separate test mod. It is excluded from the gameplay JAR. The portable runner installs the packaged gameplay JAR and fixture into a new disposable directory under `build/`, starts a loopback dedicated server, runs assertions and restarts it. It writes results and local logs to that directory. It refuses to reuse an output directory. Local runs default to offline Gradle. Add `--network online` for a clean host that still needs runtime dependencies such as NeoForge DevLaunch; CI uses that option.

```text
node tools/validation/run.mjs --jar build/libs/worldarchitect-1.1.0-dev.jar --fixture build/validation-tools/worldarchitect-1.1.0-dev-validation.jar --output build/validation-new
```

Use `--world <source-world>` to copy an existing save before testing. Source files are hashed before and after; the runner writes only to its disposable copy. Provider mods, bundles and datapacks required by that save must also be installed in the copy. The current basic runner accepts extra JARs through `--mods <directory>`; use the complete pack-specific harness for saves with bundled configuration. For a fresh bundled world, pass `--bundle <export.zip>`; omitted server generation properties then come from its manifest.

`--suite compatibility` checks startup, distant chunks and restart without using new authoring commands. It can compare the published 1.0.0 JAR with the candidate. Choose `--profile normal`, `single-block`, `layered`, `void` or `structure-heavy`. External-mod coexistence is narrower than support for every feature in those mods.

`mods.lock.json` records official matching public test builds and hashes. `node tools/validation/fetch-mods.mjs` downloads and verifies those inputs into `build/compatibility-inputs/`. Install Terralith with Lithostitched, or Biomes O' Plenty with GlitchCore and TerraBlender. Required dependencies inside a JAR must be checked as well as hosting-site metadata. Third-party binaries are not bundled in the release.

## Client and multiplayer checks

Install the gameplay and validation JARs in a clean client directory, enable `developer_mode` in that instance, and run `runCreatorValidation` with `-I tools/validation/client.init.gradle -PvalidationClientDirectory=<directory>`. The fixture checks starter exports, draft cancellation, bounds and preview views at GUI scales 1 and 2. It prints `WA_CREATOR_WORKFLOW_PASS`; Gradle exit status alone does not establish that the assertions passed. Screenshots remain in the instance's `screenshots/` directory.

```text
node tools/validation/multiplayer.mjs --jar build/libs/worldarchitect-1.1.0-dev.jar --fixture build/validation-tools/worldarchitect-1.1.0-dev-validation.jar --output build/multiplayer-new
```

This starts two real clients and a dedicated server for staged placement, ordinary-player denial, stale revision/destination rejection, ghost previews, undo, client block synchronization and restart checks. Use `--options <test-instance-options.txt>` to reuse accessibility and graphics settings. It uses synthetic offline identities on loopback. It must not be pointed at a production server.

The GitHub workflow repeats the dedicated fixture and pinned public-mod matrix on Windows and Linux. `node tools/validation/compatibility.mjs` compares original and managed terrain with the verified 1.0.0 baseline. Candidate-only failures fail the job; matching identified upstream failures are recorded as blocked. The workflow uploads diagnostics, structured results, JUnit XML and CI server logs. A scheduled workflow is not created; runs follow source changes or a manual dispatch.

The release additionally requires the pack, End progression, dependency rejection, older-save audit, thirty-minute multiplayer soak and alternating same-host performance suites described in [the roadmap](../../docs/ROADMAP-1.1.md). A passing smoke test does not substitute for those checks.
