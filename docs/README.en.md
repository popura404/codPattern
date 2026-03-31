# COD Pattern

[中文文档](README.md)

* > ****Release Status: Beta. Validate in a staging environment before production deployment, and back up world data/configuration first.***

## Overview

COD Pattern is built around **TaCZ + an embedded FPSM-compatible core**, providing a COD-like workflow for:

* Loadout presets and respawn equipment distribution
* In-match weapon refit with attachment presets
* TDM room lifecycle and match flow
* Localized UI and system messages (`zh_cn / zh_tw / en_us / ja_jp`)

The project follows a server-authoritative design with client synchronization to keep multiplayer state consistent.

## Main Features

### 1) Loadout Management and Equipment Distribution

* Supports create/clone/rename/delete/select operations for loadouts, up to `10` per player.
* Each loadout contains four slots: `primary / secondary / tactical / lethal`.
* Loadout-related UIs use a fixed text-scaling baseline across different `GUI Scale` settings to keep readability more consistent.
* Default loadout names, clone suffixes, and equipped notifications are now localized by client language.
* On respawn, the selected loadout is distributed automatically (normal flow applies to joined room/match players).
* Player loadout/filter data is persisted on server side and synced on login.
* Admin commands can force immediate distribution for all online players or selected targets.

### 2) In-Match Weapon Refit and Attachment Presets

* Supports slot-level editing for primary, secondary, and throwable slots from the loadout UI.
* Uses TaCZ weapon capabilities, with server-side validation and persistence.
* Attachment presets are now stored directly inside the loadout config by loadout id and slot.
* Supports result feedback and rollback handling to reduce client/server state drift.

### 3) TDM Room and Match Flow (Embedded FPSM Compatibility Layer)

* Adds a Team Deathmatch entry in pause menu for room list, join/leave, and team selection.
* TDM room screens use the same fixed text-scaling baseline across different `GUI Scale` settings for more consistent list/panel/button readability.
* Supports auto team assignment with balance constraints (`maxTeamDiff`).
* Supports ready state, start vote, and end vote with threshold and timeout logic.
* Full phase pipeline: `WAITING -> COUNTDOWN -> WARMUP -> PLAYING -> ENDED`.
* Includes kill feed, score tracking, respawn delay, invincibility frames, combat regen, death cam, HUD phase feedback, and match summary.
* Exports JSON match records automatically when a match ends.

### 4) Filtering, Compatibility, and Localization

* Primary/secondary category filtering via `primaryWeaponTabs` and `secondaryWeaponTabs`.
* Gunpack namespace blocking and exact weapon blacklist support via `blockedItemNamespaces` / `blockedWeaponIds`.
* Attachment namespace blocking and exact attachment blacklist support via `blockedAttachmentNamespaces` / `blockedAttachmentIds`.
* Throwable and ammo multiplier controls via `throwablesEnabled` and `ammunitionPerMagazineMultiple`.
* Optional integrations for LR Tactical and Physics Mod with graceful fallback when absent.
* Includes compatibility handling for `tacz-addon 1.1.6` in backpack refit flow to prevent unload-button lockups.
* TaCZ native refit UI is globally disabled and redirected to the COD Pattern backpack refit flow.
* Bundles `zh_cn / zh_tw / en_us / ja_jp` language resources for core UI, notices, and error messages.

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

* `backpack_rules/backpack_config.json`
  * Player loadout data (JSON).
  * Attachment presets are now embedded on each loadout slot via the `attachmentPreset` field.
* `backpack_rules/weapon_filter.json`
  * Weapon filter config (JSON).
  * Key fields:
    * `primaryWeaponTabs` / `secondaryWeaponTabs`
    * `blockedItemNamespaces`
    * `blockedWeaponIds` (format: `namespace:gunid`)
    * `blockedAttachmentNamespaces`
    * `blockedAttachmentIds` (format: `namespace:attachmentid`)
    * `throwablesEnabled`
    * `ammunitionPerMagazineMultiple`
* `tdm_rules/config.json`
  * TDM runtime parameters (time, score, respawn, voting, join policy, balance policy).
* `tdm_match_records/`
  * Exported match records (`.json`) after each match.
* Legacy paths `backpackconfig` / `filterconfig` / `attachment_preset/` / `tdmconfig/`
  * Deprecated and no longer read by this version; existing worlds require manual migration.

### Default Parameters in `tdm_rules/config.json`

| Field | Default | Description |
|---|---:|---|
| `timeLimitSeconds` | `420` | Match duration in seconds |
| `scoreLimit` | `75` | Kill score to win |
| `invincibilityTicks` | `30` | Post-respawn invincibility ticks |
| `respawnDelayTicks` | `40` | Respawn delay ticks |
| `warmupTimeTicks` | `400` | Warmup duration ticks |
| `preGameCountdownTicks` | `200` | Pre-game countdown ticks |
| `blackoutStartTicks` | `60` | Countdown blackout ticks |
| `deathCamTicks` | `30` | Death cam duration ticks |
| `minPlayersToStart` | `1` | Minimum players required to start |
| `votePercentageToStart` | `60` | Start vote pass threshold (%) |
| `votePercentageToEnd` | `75` | End vote pass threshold (%) |
| `combatRegenDelayTicks` | `120` | Delay before regen starts after taking damage (ticks) |
| `combatRegenHalfHeartsPerSecond` | `5.0` | Half-hearts restored per second while regenerating |
| `allowJoinDuringPlaying` | `true` | Allow joining during active match |
| `joinAsSpectatorWhenPlaying` | `true` | Join as spectator during active match |
| `maxTeamDiff` | `1` | Max allowed team size difference |
| `markerFocusHalfAngleDegrees` | `30.0` | Enemy health-bar focus cone half-angle (degrees) |
| `markerFocusRequiredTicks` | `20` | Ticks required in the focus cone before the enemy health bar appears |
| `markerBarMaxDistance` | `96.0` | Max distance for enemy health-bar detection (blocks) |
| `markerVisibleGraceTicks` | `3` | Anti-flicker grace ticks while the enemy health bar remains visible |

## Compatibility and Dependencies

* **Minecraft:** `1.20.1`
* **Forge:** `47.4.0+`
* **Java:** `17`
* **Required Dependencies:**
  * TaCZ `1.1.6+`
* **Embedded Component:**
  * FPSM-compatible core (no external `fpsmatch.jar` required)
* **Optional Integrations:**
  * LR Tactical (throwable/melee content path)
  * Physics Mod (ragdoll/retained death entity presentation)
  * tacz-addon `1.1.6` (backpack refit unload flow compatibility included)

## Deployment Notes

* Non-forced distribution applies only to players joined in room/match flow.
* If no match-end teleport point is configured, end phase warns but does not auto-teleport back.
* If `tacz-addon` is enabled and attachment unload behaves abnormally in refit UI, ensure `/gamerule liberateAttachment false`.
* Before production rollout, verify:
  * `tdm_rules/config.json` values match your server pacing
  * `backpack_rules/weapon_filter.json` matches your gunpack filtering policy
  * maps include team spawn points and a match-end teleport point

## Issue Reporting

When reporting issues, include:

* Reproduction steps
* Relevant log excerpts
* Full mod list and versions

## Changelog

Current version: `v0.5.9b`  
See `CHANGES.md` for detailed history.

## License

Licensed under **GPL-3.0-only**. See `LICENSE.txt` for details.

## Author

* **Author:** popura404
* **Contact:** `gzyoung2330351551@163.com`
