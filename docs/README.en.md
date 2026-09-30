# COD Pattern

> Storage paths below were updated for `0.8.3b`. See [map storage and explicit OP migration](map-storage-operations.md) before upgrading an existing save.


[Repository README](../README.md) | [中文文档](README.md) | [Detailed Guide](GUIDE.md) | [Q&A (Chinese)](QANDA.md) | [Changelog](CHANGES.md)

> Release status: Beta. This documentation currently covers `0.6.10b`. Validate in a staging environment before production rollout, and back up the world save, `serverconfig/codpattern/`, and `fpsmatch/` first.

## Overview

COD Pattern is built around **TaCZ + an embedded FPSM-compatible core**, providing a COD-like workflow for:

- Loadout presets and respawn equipment distribution
- In-match weapon refit with attachment preset persistence
- `frontline / teamdeathmatch` maps, rooms, and match flow
- Localized UI and system messages (`zh_cn / zh_tw / en_us / ja_jp`)

The project uses a server-authoritative design. Loadouts, filters, room state, and match phases are decided on the server and synchronized to clients.

## Main Features

### Loadout Management and Equipment Distribution

- Supports create / clone / rename / delete / select operations, up to `10` loadouts per player.
- Each loadout has four fixed slots: `primary / secondary / tactical / lethal`.
- New players automatically receive `3` default loadouts on first login.
- The selected loadout is distributed automatically on respawn.
- Normal auto-distribution only applies to players already joined to a room or match.
- Admins can force distribution with `/cdp distribute [target]`.

### Weapon Selection, Filtering, and Refit

- Slot updates are validated server-side for slot name, item id, NBT, category, and blacklist rules.
- Attachment refit is limited to `primary` / `secondary` and saved back into `attachmentPreset`.
- Attachment blacklist rules apply to candidate listing, installed attachment cleanup, and save-time blocking.
- TaCZ native refit UI is globally disabled and redirected to the COD Pattern backpack flow.

### Rooms, Maps, and Match Flow

- Adds a unified room entry to the pause menu.
- Supports both `frontline` and `teamdeathmatch`.
- Supports map area creation, `INITIAL / DYNAMIC_CANDIDATE` spawn-point setup, dynamic candidate merging, match-end teleport setup, and persistence.
- Room joining is only allowed during the `WAITING` phase.
- Supports ready state, start vote, end vote, phase transitions, and room-list synchronization.
- `teamdeathmatch` includes dynamic respawn candidate merging, looser spawn safety checks, and warnings for missing or unusable match-end teleports.
- Includes kill feed, score display, death cam, respawn invincibility, combat regen, ally/enemy highlights, world-space enemy health bars, and result pages.

### Persistence, Compatibility, and Localization

- Loadouts are stored in `serverconfig/codpattern/backpack_rules/backpack_config.json`
- Weapon filters are stored in `serverconfig/codpattern/backpack_rules/weapon_filter.json`
- TDM config is stored in `serverconfig/codpattern/maps/builtin/rules/config.json`
- Map data is stored under `<world save>/serverconfig/codpattern/maps/`
- Optional integrations: LR Tactical 0.3.0+, Physics Mod, and `tacz-addon 1.1.6`
- Without LR Tactical, COD Pattern disables LR melee, throwable selection, dedicated throwable slots, and default throwable distribution. With it installed, those features are available.
- Bundles `zh_cn / zh_tw / en_us / ja_jp` language resources

## Commands and Entrypoints

### `/cdp`

- `/cdp test`: Prints a test message.
- `/cdp screen`: Opens the backpack UI.
- `/cdp update`: Reloads weapon-filter configuration and syncs backpack and filter data to online players.
- `/cdp distribute [target]`: Forces equipment distribution.
- `/cdp mode debug room|entities|clear_entities|state|areas ...`: Inspects mode state or clears owned entities; provide the room or mode/map arguments required by the subcommand.

### `/cdp map`

- `/cdp map list [type]`: Lists registered types or maps under a type.
- `/cdp map delete <type> <name>`: Submits map deletion. The server ends the match, recovers and removes members and spectators, then archives the map directory and unregisters it after cleanup completes. Pending recovery retains the map; use Map Management to inspect progress, retry, or cancel.
- `/cdp map endtp show <map>`: Shows a map's match-end teleport point.
- `/cdp map endtp set`: Uses the executor's current position and yaw to overwrite the end point of every existing map that supports it.
- `/cdp map migrate check|confirm`: Run `check` to inspect legacy map storage, then `confirm` to approve migration.

