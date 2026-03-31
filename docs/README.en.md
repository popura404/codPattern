# COD Pattern

[Repository README](../README.md) | [中文文档](README.md) | [Detailed Guide](GUIDE.md) | [Q&A (Chinese)](QANDA.md) | [Changelog](CHANGES.md)

> Release status: Beta. Validate in a staging environment before production rollout, and back up the world save, `serverconfig/codpattern/`, and `fpsmatch/` first.

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
- Supports map area creation, spawn-point setup, match-end teleport setup, and persistence.
- Room joining is only allowed during the `WAITING` phase.
- Supports ready state, start vote, end vote, phase transitions, and room-list synchronization.
- `teamdeathmatch` includes dynamic respawn candidates and spawn safety validation.
- Includes kill feed, score display, death cam, respawn invincibility, combat regen, ally/enemy highlights, enemy health bars, and result pages.

### Persistence, Compatibility, and Localization

- Loadouts are stored in `serverconfig/codpattern/backpack_rules/backpack_config.json`
- Weapon filters are stored in `serverconfig/codpattern/backpack_rules/weapon_filter.json`
- TDM config is stored in `serverconfig/codpattern/tdm_rules/config.json`
- Map data is stored under `<game dir>/fpsmatch/<world name>/...`
- Supports LR Tactical, Physics Mod, and `tacz-addon 1.1.6`
- Bundles `zh_cn / zh_tw / en_us / ja_jp` language resources

## Commands and Entrypoints

### `/cdp`

- `/cdp screen`
  - Opens the backpack UI, mainly as a debug entrypoint.
- `/cdp update`
  - Reloads `weapon_filter.json` and syncs weapon-filter plus loadout data to all online players.
- `/cdp distribute [target]`
  - Forces equipment distribution.

### `/cdp map`

- `/cdp map list [type]`
  - Lists registered types or maps under a type.
- `/cdp map create <frontline|teamdeathmatch> <name> <from> <to>`
  - Creates a map area and persists it immediately.
- `/cdp map delete <type> <name>`
  - Deletes the map and its persisted data.
- `/cdp map spawn <list|add|remove|clear> ...`
  - Manages `INITIAL` / `DYNAMIC_CANDIDATE` spawn points.
- `/cdp map endtp <show|set|clear> <map> [pos]`
  - Manages the match-end teleport point.

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

### `tdm_rules/config.json`

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

- `frontline` -> `serverconfig/codpattern/tdm_match_records/`
- `teamdeathmatch` -> `serverconfig/codpattern/tactical_tdm_match_records/`

## Documentation

- Full implementation-oriented guide: [GUIDE.md](GUIDE.md)
- Common questions: [QANDA.md](QANDA.md)
- Version history: [CHANGES.md](CHANGES.md)

## Compatibility and Dependencies

- Minecraft: `1.20.1`
- Forge: `47.4.0+`
- Java: `17`
- Required dependency: TaCZ `1.1.6+`
- Embedded component: FPSM-compatible core, no external `fpsmatch.jar` required

## License

Licensed under **GPL-3.0-only**. See root `LICENSE.txt` for details.
