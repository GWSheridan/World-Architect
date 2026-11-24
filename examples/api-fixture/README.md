# External API 1 fixture

This independent NeoForge project consumes Worlds Unwritten's JAR through `compileOnly`; it does not compile against the parent source set. Minecraft 26.3, NeoForge 26.3.0.103, Java 25.

From the Worlds Unwritten repository root:

```powershell
.\gradlew.bat build
.\gradlew.bat -p examples/api-fixture build
```

For another artifact, pass `-PworldsUnwrittenJar=<absolute-JAR-path>`. Install **both** JARs in a clean instance's `mods` directory. Keep this diagnostic mod out of a production modpack. It has no block items or client assets; the fixture block exists for registry and generation audits.

The current fixture builds against Worlds Unwritten 2.1.0. To test the optional cell generator in its lab dimension, add `-Dwa.fixture.cells=true` to the server JVM. Without that property, the adapter deliberately keeps its default `supportsCellularTerrain()` value of `false`, even though its older mode declaration uses `EnumSet.allOf`. Existing API 1 providers therefore cannot opt into new terrain accidentally.

Use `level-type=wa_api_fixture\:laboratory` for a fresh dedicated server. The preset has managed vanilla dimensions, an adapter-bound lab with solid custom slate terrain, four native markers per chunk at Y45, an explicitly adapted pyramid type, and an untouched original flat dimension. Seed 194723 is used for acceptance. The slate surface is Y40. Chunk positions can coincide across feature attempts.

Run `wa_fixture verify` as console/admin, then `wa_fixture persisted` after restarting. The former checks detached template/marker and preset APIs, permission denial with a no-permission command source, invalid environment rejection, off-thread read rejection, safe respawn policy and environment persistence. The command intentionally has no tree permission guard so the checked API must enforce permissions. A successful run logs `WA_API_FIXTURE_PASS`; restart logs `WA_API_PERSISTENCE_PASS`. Console exceptions are failures even if the server subsequently exits with status zero.

For negative tests, reopen a **copy** of the successful world with exactly one JVM property:

| Property | Expected result |
| --- | --- |
| `-Dwa.fixture.skipDimension=true` | Required dimension adapter missing |
| `-Dwa.fixture.dimensionVersion=2` | Saved dimension adapter version mismatch |
| `-Dwa.fixture.skipFeature=true` | Required native feature adapter missing |
| `-Dwa.fixture.featureVersion=2` | Saved native extension dependency mismatch |
| `-Dwa.fixture.skipStructure=true` | Native structure has no compatible placement implementation |
| `-Dwa.fixture.structureVersion=2` | Saved native extension dependency mismatch |
| `-Dwa.fixture.apiVersion=2` | Mod construction rejects API contract before opening world |

The provider stays installed in missing-adapter cases so registry definitions remain available. Hash level data, world-generation settings, immutable snapshot, administrative data and region files before/after each rejected load. Then reopen with matching properties and generate distant chunks. A mismatch must not overwrite the saved contract.

This source uses only `api.*`, documented supported profile/data types, and Minecraft/NeoForge APIs. See [API contract](../../docs/EXTENSION_API.md).
