# codPattern Mode Split Preparation Plan

> Status: Preparation complete; Phases 0–7 complete
> Scope: Preparation only; no Gradle subproject, separate mod JAR, or physical source split in this plan
> Platform verified: Minecraft Forge 1.20.1, Forge 47.4.0, Java 17
> Repository snapshot verified: 2026-07-25, through commit `d363fbe`
> Boundary audit: Strengthened against live source and preparation invariants on 2026-07-25, including the current Zombies reconnect-slot and successful-cleanup semantics; implementation defects remain subject to the defect-intake rule in Section 2
> Authority: **This English document is the authoritative project execution plan. If it differs from the Chinese translation, this document takes precedence.**
> Chinese translation: [MODE_SPLIT_PREPARATION_PLAN.zh-CN.md](MODE_SPLIT_PREPARATION_PLAN.zh-CN.md)

## Authoritative Execution Plan

### 1. Objective

Prepare the current combined `codpattern` codebase for the following future ownership model without physically splitting the project yet:

```text
Future codpattern main mod
├─ common match API and reusable runtime
├─ shared team-match runtime
├─ Frontline mode
└─ Team Deathmatch mode

Future Zombies addon
├─ Zombies rules and runtime
├─ Zombies content, assets, UI, and tools
└─ adapters that consume public APIs from the main mod
```

The required dependency direction is:

```text
Zombies addon -> codpattern main API/runtime
codpattern main -X-> Zombies implementation
```

This plan deliberately extracts more neutral infrastructure into the future main mod, while keeping Zombies-specific gameplay in the future addon. The preparation must leave the current combined distribution fully functional at every completed phase.

### 2. Authoritative Requirements and Priority

The following requirements are non-negotiable and are ordered by priority:

1. **No existing mode functionality may be removed, skipped, silently downgraded, or replaced with a no-op.** This applies to Frontline, Team Deathmatch, and Zombies.
2. Existing worlds, maps, configuration files, player data, registry IDs, commands, aliases, translations, and network behavior must remain compatible during the preparation work.
3. The future main-owned code must not depend on Zombies implementation packages.
4. Reusable implementations should be extracted into the main-owned common layer when at least two modes can use them without importing mode-specific rules, or when they are clearly neutral cross-mode lifecycle/infrastructure primitives with mode-independent contracts.
5. Mode-specific rules must remain in their owning mode. Extraction must not turn the common layer into a collection of Zombies conditionals.
6. This plan must not create separate Gradle modules, source sets, mod metadata entries, or JARs. Those actions belong to the later physical split.
7. Refactoring is not authorization to rebalance gameplay, redesign UI, rename public concepts, change packet ordering, or rewrite persistence formats.
8. The preparation must stay on the existing Forge 1.20.1/Java 17 lane: keep Forge event-bus and `DeferredRegister`/`RegistryObject` semantics, `mods.toml`, and `net.minecraftforge.*` side boundaries. Do not introduce NeoForge-only APIs or Java 21 assumptions.
9. A pre-existing defect discovered during characterization must not be silently frozen as intended behavior or mixed into a structural refactor. Record it in a defect ledger, reproduce it, define the expected contract, and handle any fix as a separately approved and narrowly scoped behavior change.

If an architectural improvement conflicts with behavior preservation, behavior preservation wins and the improvement must be staged behind a compatibility facade.

A defect may enter the preparation execution sequence only when it blocks reliable characterization, prevents a required split boundary from working, or would otherwise be entrenched by the extraction. Its reproduction, expected behavior, compatibility impact, fix boundary, and regression coverage must be approved before production code changes. The defect fix should normally land before the affected generic extraction, in a separate reviewable change.

### 3. Scope

#### 3.1 In scope

- Define and enforce code ownership boundaries for future main and addon code.
- Stabilize the existing match API and room capability composition.
- Extract neutral implementations for result handling, ready state, voting, roster synchronization, cleanup, entity ownership, map occupancy, player-session recovery, staged transactions, and generic object runtime behavior.
- Introduce extension/contributor boundaries for Forge registration, events, networking, client presentation, tools, and debugging.
- Consolidate duplicated Frontline and Team Deathmatch behavior behind a shared team-match runtime and explicit policies.
- Preserve existing mode-facing classes as compatibility facades while callers migrate.
- Add architecture checks and behavioral baselines needed to make the later physical split low risk.
- Rehearse a main-only logical bootstrap without producing a separate artifact.
- Maintain a known-defect ledger and classify defects as deferred, separately approved prerequisite fixes, or out of scope.

#### 3.2 Out of scope

- Creating `main` and `zombies` Gradle subprojects.
- Producing a separate Zombies JAR or adding final addon mod metadata.
- Choosing the final addon mod ID, release versioning scheme, or distribution packaging.
- Moving packet types to a second network channel.
- Changing map/save formats or introducing forced data migrations.
- Rebalancing waves, weapons, economy, buffs, teams, scores, timers, spawn rules, or vote percentages.
- Rewriting Zombies and PVP phase machines into one universal state machine.
- Redesigning HUDs, editor screens, deploy workflows, or assets.
- Opportunistic defect fixes that have not passed the defect-intake rule in Section 2.

### 4. Baseline Snapshot and Main Findings

At the verified `d363fbe` planning snapshot, the repository already had a strong neutral match foundation:

| Area | Snapshot anchors | Observation |
|---|---|---|
| Mode registry | `app/match/GameModeRegistry.java`, `GameModeBootstrap.java`, `GameModeDefinition.java` | Definitions, aliases, runtime providers, persistence providers, editor schemas, and client presentations are already registry-driven. |
| Match contracts | `app/match/model/**`, `app/match/port/**` | These are the correct starting point for the future main API. |
| Room composition | `app/match/ModeRoomHandle.java` | It currently has 18 components, including 15 optional ports and several constructor overloads. It should be stabilized before more extension points are added. |
| Generic synchronization | `network/match/**`, including `ModeRuntimeStatePacket`, `ModeObjectStateSyncPacket`, roster packets | The packet concepts are already mode-neutral and should remain main-owned. |
| Client presentation identity | `ClientModePresentation.java`, `client/gui/screen/match/ModePreviewPanel.java` | Presentation currently stores a path while the panel supplies `CodPattern.MODID`; an addon-owned texture therefore cannot provide its own namespace without changing this contract. |
| TDM roster runtime | `compat/fpsmatch/map/CodTdmClientSyncCoordinator.java` | It already supports full snapshots, deltas, versions, bootstrap recipients, and resynchronization. |
| Zombies membership projections | `compat/fpsmatch/map/ZombiesRoomHandleFactory.java`, `ZombiesMap.java`, `ZombiesRoomLobbyFlowStaticContractCompatTest.java` | Room summaries and admission capacity use all occupied survivor slots, including active-round offline reconnect reservations, while live roster payloads, live broadcast/resync recipients, and start-vote snapshots use online survivors. Room-preview replies are a separate contract: their payload remains online-only, but the reply may target a requester who is not a room member. Successful cleanup records pending post-game recovery before releasing offline slots. These projections, recipients, and their ordering are separate compatibility contracts. |
| Zombies object runtime | `ZombiesObjectStateStore.java` (898 lines), `ZombiesObjectInteractionService.java` (1607 lines) | They contain reusable mechanics mixed with Zombies pricing, payloads, blocks, power, weapons, and purchases. They must be separated by responsibility, not moved wholesale. |
| Tactical/TDM relationship | `CodTacticalTdmMap.java`, `CodTacticalTdmPorts.java` (251 lines) | Team Deathmatch currently reuses Frontline through inheritance and delegation. This should become an explicit shared runtime plus policies. |
| Static bootstrap state | Mode definition/runtime/persistence/editor/presentation registries | These registries use process-global static maps and have no reset API. Main-only rehearsal in the same JVM after a combined bootstrap can produce a false pass unless the test uses a fresh JVM/classloader or a narrowly scoped test reset. |
| Zombies map occupancy | `ZombiesMapOccupancyService.java`, `RoomId.java` | The occupancy key and stored owner are both derived from game type plus map name, so the current service behaves like an idempotent occupied set rather than a distinct lease owner. A generalized lease must add an opaque token/generation to reject stale releases. |
| Verification balance | 61 test source files; 59 have `zombies` in their path (57 in the filename and 2 more under an `app/zombies` package) | Zombies has substantial compatibility coverage, while PVP baseline coverage is too thin for a safe shared-runtime refactor. |

Source inventory at the planning snapshot:

| Source area | Java files |
|---|---:|
| All main Java sources | 579 |
| `app/match` | 77 |
| `app/tdm` | 27 |
| `app/tactical` | 2 |
| `app/zombies` | 107 |
| `compat/fpsmatch` | 90 |

These counts are a snapshot, not a success metric. Success is measured by dependency direction, behavior preservation, and verified extension boundaries.

### 5. Ownership Boundary

#### 5.1 Future main-owned code

The future main mod owns:

- Neutral mode definitions, descriptors, room identifiers, capability declarations, snapshots, prompts, and result contracts.
- Public mode ports and extension interfaces.
- Room registry, runtime-provider registry, persistence-provider registry, editor-schema registry, and client-presentation registry.
- Reusable room infrastructure: ready state, vote engine, roster sync, cleanup orchestration, entity ownership, map leases, player-session markers/recovery, deferred actions, transaction/rollback primitives, object registry/sync/dispatch, inventory/tool lifecycle primitives, and generic event routing.
- Shared PVP team-match runtime used by Frontline and Team Deathmatch.
- Frontline and Team Deathmatch rules, adapters, UI, configuration, persistence, and assets.
- Generic packets currently used by more than one mode or defined in terms of neutral match models.

#### 5.2 Future Zombies-addon-owned code

The future Zombies addon owns:

- Zombies mode definition and its registration contribution.
- Zombies wave definitions, validation, director, timing rules, spawn groups, mob spawning, and recycling rules.
- Zombies economy, points, prices, purchases, offers, buffs, armor, revives, and life-state semantics.
- Zombies barriers, windows, power switch, soda machines, ultimate machines, ammo boxes, armor stations, mystery boxes, and weapon walls.
- Zombies TaCZ weapon rarity, upgrades, ammo rules, ItemStack tags, weapon filters, and damage modifiers.
- Zombies map payloads, map validators, deployment schema, deploy tool, deploy GUI, and mode-specific persistence adapters.
- Zombies blocks, items, renderers, HUD, labels, translations, textures, models, and other assets.
- Zombies-specific client state and packets whose payloads have no neutral meaning.
- Zombies compatibility facades that adapt addon behavior to main-owned APIs.

#### 5.3 Items that must not be generalized yet

The following stay mode-specific even when they contain superficially similar timing or state logic:

- Zombies wave state machine and TDM phase state machine.
- Zombies spawn selection and PVP respawn selection.
- Zombies economy and any generic-looking purchase operation.
- Zombies power and machine enablement.
- Zombies weapon rarity, upgrade, and TaCZ NBT/tag behavior.
- Zombies block-to-object mappings and payload codecs.
- PVP scoring, team balance, kill feed, death cam, warmup, and match records, except where Frontline and Team Deathmatch share them through `TeamMatchRuntime`.

Only neutral transaction, timer, registry, synchronization, and rollback mechanics should be extracted from these areas.

### 6. Dependency Rules

The preparation work must first create a provisional file/class ownership manifest for future main, Zombies addon, and the temporary composition shim. That manifest then drives an automated dependency check with the following rules:

1. `app.match.model`, `app.match.port`, and main-owned runtime packages must not reference `app.zombies`, `client.zombies`, `config.zombies`, Zombies map classes, or Zombies-named content classes.
2. Shared team-match code may import the neutral match layer, but not Zombies code.
3. Frontline and Team Deathmatch may import shared team-match and neutral match code, but not Zombies code.
4. Zombies may import public neutral match APIs and addon-owned compatibility facades/gateways that themselves depend only on those public APIs. A compatibility gateway must not create a privileged main-to-Zombies dependency or bypass the public extension boundary.
5. Generic Forge event handlers must dispatch through registries/ports and must not branch on `isZombies(...)` or instantiate Zombies services.
6. Client-neutral or dedicated-server-loaded code must not reference Zombies client classes.
7. During preparation only, one clearly named **combined-distribution composition shim** may import both main and Zombies bootstraps. It is not future main-owned code and must contain no gameplay logic.
8. The architecture check should begin as a ratchet: record the current future-main-to-Zombies exceptions, reject new exceptions, then reduce that allowlist to zero. The single composition shim is tracked separately because it is explicitly not future-main code; it is the only temporary location allowed to import both bootstraps.
9. The check must cover bytecode/type dependencies and fully qualified source references, not only Java `import` lines. It must detect inheritance, method/field signatures, annotations, fully qualified class names, packet bridge types, and service/resource descriptors.
10. `BuiltInGameModes.ZOMBIES` and `isZombies(...)` may remain temporarily as compatibility identity APIs, but future-main dispatch must not depend on them. Generic routing must use registered definitions/capabilities; the final ownership or deprecation of the constant is decided during the physical split.

The current `@Mod` entry point may temporarily serve as the combined-distribution shim, but future-main initialization must live in a separate core bootstrap that never calls back into the shim. The shim is packaging glue and must be replaceable without moving gameplay logic again.

The provisional ownership manifest is a Phase 0 input to the dependency ratchet, not a Phase 7-only deliverable. Phase 7 finalizes it after all migrations. Files with generic-looking package names but Zombies-owned behavior must be classified explicitly rather than inferred from package names.

Recommended logical dependency shape during preparation:

```text
combined-distribution composition shim
├─ codpattern core bootstrap
│  ├─ app.match API/runtime
│  ├─ app.teammatch shared PVP runtime
│  ├─ Frontline
│  └─ Team Deathmatch
└─ Zombies bootstrap
   ├─ app.zombies
   ├─ Zombies Forge/client/content contributions
   └─ adapters to app.match API/runtime
```

### 7. Compatibility Contract

Every phase must preserve the following contracts unless a separate migration proposal is explicitly approved later.

#### 7.1 Stable mode identity

- Canonical game types: `frontline`, `teamdeathmatch`, `zombies`.
- Existing aliases: `cdptdm`, `cdptacticaltdm`.
- Existing installed-mode ordering: `frontline`, `teamdeathmatch`, then `zombies` when the addon contribution is present.
- Existing room-key encoding: trimmed `gameType + "|" + mapName`, including the current separator and case behavior. Do not silently lowercase public room/map IDs while extracting internal indexes.
- Preserve the current distinction between public room identity and internal occupancy identity: `RoomId` keeps a trimmed, case-preserving map name, while the current Zombies occupancy key trims and lowercases the map name.
- The temporary compatibility identity `BuiltInGameModes.ZOMBIES` must not disappear during preparation even though generic dispatch stops using it.
- Existing mode display, room header, command, and translation keys.

#### 7.2 Stable registry and resource identity

All existing registry names and namespaces must remain unchanged, including but not limited to:

- `codpattern:zombies_power_switch`
- `codpattern:zombies_player_barrier`
- `codpattern:zombies_red_player_barrier`
- `codpattern:zombies_weapon_wall_box`
- `codpattern:zombies_ammo_box`
- `codpattern:zombies_armor_station_box`
- `codpattern:zombies_soda_machine_box`
- `codpattern:zombies_ultimate_machine_box`
- `codpattern:zombies_deploy_tool`

Moving registration ownership later must not create replacement IDs or duplicate registrations.

#### 7.3 Stable persistence and player data

- Existing map save locations and codecs.
- Existing Zombies rules root: `serverconfig/codpattern/zombies_rules`.
- Existing Zombies weapon filter name: `zombies_weapon_filter.json`.
- Existing player NBT root: `codpattern.zombies`.
- Existing entity ownership tag: `codpattern_room_key`.
- Existing `codpattern.zombies.*` weapon and runtime ItemStack tags.
- Existing translation keys and placeholder counts in every language file.

New neutral storage APIs must accept adapters that continue reading and writing these exact legacy keys.

#### 7.4 Stable network behavior during preparation

- Main channel: `codpattern:main`.
- Current protocol string: `10`.
- Current registered packet count: 58 total (`28` C2S and `30` S2C), including the FPSMatch packet classes under `com.phasetranscrystal.fpsmatch` that share this channel.
- Current packet registration order and discriminator sequence.
- Current direction, encoding, decoding, handler thread, and recipient semantics.
- Generic runtime/object/room packets remain on the current channel.

Zombies-specific registrars may be isolated into a contributor, but during preparation they must be invoked at the same positions in the existing registration sequence. The current Zombies-owned slots are interleaved: `ZombiesDeployToolActionC2SPacket` is discriminator 51, neutral FPSMatch packets occupy 52–56, and `OpenZombiesDeployToolScreenS2CPacket` is discriminator 57. The contributor design must therefore use two ordered anchors, explicit fixed IDs, or equivalent reservations rather than moving all Zombies packets into one contiguous call. Packet registration must have a deterministic class-to-discriminator manifest. If a main-only rehearsal omits a Zombies contributor, its legacy slots must be explicitly reserved or the rehearsal must avoid live channel registration so later main packet IDs cannot shift accidentally. Tests must not register the live static channel twice in one JVM because the current `packetId` counter is stateful and has no reset. A second addon channel is deferred to the physical split and would require an explicit compatibility plan.

Golden packet verification must distinguish current canonical encodings from legacy decode-only compatibility. `DeathCamPacket` accepts older payloads without respawn-delay/locked-rotation fields, and `ScoreUpdatePacket` accepts the legacy three-integer payload without the score map; decoding and re-encoding those legacy payloads may legitimately append current fields. Several current codecs also serialize unordered `Map`/`Set` iteration directly, so multi-entry byte reproducibility across fresh JVMs must be characterized in the known-defect/fixture ledger rather than silently repaired or incorrectly treated as a stable ordering contract during Phase 0.

#### 7.5 Stable behavior

Frontline and Team Deathmatch must retain, as applicable:

- Map creation, loading, saving, aliases, and editor data.
- Room discovery, join, leave, spectator, team selection, and team balancing.
- Ready state, start vote, end vote, vote thresholds, timeout, and member-departure behavior.
- Warmup, movement lock, spawn/respawn selection, kits, scoring, kill feed, death cam, combat markers, HUD, match end, end teleport, and match records.

Zombies must retain:

- Map creation/loading/saving, validation, deployment workflow, and all map object types.
- Lobby join/leave, ready state, start vote, current vote failure reasons, and no end-vote support.
- Membership projection semantics: waiting/start-vote logout removes the survivor immediately; an active-round disconnect retains an occupied reconnect slot that counts in the room summary and team-capacity check, while roster payloads/recipients and start-vote membership remain online-only.
- Startup preflight, map occupancy, spawn assignment, teleports, starter kits, rollback, and failure reporting.
- Wave/intermission lifecycle, spawn groups, mob counts, entity cleanup, crash recovery, and room cleanup.
- Successful cleanup ordering: capture pending post-game recovery for the full retained survivor-ID set before offline team slots are removed; release those offline slots before player/runtime state is cleared.
- Economy, points, weapons, ammo, armor, buffs, revive/death flow, power, barriers, machines, and purchases.
- Runtime/object synchronization, HUD, prompts, world labels, combat markers, reconnect recovery, post-game teleport, and pending recovery behavior.

“Compiles successfully” is not proof of behavior preservation. Each applicable item must be covered by a test, a static contract check, a GameTest, or a documented manual verification result.

#### 7.6 Stable Forge loader and lifecycle behavior

- Keep Forge 1.20.1, Java 17, `META-INF/mods.toml`, `net.minecraftforge.*`, `DeferredRegister`, and `RegistryObject` conventions.
- Preserve the event bus (`MOD` versus `FORGE`), physical side, event priority, cancellation behavior, and registration timing of every moved handler.
- Attach block/item `DeferredRegister` instances and creative-tab listeners during mod construction before registry events fire; common setup is too late for those registrations.
- Register `SimpleChannel` packets deterministically during the existing common-setup path.
- Install overlays, renderers, screens, and client packet bridges only through `Dist.CLIENT`-gated client lifecycle paths.
- When Zombies is installed in the combined distribution, missing required Zombies contributions must fail clearly rather than degrade silently. Intentional absence is valid only in the main-only rehearsal or on a side where that contribution is not applicable.

### 8. Target Main-Layer Structure

Names below are directional and may be adjusted for local naming consistency, but ownership and dependency direction are mandatory.

```text
com.cdp.codpattern.app.match
├─ model/                         neutral immutable models
│  └─ result/                    public ModeOperationResult and ModeErrorCode contracts
├─ port/                          public mode-facing ports
├─ extension/                     contributor and registration contracts
├─ runtime/
│  ├─ ready/                      DefaultReadyStateService
│  ├─ vote/                       RoomVoteEngine and policies
│  ├─ roster/                     RoomRosterSyncCoordinator
│  ├─ lifecycle/                  cleanup and participant orchestration
│  ├─ entity/                     ownership and reconciliation
│  ├─ lease/                      mode/map occupancy leases
│  ├─ player/                     session marker and recovery primitives
│  ├─ transaction/                staged operation and rollback primitives
│  ├─ object/                     registry, index, revision, dispatch, sync
│  └─ tool/                       generic preview/tool lifecycle
└─ service/                       neutral use-case services

com.cdp.codpattern.app.teammatch
├─ model/
├─ port/
├─ runtime/                       shared PVP implementation
└─ policy/                        game type, spawn, persistence, presentation

Frontline implementation
└─ adapters/policies over app.teammatch

Team Deathmatch implementation
└─ adapters/policies over app.teammatch

Existing app.zombies and related namespaces
└─ logically addon-owned; consume app.match APIs
```

Do not bulk-rename all Zombies packages during preparation. First remove common implementations from them and classify the remaining packages as addon-owned. Package moves that provide no dependency or ownership benefit should wait for the physical split.

### 9. Required Change Directions

#### 9.1 Stabilize `ModeRoomHandle` and capabilities

Current issue: `ModeRoomHandle` exposes a long positional constructor with 15 optional ports and multiple overloads. Adding more capabilities increases call-site risk.

Direction:

- Add a builder or immutable capability set with required `roomId`, summary, and lifecycle ports.
- Add named `withReady(...)`, `withVote(...)`, `withRoster(...)`, and similar methods instead of positional optionals.
- Define an explicit capability-to-port mapping only for capabilities that have a direct current port representation, and begin validation as a diagnostic/ratchet rather than a blanket equality check. Current valid definitions also advertise composite/descriptive capabilities such as dynamic respawn, match-end teleport, round-start spawns, match records, and mode-specific map features that do not each have a dedicated `ModeRoomHandle` port; the validation must not reject those handles or invent false one-to-one mappings. Initial hard validation should cover only mappings whose current contract is direct and characterized, such as team, ready, and vote capabilities.
- Keep existing constructors as deprecated compatibility paths until all room factories migrate.
- Do not change runtime behavior or port semantics in this step.

#### 9.2 Extract neutral result and error contracts

Current candidates: `ZombiesServiceResult`, `ZombiesErrorCode`, and `ZombiesDeployServiceResult`.

Direction:

- Introduce a neutral `ModeOperationResult<T>` and value-based `ModeErrorCode` in the main layer.
- Treat these result/error types as public API contracts, not implementation-only runtime classes.
- Keep mode-specific constants, message keys, and presentation mapping in Zombies.
- Preserve structured named parameters, an independent presentation message key, ordered translation arguments, optional values, and log-only diagnostic text. `ModeOperationResult<T>` must not assume the value-based error code uniquely determines presentation: `ZombiesDeployServiceResult` can use the same success/error code with different message keys, so deriving the message key from the code cannot be lossless. Adapters must faithfully cover both `ZombiesServiceResult` and `ZombiesDeployServiceResult` without changing placeholder order or cardinality.
- Keep the existing neutral `JoinRoomResult` and `LeaveRoomResult` contracts outside this migration unless a later review explicitly proves that merging them preserves their distinct network/API semantics.
- Retain deprecated Zombies facade types or conversion helpers so migration can be incremental.
- Do not mass-replace every result type in one change.

#### 9.3 Extract ready-state and vote engines

Current candidates: `ZombiesReadyService`, `ZombiesStartVoteService`, `app/tdm/service/VoteService`, and `CodTdmVoteCoordinator`.

Direction:

- Add `DefaultReadyStateService` with injected phase, membership/admission, initialization, removal, and mutation-notification policies.
- Preserve the current difference that Zombies can record a supplied UUID as a known player while waiting, whereas TDM rejects ready mutations for players not currently joined.
- Preserve operation-result and notification semantics separately. `ZombiesReadyService.setPlayerReady(...)` returns whether the operation was accepted, not whether the stored value changed: it marks room state dirty only on an actual change, while the current Zombies room facade still sends its full roster snapshot after every accepted set operation, including an idempotent repeat. The current TDM path also performs its sync action after every accepted set operation. Do not collapse acceptance and mutation into one boolean meaning.
- Preserve initialization/reset side effects: Zombies ready initialization forces the stored value to false and marks the room dirty, and Zombies clear marks it dirty; the current TDM coordinator initializes/clears its ready map without directly invoking the sync action and relies on surrounding lifecycle calls.
- Add `RoomVoteEngine` with injected vote kinds, eligibility, prerequisites, threshold, timeout, callbacks, and member-departure policy.
- Preserve the important semantic difference:
  - Zombies: a snapshot member leaving fails the active vote.
  - TDM: a leaving member is removed and the required threshold and outcome are recalculated.
- Preserve Zombies start-only voting and TDM start/end voting.
- Keep translated messages and mode-specific failure wording outside the neutral engine.
- Keep old service classes as thin facades until callers and tests migrate.

#### 9.4 Extract roster synchronization

Current candidates: `CodTdmClientSyncCoordinator` and manual full-snapshot sends in `ZombiesRoomHandleFactory`.

Direction:

- Add `RoomRosterSyncCoordinator` that owns versions, full snapshots, deltas, bootstrap recipients, resync, and periodic calibration.
- Inject roster construction, ordering, recipient selection, clock/cadence, and packet publication.
- Preserve current packet types and version semantics.
- Keep the Zombies membership projections distinct: occupied survivor IDs drive room-summary counts and capacity, online players drive live roster payloads and live broadcast/resync recipients, and online survivor IDs drive start-vote snapshots. A generic coordinator must not derive all three views from one collection.
- Keep live roster delivery, resynchronization authorization, and room-preview replies as distinct recipient policies. Zombies live full snapshots are broadcast to online survivors, explicit resync is survivor-gated, and `RoomPreviewRosterPacket` may be returned to the requesting player even when that player is not a room member; the preview payload still contains the online survivor view. TDM likewise allows a request-targeted preview independently of its joined-player/spectator live recipient set.
- Keep Zombies on its current full-snapshot behavior throughout preparation. Any later adoption of deltas is a separately approved behavior/optimization change outside this plan, even if equivalent tests exist.
- Preserve TDM’s actual current cadence: pending deltas are eligible to flush immediately from the map-tick path; the 150 ms threshold only gates non-tick sync calls. Preserve the 7000 ms full calibration behavior unless a separately measured behavior change is approved.

