# End progression test tools

This separate diagnostic mod exercises World Architect 1.0.0 on a disposable Minecraft 26.3 / NeoForge 26.3.0.103 instance. It is not needed for normal play. The commands deliberately change the fight, rewards and selected test regions; use a fresh test world.

Build World Architect first, then run this from the repository root with Java 25:

```powershell
.\gradlew.bat -p examples/end-progression-test build
```

Put the resulting test JAR and `worldarchitect-1.1.0.jar` in a fresh server's `mods/` folder. Install the [End progression example](../end-progression) before creating its world. Use ordinary clients with the production mod and an operator console for commands:

| Command | Check |
| --- | --- |
| `dragon_test entry` | Send connected players through the native End entry destination |
| `dragon_test state` | Print the native controller's saved state and dragon count |
| `dragon_test audit` | Compare arena blocks/biomes and preview queries with native generation; check outside terrain and structure exclusions |
| `dragon_test kill` | Apply player-attributed lethal damage through the native dragon death path |
| `dragon_test travel` | Use a generated gateway and its paired return |
| `dragon_test exit` | Exercise exit-portal respawn destinations |
| `dragon_test ritual` | Add the four crystals and start native respawning |
| `dragon_test abort` | Destroy a ritual crystal to check recovery |
| `dragon_test rewards` | Check one dragon egg and existing XP rewards |
| `dragon_test unsafe` | Check refusal and vertical fallback in a cleared test volume near X/Z 5000 |

Use the supplied purpur example for `audit`. Restart once with a live dragon and again during the crystal ritual. Confirm the dragon UUID persists in the first case and respawning resumes in the second. Repeat a kill and check that gateways advance without another egg.

The client probes are optional. `-Dwa.end.editor=true` runs the new-world editor checks at GUI scales 1 and 2. `-Dwa.rules.probe=<folder>` reads action files in that folder for controlled multiplayer testing; it records observations and supports screenshots. These probes use internal client classes and are tied to this release rather than the public extension API.
