# WORLD ARCHITECT
## Complete Minecraft World, Dimension and Structure Generation System

**Project:** World Architect  
**Mod ID:** `worldarchitect`  
**Author:** GWSHERIDAN  
**Original Creator:** GWSHERIDAN  
**Developer:** GWSHERIDAN  
**Platform:** Minecraft Java Edition  
**Initial target:** Minecraft 26.3  
**Mod Loader:** NeoForge  
**Java:** 25, or the version required by the selected supported Minecraft release  
**Development language:** Java  

---

# 1. Project Overview

Develop World Architect as an original, fully featured Minecraft world-generation and dimension-customisation mod.

The purpose of World Architect is to give ordinary players, server owners and professional modpack developers complete control over how Minecraft worlds are created.

The mod must allow players to design an entire Minecraft universe before creating a world, using a comprehensive, accessible in-game graphical interface.

This includes the Overworld, Nether, End, datapack dimensions and compatible dimensions supplied by other installed mods.

Players must be able to change terrain, choose which blocks generate, remove natural features, add structures, build Skyblock worlds, create completely empty void dimensions and customise generation behaviour independently for every dimension.

Modpack creators must also be able to design worlds, package custom structures and presets into their modpacks, and lock specific settings so players experience the intended world design.

An essential requirement is permanent protection of existing world saves. Updating World Architect, Minecraft or a modpack must never automatically overwrite, regenerate, reset or change an existing world's saved world-generation configuration.

World Architect must be a genuine world-generation system, not a simple block-replacement utility.

The project must be developed as a maintainable, production-quality Minecraft mod, with working gameplay functionality, proper interfaces, clean architecture, comprehensive testing and documentation.

Implement the planned systems in stages, but design the architecture from the beginning to accommodate the complete specification.

Do not stop at a proof of concept, project scaffold or collection of placeholder screens.

---

# 2. Development Standards

Begin by establishing a working NeoForge development environment for Minecraft 26.3.

Use the latest compatible stable NeoForge build and verify the required Java and Gradle versions.

Consult current official NeoForge documentation and Minecraft mappings before implementing world-generation functionality.

The Minecraft 26.3 generation system differs significantly from older versions. Pay particular attention to changes involving terrain generation, density functions, material rules, biome resolution and chunk-generation stages.

Do not copy outdated APIs from Minecraft 1.20 or 1.21 projects.

Use version-correct registries, codecs, serialization systems, events and generation interfaces.

Avoid unnecessary mixins. Where they are unavoidable, isolate them, document their purpose and test them against other world-generation mods.

The codebase should have clear packages for:

- Core mod initialization and registration
- World presets and configuration profiles
- Chunk and terrain generation
- Dimension discovery and compatibility
- Biomes, blocks, fluids and environmental rules
- Features, ores, decorations and structures
- Structure capture, importing and placement
- Skyblock and void generation
- Spawn point and island management
- In-game menus and previews
- Modpack developer tools and locking
- Save protection and compatibility validation
- Networking, permissions and multiplayer
- Public APIs and integrations
- Data generation and automated testing

Follow professional Java engineering conventions. Avoid large monolithic classes, unnecessary global state and duplicated generation logic.

Document decisions where necessary, but keep source comments concise and useful.

---

# 3. World Creation Integration

World Architect must integrate directly with Minecraft's Create New World process.

Add a clearly labelled **World Architect** button or world-customisation entry point.

Opening it should display the complete World Architect interface.

The player must be able to configure all supported dimensions before committing to world creation.

Provide these primary sections:

1. World Overview
2. Dimension Manager
3. Terrain Designer
4. Block and Layer Designer
5. Biome Manager
6. Features and Decorations
7. Structure Manager
8. Skyblock and Void Designer
9. Spawn Point Manager
10. Environment and Gameplay Rules
11. World Preview
12. Preset Library
13. Modpack Developer Settings
14. Validation and Create World

Each screen must be connected to the real configuration model.

Settings must persist while navigating between screens.

Provide tooltips, searchable selectors, sensible defaults, validation messages, reset controls and clear explanations of how settings affect generation.

Players should never need to manually edit JSON just to create a customised world.

Advanced configuration files must still be supported for modpack developers and automated tooling.

---

# 4. Universal Dimension Manager

This is one of the core features of World Architect.

The mod must not be restricted to the Overworld.

Every available registered dimension must be discoverable and represented in the Dimension Manager.

This includes:

- Minecraft Overworld
- Minecraft Nether
- Minecraft End
- Datapack-defined dimensions
- Compatible modded dimensions
- Additional custom dimensions created through supported dimension definitions

Discover dimensions by their registry identifiers rather than hardcoding a small list of known dimensions.

For every dimension, provide its own independent configuration profile.

