# World Architect 1.5.0

World Architect 1.5 adds polygon terrain regions, explicit structure anchors, water placement, vertical procedural connections and a structure site inspector. It targets Minecraft Java 26.3, NeoForge 26.3.0.103 and Java 25.

- Draw and edit concave region outlines, then reuse them in terrain, biomes and generation rules.
- Anchor assemblies to the surface, seabed or a fixed height. Allow template blocks to replace water only when explicitly selected.
- Build vertical pools with capped shafts, required rooms and a stable root entrance. Templates supply their own stairs or ladders.
- Inspect placement decisions against a saved preset without generating chunks or changing a world.

API 1 remains supported. Polygon profiles opt into terrain version 4; vertical connections opt into pool format 3. Existing profiles, pool formats, omitted anchor settings, saved templates, island assignments and End policies retain their behavior. Use matching 1.5 clients and servers. Older readers cannot open worlds using the new formats.

Back up the complete world before updating, including its archives, snapshots, settings, libraries and provider mods. Release validation and packaging are in progress. Publication is a separate step.
