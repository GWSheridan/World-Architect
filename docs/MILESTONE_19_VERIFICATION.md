# Milestone 19 verification

Acceptance date: 19 November 2026. Minecraft 26.3, NeoForge 26.3.0.103, Java 25, Windows x64. Both binaries were rerun on this host (Intel64 Family 6 Model 167 Stepping 1; 16 logical processors, JVM capped at four).

## Artifacts and API

- Published/tested 0.9.0 baseline: `7d87acdec4c53908a59d856c33ade0c16096e473b5a8ce7ccf8fa0b648385672`.
- Initial 0.18.0 API candidate: `54840c7197662b4e5ae4587cde246c99bfbcec6b73d7b90746082294b1f32b33`.
- Optimized 0.18.0 JAR: `78a951afa565431f5f06eaf8429680678f207c82256dfc25baf270e45258e642`.
- Separate fixture JAR: `901fa8c6abe491965fc4090ea97f34f290ff4301701f6320d80409d629c9293c`.

The main build passed all 46 JUnit tests. The independent fixture compiled against the JAR rather than a parent source set. Offline compilation reused the parent's cached mapped Minecraft artifacts; the fixture source and compilation remained separate. The production JAR excludes fixture/audit classes and fixture resources.

The [API 1 contract](EXTENSION_API.md) covers dimension, structure and feature adapters, block capability checks, detached template/marker validation and checked spawn/environment/preset operations. The fixture's dedicated-server command verified preset capture/validation, template decoding/markers, a modded terrain block, permission denial for no-permission sources, invalid environment rejection and off-thread access rejection. Safe respawn and environment overrides survived restart.

The initial saved-region audit checked 359,424 positions, 143 custom feature markers and four adapted native structure starts. It also verified the untouched original dimension and saved feature definitions, adapter versions/work costs and native resource pins.

## Rejection and upgrade checks

The provider stayed installed while its adapter registration was disabled, isolating missing adapters from missing native codecs/blocks. Separate copies tested missing dimension/feature/structure adapters, each adapter at version 2 instead of the saved version 1, and API contract 2 instead of installed API 1. All seven cases stopped startup. Each retained the SHA-256 hashes of all 41 monitored world data/region files.

Five existing-save copies covered the exact 0.9.0 layered performance world, a 0.9.0 world with the separately compiled 0.2.0 dimension fixture, both fresh and populated 0.17.0 fully locked Studio Skyblock worlds, and the earlier rule-engine upgrade world. Each opened and generated an unexplored region. Generation settings/snapshots retained their hashes; the Skyblock archive and multiplayer allocation ledger also retained theirs. The legacy dimension adapter remained binary compatible with the new default portal capability method. The final optimized JAR repeated all seven rejection cases and all upgrade loads. The populated Skyblock audit confirmed two owners; the fresh consumer fixture correctly retained an empty ledger.

Final saved-data audits checked 718,848 fresh extension positions across two regions (287 feature markers and eight adapted structure starts), 393,216 old/new legacy terrain positions, 589,824 Skyblock/void positions including 580 template blocks, and 1,867,776 rule-filtered positions with 2,252 template cells, eight markers, 465 ore cells and eight decorations. All sampled generation settings/snapshots, archived bundles and ledgers retained their hashes. [Audit summary](verification/milestone19/audit-summary.txt), [save/rejection results](verification/milestone19/save-compatibility.json).

The strict benchmark comparison passed all **288 managed chunks**: block states, biomes, heightmaps and structure data matched 0.9.0, including both sides of the origin. Original Generation is diagnostic: two runs of the same 0.9.0 binary differed at 8,471 block positions and 23 chunk heightmaps; the 0.9.0/0.18.0 comparison differed at 12,362 positions and 27 heightmaps. Biomes and structure data matched in both comparisons. Native decorations, overlapping features and world ticks make this benchmark unsuitable for an exact native-block identity claim. These observations establish native run-to-run variability; they do not prove that every native difference between versions has the same cause. The managed generation comparison remains strict. Detailed [same-binary native control](verification/milestone19/same-binary-0-9-0-native-control-native-differences.tsv) and [native version comparison](verification/milestone19/normal-native-differences.tsv) retain the differences.

Local logs, original worlds, negative-test copies, pre/post hashes and benchmark JVM logs are retained under `build/acceptance/milestone19/`. Historical acceptance worlds were copied rather than modified.

## Performance method and repeated slowdown

The [reproduction harness and original workload profiles](../examples/performance) use the exact 0.9.0 JAR, maximum heap 1,300 MiB, four active processors, seed 194723, view distance 4 and simulation distance 3. Every run starts a fresh dedicated JVM/world with the packaged JAR and development source loading disabled. No other build or game runs during timing. A 16-chunk warmup precedes two separate 36-chunk regions. The process working set is sampled every 200 ms. Dense-structure cases must locate their native pyramid and exit cleanly.

The initial Layered pair averaged 1.965 seconds against 1.755 for 0.9.0. A six-batch repeat averaged 1.860 against 1.607 seconds. This prompted a simpler solid-column fill path for legacy terrain without caves, fluids, strata or material rules. It moves optional-feature dispatch out of the per-block loop; fixed ore rules and later native feature/structure phases still run. Advanced terrain retains its existing path. No generation codec or snapshot version changed for this optimization.

The apparent memory increase did not repeat consistently: Layered warm working set was 797 MiB initially and 680 MiB on repeat; the repeated 0.9.0 run used 723 MiB. Six-batch peak sets were 920 and 935 MiB respectively. Working set includes adaptive JVM heap commitment and is not retained heap or evidence of a leak.

Raw initial results: [0.9.0 baseline](verification/milestone19/baseline-0.9.0.csv), [candidate before optimization](verification/milestone19/candidate-before-fix.csv), [six-batch baseline](verification/milestone19/layered-repeat-0.9.0.csv), [six-batch candidate before optimization](verification/milestone19/layered-repeat-before-fix.csv).

The optimized Layered six-batch mean was **1.317 seconds**, down 29.2% from the initial candidate's 1.860 and 18.0% below the repeated 0.9.0 mean of 1.607. The standard two-batch results follow; peaks in this table use those two batches only. The four additional Layered batches are retained in the raw CSV for the repeated comparison.

| Profile | 0.9.0 seconds / peak MiB | Optimized 0.18.0 seconds / peak MiB |
| --- | ---: | ---: |
| normal | 3.49, 3.71 / 1107 | 3.48, 2.83 / 886 |
| void | 0.66, 2.18 / 782 | 0.65, 2.39 / 842 |
| single-block | 1.97, 2.19 / 780 | 1.95, 1.1 / 672 |
| layered | 1.32, 2.19 / 724 | 1.97, 1.09 / 711 |
| structure-heavy | 1.32, 1.75 / 800 | 1.74, 1.1 / 887 |

[Optimized 0.18.0 raw results](verification/milestone19/optimized-0.18.0.csv). Void's 0.10-second mean increase in this final pair was smaller than one polling interval and did not repeat across the earlier candidate pair (0.99 seconds against baseline 1.42). No further reproducible timing regression was identified in these workloads. Memory remains variable between JVMs; the six-batch optimized Layered peak was 951 MiB against the repeated baseline's 935 MiB.

These short wall-clock batches include tick scheduling, neighbor work and I/O. They are regression smoke tests, not isolated generation throughput, a statistical performance guarantee, or a test of arbitrary third-party providers.
