# codPattern Zombies Addon Physical Split Agent Execution Plan

> Status: COMPLETE - Rounds 0-6 passed their required automated gates and independent audits; final artifacts are preserved and the post-verification dependency handoff is read-only verified
>
> Upstream authority: `docs/MODE_SPLIT_PREPARATION_PLAN.md`
>
> Starting point: Preparation Phases 0-7 are complete; no physical Gradle or JAR split has been performed
>
> Platform: Minecraft Forge 1.20.1, Forge 47.4.0, Java 17, ForgeGradle 6
>
> Repository snapshot: `HEAD=0fcb400`; the worktree contains extensive uncommitted Phase 0-7 and user changes that execution agents must preserve
>
> Execution model: The lead agent owns sequencing, shared-file integration, and gates. Sub-agents perform most implementation work. Every round receives an immediate read-only audit from an agent that did not author that round.

## 1. Objective

Physically split the already isolated Zombies implementation out of the current single JAR and into an independent Forge addon without rewriting gameplay or changing compatibility contracts:

```text
codpattern main mod
├─ neutral match API and reusable runtime
├─ TeamMatchRuntime
├─ Frontline
└─ Team Deathmatch

codpattern Zombies addon
├─ Zombies rules, runtime, and persistence
├─ Zombies blocks, items, tools, and resources
├─ Zombies client UI, HUD, and rendering
└─ contributors and adapters installed only through public main APIs
```

The final dependency direction must be:

```text
codpattern_zombies -> codpattern
codpattern -X-> codpattern_zombies
```

This is not another architecture-refactoring plan. Phase 7 already proved that:

- 489 future-main production Java sources compile with addon implementations absent;
- 151 Zombies-addon production Java sources compile only against the main-only output;
- the main side has zero known source, type, bytecode, or resource dependencies on Zombies implementations.

The remaining work is therefore limited to:

- creating a second ForgeGradle project and a second `@Mod` entry point;
- moving sources, dependent tests, and resources according to the final ownership manifest;
- changing Forge automatic-subscriber ownership from the main mod container to the addon mod container;
- producing two independent reobfuscated JARs;
- replacing logical source-tree isolation checks with real module, JAR, metadata, class-loading, and installation-matrix checks.

## 2. Frozen Decisions for the First Physical Split

These decisions minimize risk during the first split. Agents must not select alternatives during implementation. Any change requires stopping execution, updating this document, and independently reviewing the revised boundary.

| Area | Frozen decision |
|---|---|
| Main loader mod ID | Keep `codpattern` |
| Addon loader mod ID | Use `codpattern_zombies` |
| Main JAR | `codpattern-<version>.jar` |
| Addon JAR | `codpattern-zombies-<version>.jar` |
| Gradle topology | Keep the repository root as the main project and add only `:zombies-addon` |
| Java packages | Do not bulk-rename packages during the first split |
| Existing resource and registry namespace | Keep `codpattern` for every existing public ID |
| Version policy | Release main and addon in lockstep; use an exact main compatibility range for the first addon release |
| Loader ordering | Addon declares main as `mandatory=true`, `ordering="AFTER"`, `side="BOTH"` |
| Installation topology | Addon must be installed symmetrically on client and server; mismatch must be rejected during Forge negotiation |
| Verification compile dependency | During split verification, `:zombies-addon` temporarily compiles against the root main project |
| Post-verification source handoff | Remove only the temporary Gradle project dependency after all verification, reobfuscation, artifact audits, and checksums are complete; the user later supplies the release-specific dependency source |
| Final Forge runtime dependency | The addon `mods.toml` mandatory dependency on `codpattern` is permanent and must not be removed with the temporary Gradle dependency |
| Network channel | Keep `codpattern:main` |
| Protocol | Keep `10` |
| Zombies packet slots | Keep discriminators `51` and `57` |
| Second addon network channel | Explicitly deferred |
| Combined compatibility JAR | Do not ship a third JAR containing all three modes |
| Distribution bundle | An optional ZIP may contain the two independent JARs |
| Data migration | Do not add a migration; preserve formats through static fixtures and contract checks. Old-world runtime loading is manual user validation and is outside the split acceptance gate |

Keeping the root project as main is deliberate. Moving 489 main-owned sources into a new subdirectory would add no dependency isolation, while significantly increasing path churn and dirty-worktree conflict risk. The first physical split moves only the 151 addon-owned sources and their dependent tests and resources.

## 3. Verified Starting Baseline

### 3.1 Code and Artifact Inventory

| Item | Current fact |
|---|---|
| Production Java | 641 files: 489 FUTURE_MAIN, 151 ZOMBIES_ADDON, 1 COMPOSITION_SHIM |
| Main-only compilation | 489 main sources compile with addon implementation classes absent |
| Addon-against-main compilation | 151 addon sources compile against main-only output |
| Main-to-addon dependencies | Zero known source, bytecode, type-signature, or resource edges |
| Production resources | 44 files: 28 explicit Zombies assets, 1 Zombies-only TaCZ tag, 4 mixed language files, 11 main resources |
| Test Java | 97 files; most are executable `main()` compatibility programs rather than JUnit tests |
| GameTests | 2 required architecture tests and 15 optional Zombies tests in source |
| Current build | One Gradle project, only `main` and `test` source sets, one reobfuscated JAR |
| Current composition | `CodPattern.java` installs both `CoreBootstrap` and `ZombiesBootstrap` |
| Baseline JAR | `build/libs/codpattern-0.7.6b.jar` |
| Baseline snapshot SHA-256 | `cd74376291371299b5d11d2bb1161e3f4f88b9c7ef7609403d5d72a98f2abcd2` |