The player must be able to select a dimension and configure its terrain, blocks, biomes, features, structures, fluids, caves, spawning and applicable environmental properties.

## Dimension generation modes

Each dimension must offer the following where supported:

**Original Generation**

Preserve the dimension's existing world generation.

**Custom Terrain**

Generate terrain according to World Architect's own terrain configuration.

**Single Block World**

Generate every solid terrain position using one selected block.

**Layered World**

Generate terrain using individually configured block layers.

**Void World**

Generate no natural terrain whatsoever.

**Skyblock World**

Generate a void dimension containing specified islands or custom starting structures.

**Floating Islands**

Generate configurable floating terrain formations.

**Advanced Custom Generation**

Use configurable terrain functions, region rules, biome settings and custom structure placement.

These modes must be available through a shared generation framework rather than separate hardcoded systems for individual dimensions.

## Modded dimension compatibility

Not every third-party dimension uses Minecraft's standard generation mechanisms.

Design an extensible adapter system with three levels:

1. Preserve the original generator unchanged.
2. Modify supported generation features without replacing the generator.
3. Replace generation using World Architect's own compatible generator.

Discover available capabilities for each dimension and display them accurately in the GUI.

If a dimension requires a dedicated compatibility adapter, the mod must explain which features are available and which are unsupported.

Do not claim to support a dimension feature that has not been verified.

Provide a public API so other mod authors can register dimension adapters and expose their generation parameters.

The objective is the broadest possible compatibility without corrupting dimensions or breaking their gameplay mechanics.

---

# 5. Single Block World Generation

Implement a strict Single Block World mode.

Players must be able to select any compatible registered Minecraft block and use it for all naturally generated solid terrain.

For example:

**Pure Sand World**

- Primary terrain block: `minecraft:sand`
- Natural hills: enabled
- Mountains: enabled
- Valleys: enabled
- Stone generation: disabled
- Bedrock generation: disabled
- Water: disabled
- Lava: disabled
- Ores: disabled
- Trees: disabled
- Vegetation: disabled
- Structures: disabled
- Caves: disabled

The resulting world must have real hills, valleys, mountains and surface variation, with sand being the only naturally generated solid terrain block.

No hidden stone, dirt, deepslate, ores, bedrock or other solid terrain blocks may appear.

Air above the terrain is permitted.

This must be achieved within the generation pipeline, not by generating a full vanilla world and replacing it after completion.

### Strict generation rules

When strict mode is enabled, every generation stage must respect the selected block policy.

The mod must prevent disabled vanilla or modded features from introducing unexpected blocks.

Structures and feature exceptions must be explicitly configured.

The player should be warned if enabling a feature would introduce blocks other than the selected terrain block.

Gravity-affected blocks require special handling. Terrain should be designed to avoid unsupported formations unless the player deliberately selects an experimental generation option.

Never secretly substitute another block to stabilise terrain.

---

# 6. Layered Block World Designer

Create a visual block-layer configuration system.

Players must be able to define the materials used at different heights and depths.

For example:

- Surface: 5 blocks of grass
- Upper layer: 20 blocks of dirt
- Middle layer: 40 blocks of sandstone
- Deep layer: modded stone
- Bottom layer: optional bedrock

Provide support for fixed depths, absolute world heights, thickness ranges and biome-specific layer profiles.

Allow players to add, remove, reorder, copy and modify layers.

Layers should follow the natural shape of terrain where appropriate rather than automatically producing flat planes.

Include optional noise-based transitions between material layers.

Support both vanilla and modded blocks.

Make the layer editor visual, with a cross-sectional preview of the configured terrain.

---

# 7. Advanced Terrain Generator

Implement a flexible, seed-based terrain-generation system.

The system must support:

- Flat terrain
- Rolling hills
- Natural mountains
- Amplified mountains
- Deep valleys
- Plateaus
- Cliffs
- Canyons
- Basins
- Craters
- Archipelagos
- Floating islands
- Large continents
- Small isolated landmasses
- Custom underground terrain
- Fully empty dimensions

Provide configurable controls for:

- Terrain scale
- Terrain frequency
- Average elevation
- Mountain height
- Mountain spacing
- Mountain steepness
- Ridge intensity
- Erosion
- Valley depth
- Plateau frequency
- Terrain smoothness
- Continental separation
- Island frequency
- World height limits where technically supported
- Vertical offsets
- Sea level
- Noise seed and generation seed

Generation must remain consistent across chunk boundaries.

Avoid visible seams, repeating artefacts and sudden elevation discontinuities unless intentionally configured.

Use deterministic algorithms so the same saved configuration and seed produce consistent terrain.

Generation performance must remain suitable for normal Minecraft gameplay and dedicated servers.

---

# 8. Block Selection and Modded Blocks

