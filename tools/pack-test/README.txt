Worlds Unwritten modpack test kit

Extract the complete ZIP. Install Java 25 and the matching NeoForge, Forge or Fabric dedicated server separately. Export a project from Modpack Builder and put required provider mods in a separate folder. For Fabric, include Fabric API among the provider JARs and use the Fabric dedicated launcher JAR as --server.

Windows:
run.cmd --server "D:\Minecraft\NeoForge" --export "D:\Packs\my-pack-1.0.0-pack" --providers "D:\Packs\providers" --output "D:\Tests\my-pack-test" --eula true

Linux:
sh run.sh --server /opt/minecraft/neoforge --export /home/me/my-pack-1.0.0-pack --providers /home/me/providers --output /home/me/tests/new-pack-test --eula true

The kit uses the loader recorded by the export. Use --loader neoforge, --loader forge, or --loader fabric to select a different loader. Use --loader-version to select a compatible version of that loader.

Read the Minecraft EULA before supplying --eula true. The output folder must not exist. The kit copies runtime libraries and supplied mods into that new folder, installs the exported preset with developer mode disabled, then starts a loopback-only server with an empty whitelist. It checks saved island blocks, safe markers, starter contents, snapshot fingerprints and a restart. Results and logs remain in that folder.

No source checkout, Gradle, Node or development SDK is required. The original runtime, project, provider folders and production saves are never modified. Do not place this test fixture mod in a production server.

This is a smoke test. Test real multiplayer, permissions, progression and any external mod's gameplay in a separate instance before distributing a modpack. Liquids, random ticks and unsupported machines may need additional gameplay testing.
