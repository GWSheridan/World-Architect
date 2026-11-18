# Dimension adapter API

World Architect reads dimensions from the active world preset and Minecraft's `LEVEL_STEM` registry. It offers Original Generation for every discovered dimension and can replace compatible standard noise generators. Mod authors can opt a specialized generator into replacement or restrict the modes offered for their dimension.

## Register an adapter

Compile against World Architect 0.4.0-alpha to offer `TerrainMode.VOID` or `TerrainMode.SKYBLOCK`. Existing adapters continue to offer the modes they declare. On both physical sides, register a `DimensionAdapter` with NeoForge `DeferredRegister` during your mod's constructor. Use the exact dimension registry ID.

```java
var adapters = DeferredRegister.create(DimensionAdapterRegistry.KEY, "examplemod");
adapters.register("undergarden", () -> new DimensionAdapter() {
    @Override
    public int version() {
        return 1;
    }

    @Override
    public Support inspect(ResourceKey<LevelStem> dimension, LevelStem original) {
        if (!(original.generator() instanceof MyGenerator)) {
            return new Support(Set.of(), Optional.empty(),
                    Component.literal("The original Undergarden generator is required."));
        }
        return new Support(Set.of(TerrainMode.SINGLE_BLOCK), Optional.of(Biomes.LUSH_CAVES),
                Component.literal("Undergarden supports Single Block terrain."));
    }
});
adapters.register(modBus);
```

Import `DimensionAdapter` and `DimensionAdapterRegistry` from `com.gwsheridan.worldarchitect.api`. `TerrainMode` and `Support` are nested in `DimensionAdapter`. Replace `MyGenerator` with the generator class whose replacement your mod supports. An empty mode set exposes Original Generation only. The optional preferred biome becomes the fixed biome of the replacement generator. Without that preference, World Architect selects a registered biome from the original generator. Use a translatable `Component` for public-facing diagnostics.

`inspect` reads the original `LevelStem`. Keep it side-effect free. World Architect calls it in the editor and validates the selected mode when a profile replaces a datapack stem. The replacement retains the dimension type. Check your gameplay hooks for assumptions about terrain or the original generator before offering a mode.

## Saved configuration

An explicit profile stores its settings in `dimension_profile` for Single Block and Layered, or `dimension` for Void and Skyblock. Set the dimension type's exact `min_y` and `height`. Include the adapter registry ID and version for an adapter-bound profile.

```json
{
  "min_y": 0,
  "height": 256,
  "adapter": {"id": "examplemod:undergarden", "version": 1}
}
```

The server checks the ID and version before generation. Keep the adapter version stable while earlier profiles remain compatible. Removing the adapter or changing its version blocks saves that require the old version. Legacy 0.1.0 profiles omit `dimension_profile` and retain their prior Overworld range and encoding.