Implement a complete block registry browser.

Players must be able to select blocks from Minecraft and installed mods.

The block browser must support:

- Search by display name
- Search by registry ID
- Filter by originating mod
- Block preview icons
- Favourite blocks
- Recently selected blocks
- Block-state configuration
- Compatibility warnings

Support applicable block-state properties such as orientation, axis, facing and variants.

Detect blocks that require block entities, supporting blocks or special placement conditions.

Do not allow unsafe world-generation configurations to silently corrupt chunks.

For blocks that cannot safely be used as terrain, provide explanations and compatible alternatives.

Missing modded blocks in previously saved configurations must produce a dependency error, not automatic replacement with stone, dirt or air.

---

# 9. Biome Manager

Every dimension must have its own configurable biome distribution.

Players should be able to:

- Enable or disable individual biomes
- Use one biome throughout a dimension
- Select several allowed biomes
- Configure biome distribution
- Adjust biome scale
- Control biome transitions
- Configure biome-specific terrain
- Change permitted features by biome
- Assign custom block palettes to biomes
- Include compatible modded biomes

Provide selectable biome profiles and region-based generation rules.

Where biome behaviour belongs to another mod, preserve unsupported mechanics rather than forcibly replacing them.

Include a visual biome distribution preview.

Biome edits must integrate correctly with terrain, decorations, structures and environmental behaviour.

---

# 10. Feature and Decoration Manager

Every feature must be independently configurable wherever compatible registration and generation APIs permit.

Do not provide only one universal on/off switch.

Create a searchable feature browser grouped by category.

Examples include:

**Vegetation**

- Oak trees
- Birch trees
- Spruce trees
- Jungle trees
- Cherry trees
- Mangrove trees
- Mushrooms
- Flowers
- Grass
- Bushes
- Modded trees and plants

**Terrain Decorations**

- Boulders
- Patches of sand or gravel
- Ice formations
- Springs
- Lava pools
- Lakes
- Rivers
- Underground decorations
- Modded features

**Underground Generation**

- Caves
- Ravines
- Caverns
- Ore veins
- Geodes
- Underground water
- Underground lava
- Modded underground features

For each supported feature, provide:

- Enabled/disabled state
- Allowed dimensions
- Allowed biomes
- Frequency
- Height limits
- Placement restrictions
- Rarity
- Minimum spacing where appropriate
- Block replacement conditions
- Priority and conflict rules

All feature settings must be saved as part of the dimension's world-generation configuration.

---

# 11. Ore Generation Manager

Players must have individual control over vanilla and modded ore generation.

Discover registered ore-related generation features where possible.

The Ore Manager should support:

- Enable or disable individual ores
- Configure veins per region or chunk
- Adjust vein sizes
- Set generation height ranges
- Select permitted dimensions
- Select permitted biomes
- Control replacement targets
- Create custom ore distribution rules
- Disable all ores with one action

Do not confuse ore blocks with ore generation features.

Support modded ores through compatible generation registries and public integration adapters.

If an ore is generated through an unsupported third-party mechanism, disclose the limitation.

For strict single-block mode, all ore generation must remain disabled unless explicitly permitted.

---

# 12. Structure Manager

Build a complete structure management system.

Players must be able to enable, disable, add, remove and configure compatible structures independently for each dimension.

Include support for applicable vanilla structures such as:

- Villages
- Strongholds
- End Cities
- Nether Fortresses
- Bastions
- Desert Temples
- Jungle Temples
- Ocean Monuments
- Woodland Mansions
- Ancient Cities
- Mineshafts
- Trial Chambers
- Pillager Outposts
- Shipwrecks
- Ruined Portals
- Igloos
- Swamp Huts

Automatically include compatible registered structures from other mods.

### Structure placement configuration

For each structure, expose supported settings such as:

- Allowed dimensions
- Allowed biomes
- Generation frequency
- Placement spacing
- Separation distance
- Height restrictions
- Terrain suitability
- Underground or surface placement
- Floating placement
- Rotation and orientation
- Structure grouping
- Exclusion zones
- Structure collision behaviour

### Cross-dimension structure placement

Allow players to configure supported structures in dimensions where they would not normally generate.

For example:

- End Cities in the Overworld
- Villages in the End
- Nether Fortresses in custom void dimensions
- Custom structures inside modded dimensions

This must be implemented through valid generation or template-placement logic.

When a vanilla structure's original algorithm is incompatible with the destination dimension, use a supported placement adapter or clearly report that it cannot be transferred directly.

Do not silently create broken structures.

---

# 13. Custom Structure Builder

Create an integrated system allowing players and modpack developers to capture their own Minecraft builds and use them as generation templates.

The user must be able to enter Creative mode, build a structure and save it through World Architect.