#### 9.5 Converge cleanup, entity ownership, and map occupancy

Current candidates: `ZombiesCleanupService`, `ZombiesCleanupParticipant`, `ZombiesMapOccupancyService`, `ModeEntityOwnershipRegistry`, and `ModeRoomTickEventHandler`.

Direction:

- Extract `RoomCleanupCoordinator` and ordered/idempotent cleanup participants.
- Preserve the full current cleanup order through an explicit policy: before hook, entity cleanup, ordered fail-fast participants, runtime clear hooks, occupancy release, and after hooks. A participant failure currently occurs after entity cleanup but before runtime clears and occupancy release.
- Preserve the successful-cleanup reconnect ordering inside that policy: `beforeCleanup` records post-game recovery from the full retained survivor-ID list, then the player-runtime reset removes offline team entries before clearing player state. Do not release those slots before pending recovery has been recorded.
- Distinguish successful idempotent cleanup from intentional failure retention. Do not classify occupancy retained after a participant failure as a leak unless the retry/recovery contract says it should have been released.
- Generalize occupancy into a `ModeMapLeaseRegistry` keyed by canonical game type plus an adapter-supplied normalized map key, returning an opaque lease token/generation. The Zombies adapter must preserve its existing trimmed/lowercased occupancy key while the neutral registry remains unaware of that mode-specific normalization. Normal release must be idempotent and reject a stale token from an older room lifecycle. Because token ownership changes the current idempotent occupied-set/release semantics, migrating the live Zombies adapter requires a separately approved defect/behavior-change intake under Section 2 after repeated-acquire, stale-release, invalidation, and recovery expectations are characterized; it must not be smuggled in as a behavior-neutral package move.
- Before migrating Zombies to token/generation leases, characterize the current repeated-acquire, stale-release, force-invalidation, and recovery behavior and enter any intentional semantic change through the Section 2 defect-intake gate. Token protection is not presumed to be behavior-neutral merely because it is architecturally desirable.
- Preserve explicit recovery operations for server startup/stopping, residual tagged-entity cleanup, administrative force invalidation, and full ephemeral-registry clear. Define repeated-acquire and reacquire-after-invalidation behavior before migration.
- Make `ModeEntityOwnershipRegistry` the source of truth for entity-to-room attribution.
- Add neutral missing-entity reconciliation callbacks instead of a global handler importing `ZombiesActiveMobCounter`.
- Resolve the current triple tracking of active Zombies IDs across:
  - `ModeEntityOwnershipRegistry`
  - `ZombiesActiveMobCounter`
  - `ZombiesWaveRuntimeState.activeZombieEntityIds`
- Keep Zombies wave budget/completion semantics in Zombies. Derive attribution and reconciliation from the main ownership registry through a Zombies adapter.
- Preserve `codpattern_room_key` and cleanup idempotency.

#### 9.6 Extract player-session and recovery primitives

Current candidates: `ZombiesPlayerRuntimeMarkerService`, `ZombiesReconnectRecoveryService`, `ZombiesPostGameTeleportService`, `ZombiesConnectionStateService`, `CodTdmTeamMembershipCoordinator`, and `CodTdmDeferredLeaveRegistry`.

Direction:

- Add a neutral player-room session marker contract with pluggable persistence keys.
- Add grace-period/deferred-leave primitives and a pending player action or teleport queue.
- Add `PlayerRecoveryCoordinator` driven by mode adapters for active-room lookup, restore behavior, fallback teleport, inventory handling, and cleanup.
- Replace Zombies-specific logic in the global login handler with recovery contributors.
- Preserve the Zombies NBT root and current fallback behavior through its storage adapter.
- Do not force PVP and Zombies to use the same reconnect policy.

#### 9.7 Extract staged transaction and rollback mechanics

Current candidate: `ZombiesStartupFlow`.

Direction:

- Extract only the ordered stage execution, participant invocation, rollback stack, rollback report, and failure propagation mechanics.
- Keep Zombies preflight, map lease acquisition, spawn assignment, teleport, starter kit preparation/application, phase transitions, and Zombies error codes in Zombies.
- Require rollback actions to be named, idempotent where possible, and verified in reverse order.
- Avoid a universal “match startup” class that knows Zombies and PVP branches.

#### 9.8 Extract generic object runtime mechanics

Current candidates: `ModeObjectState`, `ModeInteractableObjectPort`, `ModeObjectStateSyncPacket`, `ZombiesObjectStateStore`, and `ZombiesObjectInteractionService`.

Direction:

- Main-owned mechanics may include object registration, stable object IDs, spatial lookup/indexing, revision tracking, per-tick interaction deduplication, stale-revision rejection, state publication, and handler dispatch.
- Object payloads remain opaque to the common runtime.
- Zombies owns object types, prices, purchase rules, power requirements, TaCZ weapon handling, block mappings, messages, and payload codecs.
- Split the large Zombies classes by responsibility before moving any neutral component.
- Do not move the complete Zombies store or interaction service into the main layer.

#### 9.9 Add tool, HUD, client, and debug contribution boundaries

Current candidates include `FPSMEvents`, `FPSMItemRegister`, `ClientModEvents`, `ClientPacketBridgeInstaller`, `FpsmClientPacketBridge`, `FpsmClientPacketHandler`, `ModePreviewPanel`, `TdmVanillaHudSuppressor`, and `ModeDebugCommand`.

Direction:

- Add a held-tool preview lifecycle registry so global FPSMatch events do not name the Zombies deploy tool.
- Add overlay/HUD policy contributors so TDM HUD suppression does not import `ClientZombiesState`.
- Let client presentations provide a complete namespaced resource identifier (`ResourceLocation` or an equivalent namespace/path value object) rather than relying on `ModePreviewPanel` to prepend the main mod namespace.
- Replace the typed Zombies method in the shared FPSM client packet bridge/handler with an addon-owned handler contribution or a neutral screen-open dispatcher, so future main code does not require Zombies packet or screen classes in its signatures.
- Add debug snapshot contributors keyed by mode instead of branching on Zombies in the generic debug command.
- Separate generic FPSM items from Zombies-owned items while preserving registry IDs and creative-tab visibility.
- Keep all client-only contributor implementations out of dedicated-server class-loading paths.

#### 9.10 Isolate Forge composition, event, registration, and networking hooks

Create a small family of extension contracts rather than one unrestricted “god bootstrap”. Required contribution areas are:

- Mode definition/runtime/persistence/editor registration.
- Forge block/item registration and creative-tab contribution.
- Common gameplay event contribution.
- Login/recovery contribution.
- Network packet registration contribution.
- Client packet bridge, overlay, renderer, and screen contribution.
- Tool preview and debug contribution.

During preparation:

- Consolidate remaining direct Zombies references into `ZombiesBootstrap` and a clearly disposable combined-distribution composition shim.
- Keep future-main initialization in a separate core bootstrap. If the current `@Mod` class acts as the temporary shim, it must contain only ordered bootstrap wiring.
- Preserve the lifecycle slot of every contribution:
  - mod construction/mod event bus: mode definitions, `DeferredRegister` attachment, creative-tab listeners;
  - common setup: deterministic network packet registration;
  - client setup and client MOD events: client packet bridges and overlays;
  - Forge event bus: gameplay, login, tick, render, and interaction handlers.
- Inventory Zombies-owned `@Mod.EventBusSubscriber` classes and preserve their bus, `Dist`, priority, and cancellation semantics when moving them behind the Zombies bootstrap.
- Guarantee exactly-once handler and listener registration. An automatically subscribed class must not remain active while the same handler is also registered manually through a contributor.
- Keep packet registration in the exact current sequence with explicit deterministic ordering and reserved legacy slots where an optional contribution is absent in a rehearsal.
- Do not use reflection or silent optional loading to hide initialization errors.
- Make the extension API callable directly by a future addon entry point.
- Characterize and make explicit the mode-registry conflict policy before exposing registration as an addon API. The current `GameModeRegistry` silently replaces duplicate canonical definitions and alias mappings while preserving the original insertion position and has no reset; do not accidentally change or bless those behaviors without classifying them as compatibility contracts or known defects.

#### 9.11 Build a shared `TeamMatchRuntime`

Current candidates: `CodTdmMap`, `CodTacticalTdmMap`, TDM services, tactical ports, persistence providers, and editor schemas.

Direction:

- Introduce `TeamMatchRuntime` for behavior genuinely shared by Frontline and Team Deathmatch.
- Inject a `TeamMatchPolicy` or smaller policies for:
  - canonical game type and aliases;
  - respawn selection;
  - dynamic respawn support;
  - configuration access;
  - map persistence/editor features;
  - presentation keys;
  - match-record path and mode-specific labels.
- Replace large delegation wrappers incrementally.
- Preserve Frontline’s current rules and Team Deathmatch’s current dynamic-respawn behavior.
- Perform this after the neutral ready/vote/roster/lifecycle primitives are stable, so PVP consolidation does not accidentally absorb Zombies assumptions.

