# COD Pattern

[Repository README](../README.md) | [中文文档](README.md) | [Detailed Guide (Chinese)](GUIDE.md) | [Q&A (Chinese)](QANDA.md) | [Changelog](CHANGES.md)

> Release status: Beta. This document covers `0.8.6b`. Before upgrading an existing world, back up the world save and the game directory's `fpsmatch/` folder. See [GUIDE.md](GUIDE.md) for migration steps.

## Overview

COD Pattern is a Forge mod built around **TaCZ + an embedded FPSM-compatible core**, providing COD-style loadout presets, weapon refit, room management, and team combat.

The main mod independently provides `frontline` (FTL) and `teamdeathmatch` (TDM). Zombies is a separate addon: its gameplay implementation is not bundled with the main mod, and the main mod can start without it. When installed, the addon registers its mode with the shared entry points.

Loadouts, weapon filters, room state, map edits, and match phases are validated on the server and synchronized to clients. The mod includes Simplified Chinese, Traditional Chinese, English, and Japanese language resources.

## Main Features

### Loadout Management and Equipment Distribution

- Create, clone, rename, delete, and select loadouts, with up to `10` per player.
- Players receive `3` default loadouts when their loadout data is first initialized.
- Each loadout stores four equipment categories: `primary / secondary / tactical / lethal`.
- Room equipment distribution and respawn supplies use the selected loadout; individual modes can provide their own distribution rules.
- Administrators can force distribution with `/cdp distribute [target]`. Omitting the target applies it to all online players.
- Without LR Tactical, related melee options, throwable candidates, dedicated throwable slots, and default throwable distribution are disabled automatically. With LR installed, throwables remain subject to the server's filter setting.

### Weapon Selection, Filtering, and Refit

- The server validates equipment slots, item IDs, NBT, weapon categories, and blacklists.
- Primary and secondary weapons support attachment refit. Presets are saved with the loadout in `attachmentPreset`.
- Attachment blacklists apply to candidate lists, installed attachment cleanup, and save validation.
- TaCZ's native refit screen is disabled; use COD Pattern's loadout refit screen instead.
- Attachment candidates combine attachments held by the player with compatible `tacz-addon` candidates. The server makes the final installation decision.

### Rooms and Match Flow

- The pause menu provides a room entry for browsing maps, joining rooms, choosing a team, and readying up.
- FTL/TDM rooms accept joins only during `WAITING`, with start votes, end votes, and synchronized match phases.
- FTL and TDM use their respective respawn rules. TDM supports dynamic candidate spawn points and candidate merging.
- Features include warmup, a pre-game countdown, scores and kill feed, death cam, respawn invincibility, combat regeneration, ally/enemy highlights, world-space enemy health bars, and a results screen.
- Before a start vote, the room checks that an end point is configured. An unusable destination produces a failure message when the teleport is attempted.
- Force End restores players and cleans up mode resources. Rooms with unfinished recovery show the corresponding status.

### Map Creation and Management

- The Map Creator Tool selects a region and creates FTL/TDM maps.
- The Spawn Point Tool shows the point and area layers supported by each mode, with previews, adding, deleting, clearing, and dynamic candidate merging.
- The Map Management Tool provides filtered lists, dimensions and bounds, status, renaming, end-point settings, Force End, and deletion.
- Renaming requires an idle map with its recovery completed. It validates names and conflicts, then updates the map identity and storage directory.
- Deletion first ends the match, restores and removes members and spectators, then archives the map directory and unregisters the map after cleanup. Pending recovery retains the map; its progress, retry, and cancellation are available in Map Management.
- A map's end point, the global default for future maps, and the command that updates existing maps have separate scopes. Changing the global default does not overwrite existing maps.

## Commands and Entry Points

Permission levels below are server command permission levels. In-game map tools require level `2`; end-point settings in Map Management also require level `2`.

### `/cdp`

