# COD Pattern

[中文文档](README.md)

* > ****Release Status: Beta. Validate in a staging environment before production deployment, and back up world data/configuration first.***

## Overview

COD Pattern is an add-on mod built for **TaCZ + FPSMatch**, providing a COD-like workflow for:

* Loadout presets and respawn equipment distribution
* In-match weapon refit with attachment presets
* TDM room lifecycle and match flow

The project follows a server-authoritative design with client synchronization to keep multiplayer state consistent.

## Main Features

### 1) Loadout Management and Equipment Distribution

* Supports create/clone/rename/delete/select operations for loadouts, up to `10` per player.
* Each loadout contains four slots: `primary / secondary / tactical / lethal`.
* On respawn, the selected loadout is distributed automatically (normal flow applies to joined room/match players).
* Player loadout/filter data is persisted on server side and synced on login.
* Admin commands can force immediate distribution for all online players or selected targets.

### 2) In-Match Weapon Refit and Attachment Presets

* Supports slot-level editing for primary, secondary, and throwable slots from the loadout UI.
* Uses TaCZ weapon capabilities, with server-side validation and persistence.
* Supports attachment preset storage by `player UUID + loadout id + slot` in `.snbt`.
* Supports result feedback and rollback handling to reduce client/server state drift.

### 3) TDM Room and Match Flow (FPSMatch Integration)

* Adds a Team Deathmatch entry in pause menu for room list, join/leave, and team selection.
* Supports auto team assignment with balance constraints (`maxTeamDiff`).
* Supports ready state, start vote, and end vote with threshold and timeout logic.
* Full phase pipeline: `WAITING -> COUNTDOWN -> WARMUP -> PLAYING -> ENDED`.
* Includes score tracking, respawn delay, invincibility frames, death cam, HUD phase feedback, and match summary.
* Exports JSON match records automatically when a match ends.

### 4) Filtering and Compatibility Features

* Primary/secondary category filtering via `primaryWeaponTabs` and `secondaryWeaponTabs`.
* Gunpack namespace blocking via `blockedItemNamespaces`.
* Throwable and ammo multiplier controls via `throwablesEnabled` and `ammunitionPerMagazineMultiple`.
* Optional integrations for LR Tactical and Physics Mod with graceful fallback when absent.
* Includes compatibility handling for `tacz-addon 1.1.6` in backpack refit flow to prevent unload-button lockups.

## Commands and Entrypoints

### `/cdp` Commands

* `/cdp screen`
  * Opens the backpack/loadout UI (debug entrypoint).
* `/cdp update`
  * Syncs weapon filter and loadout config to all online players (OP required).
* `/cdp distribute [target]`
  * Forces equipment distribution for all online players or selected players (OP required).

### FPSMatch Command Chain

* Use `/fpsm tdm ...` for TDM map and flow management (for example `/fpsm tdm create <mapName>`).
* Legacy `/codtdm` is deprecated.

## Configuration

Server configuration is stored under world save path: `serverconfig/codpattern/`

* `backpackconfig`
  * Player loadout data (JSON, no extension by design).
* `filterconfig`
  * Weapon filter config (JSON, no extension by design).
  * Key fields:
    * `primaryWeaponTabs` / `secondaryWeaponTabs`
    * `blockedItemNamespaces`
    * `throwablesEnabled`
    * `ammunitionPerMagazineMultiple`
* `attachment_preset/`
  * Attachment presets stored as `.snbt` by player UUID and loadout slot.
* `tdmconfig/config.json`
  * TDM runtime parameters (time, score, respawn, voting, join policy, balance policy).
* `tdm_match_records/`
  * Exported match records (`.json`) after each match.

### Default Parameters in `tdmconfig/config.json`

| Field | Default | Description |
|---|---:|---|
| `timeLimitSeconds` | `420` | Match duration in seconds |
| `scoreLimit` | `75` | Kill score to win |
| `invincibilityTicks` | `10` | Post-respawn invincibility ticks |
| `respawnDelayTicks` | `40` | Respawn delay ticks |
| `warmupTimeTicks` | `400` | Warmup duration ticks |
| `preGameCountdownTicks` | `200` | Pre-game countdown ticks |
| `blackoutStartTicks` | `60` | Countdown blackout ticks |
| `deathCamTicks` | `30` | Death cam duration ticks |
| `minPlayersToStart` | `1` | Minimum players required to start |
| `votePercentageToStart` | `60` | Start vote pass threshold (%) |
| `votePercentageToEnd` | `75` | End vote pass threshold (%) |
| `allowJoinDuringPlaying` | `true` | Allow joining during active match |
| `joinAsSpectatorWhenPlaying` | `true` | Join as spectator during active match |
| `maxTeamDiff` | `1` | Max allowed team size difference |
| `markerFocusHalfAngleDegrees` | `30.0` | Enemy bar focus cone half-angle (degrees) |
| `markerFocusRequiredTicks` | `20` | Ticks required in focus cone before enemy bar appears |
| `markerBarMaxDistance` | `96.0` | Max distance for enemy bar detection (blocks) |
| `markerVisibleGraceTicks` | `3` | Anti-flicker grace ticks while enemy remains visible |

## Compatibility and Dependencies

* **Minecraft:** `1.20.1`
* **Forge:** `47.4.0+`
* **Java:** `17`
* **Required Dependencies:**
  * TaCZ `1.1.6+`
  * FPSMatch `1.2.5+`
* **Optional Integrations:**
  * LR Tactical (throwable/melee content path)
  * Physics Mod (ragdoll/retained death entity presentation)
  * tacz-addon `1.1.6` (backpack refit unload flow compatibility included)

## Deployment Notes

* Non-forced distribution applies only to players joined in room/match flow.
* If no match-end teleport point is configured, end phase warns but does not auto-teleport back.
* If `tacz-addon` is enabled and attachment unload behaves abnormally in refit UI, ensure `/gamerule liberateAttachment false`.
* Before production rollout, verify:
  * `tdmconfig/config.json` values match your server pacing
  * `filterconfig` matches your gunpack filtering policy
  * maps include team spawn points and a match-end teleport point

## Issue Reporting

When reporting issues, include:

* Reproduction steps
* Relevant log excerpts
* Full mod list and versions

## Changelog

Current version: `v0.4.5b-fix`  
See `CHANGES.md` for detailed history.

## License

Licensed under **GPLv3**. See `LICENSE.txt` for details.

## Author

* **Author:** popura404
* **Contact:** `gzyoung2330351551@163.com`