### 10. Direct Split Blockers to Remove or Isolate

| Current file/area | Current Zombies knowledge | Preparation direction |
|---|---|---|
| `CodPattern.java` | Registers Zombies definitions and content directly | Extract a future-main core bootstrap; let the current entry point act only as the temporary ordered combined-distribution shim if needed. |
| `BuiltInGameModes.java` | Defines `ZOMBIES` and exposes `isZombies(...)` in main-owned identity code | Retain the constant as a compatibility API during preparation, but remove it from generic dispatch/branching and decide final ownership during the physical split. |
| `CodPatternBlockRegister.java` | Contains Zombies blocks/items | Isolate Zombies registrations behind an addon-owned registration contribution; preserve IDs. |
| `FpsmPacketRegistrar.java` | Registers deploy-tool packets in the shared registrar | Split generic and Zombies packet contributors while preserving exact call order/discriminators. |
| `FpsmClientPacketBridge.java` | Its shared handler signature directly contains `OpenZombiesDeployToolScreenS2CPacket` | Move the typed method to an addon-owned client contribution or neutral screen-handler registry. |
| `FpsmClientPacketHandler.java` | Imports the Zombies deploy screen and packet | Split generic FPSM screen handling from the addon-owned deploy-screen handler. |
| `ClientPacketBridgeInstaller.java` | Opens the Zombies deploy screen | Register mode/client packet handlers through a client contributor. |
| `ClientModEvents.java` | Registers Zombies HUD directly | Register overlays through client contributors. |
| `ClientModePresentation.java` / `ModePreviewPanel.java` | Stores a path but hardcodes `CodPattern.MODID` when constructing the texture ID | Carry the full namespaced texture identifier in the presentation contract. |
| `PlayerLoggedInEventHandler.java` | Implements Zombies reconnect recovery directly | Dispatch to registered player-recovery contributors. |
| `CodTdmEventHandler.java` | Checks Zombies room areas and drop rules | Replace with generic area/entity/drop protection policies. |
| `ModeRoomTickEventHandler.java` | Calls `ZombiesActiveMobCounter` | Dispatch missing-entity reconciliation through room/entity lifecycle ports. |
| `ModeObjectInteractionEventHandler.java` | Checks `ZombiesBoxInteractionBlock` | Use registered object-interaction predicates/handlers. |
| `TaczHeadshotMultiplierOverrideHandler.java` | Calls Zombies buff service | Use registered combat modifier contributors keyed by room/mode. |
| `TdmVanillaHudSuppressor.java` | Reads `ClientZombiesState` | Use a neutral HUD replacement policy registry. |
| `FPSMEvents.java` | Manages Zombies deploy-tool preview | Use a generic held-tool preview lifecycle registry. |
| `FPSMItemRegister.java` | Registers Zombies deploy tool with generic tools | Separate registration ownership while preserving `codpattern:zombies_deploy_tool`. |
| `ConfigPath.java` | Defines Zombies-specific paths in a general enum | Move path construction behind an addon-owned config path provider without changing paths. |
| `ModeDebugCommand.java` | Branches to `ZombiesDebugSnapshotService` | Use mode-keyed debug contributors. |
| Zombies-owned `@Mod.EventBusSubscriber` handlers | Bind addon behavior to the current main mod ID and implicit scan lifecycle | Register through the Zombies bootstrap or retain a documented temporary adapter while preserving bus/side/priority semantics. |

The goal is not merely to move imports. Each replacement must provide an explicit neutral extension point and a test showing that the same event still reaches the same Zombies behavior.

### 11. Phased Execution Plan

Each phase must be independently reviewable, buildable, and revertible. Do not begin the next phase until its exit gate passes.

#### Phase 0 — Freeze the compatibility baseline

Execution status: **COMPLETE (2026-07-25)**. See
`docs/mode-split/phase0/PHASE0_EXIT_AUDIT.md` for the frozen compatibility artifacts,
known-defect dispositions, and reproducible Phase 0 verification evidence.

Tasks:

- Create a provisional file/class ownership manifest for future main, Zombies addon, and the temporary composition shim before enabling the dependency ratchet. Classify behavior by responsibility rather than by package name alone.
- Record all public mode IDs, installed-mode ordering, aliases, room-key encoding/case behavior, registry IDs, commands, config/save paths, NBT keys, translation keys, packet class-to-ID/direction order, event-bus metadata, and protocol value in a machine-checkable compatibility manifest.
- Capture golden codec fixtures for all 58 packets currently registered on `codpattern:main`, including the 10 FPSMatch packets under `com.phasetranscrystal.fpsmatch`, with representative canonical encoded bytes, decode round trips, packet class-to-discriminator mapping, registration order, and direction. Add explicit legacy decode-only fixtures for `DeathCamPacket` and `ScoreUpdatePacket`; do not require their old payload bytes to equal the current re-encoding. Record any local packet-like class that is intentionally not registered so it cannot be mistaken for an omitted fixture. Characterize codecs whose unordered collections make multi-entry bytes non-reproducible across JVM launches in the known-defect/fixture ledger. Snapshot translation placeholder arity and ordered arguments, not only key presence.
- Capture a separate send-route/recipient manifest and executable route tests for packet semantics that codecs cannot prove: sender authorization, room/subscriber membership, requester-only preview replies, online-survivor versus joined-player/spectator broadcasts, server/client direction, and enqueue/handled-thread behavior.
- Preserve representative existing map/config payloads, player NBT, and Zombies `ItemStack` tags as read/write round-trip fixtures. Fixture sanitization must not normalize away legacy case, ordering, unknown fields, or exact key names.
- Add the dependency-ratchet test with a baseline exception list using bytecode/type analysis or an equivalent check that also catches fully qualified references and typed signatures.
- Add executable aggregate verification coverage for common/PVP contracts through one or more explicit Gradle tasks. Maintain a migration ledger for static-contract tests so each test is either invoked by an aggregate task, promoted to JUnit/GameTest, or explicitly recorded as pending; compilation alone does not count as execution.
- Add PVP baseline tests before changing shared PVP code.
- Capture behavior matrices for Frontline, Team Deathmatch, and Zombies.
- Record the post-`d363fbe` Zombies membership projections and cleanup ordering in the executable baseline: occupied reconnect slots versus online roster/vote membership; live broadcast/resync recipients versus request-targeted room-preview recipients; accepted ready operations versus actual ready-state mutations; pending-recovery capture before offline-slot release; and offline-slot release before runtime/player-state clear.
- Characterize duplicate canonical-mode registration, alias collision, insertion-order, and reset/isolation behavior for every static mode registry before contributor APIs are implemented. Record each observed behavior as an approved compatibility contract or known defect rather than silently freezing it.
- Create a known-defect ledger with reproduction status, expected contract, compatibility impact, disposition, owner, and required regression coverage. Characterization snapshots must label known failures instead of converting them into intended behavior.
- Confirm current build, custom compatibility suite, and GameTest status.

Required PVP baseline coverage:

- Ready-state phase restrictions.
- Start/end vote thresholds, timeout, and leave behavior.
- Roster full snapshot, delta, version, bootstrap, and resync behavior.
- Team join/leave/balance and spectator behavior.
- Frontline spawn behavior and Team Deathmatch dynamic respawn behavior.
- Warmup, score, end condition, end teleport, and cleanup.

Exit gate:

- The provisional ownership manifest drives the dependency baseline and every ambiguous shared file has an explicit provisional owner.
- Compatibility, codec, send-route/recipient, translation-placeholder, persistence, NBT, ItemStack, event, and registry snapshots are reviewable and reproducible.
- Common/PVP aggregate tasks execute their listed tests, and the static-contract migration ledger has no unclassified test.
- Known defects are recorded and classified without silently changing their expected contract.
- Baseline artifacts are present in a reviewable repository change. This plan does not itself authorize a Git commit, push, or publication action.
- Existing functionality is reproducible.
- No production behavior changed.

#### Phase 1 — Stabilize core construction and result contracts

Execution status: **COMPLETE (2026-07-25)**. See
`docs/mode-split/phase1/PHASE1_EXIT_AUDIT.md` for the implementation boundary and verification
evidence.

Tasks:

- Add `ModeRoomHandle` builder/capability composition.
- Migrate the TDM and Zombies room factories without removing legacy constructors.
- Add neutral result/error contracts and Zombies compatibility conversions.
- Add capability-to-port consistency checks.
- Enforce only the Phase 1 capability-to-port mappings that were explicitly characterized; do not introduce a blanket one-capability/one-port rule that rejects current valid handles.
- Start the extension API skeleton without moving Forge registrations yet.

Exit gate:

- All current room handles expose the same ports as before.
- Old constructors/facades still work and are marked for later removal.
- No packet, persistence, UI, or gameplay behavior changes.

#### Phase 2 — Extract ready, vote, and roster runtimes

Execution status: **COMPLETE (2026-07-25)**. See
`docs/mode-split/phase2/PHASE2_EXIT_AUDIT.md` for the implementation boundary and verification
evidence.