`testClasses` and Gradle `test` are not execution evidence for the compatibility programs. Required tasks must run each JavaExec suite explicitly and compare its discovered and executed counts with the test ledger.

The baseline SHA-256 records one pre-execution JAR snapshot only. The current JAR manifest includes
an `Implementation-Timestamp`, so rerunning `build` may produce a different whole-file checksum
without source or behavior drift. Preserve the recorded snapshot value as evidence, but compare
entry ownership, metadata, codecs, resources, and behavior rather than requiring the rebuilt JAR to
reproduce this exact hash.

### 3.2 Baseline Commands Required Before Production Changes

Round 0 must rerun the following commands before changing production files:

```bash
./gradlew compileModeSplitMainOnly compileModeSplitZombiesAgainstMainOnly \
  --no-daemon --console=plain

./gradlew runModeSplitPhase0Baseline \
  runFrontlineRegressionSuite \
  runTeamDeathmatchRegressionSuite \
  runPvpBaselineCompatSuite \
  runZombiesMvp123CompatSuite \
  runModeSplitPhase7 \
  build \
  --no-daemon --console=plain

./gradlew runGameTestServer --no-daemon --console=plain
```

GameTest results must be checked by test name, not only by Gradle exit status or a summary log line. `GT-001`, the optional ammo-box inventory synchronization failure, remains a recorded known defect. This split must not fix, hide, weaken, or relabel it.

## 4. Mandatory Compatibility Contracts

### 4.1 Identity, Registry, and Resource Contracts

The following identities must not change when the addon loader mod ID becomes `codpattern_zombies`:

- canonical game type `zombies`;
- installed-mode order `frontline`, `teamdeathmatch`, `zombies`;
- compatibility API `BuiltInGameModes.ZOMBIES`;
- every existing `codpattern:zombies_*` block, item, model, texture, and preview resource ID;
- `codpattern:zombies_deploy_tool`;
- every translation key, value, placeholder count, and ordered argument;
- room-key encoding `gameType + "|" + mapName`, including separator and case behavior.

The implementation must distinguish loader identity from legacy public namespace:

```text
ZombiesAddonConstants.MOD_ID = "codpattern_zombies"   // loader and mod-container identity
CodPatternConstants.MOD_ID   = "codpattern"           // legacy resource, registry, protocol, and data namespace
```

A global replacement of `CodPatternConstants.MOD_ID` or the string `codpattern` is prohibited.

### 4.2 Network Contracts

The first split must preserve:

- channel `codpattern:main`;
- protocol string `10`;
- all 58 class-to-discriminator registrations;
- 28 C2S and 30 S2C directions;
- discriminator `51`: `ZombiesDeployToolActionC2SPacket`;
- discriminator `57`: `OpenZombiesDeployToolScreenS2CPacket`;
- codec bytes, handler thread, authorization, and recipient behavior;
- reservation of legacy slots 51 and 57 when the addon contributor is absent.

`ZombiesNetworkPacketContributor.install()` must run during addon construction before the main mod handles `FMLCommonSetupEvent` and calls `ModNetworkChannel.register()`.

Tests must not register the real static channel twice in one JVM. Main-only and combined registration probes require independent fresh processes or isolated test channels.

### 4.3 Forge Lifecycle and Physical-Side Contracts

Moved Zombies handlers must preserve their original event bus, physical side, priority, `receiveCanceled`, lifecycle timing, and exactly-once registration.

The following addon-owned automatic subscribers currently bind to the main loader mod ID and must be rebound to the addon loader mod ID:

1. `ZombiesMapData`
2. `TaczHeadshotMultiplierOverrideHandler`
3. `ZombiesBarrierMovementEventHandler`
4. `ZombiesServerLifecycleEventHandler`
5. `ZombiesCombatMarkerWorldRenderer`
6. `ZombiesObjectLabelWorldRenderer`

These six classes contain nine event-handler methods. Only their owning loader mod ID may change. Their FORGE/MOD bus, CLIENT/BOTH side, priority, and `receiveCanceled` values must remain unchanged.

`ZombiesClientBootstrap` must retain its `DistExecutor` boundary. A dedicated server with the addon installed must not load:

- `ZombiesHudOverlay`;
- Zombies deployment screens;
- Zombies client renderers;
- `ClientZombiesState`;
- any `net.minecraft.client.*` type.

The main-owned `ClientModEvents` remains the single overlay registration route. The addon must not add an equivalent second MOD-bus overlay handler.

Forge 1.20.1 constraints remain mandatory:

