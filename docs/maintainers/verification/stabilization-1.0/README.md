# Post-release stabilization

**Passed on 19 November 2026.** No World Architect defect was confirmed by this run. A 1.0.1 patch is not required by these results; the release remains 1.0.0 and all ten published artifacts are unchanged.

The [acceptance summary](acceptance-summary.txt) contains the final success markers and saved-world audit counts. [Structured results](results.json) record each dedicated-server case, rejected-save checks, original-save protection and artifact hashes.

## Environment and inputs

Milestone 21 rechecks the published World Architect 1.0.0 JAR on Windows x64, Java 25, Minecraft 26.3 and NeoForge 26.3.0.103. Servers bind to loopback and use synthetic offline player identities. Two full Minecraft clients connect to each multiplayer case; separate diagnostic mods observe positions, send ordinary player commands and exercise native progression. The gameplay JAR contains no test instrumentation.

The tested JAR has SHA-256 `14fe208d2fed07e8dec98e33b1f06c519cb35a81a95412e60b1c808d94b332cd`. The partial and fully locked Studio bundles are the published 1.0.0 downloads. The separate API fixture is rebuilt against that production JAR and installed on the provider-pack server and both clients.

## Coverage

| Area | Checks |
| --- | --- |
| Unit and documentation checks | Fresh run of all 51 unit tests; local and versioned documentation links |
| Locked Skyblock pack | Distinct personal islands, ordinary-player command denial, safe respawn, saved positions and assignment persistence, third-player capacity refusal |
| Extended multiplayer | Ten batches of simultaneous Overworld and End generation over at least five minutes, followed by a restart and saved-chunk audits |
| External provider pack | Two clients enter the adapter-managed lab and the untouched original dimension; registry synchronization, ordinary-player permission denial, saved positions and protected files survive restart |
| Native End progression | Arena blocks, biomes and preview boundaries; active-fight restart; first kill, gateways, exit and return; crystal respawning across restart; second kill, rewards, unsafe destination refusal and interrupted-ritual recovery |
| Terrain modes | Native arena and custom-terrain boundaries for Single Block, Layered, Void and Skyblock |
| Older saves | Copies of version 1 terrain, generation-rule, Skyblock-template and external-adapter saves reopen and generate distant unexplored chunks |
| Dependency protection | Missing and mismatched dimension, feature and structure adapters; incompatible API version; missing generation files and archived bundle |
| Corruption protection | Truncated snapshot, otherwise intact snapshot with schema changed from 2 to 3, and altered archived bundle |
| Preset enforcement | Fresh partial/full bundles; rejected terrain, rule, seed and End-policy changes; invalid arena version and dimension |

## Results

All 51 unit tests passed. The dedicated matrix completed four older-save/provider cases, two fresh bundle cases, seven invalid preset/policy cases and three arena-mode cases. All nine missing or mismatched dependency/file cases were rejected. Three additional corruption cases were rejected, including the otherwise valid snapshot with unsupported schema 3.

Both Skyblock players retained their assignments, positions and safe respawns after restarting. A third player was refused without changing the two-owner ledger. The extended run kept both clients connected for 321 seconds through ten batches requesting 320 chunks, followed by another successful restart. The external-provider pack passed with both clients before and after restart, including travel into its custom and original dimensions and ordinary-player command denial.

The native End completed both kills, a gateway round trip, exit travel, crystal respawning across restart, interrupted-ritual recovery and rewards with one egg. The active dragon's UUID persisted through restart. All four native-arena terrain modes passed their boundary checks.

Saved-region audits checked 36,370,432 positions in the older saves, partial bundle, multiplayer worlds and stress regions. Rule checks also verified 2,252 template cells and eight markers; the partial bundle verified 254 embedded template cells, chest metadata and a safe marker. All 711 files across the twelve rejected existing saves retained their file sets and hashes. All fifteen pinned files across five original source worlds remained unchanged. The older Skyblock fixture's existing owner ledger was compared byte-for-byte with its baseline.

The 39 selected positive-case server and client logs contained no test assertions, unexpected server failures, packet-handling errors, chunk-save failures or memory failures. Local setup retries are retained separately from the final successful cases.

Rejected existing saves are checked for identical file sets and SHA-256 hashes, excluding the changing `session.lock`. Successful older saves retain their pinned generation settings, snapshots, templates and bundle archives. The original source saves are checked separately and remain untouched.

Saved-chunk audits read flushed region files after shutdown. They check full chunk status and block states against the pinned generation contract, including rule filters, template cells, markers, container metadata and external native feature/structure pins. The multiplayer stress regions are audited independently of the client's position observations.

## Scope

These results cover the supplied fixture packs on the pinned platform. The external provider fixture exercises the public API, custom registries and dimension coexistence. This is a local multiplayer integration run with a bounded stress workload; it does not certify arbitrary third-party packs or establish long-duration production-server performance.

Detailed local logs, copied worlds and runners are retained under `build/acceptance/milestone21/`. Only the final sanitized summary and results are included here.
