# Actual JAR refit compatibility runtime

This harness prepares the installed TaCZ 1.1.8-hotfix2, Arcana 1.1.4, KotlinForForge 4.12.0, and optionally Addon 1.1.6 for ForgeGradle's GameTest environment. It uses the actual binary implementations; it does not compile or substitute the public Arcana source.

Requirements: Python 3.11+, Java 17, a populated CDP ForgeGradle cache, and a local mods directory containing those versions. No mod JAR belongs in Git. Generated copies, manifests, and conversion logs go into `build/refit-runtime/`.

```bash
python3 tools/refit-runtime/prepare.py --mods '/absolute/path/to/game/mods'
./gradlew --offline -I tools/refit-runtime/runtime.init.gradle runGameTestServer \
  -PgameTestNamespaces=codpattern_refit \
  -PgameTestRunDir=build/refit-arcana-gametest \
  -PwithTaczAddon=false --console=plain
```

Add `--with-addon` to the preparation command for the fourth mod combination. `-PwithTaczAddon=false` deliberately disables the build's downloaded Addon dependency: the init script supplies the prepared installed Addon when requested. Use distinct `--output` / `-PrefitRuntimeDir` and `-PgameTestRunDir` values to retain both test environments. For a non-default cache, pass `--gradle-home`; use `--java` to select Java 17.

The Python step does not run Gradle, Minecraft, or a server. It reads the source mods without changing them, applies the cached SRG-to-development mappings with ForgeAutoRenamingTool, and records SHA-256 hashes. It additionally maps globally unique Minecraft SRG member IDs in `@Mixin` classes, because FART cannot resolve a shadow declared against the mixin's own class as a member of the target class. This repairs the observed `MixinEntity @Shadow f_19831_` failure; every changed class is recorded in `logs/arcana-mixin-member-remap.json`. Arcana's outer loader services and every other outer entry stay byte-identical; its embedded `META-INF/mod.jar` is replaced with a remapped copy of the original. Native libraries, mixin configurations, and refmaps remain unchanged. The init script supplies runtime refmap remapping, verifies generated JAR hashes, and removes the older compile-time TaCZ/Addon versions from the runtime classpath.

Use `-PgameTestNamespaces=codpattern_refit,codpattern_arcana` to also run the five Arcana-specific integration tests. They use loaded real guns/attachments to check the actual Arcana extended-magazine execution path, item-return isolation, the player-local skill gate and attachment laser persistence. One synthetic skill test invokes the actual reverse/repeat/scheduled-task/effect-timer entries with counted actions; it checks suppression and restored execution without claiming to verify a supplied skill pack. The nested-color test is explicitly a synthetic NBT persistence-contract test, not proof of real underbarrel support.

The three tests in separate namespace `codpattern_arcana_fixtures` require a real compatible MODULE attachment, a real underbarrel gun/attachment whose active gun ID differs from its root, and a removable underbarrel grip with a compatible different replacement. They cover shared attachments, lazy first-time underbarrel color creation, and removal/replacement resetting old underbarrel state while preserving unrelated Extras. Those tests deliberately fail with a missing-fixture explanation when resources are unavailable. Copy appropriate installed gunpacks to the isolated run directory's `tacz/` before running. The inspected game instance's cibr/gunplay/lbs/default packs do not supply these fixtures, so their absence must remain a reported verification gap. The existing `codpattern_refit` persistence tests exercise both CDP primary and secondary backpack slots through real temporary configuration files under either backend.

Preparation only validates the JAR structure and mappings. The actual GameTest log must contain the expected mod versions and the completed test results before a combination can be considered verified. In particular, successful preparation does not prove that Arcana's native code supports the development mapping environment. Production client testing with the original loader JAR remains a separate requirement.

For a production/SRG server check, use `production_prepare.py` instead. It copies the original installed mod JARs byte-for-byte, including Arcana's loader, embedded implementation and native libraries. It does not apply development mappings or runtime refmap remapping. The runtime uses Minecraft 1.20.1 and cached Forge 47.4.0; this is not a claim to reproduce a game instance running another Forge version or its entire modpack.