Provide a structure selection tool with two-position selection, adjustable boundaries and visible selection outlines.

Support:

- Structure capture
- Structure naming
- Preview generation
- Saving and loading
- Rotation
- Mirroring
- Placement offsets
- Terrain adaptation
- Block palettes
- Structure integrity
- Placement conditions
- Collision detection
- Template versioning
- Structure metadata
- Spawn markers
- Optional protection regions

Preserve applicable block states and block-entity data safely.

Avoid importing player-specific inventories, sensitive information or unsafe data unless the creator explicitly intends to include supported content.

### Structure formats

Native Minecraft structure NBT templates must be supported.

Provide an extensible importer for compatible schematic formats, with dedicated adapters where needed.

Validate imported templates and report unsupported blocks or missing mods.

### Structure placement modes

Custom builds must support:

1. Fixed world coordinates
2. Relative coordinates
3. Random natural generation
4. Selected biome generation
5. Selected dimension generation
6. Floating placement
7. Underground placement
8. Starting structure placement
9. Manual placement by an authorised player
10. Modular multi-piece placement where supported

The system must ensure that structures do not unexpectedly overwrite other structures, protected regions or player builds.

---

# 14. Structure Library

Develop a dedicated Structure Library GUI.

The library should show saved builds with names, previews, sizes, required mods and placement settings.

Provide categories such as:

- Starting Islands
- Houses
- Villages
- Castles
- Dungeons
- Towers
- Fortresses
- Floating Islands
- Custom Structures

Allow structures to be duplicated, renamed, exported, imported and assigned to generation profiles.

Modpack developers must be able to package custom structures with their modpacks.

Each packaged template should have a stable identifier and a version or content hash.

Existing worlds must retain their original structure definitions even when the modpack developer updates the source templates.

---

# 15. Void World Generator

Implement a true void-world generation mode for every compatible dimension.

When a player selects Pure Void, the world must contain no naturally generated terrain.

There must be no automatic bedrock platform, hidden island, stone layer, water, lava or other terrain.

Void mode must support optional additional features, including:

- Starting platforms
- Custom imported structures
- Floating islands
- Procedural structures
- Skyblock islands
- Custom spawn platforms
- Modded structure generation
- Isolated settlements
- Manually placed worlds or regions

Everything must be optional.

A true empty void must remain completely empty if that is what the user requests.

Provide safe-spawn validation. If no safe spawn location exists, warn the creator and require an explicit choice between providing a platform or accepting the unsafe configuration.

Do not automatically add blocks to a strict empty void.

---

# 16. Skyblock World Designer

Create a dedicated Skyblock Designer built on top of the void-generation system.

Support several Skyblock styles:

**Classic Skyblock**

A small floating island containing configurable starting resources.

**Custom Skyblock**

A floating island constructed from a saved structure template.

**Advanced Skyblock**

Multiple floating islands, custom terrain layouts and optional structures.

**Multiplayer Skyblock**

Individual or team islands with controlled allocation and spacing.

**One Block Challenge**

An optional supported game mode with a single regenerating progression block.

**Modded Skyblock**

Use vanilla and modded blocks, inventories, structures and progression resources.

### Island configuration

Allow the player to control:

- Island size
- Island shape
- Island height
- Island block palette
- Trees and vegetation
- Starting inventories
- Starting containers
- Starting structures
- Distance between islands
- Island count
- Island generation patterns
- Spawn markers
- Multiplayer allocation
- Void boundaries where applicable

Skyblock must not depend on hardcoded starter islands.

Custom build templates must be usable as complete starting islands.

---

# 17. Spawn Point Manager

Build a comprehensive spawn system.

Players and modpack developers must be able to define exact spawn points, particularly for void and Skyblock worlds.

### Supported spawn modes

- Fixed coordinates
- Spawn inside a selected custom structure
- Spawn on a generated Skyblock island
- Spawn at a designated structure marker
- Random spawn among approved structures
- Team-specific spawn
- Individual player island spawn
- Dimension-specific entry location

### Structure spawn markers

The Structure Builder must allow creators to mark a precise location inside a saved build.

Store the marker relative to the structure's origin.

When the structure is rotated, mirrored or placed at a new position, calculate the correct final spawn coordinates and facing direction.

Support separate markers for:

- Initial world spawn
- Player first join
- Respawn
- Team spawn
- Portal arrival
- Admin-defined spawn
- Optional fallback spawn

### Multiplayer requirements

Develop a server-authoritative system for assigning starting islands and spawn points.

Ensure that players joining simultaneously cannot accidentally receive overlapping islands.

Persist island ownership and allocation data in the world save.

Allow authorised server administrators to manage assignments.

Do not reset existing player assignments during mod updates.

---

# 18. Environment and Dimension Rules

