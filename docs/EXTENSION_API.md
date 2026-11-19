# Public extension API 1

World Architect 1.0.0 supports Minecraft 26.3, NeoForge 26.3.0.103 and Java 25. Compile against the distributed JAR; declare a required NeoForge dependency on World Architect. Call `WorldArchitectApi.requireVersion(1)` in your mod constructor. A different API contract fails during mod construction, before a world opens.

API 1 was introduced in 0.18.0 and remains compatible in 1.0.0.

The supported surface is `com.gwsheridan.worldarchitect.api` plus the data types listed below. Other implementation packages, mixins, generator implementation classes, client screens and low-level file writers are internal even when Java visibility is public. API 1 additions retain existing method signatures and codec defaults. Breaking changes require a new API version. Minecraft/NeoForge upgrades may require recompilation; API 1 is not a promise of binary compatibility across game versions.

## Registration and dimensions

Register on **both physical sides**, during the mod constructor, with NeoForge `DeferredRegister`. Attach the deferred register to the **mod event bus**. World Architect creates its three adapter registries during `NewRegistryEvent`; consumers must not register values manually after registry freeze. See the official [registry lifecycle](https://docs.neoforged.net/docs/concepts/registries/) and [event buses](https://docs.neoforged.net/docs/concepts/events/).

| Extension | DeferredRegister key | Entry ID |
| --- | --- | --- |
| Dimension | `DimensionAdapterRegistry.KEY` | Exact dimension ID |
| Structure | `StructureAdapters.KEY` | Exact native `STRUCTURE_TYPE` ID |
| Feature | `FeatureAdapters.KEY` | Exact native `FEATURE_TYPE` codec ID |

```java
WorldArchitectApi.requireVersion(1);
var adapters = DeferredRegister.create(FeatureAdapters.KEY, "example");
adapters.register("crystal", () -> new FeatureAdapter() {
    public int version() { return 1; }
    public int workLimit(Feature feature) {
        return feature instanceof CrystalFeature crystal && crystal.isBounded() ? 64 : 0;
    }
});
adapters.register(modBus);
```

`DimensionAdapter.inspect` reads the original stem and offers replacement modes and an optional registered biome. Original Generation remains available. Empty modes disable replacements. The existing [dimension adapter guide](DIMENSION_ADAPTERS.md) explains binding and vertical ranges. `DimensionProfile.AdapterBinding` stores the exact dimension ID and positive version. Discovery reads the selected preset and `LEVEL_STEM` registry; no hardcoded list is required.

`supportsPortalPolicies()` defaults to false. Override it only if every custom portal route calls `EnvironmentApi.canEnter(destination, entity)` before teleporting. This enables portal controls for that dimension; the declaration does not automatically intercept third-party teleports. Native Overworld/Nether/End entry is already integrated. Changing portal support may invalidate saved environment settings.

## Structures and templates

A `StructureAdapter` declares a positive `version()` and pure `supports(Structure)` check. It opts explicitly selected native structures into managed Single Block/Layered terrain. Register native structure codecs, pieces, templates and structure definitions through Minecraft/NeoForge as usual. World Architect supplies deterministic random-spread placement; your structure owns its native lifecycle, bounding boxes, biome checks, serialization and placement. Built-in pyramids, temples, huts, End Cities and World Architect modular structures retain their existing implementations.

`StructureTemplates.modular(JsonObject, HolderLookup.Provider)` decodes a detached `worldarchitect:modular` definition, checks size/overlap/rules/markers and validates metadata. It never inserts entries into a live registry. `StructureTemplates.read(CompoundTag, registries)` reads and sanitizes a detached native template: decorative supported metadata survives; inventories, commands and unsupported data are removed or rejected. Neither method places blocks.

Supported template data: `NativeTemplate`/`Cell`, `TemplateRules`, `TemplatePlacement`; `ModularStructure` supports `CODEC`, `validateTemplates`, `horizontalSpan`, `blockCount` and `hasMarker`. Constructors/native override methods on the generator implementation are not extension points. Register a decoded modular definition via a datapack before selecting its holder. See [template rules and markers](STRUCTURE_LIBRARY.md) and [cross-dimension examples](../examples/cross-dimension-structures).

## Features and blocks

Minecraft 26.3 uses `Feature` implementations and `Registries.FEATURE` definitions (`data/<namespace>/worldgen/feature/<id>.json`), with codecs in `Registries.FEATURE_TYPE`. Older `ConfiguredFeature` examples do not apply to this version.

`NativeFeatureRule` selects a registered `Holder<Feature>`, 1–8 attempts per chunk, an inclusive origin height interval, optional terrain-column-top + 1 origin and registered origin biome filters. Set the rules using `ContentProfile.withNativeFeatures`, or the optional `content.native_features` field:

```json
{"native_features":[{"feature":"example:crystals","attempts":2,"min_y":32,"max_y":64,"biomes":["minecraft:plains"]}]}
```

This is available on managed solid terrain. Void/Skyblock reject natural feature rules to preserve empty chunks. At most 16 rules and 65,536 declared work units per chunk are allowed. `FeatureAdapter.workLimit` returns a conservative positive cost per invocation or zero to reject a configuration. Cost should include blocks visited/written and expensive sampling. World Architect bounds dispatch count and declared work, not arbitrary third-party code execution. Providers are trusted code and must honor the declared limit, use the supplied random source, remain thread safe and deterministic, and avoid unrelated chunks or live server/player state. Do not cache mutable random sources or world references.

Each native type ID has one adapter provider; duplicate registrations fail through the registry. Coordinate shared adapters rather than replacing providers after registration.

The engine seeds each selection from the world seed, chunk, feature ID and selection index. Filters apply to the **native origin**, not every write performed by native code. Use the existing [rule engine](WORLDGEN_RULES.md) fixed actions when every block must obey strict height/biome/region filters. Reordering native selections changes their seed and is a generation change. Native features run after fixed decorations and before native structures. Empty native selections retain the original generation path.

The built-in native adapter accepts constant `SimpleBlockFeature` configurations only: one stable full block, no scheduled ticks, no block entity, fluid, random tick or falling block. Other native types require an adapter. `BlockCompatibility.TERRAIN_CODEC` parses registered block states, including modded IDs and properties. `terrain(state)` checks full-solid terrain capability; `feature(state)` additionally rejects falling blocks. These checks do not certify arbitrary mod code. Template blocks use the separate template/metadata rules.

Supported generation data: `DimensionProfile`, `ContentProfile` and nested rules, `SingleBlockProfile`, `LayeredProfile`, `TerrainShape`, `IslandProfile`, `IslandTemplate`, and `rules.RuleSet` with their public codecs and immutable editing helpers. The prior eight-argument `ContentProfile` constructor remains available. These data objects do not mutate active generation; decode/validate the complete preset before use.

## Spawning, environments and presets

| API | Contract |
| --- | --- |
| `SpawnApi.set/clear(source, phase, ...)` | Server thread, permission level 3, active bundle locks, codec and safe target checks. Persists administration separately from generation. Phases are INITIAL, RESPAWN, PORTAL. |
| `SpawnApi.policy` | Returns the explicit administrative override, not the rule-engine fallback. Server thread. |
| `SpawnApi.resolve` | Codec validation and safe primary target; may return empty. Island/team resolution can persist an allocation: call for actual players only. Never invent a UUID for preflight. Server thread. |
| `EnvironmentApi.profile/capabilities/canEnter` | Server-thread reads; native dimension capabilities and portal-entry decision. |
| `EnvironmentApi.set(source, dimension, profile)` | Permission level 3, server thread, codec/capability checks, dimension lock enforcement, atomic persistence, weather/cache refresh and player synchronization. |
| `PresetApi.capture/load/save/export/readImport` | Permission level 3 and server thread. File outputs retain Studio name/path/size checks and developer-mode requirements. Capture/validate author detached presets for future worlds. |
| `PresetApi.validate/duplicate` | Detached authoring; validate uses a private structure overlay and returns dependency diagnostics. Do not run registry reads concurrently with a registry reload. |

Supported data: `SpawnPolicy` and its codec; `SafeSpawns.Target`; `EnvironmentProfile` and nested records; `WorldEnvironment.Capabilities`; `BundleManifest` (including locks and `NATIVE_FEATURES`); `PresetStudio.Draft`, `Checked`, `Revision`. Draft JSON and byte arrays are copied defensively. Use checked APIs for server actions rather than low-level `WorldAdministration.save` or arbitrary paths. These guards protect ordinary callers and commands; installed mods remain trusted Java code and can access Minecraft directly.

## Saved worlds and adapter versions

Keep each adapter version unchanged while its old behavior/configurations remain compatible. Increase the version when changing an algorithm or contract incompatibly. Supply an old-compatible implementation under the saved version to reopen that world; do not edit its snapshot or silently migrate generation.

New native extension worlds pin selected adapter IDs/versions/work costs, feature definitions and native generation resources alongside the existing dimension, biome, structure and template checks. Missing or mismatched adapters stop startup before chunk generation. Changed feature definitions are rejected even when the adapter version remains unchanged. Datapack reloads cannot replace pinned native generation resources. Empty legacy configurations omit the new pin section and retain the existing save format. Client and server must install matching providers; the server validates independently.

## External consumer and verification

[The separate fixture project](../examples/api-fixture) compiles against the shipped JAR and supplies a dimension adapter, custom block, native feature and structure codec. It exercises template/marker decoding, preset validation, environment/spawn persistence, permissions and thread guards. No fixture classes or resources are included in the production JAR. See [Milestone 19 verification](MILESTONE_19_VERIFICATION.md) for adapter rejection, upgrade and benchmark results.