Tasks:

- Implement `DefaultReadyStateService`.
- Implement policy-driven `RoomVoteEngine`.
- Adapt Zombies and TDM through thin facades.
- Implement `RoomRosterSyncCoordinator` and migrate TDM first.
- Migrate Zombies while preserving full-snapshot behavior; do not enable deltas during preparation.

Exit gate:

- Zombies still accepts its current waiting-phase ready-state identity behavior, while TDM still rejects ready mutations for players who are not joined.
- Zombies still marks room state dirty only when the stored ready value changes, while its facade still emits the current full roster snapshot after every accepted set operation, including a repeated value; TDM still performs its current sync action after every accepted set operation. Ready initialization/clear side effects also remain mode-correct.
- Zombies snapshot-member departure still fails its vote.
- TDM departure still recalculates its vote.
- Start/end support remains mode-correct.
- Zombies room-summary counts and capacity still include active-round offline reconnect reservations, while live roster payloads, live broadcast/resync recipients, and start-vote snapshots remain online-only.
- Roster packet types, versions, recipients, and resync behavior are unchanged. Live broadcast recipients, resync authorization, and request-targeted preview recipients remain distinct; in particular, a room-preview reply is not restricted to the live room recipient set. TDM map-tick changes may still flush pending deltas immediately; the 150 ms threshold gates only non-tick sync calls, and the 7000 ms full calibration remains unchanged.
- Both PVP and Zombies policy tests pass against the corresponding generic engines.

#### Phase 3 — Extract lifecycle, ownership, lease, session, and transaction primitives

Execution status: **COMPLETE (2026-07-25)**. See
`docs/mode-split/phase3/PHASE3_EXIT_AUDIT.md` for the implementation boundary and verification
evidence. The neutral generation-token lease primitive is prepared and tested, but the live
Zombies occupancy adapter remains on its characterized compatibility behavior because no separate
Section 2 behavior-change intake was approved.

Tasks:

- Add generic cleanup participants/coordinator.
- Add explicit cleanup failure/finalizer policies and lock the current Zombies behavior with tests before changing it.
- After the Section 2 behavior-change intake is approved, generalize Zombies occupancy into a token/generation-based mode/map lease registry with repeated-acquire and stale-release tests. Without that approval, Phase 3 may prepare/test the neutral primitive but must not switch the live Zombies adapter away from its characterized compatibility behavior.
- Add neutral entity reconciliation callbacks.
- Converge active-entity attribution on `ModeEntityOwnershipRegistry` in controlled steps.
- Add player marker, grace/deferred action, pending teleport, and recovery primitives.
- Move global login recovery to contributors.
- Extract the staged transaction/rollback skeleton from `ZombiesStartupFlow`.

Exit gate:

- A successful cleanup is idempotent and releases all resources required by the current success path in the exact existing order.
- Successful Zombies cleanup records pending recovery for retained offline members before removing their team slots, and removes those offline slots before clearing player/runtime state. Waiting/start-vote logout still removes the member immediately; active-round logout still retains the reconnect reservation until the applicable cleanup/recovery path.
- A cleanup-participant failure still occurs after entity cleanup and before runtime clears and occupancy release; intentionally retained state remains available for the documented retry/recovery path rather than being misclassified as a successful cleanup leak.
- Missing entities reconcile without a generic handler importing Zombies.
- Normal token-matched lease release is idempotent, stale tokens cannot release a newer lifecycle, and repeated acquire/reacquire behavior is covered.
- Startup cleanup, server-stopping cleanup, administrative force invalidation, and full ephemeral-registry clearing have explicit operations and tests. No unintended room/map lease remains after the applicable recovery contract completes.
- Existing Zombies NBT keys and recovery outcomes are unchanged.
- Startup rollback tests prove reverse-order compensation and preserved error reporting.

#### Phase 4 — Extract generic object runtime mechanics

Execution status: **COMPLETE (2026-07-25)**. See
`docs/mode-split/phase4/PHASE4_EXIT_AUDIT.md` for the implementation boundary and verification
evidence.

Tasks:

- Split Zombies object storage into neutral registry/index/revision pieces and Zombies state/payload pieces.
- Split interaction dispatch/dedup/range/revision checks from purchase and object-type handlers.
- Connect the neutral runtime to existing generic object-state packets.
- Preserve all object IDs, payload fields, revisions, prompts, blocks, prices, and effects.

Exit gate:

- Every Zombies object type has before/after contract coverage.
- Duplicate interaction prevention and stale revision behavior are unchanged.
- No Zombies payload key or business rule appears in the main-owned object runtime.
- Dedicated server paths do not load client renderers or screens.

#### Phase 5 — Isolate composition and global hooks

Execution status: **COMPLETE (2026-07-25)**. See
`docs/mode-split/phase5/PHASE5_EXIT_AUDIT.md` for the implementation boundary and verification
evidence.

Tasks:

- Introduce `ZombiesBootstrap` and focused contributor implementations.
- Move direct Zombies knowledge out of generic login, tick, interaction, combat, HUD, tool-preview, client, network, config, and debug code.
- Separate generic and Zombies registration ownership while preserving IDs.
- Add a combined-distribution composition shim for the current single JAR.
- Preserve Forge lifecycle slots and all Zombies subscriber bus/side/priority/cancellation behavior.
- Introduce a deterministic packet manifest/reservation strategy so isolating packet contributors cannot shift later legacy discriminators.
- Reduce the future-main-to-Zombies architecture allowlist to zero. Track the combined-distribution shim separately as composition glue, not as a future-main exception, and remove any unnecessary shim dependency.

Exit gate:

- Future-main packages have zero Zombies implementation dependencies, including imports, inheritance, signatures, annotations, fully qualified references, packet bridge types, service descriptors, and resource metadata.
- All Zombies contributions are reachable through documented public extension points.
- Current single-JAR initialization order remains correct.
- Every moved Forge handler/listener is registered exactly once; no implicit `@Mod.EventBusSubscriber` path remains active alongside an equivalent manual contributor registration.
- Network discriminator snapshot and registry-ID snapshot are byte-for-byte/key-for-key unchanged.
- Required installed-mode contributions are validated; no combined-distribution Zombies feature silently falls back to a no-op.

#### Phase 6 — Consolidate Frontline and Team Deathmatch

Execution status: **COMPLETE (2026-07-26)**. See
`docs/mode-split/phase6/PHASE6_EXIT_AUDIT.md` for the implementation boundary and verification
evidence.

Tasks:

- Introduce `TeamMatchRuntime` and focused policies.
- Migrate shared PVP services and lifecycle behavior.
- Replace tactical delegation wrappers incrementally.
- Keep mode-specific respawn, configuration, persistence, presentation, and record behavior in policies/adapters.

Exit gate:

- Frontline and Team Deathmatch pass independent regression suites.
- Each mode still reports its canonical game type and legacy alias behavior.
- Team Deathmatch dynamic respawn remains enabled; Frontline behavior remains unchanged.
- No PVP code depends on Zombies packages or Zombies policies.

#### Phase 7 — Logical split-readiness rehearsal

Execution status: **COMPLETE (2026-07-26)**. See
`docs/mode-split/phase7/PHASE7_EXIT_AUDIT.md` for the isolated compile/class-loading fence,
fresh-process bootstrap rehearsal, synthetic external contributor coverage, final ownership-
manifest resolution, and combined-distribution verification evidence.

Tasks:

- Add a verification bootstrap that initializes main-owned definitions/runtime without installing Zombies contributions. Narrowly scoped test-only registry resets may support fast local tests, but final Phase 7 sign-off must run in a fresh JVM/classloader so static state from a previous combined bootstrap cannot contaminate the result. This is a test/rehearsal path, not a shipping feature toggle.
- Verify Zombies initialization through addon-owned compatibility facades/gateways implemented solely over public main APIs; no gateway may reach back into the composition shim or a non-public main implementation path.
- Add a test-only logical compile/class-loading fence in which addon-owned implementation classes are absent from the future-main classpath, not merely unused at runtime. This fence must not create any Gradle `SourceSet`, shipping or otherwise, or a physical module; use a filtered `JavaCompile`/classpath or an equivalent isolated compilation mechanism.
- Add a synthetic external contributor/new-mode fixture that registers through the public extension API without edits to generic main-owned routing, proving the boundary is an extension point rather than a Zombies-specific relocation. Any network-registration exercise for this fixture must use an isolated test registry/channel and must not alter production `codpattern:main` discriminators.
- Run dependency analysis with addon-owned packages treated as external and unavailable.
- Finalize the Phase 0 ownership manifest, listing files intended for the future main JAR, Zombies JAR, and temporary composition shim, and resolve every provisional/ambiguous entry.
- Document the remaining physical-split-only actions.

Exit gate:

- Main-owned code compiles with addon-owned implementation classes absent, and its fresh-JVM/classloader bootstrap tests pass without loading Zombies implementations or retaining a previously registered Zombies definition/provider.
- The synthetic external/new-mode contributor reaches definition, runtime, applicable event and network routes, and client presentation solely through documented public extension points.
- Zombies still passes its full compatibility suite when installed in the combined distribution.
- The final ownership manifest has no unresolved entry and the dependency checker reports zero future-main Zombies implementation dependencies.
- In the combined distribution, no functionality is missing from any of the three modes.
- No physical module/JAR split has been performed.