Add an Environment Designer for each dimension.

Expose configuration for supported properties such as:

- Day/night behaviour
- Weather
- Precipitation
- Fog
- Ambient lighting
- Sky appearance
- Cloud behaviour
- Temperature-related rules
- Fluid behaviour
- Mob spawning rules
- Environmental hazards
- Portal arrival and exit behaviour

Support additional mechanics such as custom gravity only through an implementation or compatible mod integration that actually provides those mechanics.

Do not assume world generation alone can modify every dimension behaviour.

Maintain a capabilities model so unsupported environmental settings are not presented as functional controls.

---

# 19. World Generation Rule Engine

Develop a data-driven rule engine for advanced world creation.

Rules should allow creators to combine conditions and actions without writing Java.

Example:

**Condition:**

Dimension is `minecraft:overworld`.

**Additional condition:**

World generation mode is Void.

**Action:**

Generate custom floating islands.

**Additional action:**

Place a saved city structure on selected islands.

**Spawn rule:**

Spawn the player inside the designated starting city.

Provide a GUI for creating, modifying, ordering and validating rules.

Supported conditions should include:

- Dimension
- Biome
- Height range
- Terrain type
- Position or region
- Distance from world spawn
- Seed-based probability
- Structure availability
- Mod or block availability

Supported actions should include:

- Choose terrain blocks
- Generate a structure
- Prevent generation
- Place decorations
- Select a block palette
- Override permitted structure placement
- Apply a spawn rule

Rules must be deterministic and safe to evaluate during world generation.

Provide limits and validation to prevent recursive placement, conflicting instructions and runaway generation workloads.

---

# 20. World Preview System

Players should be able to preview their world before generating it.

Begin with a functional top-down terrain preview that uses the same seed and generation configuration as the real world.

Add support for:

- Terrain height maps
- Biome maps
- Structure placement indicators
- Void and island layouts
- Seed regeneration
- Zooming
- Region selection
- Generation warnings

Develop an optional 3D preview with camera rotation and visual block representation once the core generator is reliable.

The preview must not be a decorative image unrelated to actual generation settings.

Preview calculations should have bounded memory and CPU usage.

---

# 21. World Preset System

Allow players to save complete world configurations as reusable presets.

Presets must support:

- Naming
- Description
- Author metadata
- Version number
- Export
- Import
- Duplication
- Editing
- Validation
- Dependency checks

Include example presets:

**Pure Sand**

Natural terrain made only from sand, with all unwanted features disabled.

**Diamond Mountains**

Amplified terrain made entirely from diamond blocks.

**Empty Void**

No terrain, structures or generated blocks.

**Classic Skyblock**

A void world with a starter island.

**Floating Kingdoms**

Floating islands populated with configurable structures.

**Custom Modded World**

Terrain and structures built from blocks supplied by installed mods.

Store presets in a documented, versioned format.

Use JSON where appropriate, alongside native structure templates and supporting assets.

---

# 22. Modpack Developer Mode

This is a major requirement.

World Architect must include a dedicated Modpack Developer Mode for creating controlled world-generation experiences.

Modpack developers should be able to create a complete world profile, package custom structures and choose which settings players may modify.

Provide a separate developer interface with tools to:

- Create modpack presets
- Import custom structures
- Set mandatory starting structures
- Define spawn positions
- Configure dimensions
- Lock world-generation modes
- Lock selected features
- Lock structures
- Lock dimension settings
- Lock spawn configuration
- Specify player permissions
- Export a distribution-ready preset bundle

### Modpack locking modes

**Unlocked**

Players can customise all supported settings.

**Partially Locked**

Players can change explicitly permitted settings while mandatory settings remain fixed.

**Fully Locked**

The modpack developer's world-generation profile is mandatory.

Players may only change settings the developer has explicitly allowed, such as world name or permitted gameplay rules.

### Example: Locked Skyblock Modpack

A developer creates a custom island, saves it as a template, configures a completely empty Overworld and assigns a spawn marker inside the island.

They then lock the profile.

When a player installs the modpack and creates a new world:

- Skyblock is already selected.
- The creator's custom island is automatically placed.
- The starting spawn is assigned correctly.
- The Overworld remains void outside the intended generation.
- The player cannot change locked world-generation settings.
- Other dimensions generate using the developer's saved configurations.

The system must not silently ignore developer locks.

Dedicated servers must enforce relevant configuration restrictions on the server, not just through disabled GUI controls.

Local single-player configurations cannot be made completely tamper-proof against players who control their own installations. Implement practical modpack restrictions without claiming absolute protection.

---

# 23. Structure Protection

Structure placement locks and gameplay protection are separate features.

Modpack developers must be able to make a structure mandatory without preventing players from modifying it during normal gameplay.