- use `META-INF/mods.toml`, not NeoForge metadata;
- use Java 17 and ForgeGradle 6;
- attach `DeferredRegister` instances during mod construction, before registry events;
- preserve MOD-bus versus FORGE-bus ownership;
- install client code only from client-gated lifecycle paths.

### 4.4 Zombies Behavior Contracts

Physical movement must not change:

- occupied reconnect slots, online roster recipients and payloads, and start-vote membership as three separate projections;
- immediate removal on waiting/start-vote logout versus retained reconnect occupancy during active rounds;
- distinction among accepted ready operations, actual mutations, dirty notifications, and full-roster snapshots;
- start-only voting and cancellation when a snapshot member leaves;
- full-snapshot roster behavior;
- distinction among live broadcasts, resync authorization, and non-member room-preview requests;
- successful cleanup ordering: record post-game recovery, release offline slots, then clear player and runtime state;
- wave, intermission, spawn, economy, points, buff, armor, revive, power, barrier, machine, purchase, TaCZ weapon, and object-runtime rules.

### 4.5 Configuration, Map, and Player-Data Contracts

Do not change or migrate:

- `serverconfig/codpattern/zombies_rules`;
- current Zombies rules, waves, and weapon-filter paths and filenames;
- existing FPSMatch Zombies map locations and codecs;
- player NBT root `codpattern.zombies`;
- entity tag `codpattern_room_key`;
- existing `codpattern.zombies.*` weapon and runtime ItemStack tags;
- case preservation, unknown-field preservation, or current normalization behavior.

## 5. Target Repository Structure

The first split uses the minimum-movement topology:

```text
codPattern/                              # root remains the codpattern main mod
├─ settings.gradle                      # include(':zombies-addon')
├─ build.gradle                         # main ForgeGradle and aggregate gates
├─ gradle.properties                    # main and shared platform versions
├─ src/main/java/                       # 489 FUTURE_MAIN sources plus the converted main @Mod entry
├─ src/main/resources/                  # main metadata, mixins, shared/PVP resources
├─ src/test/java/                       # main-only, common, and PVP tests
├─ docs/mode-split/...
└─ zombies-addon/
   ├─ build.gradle                      # Forge 1.20.1 addon project
   ├─ src/main/java/                    # 151 ZOMBIES_ADDON sources plus addon entry/constants
   ├─ src/main/resources/
   │  ├─ META-INF/mods.toml
   │  ├─ pack.mcmeta
   │  ├─ assets/codpattern/...          # legacy block/model/texture/preview IDs
   │  ├─ assets/codpattern_zombies/lang/... # physical language namespace only; keys remain unchanged
   │  └─ data/tacz/...                  # Zombies-only whitelist
   └─ src/test/java/                    # Zombies and combined cross-module tests
```

The root project must not package addon classes or resources into the main JAR. During verification,
the addon uses a normal temporary Gradle project dependency on main; that dependency must not shade,
copy, or embed main classes. Keep it through every compile, test, GameTest, run configuration,
reobfuscation, JAR audit, and checksum gate. After the final verification evidence and artifacts are
preserved, remove only that Gradle project dependency. The post-removal addon source project is
intentionally not required to pass a new clean build until the user supplies the release-specific
dependency source. The addon's mandatory Forge runtime dependency in `mods.toml` remains present.

## 6. Agent Organization and Exclusive Ownership

### 6.1 Roles

| Agent | Exclusive scope | Primary deliverables | Prohibited changes |
|---|---|---|---|
| Lead / Integrator | sequencing, file locks, merge order, evidence | decisions, conflict resolution, final gates | large production moves or unapproved scope expansion |
| Agent V - Verification Harness | `gradle/mode-split-*.gradle`, architecture and JAR checks | multi-module verification, artifact checks, fresh-JVM tasks | gameplay production code |
| Agent B - Build and Metadata | settings, root/addon Gradle, versions, publishing, both metadata files | two projects, two reobfuscated JARs, run configurations, post-verification dependency handoff | gameplay, packet protocol, registry IDs |
| Agent Z - Zombies Source Move | 151 manifest-owned production sources | mechanical path move with packages preserved | shared main files or opportunistic refactors |
| Agent F - Forge and Network | two entries, six subscriber classes, registration lifecycle, slots 51/57 | loader ownership, exactly-once evidence, packet-order evidence | protocol, packet ID, codec, or handler semantics |
| Agent C - Client and Resources | Zombies client sources, 28 assets, four languages, TaCZ tag | resource partition, side isolation, translation-union proof | translation values, registry IDs, UI redesign |
| Agent D - Data Compatibility | config, map, NBT, and ItemStack fixtures | static zero-migration evidence and format preservation | data formats, paths, normalization, or old-world runtime testing |
| Agent T - Test Migration | 97 tests, JavaExec suites, GameTest roots | module ownership, aggregate tasks, named-result checks | treating compilation as test execution |
| Agent H - Independent Auditor | read-only review after each round | gate-by-gate audit and stop decision | production changes in the reviewed round |

### 6.2 Serialized Shared Files

Only one agent may edit each of the following at a time:

- `settings.gradle`;
- root `build.gradle`, `gradle.properties`, and `zombies-addon/build.gradle`;
- `CodPattern.java` and the new addon `@Mod` entry;
- both `META-INF/mods.toml` files;
- `ModNetworkChannel.java`, `FpsmPacketRegistrar.java`, and `ZombiesNetworkPacketContributor.java`;
- ownership, resource, test, and API manifests;
- the four language files;
- GameTest run configuration and exploded mod roots;
- `buildAndCopyToMods` or its replacement distribution tasks.

### 6.3 Required Sub-Agent Report Format

Every sub-agent report must include:

- files actually modified;
- critical files reviewed but not modified;
- complete commands executed;
- discovered and executed test counts;
- differences from the frozen baseline;
- known-defect results;
- whether the round gate passed;
- shared files that require serialized lead integration.

A conclusion such as "complete" or "passed" without readable file and command evidence is not acceptable.

## 7. Gated Execution Rounds

Every round must be independently reviewable. The next round must not begin until Agent H has completed a readable, read-only audit and confirmed the current exit gate.

### Round 0 - Freeze the Physical-Split Baseline

Owners: Lead, Agent V, Agent H.

#### Tasks

1. Record `git status --short`, `git diff --stat`, current HEAD, untracked files, and all Phase 0-7 artifacts.
2. Do not clean, reset, normalize, or overwrite the existing dirty worktree.
3. Do not create a commit, branch, tag, patch, archive, stash, or other automated recovery artifact. The user will perform any required recovery manually through Git; recovery-point creation is outside this plan and is not an exit gate.
4. Create a physical-path overlay from `docs/mode-split/phase0/ownership-manifest.tsv` instead of rewriting historical Phase 0-7 evidence.
5. Each overlay row must include:
   - `owner`;
   - `old_path`;
   - `new_path`;
   - `kind`;
   - `rationale`;
   - `checksum`.
6. Classify all:
   - 641 production Java files;
   - 44 production resources;
   - 97 test Java files;
   - Gradle scripts;
   - fixtures, GameTest structures, and documentation.
7. Generate an addon-to-main binary API baseline, such as `docs/mode-split/physical/round0/zombies-addon-main-api-baseline.tsv`.
8. Record actual referenced main classes and member descriptors, not only whether top-level classes are public.
9. Freeze the mod ID, JAR, version, installation-symmetry, and network decisions from Section 2.
10. Rerun every command from Section 3.2.

#### Exit Gate

- every source, resource, test, script, and fixture has one target;
- the addon-to-main API symbol baseline is reproducible;
- main-only and addon-against-main isolated compilation passes;
- named GameTest results and all known defects are recorded;
- no production behavior changed.

### Round 1 - Migrate the Verification Harness First

Owners: Agent V, Agent T; Agent H audits.

This round must precede bulk source movement. Current checks hard-code the single-project layout, and `verifyModeSplitPhase7NoPhysicalSplit` actively rejects the target state. Moving production code first would make later failures ambiguous.

#### Tasks

1. Make ownership, dependency, packet, translation, and fixture checks accept multiple production, test, and resource roots.
2. Replace the old "no physical split" assertion with a two-stage gate:
   - Round 1 still validates the current combined baseline;
   - Round 2 and later validate the target two-artifact topology.
3. Add or prepare:

```text
verifySplitArtifactOwnership
verifySplitModMetadata
verifySplitResourcePartition
verifySplitBytecodeFence
verifySplitMainApiBaseline
runCoreOnlyFreshJvm
runCombinedSplitCompat
runSplitPackagingGate
```

4. Make the final dependency checks operate on real modules and final JARs:
   - main compile and runtime dependencies must not include addon;
   - main classes, descriptors, annotations, metadata, and resources must not reference addon implementations;
   - addon references must remain within the frozen public main API;
   - the two JARs must not contain duplicate `.class` entries.
5. Parse GameTest results by name and explicitly report 2 required and 15 optional tests.
6. Preserve the known `GT-001` result.
7. Preserve fresh-JVM constraints for static mode registries and the static packet ID counter.

#### Exit Gate

- the generalized verification harness passes on the current unsplit tree;
- target two-JAR checks exist but cannot pass against temporary empty artifacts;
- the single-project assertion in `ModeSplitPhase7BoundaryCompatTest` has a documented replacement rather than silent deletion;
- every JavaExec compatibility program has an executable owner.

### Round 2 - Create the Addon Project and Atomically Move Sources and Dependent Tests

Execution order: Agent B, then Agents Z and T, then Agent F, then lead integration. Agent H audits.

Production sources and tests that directly depend on them must move in the same atomic round. Moving production classes first and waiting until Round 4 to move tests would leave `compileTestJava` and `build` in an unreviewable broken state.

#### 2A. Build Agent

