#!/usr/bin/env python3
"""Prepare an offline, isolated Forge production/SRG runtime. Never launches Minecraft.

Uses the cached Forge-patched joined SRG classes, separated from Forge's universal
module, and resources/libraries from Mojang's cached server bundle. Actual mod JARs
are copied byte-for-byte. --gametest adds an isolated test-only bootstrap mod that
enables Forge's production-disabled GameTest hooks; it never enters the CDP JAR.
"""

import argparse
import hashlib
import io
import json
import locale
import os
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import zipfile

PROJECT = Path(__file__).resolve().parents[2]
MC = "1.20.1"
FORGE = "47.4.0"
MCP = "20230612.114412"


def file_hash(path, algorithm="sha256"):
    digest = hashlib.new(algorithm)
    with Path(path).open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


sha256 = file_hash


def declares_mod(path, mod_id):
    with zipfile.ZipFile(path) as archive:
        if "META-INF/mods.toml" not in archive.namelist():
            return False
        metadata = archive.read("META-INF/mods.toml").decode()
    sections = re.findall(r'(?ms)^\s*\[\[mods\]\]\s*\n(.*?)(?=^\s*\[|\Z)', metadata)
    return any(re.search(r'(?m)^\s*modId\s*=\s*["\x27]' + re.escape(mod_id) + r'["\x27]', s)
               for s in sections)


def write_java_args(path, args):
    # The Java launcher parses quoted argument files on both Windows and Linux.
    # Java 17 reads launcher and javac @files using the host code page (e.g. GBK on Windows).
    encoding = locale.getpreferredencoding(False) if os.name == "nt" else "utf-8"
    with path.open("w", encoding=encoding) as stream:
        for value in args:
            value = str(value).replace("\\", "\\\\").replace('"', '\\"')
            stream.write('"' + value + '"\n')
    return "@" + str(path)


def write_launcher(directory, command):
    args = write_java_args(directory / "launch.args", command[1:])
    if os.name == "nt":
        script = directory / "launch.cmd"
        script.write_text('@echo off\ncd /d "%~dp0"\n"' + command[0] + '" @launch.args\n')
    else:
        script = directory / "launch.sh"
        script.write_text("#!/usr/bin/env sh\nset -eu\ncd " + shlex.quote(str(directory)) +
                          "\nexec " + shlex.quote(command[0]) + " " + shlex.quote(args) + "\n")
        script.chmod(0o755)
    return script


def one(paths, name):
    matches = sorted({Path(p).resolve() for p in paths if Path(p).is_file()})
    if len(matches) != 1:
        raise RuntimeError(f"Expected one {name}, found {len(matches)}: {matches}")
    return matches[0]


def coordinate_path(coordinate):
    group, artifact, version, *classifier = coordinate.split(":")
    filename = f"{artifact}-{version}" + ("-" + classifier[0] if classifier else "") + ".jar"
    return Path(group.replace(".", "/")) / artifact / version / filename


def copy_verified(source, target):
    target.parent.mkdir(parents=True, exist_ok=True)
    if not target.exists() or sha256(source) != sha256(target):
        shutil.copyfile(source, target)
    return target


def locate_library(coordinate, game_libraries, cache):
    relative = coordinate_path(coordinate)
    direct = game_libraries / relative
    if direct.is_file():
        return direct
    group, artifact, version, *_ = coordinate.split(":")
    matches = list((cache / "modules-2/files-2.1" / group / artifact / version).glob("*/" + relative.name))
    matches += list((cache / "forge_gradle/maven_downloader" / relative.parent).glob(relative.name))
    return one(matches, coordinate)


def plain_jar(path, entries):
    path.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n")
        for name, data in entries:
            archive.writestr(name, data)