Build the current CDP production artifact first with the project's normal `jar` / `reobfJar` tasks. Pass `build/reobfJar/output.jar`, or the verified main-only distribution JAR, explicitly; do not assume `build/libs/*.jar` has been reobfuscated. The preparation script checks the mod ID but cannot establish that an arbitrary supplied CDP JAR uses SRG names.

```bash
python3 tools/refit-runtime/production_prepare.py \
  --mods '/absolute/path/to/game/mods' \
  --libraries '/absolute/path/to/game/libraries' \
  --gradle-home '/absolute/path/to/.gradle' \
  --cdp-jar build/reobfJar/output.jar \
  --output build/refit-production-arcana-addon \
  --with-addon --gametest \
  --game-test-namespaces codpattern_refit,codpattern_arcana
```

Preparation reads the game directories and cache, writes only the selected output directory, and invokes `javac` for the isolated test bootstrap. It never starts Gradle or Minecraft. Omitting `--cdp-jar` prepares dependencies only; generated launch scripts then refuse to run. Use a fresh output directory for each mod combination and do not prepare an environment while its server is running. Omit `--with-addon` for Arcana alone; add `--without-arcana` and select `codpattern_refit` for the baseline or Addon-only combinations. `--java` and `--javac` select the Java 17 executables.

The two profiles have separate run directories and logs. `smoke` starts an ordinary dedicated server with `forgeserver`, production mode and SRG names. It contains no test bootstrap and binds only to loopback with an automatically selected port. Wait for server startup to complete, then type `stop`; startup alone does not execute the refit tests. `gametest` uses the same production launch target with `forge.gameTestServer=true`, which selects Minecraft's GameTest server and exits after its tests complete. Run these separately and keep both console logs:

```bash
bash build/refit-production-arcana-addon/smoke/launch.sh \
  > build/refit-production-arcana-addon/smoke-console.log 2>&1
bash build/refit-production-arcana-addon/gametest/launch.sh \
  > build/refit-production-arcana-addon/gametest-console.log 2>&1
```

Forge 47.4.0 normally disables its GameTest hooks in production. Only the `gametest` profile adds `refit-production-test-bootstrap.jar` and the explicit `codpattern.productionTestBootstrap=true` property. That isolated mod enables the two Forge GameTest hook predicates; it does not change the CDP or Arcana JARs, does not switch the launch target to userdev, and must never be copied into a normal installation or the delivered CDP artifact. The smoke profile provides the separate check without this test-only change.

The production runtime is assembled offline from the cached Forge-patched SRG classes, Forge's universal module, and Mojang's cached server resources/libraries. `production-manifest.json` records source-mod hashes, runtime hashes, exact commands and the bootstrap difference. Each profile also writes `command.json` and its normal `logs/` directory. Require logs showing the expected mod versions, expected test count, and completed results; a zero exit code or a generated manifest alone is insufficient. Gunpacks and fixture requirements described above still apply: copy them into the selected profile's `tacz/` directory before launch. These dedicated-server checks do not exercise the graphical refit screen, HSV slider mixin, or client packet routing.

GameTestServer advances ticks faster than real time: passing the optional 2,420-tick duration test there does not prove that an editing session survives two real minutes. To verify normal timing, run the helper below against an existing ready production preparation with `--gametest`. This helper actually starts a server; it does not build or reobfuscate the mod.

```bash
python3 tools/refit-runtime/run_duration.py \
  --runtime-dir build/refit-production-arcana-addon
```

The helper validates the preparation manifest and recorded mod/runtime hashes, then copies mods and any prepared gunpacks into a new `duration-normal-server` profile inside that runtime. If the directory already exists, it uses a fresh suffixed directory; it never modifies an existing server profile. It retains the isolated test bootstrap, sets `forge.gameTestServer=false`, enables only `codpattern_refit_duration`, and starts an ordinary dedicated server on loopback with an automatically selected port. Once startup completes, it sends `test runall` through that child's console.