1. Add `:zombies-addon` to `settings.gradle`.
2. Create `zombies-addon/build.gradle` on Forge 1.20.1, Forge 47.4.0, Java 17, and ForgeGradle 6.
3. Create the addon `META-INF/mods.toml` and `pack.mcmeta` in this round, before any Forge discovery, dependency-resolution, automatic-subscriber, client, or dedicated-server gate.
4. Compile addon against main through a temporary normal Gradle project dependency.
5. Keep that project dependency through Round 6 final verification and artifact generation. Do not remove it during source, test, resource, metadata, GameTest, or installation-matrix work.
6. Do not use shading, a fat JAR, or copied main classes.
7. Give main and addon separate `jar`, `reobfJar`, and `build` outputs.
8. Make root `build` or an explicit aggregate task build both projects while the temporary project dependency is present.
9. Add:
   - main-only client and server runs;
   - combined main-plus-addon client and server runs;
   - combined GameTest run configuration.
10. Keep the existing mixin configuration in main. Do not add an addon mixin configuration unless a real addon-owned mixin is later approved.

#### 2A.1 Addon `mods.toml`

The addon metadata must exist before the Round 2 runtime gates and must include:

- `modId="codpattern_zombies"`;
- a version synchronized with main;
- Forge and Minecraft 1.20.1 dependencies;
- a mandatory, AFTER, BOTH, exact first-release dependency on `codpattern`;
- a direct TaCZ dependency rather than relying on main metadata;
- no claim that dependency `side="BOTH"` alone enforces remote client/server addon symmetry.

The mandatory `codpattern` dependency in this file is a final runtime contract. It must remain after
the temporary Gradle project dependency is removed.

#### 2B. Zombies Source Move Agent

1. Move exactly the 151 final-manifest addon production sources to `zombies-addon/src/main/java`.
2. Preserve package names, class names, and source content.
   - Authorized Round 2 exception (2026-07-27): relocate only the 18 classes listed in
     `docs/mode-split/physical/round2/SPLIT_PACKAGE_RELOCATION_PROPOSAL.tsv` by appending the listed
     `zombies` subpackage. This exception exists solely to remove the eight JPMS split packages proven by
     the real combined-server launch. Preserve simple class names and every frozen behavior/loader contract.
     The detailed amendment and verification requirements are recorded in
     `docs/mode-split/physical/round2/ROUND2_PACKAGE_RELOCATION_SUPPLEMENT.md`.
3. Make only path and ownership changes required for compilation.
4. Do not reformat, rename, or refactor moved classes.
5. Do not infer ownership from package names; generic-looking Zombies files remain governed by the manifest.
6. Do not move any FUTURE_MAIN source into addon.

#### 2C. Test Migration Agent

1. Use the Round 0 test overlay to move every test that directly depends on addon implementations.
2. Move Zombies-only tests to `zombies-addon/src/test/java`.
3. Place combined packet, translation, data, and boundary tests in addon tests or root aggregate verification so they can see both modules.
4. Keep main-only, common, Frontline, Team Deathmatch, and PVP tests in the root project.
5. Update hard-coded source or resource paths that would otherwise block this round's `compileTestJava`.
6. Defer final GameTest exploded-root restructuring to Round 4.

#### 2D. Forge and Network Agent

1. Reduce root `CodPattern.java` to:
   - `@Mod("codpattern")`;
   - only `CoreBootstrap.install(mainModEventBus)`.
2. Add one addon entry point:
   - `@Mod("codpattern_zombies")`;
   - obtains the addon mod event bus;
   - calls only `ZombiesBootstrap.install(addonModEventBus)`.
3. Add `ZombiesAddonConstants.MOD_ID` for loader identity only.
4. Update the six automatic subscribers from Section 4.3 to the addon loader mod ID.
5. Preserve every other event metadata field.
6. Keep `DeferredRegister`, creative-tab, client, and packet contributions attached from addon construction.
7. Prove addon construction installs packet contributions before main common setup registers the real channel.
8. Do not change channel, protocol, discriminator, codec, direction, or handler behavior.
9. Add addon-owned client/server compatibility enforcement through Forge `DisplayTest` or an equivalent loader-level check. Dependency `side="BOTH"` does not replace this explicit remote-topology contract.
10. Test both mismatch directions independently:
    - addon client connecting to a main-only server;
    - main-only client connecting to a server with main plus addon.
11. Both mismatches must be rejected before any unknown packet is decoded. The main channel, protocol, discriminators, codecs, and handler behavior must remain unchanged.

#### Round 2 Exit Gate

- the main production tree contains no addon-owned source;
- the addon production tree contains every targeted addon source and no copied main source;
- main and addon tests both complete `compileTestJava`;
- the main JAR contains no Zombies implementation class;
- the addon JAR embeds no main class;
- a fresh main-only JVM registers only Frontline and Team Deathmatch;
- a fresh combined JVM preserves mode order Frontline, Team Deathmatch, Zombies;
- addon metadata is present and Forge discovers `codpattern_zombies` as a separate mod container;
- addon without main is rejected by dependency resolution rather than `ClassNotFoundException`;
- both asymmetric addon installation directions have executable rejection tests;
- all nine addon event handlers register exactly once;
- addon dedicated-server compilation and startup do not load client classes.

### Round 3 - Partition Resources and Languages

Owners: Agent C, Agent B; Agent D audits data; Agent H audits the round.

#### Resource Movement

