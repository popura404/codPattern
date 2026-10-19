#!/usr/bin/env python3
"""Prepare an isolated Linux production/SRG client from a verified server runtime.

Never launches Minecraft, Gradle, or an external desktop program. Original mod
JARs remain byte-identical; --test-mod adds separately compiled test-only mods.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shlex
import shutil
import sys
import uuid
import zipfile

sys.dont_write_bytecode = True
from production_prepare import MC, FORGE, MCP, PROJECT, coordinate_path, copy_verified, one, plain_jar, sha256


def local(root, path):
    result = (root / path).resolve()
    if result == root or not result.is_relative_to(root):
        raise ValueError(f"Expected a file inside the prepared runtime: {path}")
    return result


def linux_allowed(library):
    rules = library.get("rules", [])
    allowed = not rules
    for rule in rules:
        os_rule = rule.get("os", {})
        if ("features" not in rule and os_rule.get("name", "linux") == "linux"
                and os_rule.get("arch", "x86_64") in ("x86_64", "amd64")
                and re.search(os_rule.get("version", ".*"), platform.release())):
            allowed = rule["action"] == "allow"
    return allowed


def artifact_from_cache(library, artifact, cache, game_libraries):
    relative = Path(artifact["path"])
    group, name, version, *_ = library["name"].split(":")
    candidates = [cache / "forge_gradle/maven_downloader" / relative]
    if game_libraries:
        candidates.append(game_libraries / relative)
    candidates.extend((cache / "modules-2/files-2.1" / group / name / version).glob("*/" + relative.name))
    source = next((path for path in candidates if path.is_file()), None)
    if source is None:
        raise ValueError(f"Missing cached client dependency (no download attempted): {artifact['path']}")
    with source.open("rb") as stream:
        if hashlib.file_digest(stream, "sha1").hexdigest() != artifact["sha1"]:
            raise ValueError(f"Cached client dependency hash mismatch: {source}")
    return source, relative


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime-dir", required=True, type=Path, help="Ready production_prepare.py output")
    parser.add_argument("--output", type=Path, default=PROJECT / "build/refit-client", help="New isolated output directory")
    parser.add_argument("--gradle-home", type=Path, default=Path.home() / ".gradle")
    parser.add_argument("--libraries", type=Path, help="Optional read-only game library cache fallback")
    parser.add_argument("--assets", type=Path, help="Existing assets root; defaults to the ForgeGradle cache")
    parser.add_argument("--test-mod", action="append", type=Path, default=[], help="Additional production/SRG test-only JAR")
    parser.add_argument("--cdp-jar", type=Path, help="Replace only CDP with a freshly built production/SRG artifact")
    parser.add_argument("--java", help="Override the prepared Java 17 executable")
    args = parser.parse_args()
    if sys.platform != "linux" or platform.machine() not in ("x86_64", "amd64"):
        raise ValueError("This preparation targets Linux x86_64 only")
    runtime = args.runtime_dir.resolve(strict=True)
    output = args.output.resolve()
    if output == runtime or runtime in output.parents or output in runtime.parents or output.exists():
        raise ValueError("Choose a new output directory separate from the prepared server runtime")
    cache = args.gradle_home.resolve() / "caches"
    assets = (args.assets or cache / "forge_gradle/assets").resolve()
    game_libraries = args.libraries.resolve() if args.libraries else None
    manifest_path = runtime / "production-manifest.json"
    manifest = json.loads(manifest_path.read_text())
    if (manifest.get("preparationOnly") is not True or manifest.get("readyToLaunch") is not True
            or manifest.get("launchTarget") != "forgeserver" or manifest.get("naming") != "srg"
            or manifest.get("minecraft") != MC or manifest.get("forge") != FORGE):
        raise ValueError("Expected a ready production/SRG server test preparation for the supported versions")
    for entry in manifest["generatedRuntime"]:
        if sha256(local(runtime, entry["path"])) != entry["sha256"]:
            raise ValueError(f"Prepared runtime hash mismatch: {entry['path']}")
    source_mods = []
    for entry in manifest["sourceMods"]:
        source = local(runtime, "mods/" + Path(entry["path"]).name)
        if sha256(source) != entry["sha256"]:
            raise ValueError(f"Prepared source mod hash mismatch: {source.name}")
        source_mods.append(source)
    if args.cdp_jar:
        replacement = args.cdp_jar.resolve(strict=True)
        def declares_cdp(path):
            with zipfile.ZipFile(path) as archive:
                if "META-INF/mods.toml" not in archive.namelist():
                    return False # Loader/Jar-in-Jar containers are not the CDP artifact.
                metadata = archive.read("META-INF/mods.toml").decode()
                return any(re.search(r'(?m)^\s*modId\s*=\s*[\"\x27]codpattern[\"\x27]', section)
                           for section in re.findall(r'(?ms)^\s*\[\[mods\]\]\s*\n(.*?)(?=^\s*\[|\Z)', metadata))
        if not declares_cdp(replacement):
            raise ValueError("The replacement JAR must declare the codpattern mod")
        original_cdp = [source for source in source_mods if declares_cdp(source)]
        if len(original_cdp) != 1:
            raise ValueError("Expected exactly one original CDP mod to replace")
        source_mods[source_mods.index(original_cdp[0])] = replacement
    test_mods = [path.resolve(strict=True) for path in args.test_mod]
    names = [path.name for path in source_mods + test_mods]
    if len(names) != len(set(names)):
        raise ValueError("Test mod filenames must not replace any original mod")
    for path in test_mods:
        with zipfile.ZipFile(path) as archive:
            if "META-INF/mods.toml" not in archive.namelist():
                raise ValueError(f"Test-only JAR has no Forge mod metadata: {path}")

    version_root = cache / "forge_gradle/minecraft_repo/versions" / MC
    version = json.loads((version_root / "version.json").read_text())
    asset_id = version["assetIndex"]["id"]
    asset_index = assets / "indexes" / (asset_id + ".json")
    objects = json.loads(asset_index.read_text())["objects"]
    missing_assets = [name for name, item in objects.items()
                      if not (assets / "objects" / item["hash"][:2] / item["hash"]).is_file()
                      or (assets / "objects" / item["hash"][:2] / item["hash"]).stat().st_size != item["size"]]
    if missing_assets:
        raise ValueError(f"Missing or incomplete client assets (no download attempted): {missing_assets[:10]}")
    dependencies = []
    for library in version["libraries"]:
        if not linux_allowed(library):
            continue
        downloads = library["downloads"]
        if "artifact" in downloads:
            dependencies.append(artifact_from_cache(library, downloads["artifact"], cache, game_libraries))
        native = library.get("natives", {}).get("linux")
        if native:
            dependencies.append(artifact_from_cache(library, downloads["classifiers"][native.replace("${arch}", "64")],
                                                    cache, game_libraries))

    output.mkdir(parents=True)
    libraries = output / "libraries"
    shutil.copytree(runtime / "libraries", libraries)
    classpath = []
    for line in (runtime / "legacy-classpath.txt").read_text().splitlines():
        source = local(runtime, line)
        if "/net/minecraft/server/" not in str(source) and not source.name.endswith("-server.jar"):
            classpath.append(libraries / source.relative_to(runtime / "libraries"))
    for source, relative in dependencies:
        target = copy_verified(source, libraries / relative)
        if target not in classpath:
            classpath.append(target)
    mc_relative = Path("net/minecraft/client") / f"{MC}-{MCP}"
    mc_srg = libraries / mc_relative / f"client-{MC}-{MCP}-srg.jar"
    server_srg = runtime / "libraries/net/minecraft/server" / f"{MC}-{MCP}" / f"server-{MC}-{MCP}-srg.jar"
    # This tested cache artifact contains both client and server Forge-patched MC
    # classes, already separated from Forge's universal module by production_prepare.
    copy_verified(server_srg, mc_srg)
    mc_extra = copy_verified(version_root / "client-extra.jar", libraries / mc_relative / f"client-{MC}-{MCP}-extra.jar")
    forge_client = libraries / coordinate_path(f"net.minecraftforge:forge:{MC}-{FORGE}:client")
    plain_jar(forge_client, [])
    classpath.extend([mc_srg, mc_extra, forge_client])
    (output / "legacy-classpath.txt").write_text("\n".join(map(str, classpath)) + "\n")
    natives = output / "natives"
    natives.mkdir()
    for source, _ in dependencies:
        if "natives-linux" not in source.name:
            continue
        with zipfile.ZipFile(source) as archive:
            for name in archive.namelist():
                if name.endswith(".so"):
                    (natives / Path(name).name).write_bytes(archive.read(name))

    run = output / "client"
    mods = run / "mods"
    mods.mkdir(parents=True)
    for source in source_mods + test_mods:
        copy_verified(source, mods / source.name)
    gunpacks = runtime / "gametest/tacz"
    if gunpacks.is_dir():
        shutil.copytree(gunpacks, run / "tacz")
    (run / "options.txt").write_text("renderDistance:2\nsimulationDistance:5\nmaxFps:60\nfullscreen:false\n"
                                    "enableVsync:false\npauseOnLostFocus:false\nguiScale:2\nsoundCategory_master:0.0\n"
                                    "onboardAccessibility:false\n")
    command = [arg.replace(str(runtime), str(output)) for arg in manifest["profiles"]["smoke"]["command"]]
    command = [arg for arg in command if arg != "--nogui"]
    command[0] = args.java or command[0]
    command[command.index("--launchTarget") + 1] = "forgeclient"
    command[command.index("--gameDir") + 1] = str(run)
    for index, arg in enumerate(command):
        if arg.startswith("-DignoreList="):
            command[index] = arg + ",client-"
    command[1:1] = ["-Djava.library.path=" + str(natives), "-Dorg.lwjgl.librarypath=" + str(natives),
                    "-Dmixin.debug.verbose=true"]
    command.extend(["--username", "CodPatternTest", "--version", "CDP-production-test", "--assetsDir", str(assets),
                    "--assetIndex", asset_id, "--uuid", str(uuid.uuid3(uuid.NAMESPACE_DNS, "OfflinePlayer:CodPatternTest")),
                    "--accessToken", "0", "--userType", "legacy", "--versionType", "release",
                    "--width", "1280", "--height", "720"])
    (run / "command.json").write_text(json.dumps(command, indent=2) + "\n")
    launch = run / "launch.sh"
    launch.write_text("#!/usr/bin/env bash\nset -euo pipefail\ncd " + shlex.quote(str(run)) + "\nexec " + shlex.join(command) + "\n")
    launch.chmod(0o755)
    named = one((cache / "forge_gradle/minecraft_user_repo/net/minecraftforge/forge").glob(
        f"{MC}-{FORGE}_mapped_parchment_*/*.jar"), "named Forge compile JAR")
    compile_classpath = [named] + [path for path in classpath if path not in (mc_srg, mc_extra, forge_client)]
    compile_classpath.extend(mods / source.name for source in source_mods)
    (output / "named-compile-classpath.txt").write_text(os.pathsep.join(map(str, compile_classpath)) + "\n")
    (output / "client-manifest.json").write_text(json.dumps({
        "preparationOnly": True, "testOnly": True, "readyToLaunch": True, "naming": "srg", "launchTarget": "forgeclient",
        "minecraft": MC, "forge": FORGE, "sourceRuntime": str(runtime), "sourceManifestSha256": sha256(manifest_path),
        "assets": str(assets), "assetIndex": asset_id, "assetCount": len(objects),
        "sourceMods": [{"path": str(source), "sha256": sha256(source)} for source in source_mods],
        "testOnlyMods": [{"path": str(source), "sha256": sha256(source)} for source in test_mods],
        "clientLibraries": [{"path": str(relative), "sha256": sha256(source)} for source, relative in dependencies],
        "clientRuntime": [{"path": str(path.relative_to(output)), "sha256": sha256(path)} for path in (mc_srg, mc_extra, forge_client)],
        "runDirectory": str(run), "command": command,
    }, indent=2) + "\n")
    print(f"Prepared isolated Linux production client: {output}")
    print(f"Launch manually: {launch}; named compiler classpath: {output / 'named-compile-classpath.txt'}")
    print("No game, Gradle, or desktop automation was started; preparation does not verify client behavior.")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, KeyError, zipfile.BadZipFile) as failure:
        print(f"Client preparation failed: {failure}", file=sys.stderr)
        raise SystemExit(1)
