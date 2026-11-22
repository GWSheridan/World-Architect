# Administration panel

Use `/wa_admin` or `/worldsunwritten island panel` while connected to the server. Ordinary players see their own island information and a **Home** button. Operator level 3 opens the administration controls. Other players' assignments and server settings are excluded from ordinary-player views.

The panel uses the existing server commands and data files. Every change is staged first. Review the exact action and choose **Confirm change**. The server checks your current permissions, ownership, team membership and settings again before executing it. Confirmations expire after 60 seconds. If another administrator changes the relevant server state, refresh and stage again.

## Islands and teams

**Islands** lists personal reservations. Reassign a player to an available slot, or swap two existing assignments. Reassignment changes ownership; it does not move terrain, buildings or inventories. Online names and saved UUIDs are supported.

**Teams** manages scoreboard team creation, online membership and separate team-island reservations. Joining a team does not release a personal reservation. Configure a team spawn policy when team-based Home behaviour is desired. Membership controls require online names; they do not accept selectors.

## Spawning

**Spawns** sets or clears administrative overrides for initial, respawn and portal events. Supported kinds are fixed coordinates, personal island, team island, a library marker and a generated module marker. Structure IDs, module names, markers and transformations retain the [spawn policy rules](Multiplayer-and-Islands.md).

Safe destinations, personal respawn precedence, existing saved generation rules and modpack locks still apply. A confirmation cannot bypass those checks. Clearing an override restores the existing fallback behaviour.

## Protection

**Protection** controls optional breaking, placement, container and shared-island protection. **Container types** adds or removes a registered block ID from the protected list. These controls require a world with personal Overworld island allocations.

Changing ownership, teams or protection rechecks open containers. Players who lose access have the menu closed before further interaction. Operator bypasses and double-chest checks retain their existing behaviour. The panel never transfers or clears inventory contents.

Administration settings, scoreboard teams and island reservations persist through restart. They remain separate from immutable generation snapshots. Include all of them in [complete-world backups](Backups-and-Upgrades.md).

The lists show up to 128 reservations or teams per page. Views expire after ten minutes and the server retains at most 32 administrator views. Reopen or refresh the panel when a view expires.
