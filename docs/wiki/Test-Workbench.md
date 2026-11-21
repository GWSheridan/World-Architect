# Modpack test workbench

The workbench runs reviewed preset scenarios in new disposable dedicated-server instances. It is distributed as source tools and a separately built validation fixture. Test code is excluded from the gameplay JAR.

With developer mode enabled, use `/wa_workbench export <Studio name> <release>` as a permission-level-4 administrator. It validates the saved revision and exports its bundle and a `.scenario.json` file to `config/worldarchitect/exports/`. Choose a release that has not already been exported. This does not alter the current world's generation. Scenarios contain bundle fingerprints and explicit test inputs; they are not uploaded.

From an extracted source distribution, build the fixture with JDK 25, JDK 21 and Node 22 or newer installed:

```powershell
.\gradlew.bat build acceptanceJar
node tools/workbench/run.mjs --scenario path/to/preset.scenario.json --jar build/libs/worldarchitect-1.9.0.jar --fixture build/validation-tools/worldarchitect-1.9.0-validation.jar --output build/pack-test
```

Use `bash ./gradlew` on Linux. Add `--network online` if Gradle dependencies/assets have not been cached; the runner otherwise uses offline mode. `JAVA21_HOME` selects the asset downloader's JDK. `--mods <directory>` copies explicitly selected provider JARs and records their hashes. Use matching provider versions. Review the EULA before running: each disposable instance writes `eula=true`.

Outputs must be new directories under the source tree's `build/` directory. The runner refuses an existing directory, checks its resolved parent, never accepts a production-world path and never deletes test instances. Servers bind to loopback, whitelist access, disable RCON and management services, and stop after the scenario. Do not run two workbench jobs concurrently from the same checkout, because their Gradle server launch configuration is shared.

Scenario format 1 selects a local bundle filename and SHA-256, a heap/time budget and up to 16 uniquely named runs. Seeds are signed 64-bit **strings** to avoid JavaScript rounding. Each run selects dimensions, requested chunk rectangles, optional block-state/biome/structure-start probes, safe-spawn checking, restart checking and an expected startup result. Structure probes require a valid start in the probe's chunk; they do not search the world. Safe-spawn checking requires a safe position within four blocks of the saved initial spawn; disable it deliberately for an intentionally uninhabitable empty void.

Runs may request 256 chunks, 32 rectangles and 128 probes; a scenario may request 2,048 chunks in total. Rectangle width/depth are 1–16 chunks. Heap budgets are 768–4096 MiB and each server stage has a 30–900 second deadline. A rejection case needs a specific `expected_error` substring and must fail before the server finishes startup. Use this to check a missing provider or a forbidden seed override in a full-lock pack. An unexpected timeout is a failed test.

Local `results.json` records the input hashes, stage results and immutable generation hashes across restart. Per-server `workbench-observed.json` records probe counts, elapsed checks and heap use **at completion**, not peak memory. Console logs remain inside the test directory. These checks complement real-client multiplayer tests; they do not simulate island joins or certify every setting of another mod.

For performance comparisons, `tools/validation/benchmark-pairs.ps1` alternates three same-host baseline/candidate pairs. Its defaults use 64 warmup chunks and five measured batches of 64 chunks per profile, with 200 ms resident-memory polling. Retain initial flags and investigate reproducible changes above 10% generation time or 15% sampled resident memory. The sampler requires Windows; workbench scenarios support Windows and Linux. See [Installation compatibility guidance](Installation.md#compatibility).

The integration cases cover a clean install/restart, a deliberately incorrect block probe, a full-lock seed override, a missing provider and an empty void with safe-spawn checking enabled. Runtime probe and spawn failures are expected only in those deliberately failing cases.

## Edit scenarios in game

Open **Preset Studio → Workbench** after saving and validating the current preset version. On a dedicated server, a level-4 administrator with developer mode enabled can use `/wa_workbench studio <name> <release>`. Edit runs, exact seed strings, dimension lists, startup expectations, safe-spawn/restart policies, chunk areas, probes and budgets. Runs can be duplicated and reordered through history. Undo/Redo and explicit recovery work on the detached scenario draft.

The selected saved preset and its SHA-256 remain fixed. Export revalidates the scenario and that source revision, then creates a new named scenario beside the identical bound bundle. Existing scenario files cannot be overwritten. The server checks the current actor, developer mode, session token and source fingerprint for each export; losing permission while the editor is open prevents further writes. Export status reports the server's acknowledgement. This screen only authors inputs; it never starts servers inside the game.

For version/provider/seed matrices, total deadlines and combined HTML/JSON reports, see [Batch Testing](Batch-Testing.md).

Fixture assertion failures stop a run immediately. Failed reports distinguish `assertion`, `error`, `timeout` and `interrupted`; a budget expiration cannot stand in for an expected probe failure.