| Command | Permission | Purpose |
|---|---:|---|
| `/cdp test` | No additional restriction | Shows a test message to the executing player |
| `/cdp screen` | No additional restriction | Opens the executing player's loadout screen |
| `/cdp update` | `2` | Reloads weapon filters and synchronizes filters and loadout data to online players |
| `/cdp distribute [target]` | `2` | Forces loadout distribution; omitting the target applies it to all online players |
| `/cdp mode debug room` | `2` | Shows the executing player's current room |
| `/cdp mode debug entities <room>` | `2` | Inspects entities owned by a room |
| `/cdp mode debug clear_entities <room>` | `2` | Clears entities owned by a room |
| `/cdp mode debug state <room>` | `2` | Inspects mode runtime state; player execution only |
| `/cdp mode debug areas <type> <map>` | `2` | Inspects a map's area layers |

`<room>` uses `mode|map`, passed as a quoted argument, for example `"frontline|arena"`. Use double quotes around map names as well, such as `"训练场"` or `"Training Arena"`, to avoid argument parsing errors with non-ASCII characters, spaces, or special characters. Equipment distribution clears and rebuilds the recipient's inventory; spectators are skipped.

### `/cdp map`

| Command | Permission | Purpose |
|---|---:|---|
| `/cdp map list [type]` | `2` | Lists registered modes or maps in a specified mode |
| `/cdp map delete <type> <map>` | `2` | Submits map deletion; recovery and cleanup progress are shown in Map Management |
| `/cdp map endtp show <map>` | `3` | Shows a map's end point; identical names across modes produce an ambiguity message |
| `/cdp map endtp set` | `3` | Overwrites all supported existing maps' end points with the executor's position, dimension, and horizontal facing |
| `/cdp map migrate check` | `4` | Inspects legacy storage and lists the migration plan and conflicts |
| `/cdp map migrate confirm` | `4` | Inspects again and migrates eligible units; restart afterward |

`endtp set` does not set the global default. Use Map Management to configure a default for future maps.

### Force End and Map Tools

- `/roomforceend <mode> <map>`: Requires permission level `2`; ends a match and restores players while retaining room membership. Map Management also provides this action.
- `codpattern:map_management_tool`: Right-click to open Map Management.
- `codpattern:map_creator_tool`: Left-click and right-click to select the two corners. Use `Ctrl + right-click` to open the creation screen, choose a mode, and enter a map name.
- `codpattern:spawn_point_tool`: Use `Ctrl + right-click` to open its settings, then select the map, team, and available point or area layer.
- Map creation, spawn editing, and area editing use tools; the former commands have been removed. Zombies map deployment uses tools supplied by the addon.

## Configuration and Directories

All paths below are relative to the current world directory, not the game directory.

### `serverconfig/codpattern/backpack_rules/`

- `backpack_config.json`: Player loadouts, the selected loadout, and slot item data. Attachment presets are stored in each slot's `attachmentPreset`.
- `weapon_filter.json`: Weapon categories, item and attachment blacklists, throwable enablement, and the ammo multiplier.

The main filter fields are `primaryWeaponTabs`, `secondaryWeaponTabs`, `blockedItemNamespaces`, `blockedWeaponIds`, `blockedAttachmentNamespaces`, `blockedAttachmentIds`, `throwablesEnabled`, and `ammunitionPerMagazineMultiple`. Use `/cdp update` to reload and synchronize filters after editing. It is not a general configuration reload command, and synchronizes cached loadout data rather than reloading `backpack_config.json` from disk.

### `serverconfig/codpattern/maps/`

| Path | Contents |
|---|---|
| `defaults.json` | Global end-point default for future maps |
| `builtin/rules/config.json` | Shared FTL/TDM match configuration |
| `builtin/frontline/m-<encoded>/map.json` | FTL map definition |
| `builtin/teamdeathmatch/m-<encoded>/map.json` | TDM map definition |
| `builtin/frontline/records/` | FTL match exports |
| `builtin/teamdeathmatch/records/` | TDM match exports |
| `.storage/` | Migration records, management operation records, and archives |

