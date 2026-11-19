# Troubleshooting

## Missing world type

Check the Minecraft, NeoForge and Java versions and this instance's `mods/` folder. Restart after adding a JAR. Preset bundles belong in `config/worldarchitect/bundles/`.

## A dimension cannot be replaced

Its specialised generator needs a compatible dimension adapter. Keep Original Generation when no replacement is offered. The adapter must support the requested mode.

## A world refuses to load

Read the first compatibility error in the server log. Restore the named dependency, original bundle archive or matching saved file from a full backup. Required adapters, templates, resources and generation files are checked before generation. Deleting the snapshot does not repair a missing dependency.

## Structures do not appear

Check Generate Structures, structure support, allowed biomes, spacing/separation, height and slope. Full modular assemblies must fit their bounds and filters. The native End arena excludes custom structures. Native `/locate structure` can help find valid starts.

## No safe spawn or island space

Check for a safe floor and two air blocks above the marker. Clear obstructions or restore the original template. A full personal-island ledger needs administrator reassignment or a new world with larger capacity, not an edited generation snapshot.

## No dragon in the custom End

The world must have been created with [End progression](End-Progression.md) enabled or use Original Generation. Existing custom End saves and imported presets with no policy keep their previous disabled behaviour. Players must be near the arena for the native controller to run.

## Gateway travel refused

There is no safe arrival within the bounded search. Repair the destination with administrator access and try again. The mod does not clear existing builds to force a destination.

For bug reports, include versions, relevant provider mods, the first error and stack trace, and the smallest reproducing preset. Remove credentials, addresses and private player details from shared logs.