1. Move the 28 manifest-owned Zombies blockstate, model, texture, and preview assets into addon.
2. Move `data/tacz/tags/blocks/interact_key/whitelist.json` into addon.
3. Preserve:
   - `"replace": false`;
   - all existing `codpattern:zombies_*` tag values;
   - the tag's existing semantics.
4. Main retains:
   - `codpattern.mixins.json`;
   - Frontline, Team Deathmatch, and shared previews;
   - `sounds.json`;
   - both current `20s_se.ogg` files;
   - the main icon;
   - main-owned GameTest structures.
5. Do not clean up the duplicate `20s_se.ogg` files during the split.
6. Preserve the separate `META-INF/mods.toml` and `pack.mcmeta` files created in Round 2. Update resource-processing inputs only as required by the physical partition; do not defer addon mod discovery or dependency metadata to this round.

#### Language Files

Classify all four languages from actual call ownership rather than only checking whether a key contains `zombies`.

Avoid two JARs providing the same physical language resource path:

```text
main:  assets/codpattern/lang/<locale>.json
addon: assets/codpattern_zombies/lang/<locale>.json
```

Rules:

- move addon-only keys to addon;
- keep main-only and shared keys in main;
- the two key sets must not overlap;
- their union must equal the old key set;
- values, placeholder arity, and ordered arguments must remain identical;
- moving the physical language namespace must not change translation keys;
- static checks must prove the addon namespace language files are packaged and that main/addon key sets are disjoint with an exact semantic union;
- a real combined-client language smoke test is recorded as manual validation rather than an automated Round 3 exit gate;
- if manual validation later shows that the namespace strategy fails in the real client, submit a separately reviewed alternative rather than silently changing keys or duplicating paths;
- do not silently lose keys, duplicate conflicting keys, or fall back to unverified same-path resource overriding.

#### Exit Gate

- the semantic union of main and addon resources equals the original 44-resource baseline;
- no unapproved same-path duplicate exists, excluding required per-JAR metadata;
- main-only JAR contains no Zombies asset or addon-only translation;
- all legacy `codpattern:zombies_*` models, textures, previews, tooltips, and GUI translations pass static path, ownership, key-union, and packaging checks;
- the real combined-client language smoke result is listed separately as manual validation and does not determine the automated Round 3 gate;
- the main mixin descriptor exists only in main;
- addon declares and packages no unused mixin configuration.

### Round 4 - Finalize Verification, Fixtures, and GameTest Topology

Owners: Agent T, Agent V; Agent H audits.

#### Test and Verification Finalization

1. Audit the test ownership completed in Round 2; do not perform another bulk test move.
2. Finish non-blocking path updates and JavaExec task ownership.
3. Keep combined architecture tests in addon tests or root aggregate verification.
4. Never introduce a main production dependency on addon.
5. Split mixed fixture runners without rewriting historical Phase 0 fixtures:
   - main fixtures cover common, backpack, Frontline, and Team Deathmatch;
   - addon fixtures cover Zombies maps, rules, waves, filters, NBT, and ItemStack data;
   - a combined gate validates their union.
6. Update every hard-coded single-tree path, single `mods.toml` path, and single-JAR path.
7. Ensure all required JavaExec compatibility tests execute. `testClasses` remains compilation evidence only.

#### GameTest Topology

1. Create separate exploded mod roots for main and addon.
2. Do not merge both modules back into one fake mod root.
3. Move `ZombiesRuntimeGameTests` with addon ownership.
4. Treat `@GameTestHolder`, namespace, `empty.snbt`, enabled namespaces, and launch arguments as one coordinated migration.
5. Do not mechanically replace the GameTest namespace constant.
6. Preserve named-result checks:
   - 2 required architecture tests;
   - 15 optional Zombies tests;
   - visible `GT-001` known failure unless separately authorized.

#### Recommended Aggregate Tasks

```text
runMainCompatibilitySuite
runZombiesCompatibilitySuite
verifyMainOnlyDistribution
verifyCombinedDistribution
verifySplitJarContents
runCombinedGameTestServer
assembleSplitDistribution
```

#### Exit Gate

- common, Frontline, Team Deathmatch, and PVP suites execute and pass;
- Zombies MVP1-MVP3 suites execute and pass;
- all 58 packet fixtures, including slots 51 and 57, remain unchanged;
- codec, send-route, translation, map, config, NBT, and ItemStack fixtures pass;
- fresh main-only and combined probes do not share static registry or channel state;
- named GameTest results match the frozen baseline.

### Round 5 - Validate Static Data Compatibility and Installation Matrix

Owners: Agent D, Agent F, Agent C; Agent H audits.

#### Required Installation Matrix

| Installation | Expected result |
|---|---|
| Main-only client | Starts; only Frontline and Team Deathmatch appear; no Zombies content is registered |
| Main-only dedicated server | Starts; loads neither Zombies nor client-only classes |
| Main plus addon client | Starts; all three modes, deployment GUI, HUD, preview, rendering, and translations work |
| Main plus addon dedicated server | Starts; all three server runtimes work; no client class loads |
| Main plus addon multiplayer | Mode order, rooms, ready, vote, roster, and packet routes remain correct |
| Addon without main | Rejected during Forge dependency resolution |
| Addon with incompatible main | Rejected by the declared version range |
| Addon client with main-only server | Rejected during Forge negotiation before any unknown packet is decoded |
| Main-only client with main-plus-addon server | Rejected during Forge negotiation before any unknown packet is decoded |
| Old combined JAR plus new split JARs | Explicitly unsupported and prevented to avoid duplicate mod or registry loading |

