# World Architect 1.0 release verification

Acceptance date: 19 November 2026. Minecraft Java **26.3**, NeoForge **26.3.0.103**, Java **25**, Windows x64. Production JAR SHA-256: `c2daf450ea6507c51fe8484e3aa4fe573f74c2ebc48d06637e5bd6d152feac26`.

## Release changes

The 1.0 promotion retains the 0.18 generation algorithms, versioned codecs, API 1 and immutable-save contracts. Release work updates the version/developer fixture, installation and example documentation, current release notes and reproducible artifact/checksum packaging. Historical reports and the exact preset-bundle bytes remain available.

The build passes all **46 JUnit tests**. The independent API fixture builds against the 1.0 JAR with a 1.x dependency range. Test probes are compiled into a separate diagnostic mod; both multiplayer clients and the dedicated server load the packaged production JAR. Diagnostic classes/resources are excluded from the shipping JAR.

## Multiplayer and client checks

Two ordinary localhost clients, WA20Alice and WA20Bob, joined a fresh instance of the fully locked Skyblock example. They received distinct personal islands, were refused authoring/library commands, and returned to their own safe island after death. In the same server phase, new 6×6 chunk regions were requested in the Overworld and End while both clients were connected.

After a clean restart, both clients resumed at their saved position and dimension, then respawned on their original islands. Generation settings, snapshot, archived bundle and assignments retained their hashes. A third new player was refused with the island-capacity message; the two-owner allocation ledger remained unchanged and the server stopped cleanly.

Clean partial and full client instances tested the exact example ZIPs at GUI scales **1 and 2**. Checks covered selected presets/defaults, locked world options, bundled modular definitions, permitted ore edits, disabled controls, help, widget bounds and rejected tampered creation before a world could be made.

## Compatibility and save protection

Copies of the 0.9.0 Layered world, populated 0.17.0 Studio Skyblock world, earlier rule-engine upgrade world and 0.18.0 API fixture reopened on 1.0. They generated previously unexplored chunks. Pinned generation files, archived bundles and island ledgers retained their hashes. The API fixture repeated permission, thread, template, marker, preset, environment and spawn checks.

Nine rejected saved-world copies tested missing and version-2 dimension, structure and feature adapters; API contract 2; both generation files missing from a marked world; and the original world-local bundle archive missing. Every case stopped startup. The seven adapter/API copies retained 52 monitored hashes each; missing-files copies retained all remaining monitored files. Missing files must not be silently regenerated.

Clean dedicated installations use the unchanged partial/full example bundles. Partial policy allows the selected seed/ore/End changes; forbidden terrain/rule changes and a full-lock seed change must stop startup without a generation snapshot. Administrator environment/library operations remain subject to the active bundle.

Saved-region audits checked **4,226,048 positions** across legacy, rule, API, multiplayer, clean-bundle and upgrade fixtures. This includes 2,252 rule-template cells, eight spawn markers, 465 rule ore cells, eight rule decorations; 144 native feature blocks and four adapted structure starts; three sets of 435 Skyblock template blocks; and 254 partial-bundle template cells with chest metadata and a safe marker. Partial-policy gold ore edits and the independent End replacement passed their audits. [Acceptance summary](verification/milestone20/acceptance-summary.txt), [save hash results](verification/milestone20/save-compatibility.json).

The nine saved-world rejection copies retained the complete monitored file sets as well as their hashes. Neither missing generation files nor a missing archive was silently replaced. All three forbidden new-world selections stopped before writing a generation snapshot.

## Artifact and source checks

`assembleRelease` stages five assets plus `SHA256SUMS-1.0.0.txt`. Independent SHA-256 verification checks the staged files and the JAR embedded in the complete ZIP against the exact tested production binary. ZIP CRC checks pass; source/distribution archives exclude build/run/Gradle caches and diagnostic binaries. All tested clients and servers used the same production JAR hash above. The two example ZIPs match the clean-instance copies byte for byte.

A clean extraction of the source ZIP rebuilt a **byte-identical production JAR** and passed all 46 tests. The offline check reused cached mapped Minecraft artifacts, without reusing parent classes or resources. The source includes the Gradle wrapper and independent API fixture project. Current release-doc local links were checked. The GitHub release is prepared as a draft with the tested assets; publication is a separate action.

## Performance scope and limitations

The prior [same-host 0.9.0 comparison](MILESTONE_19_VERIFICATION.md) measured and fixed the Layered regression: its repeated mean fell from 1.860 to 1.317 seconds per 36 chunks, against 1.607 for the repeated 0.9.0 control. This release does not change that generation implementation. The final multiplayer test exercises simultaneous generation; it is not a new isolated timing benchmark.

Native Original Generation showed block/heightmap variation between runs of the same 0.9.0 binary. Exact equality claims apply to the managed fixtures and their versioned profiles. Provider mods remain trusted code; passing this fixture does not certify every mod or a different Minecraft data format.

Local server/client logs, screenshots, before/after hashes and copied acceptance worlds are retained under `build/acceptance/milestone20/`. The shipped source contains the unit/region audits, API fixture and performance reproduction project. Original historical worlds were preserved.