def prepare_game_test_bridge(output, libraries, universal, javac):
    build = output / "test-bootstrap"
    source = build / "src/refit/productiontest"
    mixin_source = source / "mixin"
    classes = build / "classes"
    source.mkdir(parents=True, exist_ok=True)
    mixin_source.mkdir(exist_ok=True)
    # A re-preparation must not retain classes from an older bootstrap package.
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir(parents=True, exist_ok=True)
    (source / "EnableGameTestMixin.java").unlink(missing_ok=True)
    (source / "BootstrapMod.java").write_text('''package refit.productiontest;
import net.minecraftforge.fml.common.Mod;
@Mod("refitproductiontests")
public final class BootstrapMod { public BootstrapMod() {} }
''')
    (mixin_source / "EnableGameTestMixin.java").write_text('''package refit.productiontest.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(targets = "net.minecraftforge.gametest.ForgeGameTestHooks", remap = false)
public abstract class EnableGameTestMixin {
    @Inject(method = "isGametestEnabled", at = @At("HEAD"), cancellable = true)
    private static void enableTests(CallbackInfoReturnable<Boolean> result) {
        if (Boolean.getBoolean("codpattern.productionTestBootstrap")) result.setReturnValue(true);
    }
    @Inject(method = "isGametestServer", at = @At("HEAD"), cancellable = true)
    private static void enableServer(CallbackInfoReturnable<Boolean> result) {
        if (Boolean.getBoolean("codpattern.productionTestBootstrap"))
            result.setReturnValue(Boolean.getBoolean("forge.gameTestServer"));
    }
}
''')
    command = [javac, "-proc:none", "-source", "17", "-target", "17", "-cp",
               os.pathsep.join(map(str, [*libraries, universal])), "-d", str(classes),
               str(source / "BootstrapMod.java"), str(mixin_source / "EnableGameTestMixin.java")]
    log = build / "compile.log"
    with log.open("w") as stream:
        result = subprocess.run([javac, write_java_args(build / "compile.args", command[1:])], stdout=stream, stderr=subprocess.STDOUT, check=False)
    if result.returncode:
        raise RuntimeError(f"Test bootstrap compilation failed; inspect {log}")
    jar = output / "gametest-mods/refit-production-test-bootstrap.jar"
    jar.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(jar, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\nMixinConfigs: refit-production-test.mixins.json\r\n\r\n")
        archive.writestr("META-INF/mods.toml", '''modLoader="javafml"
loaderVersion="[47,)"
license="Test-only local harness"
[[mods]]
modId="refitproductiontests"
version="1"
displayName="Isolated production GameTest bootstrap"
''')
        archive.writestr("pack.mcmeta", json.dumps({"pack": {"pack_format": 15, "description": "Refit GameTests"}}))
        archive.writestr("refit-production-test.mixins.json", json.dumps({
            "required": True, "minVersion": "0.8", "package": "refit.productiontest.mixin",
            "compatibilityLevel": "JAVA_17", "mixins": ["EnableGameTestMixin"],
            "injectors": {"defaultRequire": 1}}))
        for path in classes.rglob("*.class"):
            archive.write(path, path.relative_to(classes).as_posix())
    return jar


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tacz-jar", type=Path, required=True, help="Installed production TaCZ JAR")
    parser.add_argument("--libraries", type=Path, required=True, help="Read-only actual game's libraries directory")
    parser.add_argument("--gradle-home", type=Path, default=Path.home() / ".gradle")
    parser.add_argument("--output", type=Path, default=PROJECT / "build/refit-production")
    parser.add_argument("--cdp-jar", type=Path, required=True, help="Reobfuscated CDP JAR")
    parser.add_argument("--gunpack", action="append", type=Path, default=[], help="Optional TaCZ gunpack ZIP or directory")
    parser.add_argument("--gametest", action="store_true", help="Prepare an additional isolated GameTest profile and bootstrap mod")
    parser.add_argument("--game-test-namespaces", default="codpattern_refit",
                        help="Comma-separated GameTest namespaces (the dedicated duration runner selects its own)")
    parser.add_argument("--java", default="java")
    parser.add_argument("--javac", default="javac")
    args = parser.parse_args()
    output = args.output.resolve()
    game_libraries = args.libraries.resolve()
    sources = [args.tacz_jar.resolve(strict=True), args.cdp_jar.resolve(strict=True)]
    for source, mod_id in zip(sources, ("tacz", "codpattern")):
        if not declares_mod(source, mod_id):
            raise ValueError(f"Expected {mod_id} in {source}")
    gunpacks = [pack.resolve(strict=True) for pack in args.gunpack]
    for source in [game_libraries, *sources, *gunpacks]:
        if output == source or output in source.parents or source in output.parents:
            raise ValueError(f"Output must be separate from input: {source}")
    if len({pack.name for pack in gunpacks}) != len(gunpacks):
        raise ValueError("Gunpack fixtures must have distinct names")
    if output.exists():
        raise ValueError(f"Choose a new output directory: {output}")
    cache = args.gradle_home.resolve() / "caches"
    forge_cache = cache / "forge_gradle"
    forge_coord = f"net.minecraftforge:forge:{MC}-{FORGE}"
    forge_repo = forge_cache / "maven_downloader" / coordinate_path(forge_coord).parent
    userdev = one(forge_repo.glob("*-userdev.jar"), "Forge userdev metadata JAR")
    universal_source = one(forge_repo.glob("*-universal.jar"), "Forge universal JAR")
    patched_srg = one((forge_cache / "minecraft_user_repo" / coordinate_path(forge_coord).parent).glob("*-srg.jar"),
                      "cached Forge-patched SRG classes")
    server_bundle = one(forge_cache.glob(f"mcp_repo/de/oceanlabs/mcp/mcp_config/{MC}-{MCP}/joined/downloadServer/server.jar"),
                        "Mojang server bundle")
    output.mkdir(parents=True, exist_ok=True)
    library_root = output / "libraries"
    with zipfile.ZipFile(userdev) as archive:
        config = json.loads(archive.read("config.json"))
    coordinates = list(dict.fromkeys(config["libraries"]))
    classpath = []
    source_libraries = []
    for coordinate in coordinates:
        original = locate_library(coordinate, game_libraries, cache)
        target = copy_verified(original, library_root / coordinate_path(coordinate))
        classpath.append(target)
        source_libraries.append({"coordinate": coordinate, "source": str(original), "sha256": sha256(original)})

    with zipfile.ZipFile(server_bundle) as bundle:
        for line in bundle.read("META-INF/libraries.list").decode().splitlines():
            expected, coordinate, relative = line.split("\t")
            data = bundle.read("META-INF/libraries/" + relative)
            if hashlib.sha256(data).hexdigest() != expected:
                raise RuntimeError(f"Bundled library hash mismatch: {coordinate}")
            target = library_root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            if not target.exists() or sha256(target) != expected:
                target.write_bytes(data)
            if target not in classpath:
                classpath.append(target)
        _, _, relative = bundle.read("META-INF/versions.list").decode().strip().split("\t")
        vanilla_bytes = bundle.read("META-INF/versions/" + relative)
    mc_root = library_root / "net/minecraft/server" / f"{MC}-{MCP}"
    mc_srg = mc_root / f"server-{MC}-{MCP}-srg.jar"
    mc_extra = mc_root / f"server-{MC}-{MCP}-extra.jar"
    with zipfile.ZipFile(universal_source) as universal:
        forge_entries = set(universal.namelist())
    with zipfile.ZipFile(patched_srg) as patched:
        # Forge must remain its own mod/module. Putting its classes in minecraft as
        # well produces split packages during module loading.
        entries = [(name, patched.read(name)) for name in patched.namelist()
                   if name.endswith(".class") and name not in forge_entries
                   and not name.startswith("META-INF/")]
        plain_jar(mc_srg, entries)
    with zipfile.ZipFile(io.BytesIO(vanilla_bytes)) as vanilla:
        entries = [(name, vanilla.read(name)) for name in vanilla.namelist()
                   if not name.endswith((".class", "/")) and not name.startswith("META-INF/")]
        plain_jar(mc_extra, entries)
    universal = copy_verified(universal_source, library_root / coordinate_path(forge_coord + ":universal"))
    forge_server = library_root / coordinate_path(forge_coord + ":server")
    # The cached SRG file already contains Forge-patched MC classes. This required
    # location is therefore an empty overlay rather than a second copy of the classes.
    plain_jar(forge_server, [])
    classpath.extend([mc_srg, mc_extra, forge_server, universal])

    mods_root = output / "mods"
    for source in sources:
        copy_verified(source, mods_root / source.name)
    # The launch target remains production/SRG even for the additional GameTest profile.
    modules = [library_root / coordinate_path(coord) for coord in config["modules"]]
    (output / "legacy-classpath.txt").write_text("\n".join(map(str, classpath)) + "\n")
    # BootstrapLauncher treats ignoreList entries as filename prefixes. Minecraft's
    # server-<version>-srg/extra JARs belong to the transforming GAME layer located
    # by CommonServerLaunchHandler, never the untransformed MC-BOOTSTRAP parent.
    jvm = ["-Xmx3G", "-Djava.net.preferIPv6Addresses=system", "-Dfile.encoding=UTF-8",
           "-DignoreList=bootstraplauncher,securejarhandler,asm-commons,asm-util,asm-analysis,asm-tree,asm,JarJarFileSystems,server-,fmlcore,javafmllanguage,lowcodelanguage,mclanguage,forge-",
           "-DmergeModules=jna-5.10.0.jar,jna-platform-5.10.0.jar",
           "-DlibraryDirectory=" + str(library_root), "-DlegacyClassPath.file=" + str(output / "legacy-classpath.txt"),
           "-p", os.pathsep.join(map(str, modules)), "--add-modules", "ALL-MODULE-PATH",
           "--add-opens", "java.base/java.util.jar=cpw.mods.securejarhandler",
           "--add-opens", "java.base/java.lang.invoke=cpw.mods.securejarhandler",
           "--add-exports", "java.base/sun.security.util=cpw.mods.securejarhandler",
           "--add-exports", "jdk.naming.dns/com.sun.jndi.dns=java.naming", "-cp", str(modules[0])]
    launcher = ["cpw.mods.bootstraplauncher.BootstrapLauncher", "--launchTarget", "forgeserver",
                "--fml.forgeVersion", FORGE, "--fml.mcVersion", MC, "--fml.forgeGroup", "net.minecraftforge",
                "--fml.mcpVersion", MCP, "--nogui"]
    profiles = {}
    bridge = prepare_game_test_bridge(output, classpath, universal, args.javac) if args.gametest else None
    for profile in (["smoke", "gametest"] if args.gametest else ["smoke"]):
        run = output / profile
        run.mkdir(exist_ok=True)
        profile_mods = run / "mods"
        for source in sources:
            copy_verified(source, profile_mods / source.name)
        if gunpacks:
            (run / "tacz").mkdir()
            for pack in gunpacks:
                target = run / "tacz" / pack.name
                if pack.is_dir():
                    shutil.copytree(pack, target)
                else:
                    copy_verified(pack, target)
        properties = []
        if profile == "gametest":
            copy_verified(bridge, profile_mods / bridge.name)
            properties = ["-Dcodpattern.productionTestBootstrap=true", "-Dforge.gameTestServer=true",
                          "-Dforge.enableGameTest=true", "-Dforge.enabledGameTestNamespaces=" + args.game_test_namespaces]
        command = [args.java, *properties, *jvm, *launcher, "--gameDir", str(run)]
        (run / "command.json").write_text(json.dumps(command, indent=2) + "\n")
        (run / "eula.txt").write_text("eula=true\n")
        # Local test profile; bind only loopback and avoid querying authentication servers.
        (run / "server.properties").write_text("online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nmax-tick-time=0\n")
        script = write_launcher(run, command)
        profiles[profile] = {"directory": str(run), "launchScript": str(script), "command": command}
    manifest = {"preparationOnly": True, "readyToLaunch": True, "minecraft": MC,
                "forge": FORGE, "naming": "srg", "launchTarget": "forgeserver",
                "sourceMods": [{"path": str(p), "sha256": sha256(p)} for p in sources],
                "sourceLibraries": source_libraries,
                "patchedSrg": {"path": str(patched_srg), "sha256": sha256(patched_srg)},
                "serverBundle": {"path": str(server_bundle), "sha256": sha256(server_bundle)},
                "generatedRuntime": [{"path": str(p.relative_to(output)), "sha256": sha256(p)}
                                     for p in [mc_srg, mc_extra, forge_server, universal] + ([bridge] if bridge else [])],
                "gameTestDifference": "Only isolated bootstrap Mixin enables Forge production-disabled GameTest hooks" if bridge else None,
                "profiles": profiles}
    (output / "production-manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Prepared production/SRG test runtime: {output}")
    print(f"Profiles: {', '.join(profiles)}; CDP JAR supplied: {bool(args.cdp_jar)}")
    print("No Minecraft or Gradle process was started. These files are not evidence of a passed runtime test.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, ValueError, zipfile.BadZipFile, KeyError) as failure:
        print(f"Production preparation failed: {failure}", file=sys.stderr)
        raise SystemExit(1)