The encoded directory suffix is the hexadecimal representation of the map name's UTF-8 bytes. Use Map Management to rename maps. Addon map and rule directories are registered by their respective modes; not every mode uses `builtin/`.

Legacy `fpsmatch/` maps and `serverconfig/codpattern/tdm_rules/config.json` are transferred through the migration flow. Back up first, then run `check` to inspect sources, destinations, and conflicts. Run `confirm` once affected rooms are empty and their matches have ended. Affected modes remain locked until the server restarts or the single-player world is reopened. See [GUIDE.md](GUIDE.md) for recovery details.

### FTL/TDM Match Configuration

The table lists code defaults for active settings in `builtin/rules/config.json`. Fields ending in `Ticks` use game ticks. The retained `warmupTimeTicks`, `preGameCountdownTicks`, and `blackoutStartTicks` fields do not currently control the corresponding PvP phase timings; see [GUIDE.md](GUIDE.md).

| Field | Default | Description |
|---|---:|---|
| `timeLimitSeconds` | `420` | Playing-phase duration in seconds |
| `scoreLimit` | `75` | Kill score cap |
| `invincibilityTicks` | `30` | Respawn invincibility duration |
| `respawnDelayTicks` | `40` | Respawn delay |
| `deathCamTicks` | `30` | Death-cam duration |
| `minPlayersToStart` | `1` | Minimum players for a start vote; the current default is intended for testing |
| `votePercentageToStart` | `60` | Start-vote percentage threshold |
| `votePercentageToEnd` | `75` | End-vote percentage threshold |
| `combatRegenDelayTicks` | `120` | Delay before regeneration after damage |
| `combatRegenHalfHeartsPerSecond` | `5.0` | Half-hearts restored per second |
| `maxTeamDiff` | `1` | Maximum team-size difference allowed for automatic assignment, explicit joining, and team switching |
| `markerFocusHalfAngleDegrees` | `30.0` | Enemy health-bar detection cone half-angle |
| `markerFocusRequiredTicks` | `20` | Detection duration required to show an enemy health bar |
| `markerBarMaxDistance` | `96.0` | Maximum enemy health-bar detection distance |
| `markerVisibleGraceTicks` | `3` | Enemy health-bar anti-flicker grace period |

## Documentation

- Installation, map setup, matches, map management, migration, and builds: [GUIDE.md](GUIDE.md)
- Common questions and troubleshooting: [QANDA.md](QANDA.md)
- Version history: [CHANGES.md](CHANGES.md)
- Chinese documentation: [README.md](README.md)

## Compatibility and Dependencies

- Minecraft: The development and build target is `1.20.1`.
- Forge: The project builds against `47.4.0`; mod metadata declares a minimum of `47`. Other combinations need verification; the declared range is not a fully tested range.
- Java: `17`.
- Required dependency: TaCZ `1.1.6+` on both client and server.
- Embedded component: The FPSM-compatible core requires no separate `fpsmatch.jar`.
- Optional integration: LR Tactical `0.3.0+`. FTL/TDM remain available without LR.
- Optional mode: The `codpattern_zombies` addon. Main-mod metadata accepts `0.2.0b+`; the addon's own dependency requirements must also be satisfied. The main mod and addon are built and installed separately.
- Physics Mod and `tacz-addon` appear in the client development runtime configuration. Neither is declared as a required dependency of the main mod.

Clients and servers should use matching main-mod and required addon combinations. See [GUIDE.md](GUIDE.md) for source builds and development launches with optional LR support.

## License

Licensed under **GPL-3.0-only**. See [LICENSE.txt](../LICENSE.txt) and [CREDITS.txt](../CREDITS.txt) in the repository root.
