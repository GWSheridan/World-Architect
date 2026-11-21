# Same-host performance comparison

Run from the repository root with Java 25 in `JAVA_HOME` and Java 21 in `JAVA21_HOME` for asset tools. The profiles and datapacks retain the fixed 0.9.0 workload. The 1.7 runner loads packaged JARs and a separate measurement mod. See [validation runners](../../tools/validation/README.md).

## Direct generation runner

```text
gradlew.bat build benchmarkProbeJar
node tools/validation/benchmark-pairs.mjs --baseline build/validation-inputs/worldarchitect-1.6.0.jar --candidate build/libs/worldarchitect-1.7.0.jar --probe build/validation-tools/worldarchitect-1.7.0-benchmark-probe.jar --output build/performance-new
```

On Linux, build with `sh ./gradlew` and use the same Node command. An equivalent Windows wrapper is `tools/validation/benchmark-pairs.ps1 -BaselineJar <JAR> -CandidateJar <JAR> -OutputRoot build/performance-new`. New outputs must stay under `build/` and must not exist already. First-time dependency preparation needs `--network online`; cached local runs default to offline. The runner records SHA-256 for both inputs, the probe, runtime configuration and the JAR actually loaded by each server.

Defaults are three comparison pairs, one identical-baseline control pair and all five profiles, alternating baseline/candidate order. Every fresh server warms up 1,024 chunks, then generates five separate 576-chunk samples. The probe times synchronous FULL chunk requests with `System.nanoTime`; console polling, startup and shutdown are outside that timer. Generation still includes the requested chunks' dependencies and any GC that occurs inside the measurement. The probe records GC deltas, sampled heap and actual resident memory. Windows uses a separate working-set sampler; Linux reads `/proc/self/status`. Missing resident samples make a result inconclusive.

Each JVM uses four active processors and a fixed 1,300 MiB heap. Stop other game servers, clients, builds and heavy work before measuring. Pair totals, median time, sampled heap and resident memory are reported separately. Generation above 10% or memory above 15% needs investigation. An identical-JAR control outside those limits makes the comparison inconclusive, even when the candidate looks faster. Preserve every raw result, threshold flag and failed control. A small `--purpose smoke --pairs 1 --control-pairs 1 --warmup-size 8 --measured-size 6 --samples 2 --profiles void` run checks the tools, never release performance.

Use `--deadline-seconds` for a total deadline, or create an empty `stop` file in the output directory to cancel. The runner stops its own server and retains completed records. Increase `--control-pairs` up to 3 when a planned repeat needs more controls. Strict terrain audits run after timed pairs; native diagnostics below remain separate from existing-save protection.

## Historical polling runner

The original scripts remain available to reproduce earlier release measurements:

```powershell
.\gradlew.bat build
.\examples\performance\prepare.ps1 -BaselineJar build/release/worldarchitect-1.1.0.jar -CandidateJar build/libs/worldarchitect-1.2.0.jar
.\examples\performance\benchmark.ps1 -Version baseline
.\examples\performance\benchmark.ps1 -Version candidate
.\gradlew.bat auditBenchmarkWorlds -PbenchmarkBaseline=build/performance/bench-baseline -PbenchmarkCandidate=build/performance/bench-candidate
```

The prepare script creates fresh disposable worlds and writes their EULA acknowledgment. Use a new `-OutputRoot` for each repeat. The benchmark never deletes worlds or reuses a completed case. Set `JAVA21_HOME`, or pass `-Jdk21`, if the asset toolchain cannot find its Java 21 runtime. Offline runs require cached Gradle/Minecraft artifacts; remove `--offline` from the script for first-time dependency setup. `.gradle-local` must already exist when using the default cache path.

No other game servers, clients, builds or CPU-heavy work should run during timing. The packaged mod JAR alone is loaded from each `mods` folder (`loadedMods=[]` excludes development source output). Each JVM has four active processors and a 1,300 MiB maximum heap. Seed 194723, view distance 4, simulation distance 3. Warm up 4×4 chunks at block 8000, then measure two new 6×6 regions at blocks 16000 and -16000. Poll every 200 ms until all chunks are loaded; collect sampled server-JVM working set from its GC-log PID. Locate a native desert pyramid in the structure workload and stop cleanly.

Times include tick scheduling, I/O and neighbor work; this small benchmark is a regression smoke test, not an isolated throughput or tail-latency measurement. Repeat suspicious cases with fresh worlds, preferably alternating order. Preserve both CSVs, GC logs, settings, JAR hashes and region files. `auditBenchmarkWorlds` strictly compares 288 managed chunks' block states, biomes, heightmaps and structures. The 72 Original Generation chunks are diagnostic: native decoration/ore overlaps and heightmaps vary between runs of the same 0.9.0 binary. Supply `-PbenchmarkNativeControl=<second-0.9.0-normal-world>` to include a same-binary control; detailed native differences are written as TSV files under the candidate benchmark root. Native biomes are still required to match. Preserve the historical release report rather than replacing its timings with contemporary measurements.

For a focused repeat, pass `-Profiles @('void','single-block')` to `benchmark.ps1` for both versions in each new pair directory. Record that profile list in `inputs.json`; `compare-performance.mjs` checks all three pairs for every listed profile. Retain the initial threshold flags alongside the repeats.

The structure-heavy case includes vanilla desert pyramids. Minecraft 26.3 chooses a burial offset with `-random.nextInt(3)` when a pyramid is first processed, so chunk-processing order can move a native piece even with the same binary and seed. The default audit still refuses any difference. After reproducing a difference with the **same JAR**, use `auditNativePyramidPair -PbaselineWorld=<world> -PcandidateWorld=<world>` for the narrower diagnostic. It accepts only the vanilla 0–2 burial offset, requires matching start data after that normalization, and checks every biome, every block outside the exact pyramid footprints, and every heightmap column outside those footprints. Native blocks inside the footprints remain diagnostic. This command is specific to the fixed desert fixture and does not replace strict existing-save comparisons. Keep diagnostic output locally with the exact inputs and JAR hashes.