### 12. Recommended Sub-Agent Work Allocation

The following allocation applies to the implementation run. The lead agent owns sequencing and integration; sub-agents must not independently broaden scope.

| Agent | Ownership | Primary deliverables | Must not change |
|---|---|---|---|
| Lead / Integrator | Plan, compatibility manifest, shared-file locks, merge order, final verification | Phase gates, conflict resolution, final dependency audit | Gameplay balance or compatibility contracts without explicit approval |
| Agent A — Core API | `app.match` construction, capabilities, result/error, architecture checks | `ModeRoomHandle` builder, neutral result types, dependency ratchet | Zombies business rules, packets, Forge registration order |
| Agent B — Lobby and Sync | ready, vote, roster runtime and tests | Generic engines, policy adapters, PVP/Zombies semantic tests | Message wording, vote policy semantics, packet formats/cadence |
| Agent C — Lifecycle and Session | cleanup, leases, entities, reconnect, deferred actions, transactions | Coordinators, source-of-truth convergence, recovery adapters | Wave rules, spawn balance, existing NBT keys |
| Agent D — Object Runtime | generic object registry/index/revision/dispatch/sync | Neutral mechanics plus Zombies adapters | Prices, payload codecs, power, weapons, blocks, UI |
| Agent E — Team Match | Frontline/Team Deathmatch shared runtime | `TeamMatchRuntime`, policies, PVP regression coverage | Zombies code; mode-specific respawn differences |
| Agent F — Forge Integration | bootstraps, events, network, registries, tools, client contributors | Composition isolation, lifecycle-correct wiring, subscriber migration, deterministic packet slots | Packet order/IDs, registry IDs, event bus/side/priority, client/server boundaries |
| Agent G — Zombies Compatibility | Zombies facades and mode-specific adapters for every extraction | Preserve behavior and remove common implementation duplication | Moving Zombies business logic into main; deleting legacy compatibility early |
| Agent H — Verification | Independent tests, static contracts, GameTests, manual matrix | Phase audit and no-regression sign-off | Production behavior except narrowly approved test hooks |

#### 12.1 File ownership and conflict rules

- The lead assigns exclusive ownership for every shared file before a phase starts.
- `CodPattern.java`, `ModNetworkChannel.java`, `GameModeDefinition.java`, `ModeRoomHandle.java`, global Forge event handlers, and shared packet registrars are serialized integration files; only one agent edits each at a time.
- Agent G reviews every change that replaces a Zombies service with a generic implementation.
- Agent E reviews any common API change that affects team or PVP semantics.
- Agent H reviews from behavior contracts and dependency rules, not from the author’s implementation assumptions.
- Sub-agents deliver small review units by workstream, using commits only when version-control actions are separately authorized. Do not combine package moves, behavior changes, and network/registry changes in one review unit.
- If a required abstraction changes another agent’s contract, stop and update the shared contract first; do not create parallel near-duplicate APIs.

#### 12.2 Recommended merge order

1. Phase 0 verification baseline from Lead and Agent H.
2. Agent A core API changes.
3. Agent B lobby/sync changes.
4. Agent C lifecycle/session changes.
5. Agent D object runtime changes.
6. Agent F composition and Forge hook isolation, with Agent G adapters.
7. Agent E team-match consolidation.
8. Agent H full independent audit and Phase 7 rehearsal.

Agent G participates throughout steps 2–6 rather than waiting until the end, so compatibility facades never lag behind the extracted implementation.

### 13. Verification Strategy

#### 13.1 Verification ladder for every phase

Run the smallest relevant checks first, then the complete checks:

1. Pure JVM/static contract tests for the changed services.
2. Executable common/PVP aggregate compatibility task, with its discovered/executed test count checked against the static-contract migration ledger.
3. Packet codec golden-byte/round-trip checks, translation placeholder/argument checks, and map/config/NBT/ItemStack fixture round trips.
4. `./gradlew testClasses`.
5. `./gradlew runZombiesMvp123CompatSuite`.
6. Focused Forge GameTests for changed runtime behavior.
7. `./gradlew runGameTestServer` when the environment supports it.
8. `./gradlew build`.
9. Dedicated-server startup smoke test.
10. Client smoke test and multiplayer room-flow test for affected modes.
11. Architecture dependency check and compatibility-manifest snapshot check.

Phase 7 additionally requires the fresh-JVM/classloader logical compile/bootstrap fence and the synthetic external/new-mode contributor test. Test-only registry reset coverage is useful for iteration but cannot replace that final isolated run.

A skipped test due to a missing Minecraft/Forge runtime is not a pass. It must be rerun under the appropriate Forge/GameTest classpath before the phase is signed off.

#### 13.2 Required regression matrix

| Area | Frontline | Team Deathmatch | Zombies |
|---|---|---|---|
| Definition/alias/room listing | Required | Required | Required |
| Map create/load/save/editor | Required | Required | Required, including deploy workflow |
| Join/leave/rejoin | Required | Required | Required, including active-round offline slot retention, occupied-slot room counts/capacity, online-only roster/vote views, reconnect recovery, and cleanup release ordering |
| Spectator/team selection/balance | Required | Required | Verify unsupported/coop behavior remains unchanged |
| Ready state | Required | Required | Required |
| Start vote | Required | Required | Required |
| End vote | Required | Required | Verify it remains unsupported |
| Vote member departure | Recalculate current shared PVP/TDM behavior | Recalculate current shared PVP/TDM behavior | Fail snapshot vote |
| Spawn/respawn/kits | Required | Required, including dynamic respawn | Required, including startup assignment and revive/intermission |
| Lifecycle/end/cleanup | Required | Required | Required, including rollback/crash recovery/entity cleanup |
| Combat/score/HUD | Required | Required | Required, including points, buffs, markers, and wave HUD |
| Objects/content | Mode-specific map features | Mode-specific map features | Every barrier, machine, box, wall, power, weapon, and purchase path |
| Persistence compatibility | Required | Required | Required for maps, rules, waves, filters, NBT, and ItemStack tags |
| Network/client/server sides | Required | Required | Required, including deploy screen and object sync |

#### 13.3 Architecture checks

The verification suite must fail when:

- A future-main package or type has any Zombies implementation dependency, even when no Java import is present.
- A future-main type depends on Zombies through a fully qualified reference, inheritance, annotation, field/method signature, packet bridge type, service descriptor, or resource metadata even when no `import` line exists.
- A generic handler branches directly on Zombies mode identity when a port/contributor should be used.
- A main-owned model contains Zombies payload keys or error constants.
- A common runtime requires a Zombies block, item, map, service, client state, or renderer class.
- A dedicated-server-loaded class imports a client-only contributor.
- A new packet is inserted in a way that changes existing discriminators during preparation.
- An omitted rehearsal contribution causes later packet IDs to collapse into its legacy slots instead of reserving them.
- A registry ID, translation-key set, config path, NBT key, mode ordering, room-key encoding rule, or alias disappears.
- A packet codec changes existing bytes/direction/discriminator without approval, a translation placeholder loses or reorders an argument, or a legacy map/config/NBT/ItemStack fixture no longer round-trips under its documented preservation rule.
- A moved Forge handler changes MOD/FORGE bus, physical side, priority, cancellation behavior, or registration timing.
- A Forge handler/listener is registered zero times or more than once in the combined distribution.
- A static-contract test is compiled but is neither executed by an aggregate task nor classified in the migration ledger.
- A synthetic external/new-mode contributor requires a Zombies branch or an edit to generic routing to become reachable.

### 14. Checklists

#### 14.1 Per-change checklist

- [ ] The change belongs to the current phase and does not perform the physical split.
- [ ] Existing behavior is characterized by a test before or with the refactor.
- [ ] Any discovered pre-existing defect is entered through the Section 2 defect-intake rule and is not silently frozen or opportunistically fixed.
- [ ] No mode feature is deleted or replaced by a no-op.
- [ ] The common implementation contains no mode-specific conditionals or constants.
- [ ] Mode-specific presentation/messages remain outside neutral mechanics.
- [ ] Compatibility facade/adapters remain until all callers and tests migrate.
- [ ] Registry IDs, aliases, paths, keys, packet order, and protocol are unchanged.
- [ ] Packet bytes/codecs, translation placeholder arity/order, and representative map/config/NBT/ItemStack fixtures match the approved baseline.
- [ ] Installed-mode ordering and room-key separator/case behavior are unchanged.
- [ ] Forge 1.20.1/Java 17 loader APIs, event bus, side, priority, cancellation, and lifecycle timing are preserved.
- [ ] Implicit and manual Forge registration paths were audited for exactly-once handler/listener registration.
- [ ] Packet contributors have deterministic IDs; intentionally absent rehearsal contributions reserve legacy slots or do not register the live channel.
- [ ] Client-only code is not reachable from dedicated-server loading paths.
- [ ] Shared files were edited by their assigned owner only.
- [ ] Focused tests and the relevant full suite pass.
- [ ] Tests claimed by the change were actually executed, not merely compiled, and the static-contract migration ledger is updated.