Success requires the duration result to report more than 2,400 ticks, at least 120,000 elapsed milliseconds, at least 120 seconds measured by the helper after sending the test command, and a clean server exit. A duration marker with insufficient elapsed time is a failure and also triggers shutdown. Startup plus testing has a fixed 420-second deadline; shutdown gets 30 seconds before termination and another 10 seconds before a forced kill. All stop/termination requests target only the helper's own child process. The script never attaches to or stops another running server.

The new profile retains `command.json`, `duration-run.json`, `duration-result.json`, and `console.log`. Use `--log /path/to/new-duration.log` to select another console log; existing log files are never overwritten. This normal-rate check still uses a dedicated server and the isolated test bootstrap, so it does not replace graphical client testing.

`client_prepare.py` creates a separate Linux x86_64 production client from a ready server preparation. It verifies the recorded source-mod/runtime hashes, uses the cached Minecraft 1.20.1 Linux libraries and assets, and copies original mod JARs without remapping them. It creates no window and starts no process. The output must be a new directory outside the source server runtime:

```bash
python3 tools/refit-runtime/client_prepare.py \
  --runtime-dir build/refit-production-arcana-addon \
  --cdp-jar build/distributions/codpattern-main-0.8.16b/codpattern-0.8.16b.jar \
  --output build/refit-client-arcana-addon
bash build/refit-client-arcana-addon/client/launch.sh \
  > build/refit-client-arcana-addon/client-console.log 2>&1
```

The launch command uses `forgeclient`, production/SRG names, an isolated game directory and offline test identity. It needs a working Linux X11/OpenGL display, such as WSLg; the executing environment must allow access to its local display socket. No Windows executable or OS input automation is involved. `--gradle-home`, `--libraries` and `--assets` select existing read-only caches; missing dependencies are reported without downloading them. Client mixin verbose logging is enabled, but reaching the title screen alone does not establish that every refit-screen mixin has been exercised.

Use repeatable `--test-mod /path/to/production-test.jar` arguments when preparing a fresh output to add separately compiled client integration probes. Those JARs must already use production/SRG names and remain outside the delivered CDP artifact. `named-compile-classpath.txt` supplies a classpath for compiling such probes against the cached named Forge API; the project's `build/createMcpToSrg/output.tsrg` maps named references back to SRG for the cached ForgeAutoRenamingTool. The client preparation records original and test-only JARs separately in `client-manifest.json`. A probe may drive actual Minecraft screen event methods and check client/server results internally; this preparation script itself does not simulate input or claim an integration-test result.

`--cdp-jar` optionally replaces the recorded server preparation's CDP artifact with an explicitly selected production JAR. Other source mods remain unchanged. This permits testing a new CDP build without reconstructing the server runtime first; the new client manifest records the replacement artifact and hash.

The separate `client_probe.py` compiles `client-test-src` with cached named APIs, remaps only that test bootstrap to SRG, and copies a stopped isolated test world into the prepared client's `saves/refit-client-fixture`. It then starts its own bounded Linux client. This bootstrap uses ordinary Forge client/player events and does not enable production GameTest hooks or enter the delivered mod:

```bash
python3 tools/refit-runtime/client_probe.py \
  --runtime-dir build/refit-client-arcana-addon \
  --world build/refit-production-arcana-addon/duration-normal-server/world \
  --stage refit
```

Use an existing stopped test world, such as the world produced by `run_duration.py`; the source is copied, never opened by the client. Subsequent probe runs reuse only that isolated client copy. Do not prepare or rebuild its bootstrap while this client is running. `--prepare-only` writes the bootstrap and exact command without starting a client. `--stage world` limits assertions to reaching the title screen and loading the copied single-player world.

The `refit` stage prepares a real compatible gun in the integrated server's isolated backpack file, then drives the actual pause-menu button, backpack configuration, and attachment screen through Minecraft `Screen` events. It checks that the menu receives a unique fixture marker through CDP's normal request/response, clicks two scope candidates within one client tick, installs a muzzle and stock, unloads the stock, closes with ESC, and reopens. Assertions inspect both client/server working guns and the persisted configuration file. The test reads queue state to await acknowledgements; it never directly invokes CDP's operation queue or server action service. Focus is used only to reveal the normal hover-dependent configuration buttons, and all actions use screen click/key methods.