Add optional protection settings:

- Protect selected regions from block breaking
- Protect against block placement
- Protect important spawn structures
- Restrict access to designated containers
- Allow operator bypass
- Allow configurable player permissions
- Disable protection after first join if configured

All protection must be optional and server-authoritative.

Skyblock modpacks must still be able to let players destroy or rebuild their starting islands.

---

# 24. Permanent Save and World Protection

**This is the most important safety requirement in the entire project.**

World Architect updates and modpack updates must never automatically overwrite, reset, regenerate or alter existing worlds.

An existing world's generation profile must be treated as an immutable historical snapshot.

New versions of the mod may introduce additional capabilities, new presets and revised defaults, but those changes must only affect newly created worlds.

### Save isolation

Every world must store its own independent configuration.

This must include:

- Original world type
- World-generation seed
- Generator version
- Terrain rules
- Block palettes
- Biome configuration
- Dimension configurations
- Structure rules
- Custom starting builds
- Custom structure templates
- Spawn markers
- Island allocations
- Feature settings
- Modpack locks
- Dependency information
- Configuration schema version

Store complete world-specific data rather than references to mutable global preset files.

Custom structure templates required by a world should be copied into the save's private data area or stored as immutable content-addressed assets.

### Updates must not alter old worlds

If a modpack developer updates a starting Skyblock island, existing worlds keep their original island template.

If a developer changes a dimension from normal generation to void, existing worlds retain their original dimension configuration.

If new ores or structures are added to the modpack, old worlds must not automatically begin generating them.

This protection also applies to unexplored chunks.

An existing world must continue using the generation rules and compatible generator implementation associated with the version under which it was created.

Do not silently use the updated global preset.

### Compatibility handling

If an updated game or mod no longer supports an old world's generator, blocks or dimension dependencies:

1. Detect the incompatibility before normal world loading.
2. Explain what is missing or incompatible.
3. Do not substitute materials or generation rules.
4. Do not regenerate or delete affected chunks.
5. Do not rewrite the old configuration.
6. Prevent unsafe loading or new chunk generation.
7. Offer guidance for restoring the compatible environment.

Where practical, retain versioned generator implementations to support existing worlds.

However, never claim exact cross-version generation compatibility without testing it.

Do not introduce automatic migrations.

If a future maintenance tool supports an explicit, owner-authorised migration, it must operate on a verified backup or separate world copy, never silently modify the original.

Normal gameplay save writes are allowed. The restriction applies to update-driven changes to world configuration, world generation and existing content.

### Automated save-protection tests

Create tests that:

- Generate an example world.
- Save its configuration and structures.
- Change the global preset.
- Simulate a modpack update.
- Reload the original world.
- Verify its configuration remains unchanged.
- Generate previously unexplored chunks.
- Confirm original rules are still used.
- Verify the mod has not replaced old templates or blocks.

Use checksums and version identifiers where useful.

Save protection is not optional.

---

# 25. Dedicated Server and Multiplayer Support

World Architect must support both integrated single-player servers and dedicated NeoForge servers.

World generation must run authoritatively on the server.

Clients should receive only the configuration and state required to display appropriate interfaces and previews.

Implement proper networking and permission validation.

Support multiplayer Skyblock island allocation and dimension-specific spawn behaviour.

Avoid client-only classes in common or dedicated-server code.

Test server startup, new world creation, dimension loading, multiplayer joins and world reopening.

No interface feature may become a substitute for server-side validation.

---

# 26. Public API and Mod Compatibility

Create a documented public API for other Minecraft mod developers.

The API should allow compatible third-party mods to register:

- Custom world preset providers
- Dimension adapters
- Structure placement adapters
- Generation feature controls
- Block compatibility checks
- Spawn strategies
- Custom environment settings
- Additional editor panels where appropriate

Use stable interfaces and versioned data formats.

Detect unsupported or conflicting generation modifications.

Avoid hard dependencies on optional dimension or world-generation mods.

The mod should load cleanly when optional integrations are absent.

Provide useful diagnostic information for pack developers.

---

# 27. In-Game GUI and Visual Design

All interfaces should look like a professionally developed Minecraft mod.

Use a consistent visual identity while retaining familiar Minecraft menu behaviour.

Requirements include:

- Clear typography
- High-resolution UI artwork
- Consistent icons
- Logical menu navigation
- Searchable configuration panels
- Responsive layouts
- Scrolling for large registries
- Keyboard navigation where practical
- Mouse tooltips
- Accessible text contrast
- Working back and cancel buttons
- Proper GUI scaling
- No text clipping
- No overlapping controls
- No placeholder controls presented as functional

### Texture and artwork requirements

All original block-style textures and pixel-art icons created specifically for World Architect must use professionally finished **64×64 assets** unless another size is technically necessary.

