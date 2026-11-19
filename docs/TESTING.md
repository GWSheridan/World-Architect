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
