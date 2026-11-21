# Public extension API 1

World Architect 1.0.0 supports Minecraft 26.3, NeoForge 26.3.0.103 and Java 25. Compile against the distributed JAR; declare a required NeoForge dependency on World Architect. Call `WorldArchitectApi.requireVersion(1)` in your mod constructor. A different API contract fails during mod construction, before a world opens.

Read the full guide: [Public API](wiki/Public-API.md), also available in the [GitHub wiki](https://github.com/GWSheridan/World-Architect/wiki/Public-API).

## Cell progression

`CellProgressionApi.VERSION` is 1. Quest adapters call `CellProgressionApi.signal(territoryLevel, player, signalId)` on the logical server thread after validating the quest reward. The overload taking only a player targets their current dimension. The target level and player must belong to the same server.

Signals can only complete objectives defined in the saved preset. The server resolves the player's saved territory, prerequisites and rewards. Completion is deduplicated per territory, and early signals are retained until their prerequisites are met. Team objectives require a scoreboard team. This API does not regenerate arbitrary chunks or change the immutable generation snapshot.

Dimension adapters advertise `DimensionAdapter.TerrainMode.CELLULAR` and return `true` from `supportsCellularTerrain()`. The default is false, so older adapters using `EnumSet.allOf` cannot silently claim support for a new generator. Existing adapter versions and terrain modes keep their contracts. `CellLayout` supplies seed-independent square/hex geometry; `CellWorldProfile` defines the version-one palette and `GrowthRules` defines its immutable objective graph. See the [cell-world guide](wiki/Cell-Worlds.md) for preset limits and command permissions.