GUI panels and scalable artwork should use suitable high-resolution textures or rendering methods.

Do not upscale poor-quality 16×16 artwork and present it as professional 64×64 work.

Use consistent pixel-art style, deliberate shading and visually coherent materials.

Use the actual registered item or block appearance when previewing vanilla and modded blocks.

Do not unnecessarily create replacement textures for blocks owned by other mods.

All original graphics must be licensed appropriately for distribution.

---

# 28. Documentation and In-Game Help

Provide an accessible in-game guide or Help section.

Explain:

- How to create a custom world
- How to configure dimensions
- How to make a single-block world
- How to create a void world
- How to build a Skyblock preset
- How to capture custom structures
- How to assign spawn markers
- How to import structures
- How to save presets
- How modpack locking works
- How old saves are protected
- How compatibility warnings are handled

Include screenshots or illustrations where useful.

The documentation should also explain which settings require a new world and which features are unavailable for particular third-party dimensions.

Provide developer documentation for the public API and preset bundle format.

---

# 29. Recommended Source Architecture

Use a modular project architecture similar to:

```text
worldarchitect/
├── build.gradle
├── gradle.properties
├── settings.gradle
├── README.md
├── docs/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── .../worldarchitect/
│   │   │       ├── WorldArchitect.java
│   │   │       ├── api/
│   │   │       ├── config/
│   │   │       ├── registry/
│   │   │       ├── worldgen/
│   │   │       │   ├── terrain/
│   │   │       │   ├── palette/
│   │   │       │   ├── biome/
│   │   │       │   ├── feature/
│   │   │       │   ├── structure/
│   │   │       │   ├── voidworld/
│   │   │       │   └── skyblock/
│   │   │       ├── dimension/
│   │   │       ├── spawning/
│   │   │       ├── structures/
│   │   │       ├── presets/
│   │   │       ├── modpack/
│   │   │       ├── protection/
│   │   │       ├── compatibility/
│   │   │       ├── networking/
│   │   │       ├── client/
│   │   │       │   ├── screens/
│   │   │       │   ├── widgets/
│   │   │       │   ├── preview/
│   │   │       │   └── rendering/
│   │   │       └── commands/
│   │   └── resources/
│   │       ├── assets/worldarchitect/
│   │       ├── data/worldarchitect/
│   │       └── META-INF/
│   ├── test/
│   └── gametest/
└── examples/
    ├── pure-sand/
    ├── locked-skyblock/
    ├── custom-structures/
    └── multi-dimension/
```

Adjust the precise layout to current NeoForge project conventions.

Do not create unused classes just to match this directory tree.

Each module must have a clear responsibility and appropriate tests.

---

# 30. Development Milestones

Implement the project in the following order.

## Milestone 1 — Working Foundation

Create the NeoForge mod project.

Implement registration, configuration serialization, basic presets and initial world creation integration.

Deliver a working Single Block World generator.

Acceptance test:

Create a world consisting entirely of sand terrain, with natural hills, and no water, lava, ores, caves, trees, structures or bedrock.

Confirm that newly generated chunks follow the configuration.

Do not proceed until the build works.

## Milestone 2 — Terrain and Block Systems

Implement:

- Terrain presets
- Custom terrain parameters
- Layered block generation
- Modded block selection
- Strict generation restrictions
- Seed consistency tests

Test multiple chunk boundaries and negative coordinates.

## Milestone 3 — Universal Dimensions

Implement dynamic dimension discovery and per-dimension configuration.

Demonstrate independently configured Overworld, Nether and End generation.

Introduce the capability and adapter model for modded dimensions.

Test compatibility and graceful failure for unsupported features.

## Milestone 4 — Features and Structures

Implement biome controls, ores, decorations, caves, fluid settings and structure configuration.

Add supported cross-dimension structure placement.

Test strict filtering and generation rules.

## Milestone 5 — Void and Skyblock

Implement true void dimensions and custom starting islands.

Add structure-based spawning, safe-spawn validation and multiplayer island allocation.

Verify that pure void mode contains no unwanted terrain.

## Milestone 6 — Structure Library

Implement structure selection, capture, saved templates, importing, previews, placement rules and spawn markers.

Verify that imported structures retain appropriate block states and required metadata.

## Milestone 7 — Modpack Developer Tools

Implement preset packaging, developer configuration, partial and full locks, structure bundling and server enforcement.

Test installation of a locked Skyblock preset in a fresh modpack.

## Milestone 8 — Permanent Save Protection

Implement immutable world-specific configuration snapshots, template preservation, compatibility validation and regression tests.

Verify updates cannot silently change old worlds or their unexplored chunks.

## Milestone 9 — Advanced GUI and Preview