#### Static Data Compatibility Only

- run the existing map, rules, wave, weapon-filter, NBT, ItemStack, and configuration fixtures against the split source/resource topology;
- prove statically that paths, codecs, root keys, tag names, field names, case handling, unknown-field handling, and current normalization rules remain unchanged;
- compare fixture round trips and canonical static outputs with the frozen Phase 0 baseline;
- do not copy, generate, open, mutate, or require a representative old combined world as part of this plan;
- actual old-world loading, removal-risk evaluation, backup choice, and release-specific upgrade checks are manual user validation and are outside every automated and final split acceptance gate;
- retain release documentation warning that removing the addon from a world that has used Zombies content is a high-risk unsupported path.

#### Exit Gate

- every installation-matrix row produces its expected success or explicit rejection;
- no `ClassNotFoundException`, unknown discriminator, missing registry, or client-on-server crash occurs;
- Zombies behavior and cleanup-order matrices pass;
- all static data fixtures and format-preservation checks pass without introducing migration code;
- all existing known defects retain their approved disposition;
- no defect fix is mixed into the structural split.

### Round 6 - Audit Artifacts, Distribution, and Final Sign-Off

Owners: Agent B, Lead; Agent H provides final sign-off.

#### Artifact Gates

Audit the two final reobfuscated JARs rather than only development output:

```text
main JAR
├─ main @Mod entry
├─ main classes, resources, mods.toml, and mixin descriptor
├─ no Zombies implementation class
└─ no addon-only asset, language, or data resource

addon JAR
├─ addon @Mod entry
├─ addon classes, resources, and mods.toml
├─ no copied main class
└─ only dependency-based access to the frozen main API
```

At minimum, verify:

- `jar tf` entry inventories;
- mod IDs, dependencies, side rules, and version ranges in both metadata files;
- duplicate class and resource entries across the two JARs;
- zero reobfuscated main-to-addon bytecode dependencies;
- addon-to-main symbols match the Round 0 API baseline;
- checksums;
- fresh-process main-only and combined startup.

#### Distribution Tasks

`assembleSplitDistribution` may output only:

- main reobfuscated JAR;
- addon reobfuscated JAR;
- checksums for both;
- installation and upgrade documentation;
- optional ZIP containing both JARs.

Replace or extend `buildAndCopyToMods` with explicit split deployment tasks:

- remove only precisely identified old `codpattern-*.jar` and `codpattern-zombies-*.jar` artifacts;
- prevent old combined and new split JARs from coexisting;
- do not use broad deletion patterns that could remove unrelated mods;
- provide separate explicit main-only and combined deployment tasks.

#### Post-Verification Gradle Dependency Handoff

The temporary `:zombies-addon -> root project` Gradle dependency exists only to compile and verify
the split against the current main checkout. Perform this handoff only after all Round 0-6 compile,
test, GameTest, client/server, reobfuscation, installation-matrix, JAR-content, metadata, checksum,
and independent-audit evidence has been preserved:

1. Preserve the verified main and addon reobfuscated JARs, checksums, reports, and exact Gradle configuration used to produce them.
2. Remove only the temporary Gradle project dependency from `zombies-addon/build.gradle`.
3. Do not remove or weaken the addon's mandatory/AFTER/BOTH exact `codpattern` dependency in `META-INF/mods.toml`.
4. Do not claim or require a new clean addon build after the temporary dependency is removed.
5. Record that the user will later configure the release-specific main dependency source before rebuilding the addon source project.
6. Do not substitute copied main classes, source inclusion, shading, or a fat JAR for the removed dependency.

#### Final Exit Gate

- main and addon build, reobfuscate, and package as two independent artifacts while the temporary verification project dependency is present;
- main JAR contains no addon implementation;
- addon JAR contains no main class copy;
- main-only and combined matrices pass;
- missing dependency, incompatible version, and topology mismatch are explicitly rejected;
- combined mode, network, registry, translation, static data, and event baselines remain stable;
- Agent H produces a readable item-by-item Definition of Done audit;
- the verified artifacts and evidence are preserved before the temporary Gradle project dependency is removed;
- only the temporary Gradle dependency is removed during handoff; the final Forge runtime dependency remains;
- the post-handoff addon source project's lack of a configured release dependency is documented and is not counted as a split failure;
- no commit, push, or remote publication occurs without separate authorization.

## 8. Verification Ladder for Every Round

Run the smallest relevant checks first, then broader gates:

