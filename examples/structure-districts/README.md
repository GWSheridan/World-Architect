# Structure districts

Install `bundles/structure-districts-1.7.0.zip` on clients and servers using the bundle selection in `config/worldsunwritten/developer.json`.

The Overworld selects three modular houses. Dark masonry and calcite houses share the `village` grid with weights 3 and 1; a plain andesite `outpost` uses its own grid. Both groups share the `settlements` exclusion family, an eight-chunk minimum origin distance and the `district_land` rectangle. Their native grids use spacing 8 and separation 6. Footings, stair states, supply chest metadata and safe entry markers are captured with each house.

Sites are reserved before terrain eligibility, so failed sites can leave gaps. Weights select one house without falling back to another. The named region covers positive coordinates from block 256 to 8192; explore there or use a bounded Site Inspector search. No district writes are permitted outside that region. Nether and End generation remain native.

The source directory contains the preset, three definitions and manifest. Adjust them in **Features → Structures → Structure districts**. See [Structures](../../docs/wiki/Structures.md) for placement limits and [Site Inspector](../../docs/wiki/Structure-Site-Inspector.md) for previews. New content explicitly uses schema version 2; existing saves keep their original definitions.