Complete the interfaces, visual block browser, previews, rule editor and professional artwork.

Verify every control connects to working functionality.

## Milestone 10 — Final Integration and Release

Perform compatibility, performance, multiplayer, save-protection and gameplay testing.

Resolve critical issues.

Produce a working release JAR, source code, documentation and example presets.

---

# 31. Required Test Scenarios

The completed mod must pass at least these practical tests.

**Test A — Pure Sand Overworld**

Natural hills and mountains made entirely from sand. No other generated solid terrain, ores, structures or fluids.

**Test B — Modded Block Terrain**

Generate a world from a compatible block supplied by a separate installed mod.

**Test C — Fully Empty Void**

Create an entirely empty Overworld without any automatic platform or terrain.

**Test D — Custom Skyblock**

Import a custom island structure, place it in the void and spawn the player at its saved spawn marker.

**Test E — Custom Nether**

Configure the Nether independently of the Overworld and verify its generation follows its selected profile.

**Test F — Custom End**

Create a customised End with specifically selected terrain and structure rules.

**Test G — Cross-Dimension Structures**

Place a supported End City or adapted structure in a configured non-End dimension without breaking generation.

**Test H — Modded Dimension**

Detect an installed modded dimension, apply supported configuration changes and preserve any unsupported original behaviour.

**Test I — Locked Modpack**

Install a modpack preset with a mandatory Skyblock world and custom starting island. Verify the player cannot change locked settings through normal controls.

**Test J — Existing Save Protection**

Generate a world, update the modpack preset and reopen the save. Verify original structures, spawn points and generation settings remain unchanged, including in newly explored chunks.

**Test K — Multiplayer**

Create a multiplayer Skyblock world and verify that player island assignments remain stable across restarts.

**Test L — Missing Dependencies**

Remove a required modded block or structure dependency in a test environment. Confirm World Architect reports the incompatibility without silently replacing world content.

**Test M — Performance**

Measure chunk-generation time and memory use across normal, void, single-block, layered and structure-heavy worlds.

**Test N — GUI Reliability**

Verify that world creation, configuration editing, cancellation, import/export and preset locking work at different GUI scales.

---

# 32. Completion Requirements

Do not mark a milestone complete unless its functionality is implemented and tested.

Every release should compile using the verified Gradle configuration.

Run available automated tests and appropriate game tests.

Use real test worlds to verify important generation behaviour.

Avoid leaving placeholder implementations that appear complete in the GUI but do nothing.

Do not knowingly introduce incompatibilities with world saves from previous World Architect versions.

If a feature is not yet implemented, label it accurately in the development status rather than silently omitting it.

Maintain a development changelog that records:

- Completed functionality
- Test results
- Known limitations
- Compatibility status
- Save-format changes
- Remaining work

Provide accurate build and launch instructions.

Do not claim a feature has been tested unless the tests have actually been performed.

---

# 33. Credits and Project Identity

Use these credits consistently in mod metadata, documentation and appropriate in-game information screens.

**Mod Name:** World Architect

**Author:** GWSHERIDAN  
**Original Creator:** GWSHERIDAN  
**Developer Credit:** GWSHERIDAN  

Use the package namespace and mod ID consistently throughout the project.

Do not describe the project as a fork or recreation of another world-generation mod.

World Architect must be an original implementation with its own systems, interface design and source architecture.

---

# 34. Final Development Instructions

Begin implementing World Architect immediately in the existing repository, if one is supplied, or establish the new NeoForge project if the repository is empty.

First inspect the repository and verify the development environment.

Then establish the architecture for immutable world configurations, dimension-independent generation profiles and the core chunk generator.

Prioritise a fully working pure-sand world-generation test, since this establishes the foundation for single-block, layered, void and custom terrain generation.

Continue through the milestones in dependency order.

At the end of each milestone, compile the project, perform the available tests and correct failures before extending the implementation.

Keep the complete feature specification in the project's development documentation so work can continue consistently across multiple development sessions.

Where a requirement is limited by the current Minecraft or NeoForge APIs, investigate a maintainable implementation or compatibility adapter. Document the restriction rather than inventing a non-functional solution.

Above all, preserve these requirements throughout development:

**Every compatible dimension must be individually configurable.**

**Players must be able to create worlds from any suitable vanilla or modded block while retaining natural terrain shapes.**

**Void and Skyblock worlds must support imported custom structures and precise spawn points.**

**Modpack developers must be able to package and lock world types, structures, generation rules and starting locations.**

**Existing worlds must never be automatically altered by updates.**

The finished result should be a complete, reliable Minecraft world-design platform suitable for normal players, advanced creators, multiplayer servers and large custom modpacks.

**Start development with Milestone 1 and continue systematically toward the complete World Architect release.**