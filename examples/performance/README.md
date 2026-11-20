# Same-host performance comparison

Run from the repository root with Java 25 in `JAVA_HOME`. Build the candidate and prepare the Gradle run artifacts first. The profiles and datapacks are the fixed 0.9.0 benchmark workload, with local test server settings and no server management credentials. See [verification results](../../tools/validation/README.md).

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
