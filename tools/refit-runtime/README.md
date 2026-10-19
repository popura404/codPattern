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