### Force End and Map Tools

- `/roomforceend <mode> <map>`: Ends the match and recovers players while retaining room membership. Map Management also provides Force End.
- Create FTL/TDM maps with the Map Creator Tool (`codpattern:map_creator_tool`). Select two corners, then use `Ctrl + right-click` to open its creation screen. Zombies uses the addon's deployment tool.
- Use the Spawn Point Tool (`codpattern:spawn_point_tool`) to view, add, remove, or clear spawn points and areas. Select the map, team, and point or area layer in its screen. Modes with dynamic spawns also support merging candidates.
- Creation, spawn editing, and area editing now use these tools; their former commands are no longer registered.

## Configuration and Directories

### `backpack_rules/backpack_config.json`

- Stores per-player loadouts, selected loadout id, and slot item data.
- Attachment presets are embedded directly on each slot via `attachmentPreset`.

### `backpack_rules/weapon_filter.json`

- Controls weapon categories, blacklists, throwable enablement, and ammo multiplier.
- Main fields:
  - `primaryWeaponTabs`
  - `secondaryWeaponTabs`
  - `blockedItemNamespaces`
  - `blockedWeaponIds`
  - `blockedAttachmentNamespaces`
  - `blockedAttachmentIds`
  - `throwablesEnabled`
  - `ammunitionPerMagazineMultiple`

### `maps/builtin/rules/config.json`

These are the actual default fields in the current code:

| Field | Default | Description |
|---|---:|---|
| `timeLimitSeconds` | `420` | Playing-phase duration in seconds |
| `scoreLimit` | `75` | Kill score cap |
| `invincibilityTicks` | `30` | Respawn invincibility ticks |
| `respawnDelayTicks` | `40` | Respawn delay ticks |
| `warmupTimeTicks` | `400` | Warmup duration ticks |
| `preGameCountdownTicks` | `200` | Pre-game countdown ticks |
| `blackoutStartTicks` | `60` | End-of-countdown blackout ticks |
| `deathCamTicks` | `30` | Death-cam duration ticks |
| `minPlayersToStart` | `1` | Minimum players before start vote |
| `votePercentageToStart` | `60` | Start-vote threshold |
| `votePercentageToEnd` | `75` | End-vote threshold |
| `combatRegenDelayTicks` | `120` | Delay before regen starts after damage |
| `combatRegenHalfHeartsPerSecond` | `5.0` | Half-hearts restored per second |
| `maxTeamDiff` | `1` | Maximum allowed team-size difference for auto join |
| `markerFocusHalfAngleDegrees` | `30.0` | Enemy health-bar focus cone half-angle |
| `markerFocusRequiredTicks` | `20` | Continuous ticks required to trigger the enemy health bar |
| `markerBarMaxDistance` | `96.0` | Maximum enemy health-bar detection distance |
| `markerVisibleGraceTicks` | `3` | Anti-flicker grace ticks for enemy health bars |

### Match Result Export Directories

- `frontline` -> `serverconfig/codpattern/maps/builtin/frontline/records/`
- `teamdeathmatch` -> `serverconfig/codpattern/maps/builtin/teamdeathmatch/records/`

## Documentation

- Full implementation-oriented guide: [GUIDE.md](GUIDE.md)
- Common questions: [QANDA.md](QANDA.md)
- Version history: [CHANGES.md](CHANGES.md)
- Main-mod / Zombies addon build, run, and installation entry points:
  [SPLIT_INSTALLATION_AND_UPGRADE.md](mode-split/physical/SPLIT_INSTALLATION_AND_UPGRADE.md)

## Compatibility and Dependencies

- Minecraft: `1.20.1`
- Forge: `47.4.0+`
- Java: `17`
- Required dependency: TaCZ `1.1.6+`
- Embedded component: FPSM-compatible core, no external `fpsmatch.jar` required

## License

Licensed under **GPL-3.0-only**. See root `LICENSE.txt` for details.