The default configuration slot is `primary`. Pass `--slot secondary` to run the same actual menu/save/reopen path through the secondary weapon button and its separate saved backpack slot; the result records the selected slot.

Additional independent `--stage` values use the same actual menu entry:

- `immediate-close`: click a scope and press ESC in the same client tick; require SAVE to follow the in-flight installation, then check the saved file and reopened gun.
- `duration`: start timing only after entering the active editor and finishing its opening animation; require at least 120,000 elapsed milliseconds and more than 2,400 client ticks before installation, saving and reopening.
- `hot-filter`: change the isolated server filter file and execute the normal `cdp update` command; check UI hiding, retained empty slot, explicit rejection of a replayed stale native TaCZ packet, then restoration of the same candidate slot after unbanning and successful UI installation/save. The stale packet is deliberately replayed, not described as a click on a widget already removed by the UI.
- `module`: find a real loaded Arcana MODULE-compatible gun and attachment, then install, uninstall, reinstall, save and reopen through the actual controls. Missing real gunpack fixtures fail the stage; add the required fixture pack only to the isolated client `tacz/` directory before launch.
- `form`: require Vanguard's real `ts:ak308` plus `ts:gpr20obr28` conversion to `ts:ak308_gpr20`. Install the module through the real UI, verify the new root GunId and that the installed module remains unloadable without being offered for reinstallation, save, reconstruct the synchronized client preview without server form rules, reopen, unload and save the reverse conversion. Preinstalled real scope, stock and laser attachments must retain their complete stored stacks while the derived form temporarily disables their types, including after both saves and reopens. The test reads their actual `Attachment<type>` NBT because TaCZ's normal getters intentionally return empty for disabled types. Only the validated root GunId may change in the original configuration NBT. After each saved form, execute the registered `cdp distribute <player name>` administrator command in the isolated integrated server, then verify the issued stack in the real server and client inventories after normal synchronization. `--slot primary` checks inventory slot 0 and `--slot secondary` checks slot 1; this does not simulate a chat command or a match-room respawn.
- `hsv`: require the real `swpu:m4a1` gun from the loaded gunplay pack and visible `tacz:laser_lopro` attachment. Drive actual hue/saturation slider press, drag and release events for both gun and attachment colors; hold drags across server acknowledgements, verify same-tick color coalescing and INSTALL-then-LASER ordering, then check exact saved RGB values and reopened slider positions. The fixture starts with its real laser attachment installed; no color is injected into the fixture. Missing fixtures fail the stage, which never sets slider values directly or substitutes a color-service call for dragging.
- `large-catalog`: after the normal editor request establishes a session, the test bootstrap substitutes a synthetic 1,057-entry directory of copies of two real compatible scopes, preserving the original inventory-restoration snapshot and session UUID. It sends the directory through CDP's normal bounded packet fragments, clicks the actual page buttons to the end, verifies the last stable slot and installs its distinct final attachment. This tests directory transport/UI capacity; it does not claim 1,057 distinct real attachment definitions. The normal loaded-gunpack catalog size is logged separately. TaCZ 1.1.8-hotfix2 has no wheel pagination, so the probe verifies that scrolling does not change pages and uses the real page buttons instead.

Each run retains `client-probe-runs/<run-id>/console.log`, `result.json`, its exact command, a copy/hash of the test bootstrap, source-mod identities, and Minecraft screenshots in that run's `screenshots/` directory. Compile/remap logs also remain in the isolated runtime. Passing requires the expected result phase and a clean client exit, not just a rendered title. Loading failures are reported explicitly. A 360-second process deadline (540 seconds for `duration`, including startup) terminates only the helper's own child, with a 15-second graceful termination allowance before a forced kill. Run only one probe per prepared client directory at a time. This is programmatic testing inside a real client; it does not test physical mouse/keyboard delivery or audio output.