1. affected-module `compileJava` and `compileTestJava`;
2. affected JavaExec compatibility suites with executed-count checks;
3. ownership, API, dependency, packet, translation, and fixture gates;
4. main-only fresh JVM;
5. combined fresh JVM;
6. main and addon `build` and `reobfJar`;
7. `jar tf` and metadata checks;
8. dedicated-server smoke tests;
9. combined GameTest;
10. client and multiplayer manual checks;
11. Agent H read-only audit.

Any skipped body, optional failure, bootstrap failure, or compiled-but-not-executed test must be reported separately and must not be counted as PASS.

## 9. Work Explicitly Excluded from This Split

Do not combine the physical split with:

- Zombies gameplay balance changes;
- migration to a separate addon network channel;
- protocol-version changes or discriminator reordering;
- registry or resource ID renaming;
- config, map, NBT, or ItemStack format migration;
- bulk package renaming;
- compatibility-facade removal;
- removal of `BuiltInGameModes.ZOMBIES`;
- automated recovery-point creation, restore rehearsal, or recovery-artifact maintenance; the user handles recovery manually through Git;
- actual old-world loading or release-specific upgrade execution; these remain manual user validation and are not split acceptance gates;
- selection or configuration of the post-handoff release dependency source; after the temporary Gradle project dependency is removed, the user supplies that source separately;
- opportunistic fixes for `GT-001`, `TEST-001`, `NET-001`, `NET-002`, `LEASE-001`, or `REG-001`;
- UI or asset redesign;
- NeoForge, Minecraft 1.21, or Java 21 migration;
- unrelated formatting, CRLF normalization, license, README, or cleanup work.

If a new defect blocks the split, add it to a physical-split defect ledger with reproduction, expected behavior, compatibility impact, minimum fix boundary, and required regression. Do not mix an unapproved fix into the active round.

## 10. Physical-Split Definition of Done

The split is complete only when every item is true:

- [x] With the temporary verification project dependency present, the repository produces separately reobfuscated main and `:zombies-addon` Forge 1.20.1 artifacts.
- [x] Main loader mod ID, JAR identity, legacy resource namespace, and public IDs remain `codpattern`.
- [x] Addon loader mod ID is `codpattern_zombies` and declares compatible mandatory/AFTER/BOTH main dependency metadata.
- [x] The addon's permanent Forge runtime dependency is distinct from the temporary Gradle project dependency and remains after handoff.
- [x] Main entry installs only `CoreBootstrap`; addon entry installs only `ZombiesBootstrap`.
- [x] The composition shim is gone and no third combined shipping JAR exists.
- [x] Main JAR contains no Zombies implementation class, resource, or metadata dependency.
- [x] Addon JAR copies no main class and uses only the frozen main API.
- [x] All 151 addon sources and all addon-owned resources and tests have one physical location.
- [x] Fresh main-only JVM and dedicated-server paths do not load Zombies implementations or register Zombies content; the real main-only client smoke is recorded as non-blocking manual validation.
- [x] Fresh combined JVM and dedicated-server paths preserve all three modes and their order; the real combined-client GUI/render/language smoke is recorded as non-blocking manual validation.
- [x] Network remains `codpattern:main`, protocol `10`, 58 packets, with slots 51 and 57 preserved.
- [x] All nine handlers in the six addon automatic-subscriber classes preserve metadata and register exactly once.
- [x] Addon dedicated server loads no client class.
- [x] Every existing `codpattern:zombies_*` registry and resource ID remains unchanged.
- [x] All four languages preserve keys, values, placeholder arity, ordered arguments, physical ownership, and static packaging; real combined-client language loading is recorded separately as manual validation.
- [x] Map, configuration, NBT, ItemStack, rules, waves, and weapon-filter fixtures preserve paths, codecs, keys, case behavior, unknown fields, and normalization without migration code.
- [x] Actual old-world loading is explicitly excluded from split acceptance and left to manual user validation.
- [x] Frontline, Team Deathmatch, PVP, and Zombies suites execute and satisfy the baseline.
- [x] Named GameTest results preserve 2 required tests, 15 optional tests, and the approved `GT-001` disposition.
- [x] Main-only and combined JVM/server rows pass; dependency and asymmetric-install rows satisfy exact metadata, DisplayTest, and artifact-fence contracts, while real negative Forge client launches remain non-blocking manual validation.
- [x] Both final reobfuscated JAR contents, metadata, dependencies, and checksums are audited.
- [x] Agent H completes the final read-only sign-off.
- [x] After all evidence and artifacts are preserved, only the temporary Gradle project dependency is removed; the user will later configure the release-specific dependency source before rebuilding addon sources.
- [x] No commit, push, or remote publication occurs without explicit authorization.

## 11. Recommended First Execution Instruction

The lead agent executing this plan should begin with the following boundary and must not immediately move sources:

```text
Execute Round 0 only:
1. inventory and preserve the current dirty worktree without creating a commit, branch,
   tag, patch, archive, stash, or automated recovery artifact;
2. create physical ownership, test, resource, and main-API overlays;
3. rerun the existing main-only, addon-against-main, Phase 0, Phase 7,
   three-mode, build, and named GameTest baselines;
4. obtain an immediate readable audit from an independent sub-agent;
5. do not enter Round 1 or modify production code unless the Round 0 gate passes.
```
