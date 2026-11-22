Worlds Unwritten 1.8.0 — optional modpack test kit

Extract the complete ZIP. Install Java 25 and a matching NeoForge 26.3.0.103 dedicated server separately. Export a project from Modpack Builder, then place the required provider mods in a separate folder. Minecraft, NeoForge, provider mods and Java are not included in this kit.

Windows:
run.cmd --server "D:\Minecraft\NeoForge" --export "D:\Packs\my-pack-1.0.0-pack" --providers "D:\Packs\providers" --output "D:\Tests\my-pack-test" --eula true

Linux:
sh run.sh --server /opt/minecraft/neoforge --export /home/me/my-pack-1.0.0-pack --providers /home/me/providers --output /home/me/tests/new-pack-test --eula true

Read the Minecraft EULA before supplying --eula true. The output folder must not exist. The kit copies runtime libraries and supplied mods into that new folder, installs the exported preset with developer mode disabled, then starts a loopback-only server with an empty whitelist. It checks saved island blocks, safe markers, starter contents, snapshot fingerprints and a restart. Results and logs remain in that folder.

No source checkout, Gradle, Node or development SDK is required. The original runtime, project, provider folders and production saves are never modified. Do not place this test fixture mod in a production server.

This is a smoke test. Test real multiplayer, permissions, progression and any external mod's gameplay in a separate instance before distributing a modpack. Liquids, random ticks and unsupported machines may need additional gameplay testing.