#### 14.2 Phase exit checklist

- [ ] Phase deliverables are complete and documented.
- [ ] The ownership manifest and known-defect ledger are updated for this phase.
- [ ] Architecture exception count did not increase.
- [ ] Frontline regression subset passes.
- [ ] Team Deathmatch regression subset passes.
- [ ] Zombies compatibility subset passes.
- [ ] Any skipped Forge-dependent test was rerun in Forge/GameTest.
- [ ] Save/config/NBT/registry/network snapshots match the baseline.
- [ ] Codec golden bytes, translation placeholder/argument snapshots, and map/config/NBT/ItemStack fixtures match the baseline.
- [ ] The dependency check covers bytecode/type references and fully qualified names, not only imports.
- [ ] Dedicated server and client smoke tests pass where the phase affects integration.
- [ ] Rollback/recovery paths were tested, not only success paths.
- [ ] Any behavior fix was approved and reviewed separately from structural extraction under the defect-intake rule.
- [ ] Agent H or another independent reviewer signed off against this document.

#### 14.3 Final preparation-complete checklist

- [ ] Future-main code has zero Zombies implementation dependencies, not merely zero import statements.
- [ ] The only combined-distribution knowledge is in a disposable composition shim, or that shim has also been eliminated through explicit contributors.
- [ ] Zombies registers through public extension points exposed by main-owned code.
- [ ] Frontline and Team Deathmatch run through the shared team-match runtime with explicit policies.
- [ ] Generic ready, vote, roster, cleanup, lease, ownership, recovery, transaction, and object mechanics have cross-mode/policy tests when multiple live modes consume them, and synthetic external-consumer contract tests when only one live mode currently uses the neutral primitive.
- [ ] Zombies-only wave, economy, buff, weapon, barrier, power, machine, content, and UI logic remains addon-owned.
- [ ] All three modes retain their complete pre-refactor functionality.
- [ ] All compatibility contracts in Section 7 remain unchanged.
- [ ] Main-only code compiles with addon-owned implementation classes absent, and the final logical bootstrap rehearsal passes in a fresh process/classloader; test-only resets were not used as final sign-off evidence.
- [ ] A synthetic external/new-mode contributor reaches the documented public routes without modifying generic main-owned routing.
- [ ] Forge lifecycle and side-boundary audit passes for every moved registration/event/client hook.
- [ ] Every Forge handler/listener is registered exactly once in the combined distribution.
- [ ] The final ownership manifest has no unresolved entries, every static-contract test used as compatibility evidence is actually executed by a required gate, no such evidence remains merely `PENDING` in the migration ledger, and every known defect has an approved disposition.
- [ ] Combined-distribution full build and verification pass.
- [ ] No separate Gradle module or JAR has been created.

### 15. Principal Risks and Controls

| Risk | Control |
|---|---|
| Over-generalizing Zombies behavior into main | Require at least two consumers or a clearly neutral lifecycle concern; reject Zombies constants/branches in common code. |
| Vote semantic drift | Inject member-departure and vote-kind policies; run the corresponding generic engines against separate TDM and Zombies contract tests. |
| Packet discriminator drift | Snapshot the registration sequence; isolate registrars without reordering calls. |
| Optional contributor shifts later packet IDs | Use an explicit class-to-ID manifest and reserve legacy slots during main-only rehearsal. |
| Registry duplication or missing content | Snapshot all registry IDs; keep one registration owner per ID in every review unit. |
| Static registry contamination gives a false main-only pass | Run the rehearsal in a fresh JVM/classloader or use narrow test-only reset hooks for every static mode registry. |
| Test-only registry resets hide production classpath coupling | Allow resets only for fast iteration; require a fresh-JVM/classloader run with addon implementation classes absent for final sign-off. |
| Save/NBT incompatibility | Use storage adapters over exact legacy paths/keys; add round-trip tests with existing fixtures. |
| Codec or translated-message drift survives API-level tests | Snapshot representative packet bytes, directions, discriminators, placeholder arity, and ordered translation arguments. |
| Stale cleanup releases a newer room's map lease | Return an opaque lease token/generation and require token-matched idempotent release. |
| Dedicated-server client crash | Separate client contribution interfaces and test server startup after integration phases. |
| Event-subscriber behavior changes during relocation | Snapshot and preserve event bus, side, priority, cancellation, and lifecycle timing. |
| Implicit plus manual subscriber registration invokes behavior twice | Inventory subscriber classes and assert exactly-once registration after contributor migration. |
| Import-only architecture scan misses real coupling | Analyze bytecode/type signatures and scan fully qualified references plus service/resource descriptors. |
| Entity count divergence | Make ownership attribution authoritative and reconcile Zombies counters through one adapter path. |
| Zombies occupied-slot, online-roster, and vote-member views collapse into one collection | Preserve separate injected membership projections and lock room count/capacity, roster recipients/payloads, vote snapshots, logout phase behavior, and cleanup slot-release ordering with executable contracts. |
| Ready acceptance is mistaken for state mutation | Model accepted, changed, dirty-notification, and roster-sync outcomes separately; lock Zombies idempotent accepted-set snapshots and mode-specific initialization/clear side effects with tests. |
| Room-preview recipients are collapsed into live roster recipients | Keep live broadcast, resync authorization, and request-targeted preview policies separate; test non-member preview requests without widening live room broadcasts. |
| Large object-runtime rewrite | Split responsibilities first, migrate one mechanic at a time, and retain the Zombies facade. |
| PVP regressions hidden by Zombies-heavy tests | Build PVP baseline tests in Phase 0 before shared PVP refactoring. |
| Static contract sources compile but are never executed | Add executable common/PVP aggregate tasks and maintain an execution/migration ledger. |
| A pre-existing defect becomes a frozen compatibility contract or is mixed into a refactor | Use the Section 2 defect ledger and approval gate; land an approved prerequisite fix separately before the affected extraction. |
| Parallel-agent conflicts | Exclusive file ownership, serialized shared-file edits, small workstream commits, and lead-controlled merge order. |
| Compatibility facades becoming permanent | Track every facade in a migration ledger with owner, callers remaining, and earliest safe removal phase. |

### 16. Definition of Done for the Preparation Stage

The preparation stage is complete only when all of the following are true:

1. The code has an enforceable future-main versus Zombies-addon ownership boundary.
2. Future-main code has no Zombies implementation dependency through imports, bytecode/type signatures, metadata, resources, or class-loading requirements.
3. Zombies consumes stable public main APIs for all extracted mechanics.
4. Frontline and Team Deathmatch share an explicit policy-driven team-match runtime where behavior is truly common.
5. All three modes retain complete behavior, data compatibility, registry identity, and network compatibility.
6. The combined single-JAR distribution still builds and runs.
7. Main-only code compiles with addon-owned implementations absent, and a fresh-JVM/classloader logical bootstrap passes without loading Zombies implementations or inheriting their static registry entries.
8. The future physical split is reduced to packaging, metadata, resource ownership, dependency declaration, and any explicitly deferred network-channel migration—not another architectural rewrite.
9. Forge 1.20.1 loader lifecycle, client/server side isolation, registry timing, event semantics, and packet discriminator compatibility remain intact.
10. A synthetic external/new-mode contributor is routable solely through public extension APIs, proving generic main-owned routing is not hard-coded to Zombies.
11. The final ownership manifest is complete, all baseline fixtures and executable test ledgers are current, and every known defect has an approved disposition.

### 17. Deferred Physical-Split Work

After this preparation plan is complete, a separate approved plan may cover:

- Creating Gradle subprojects/source sets and separate JAR tasks.
- Adding final main/addon `mods.toml` metadata and mandatory dependency ranges.
- Choosing the addon mod ID while preserving the existing `codpattern` resource namespace where required.
- Assigning assets/data resources to the correct artifact.
- Moving Zombies-only packets to an addon channel, if desired, with a compatibility/version policy.
- Publishing/versioning and migration documentation.
- Removing the combined-distribution composition shim and compatibility facades that are no longer needed.

None of those actions are authorized by this preparation document.

### 18. Recommended Initial Gated Execution Sequence

The initial implementation horizon is deliberately bounded and must not include an unapproved behavior fix. It consists of three independently reviewable rounds, not one cross-phase merge unit. Each round requires its phase exit gate and an immediate independent audit before the next round begins:

1. **Round 0 / Phase 0:** provisional ownership manifest, compatibility/codec/translation/data fixtures, known-defect ledger, dependency ratchet, executable common/PVP aggregate tasks, static-test migration ledger, and PVP baseline tests.
2. **Round 1 / Phase 1:** `ModeRoomHandle` builder/capability stabilization and neutral result/error contracts with Zombies facades.
3. **Round 2 / Phase 2:** generic ready and vote engines with explicit TDM/Zombies policies, followed by generic roster synchronization that migrates TDM first and Zombies second.

Only after Round 2 is stable and all three phase gates have passed should cleanup/entity/session/object/composition work begin.
