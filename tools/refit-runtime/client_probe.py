#!/usr/bin/env python3
"""Compile an isolated SRG client test mod and optionally run its own bounded client."""

import argparse
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
import zipfile

sys.dont_write_bytecode = True
from production_prepare import PROJECT, copy_verified, one, sha256


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime-dir", required=True, type=Path, help="client_prepare.py output")
    parser.add_argument("--world", required=True, type=Path, help="Stopped isolated test world to copy")
    parser.add_argument("--gradle-home", type=Path, default=Path.home() / ".gradle")
    parser.add_argument("--prepare-only", action="store_true")
    parser.add_argument("--stage", choices=["world", "refit", "immediate-close", "duration", "hot-filter", "module", "large-catalog", "hsv"], default="refit")
    parser.add_argument("--slot", choices=["primary", "secondary"], default="primary")
    parser.add_argument("--java", default="java")
    parser.add_argument("--javac", default="javac")
    args = parser.parse_args()
    runtime = args.runtime_dir.resolve(strict=True)
    manifest_path = runtime / "client-manifest.json"
    manifest = json.loads(manifest_path.read_text())
    if not (manifest.get("testOnly") is True and manifest.get("readyToLaunch") is True
            and manifest.get("launchTarget") == "forgeclient" and manifest.get("naming") == "srg"):
        raise ValueError("Expected an isolated ready production client preparation")
    run = runtime / "client"
    if Path(manifest["runDirectory"]) != run:
        raise ValueError("Unexpected client run directory")
    for entry in manifest["sourceMods"] + manifest["testOnlyMods"]:
        if sha256(run / "mods" / Path(entry["path"]).name) != entry["sha256"]:
            raise ValueError("Prepared mod hash mismatch: " + entry["path"])
    for entry in manifest["clientRuntime"]:
        if sha256(runtime / entry["path"]) != entry["sha256"]:
            raise ValueError("Prepared Minecraft runtime hash mismatch")
    world = args.world.resolve(strict=True)
    if not (world / "level.dat").is_file():
        raise ValueError("Expected a stopped isolated world containing level.dat")
    target_world = run / "saves/refit-client-fixture"
    build = runtime / "client-test-bootstrap"
    build.mkdir(exist_ok=True)
    classes = build / "classes"
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir()
    compile_classpath = (runtime / "named-compile-classpath.txt").read_text().strip()
    sources = sorted((PROJECT / "tools/refit-runtime/client-test-src").rglob("*.java"))
    with (build / "compile.log").open("w") as log:
        subprocess.run([args.javac, "-proc:none", "-source", "17", "-target", "17", "-cp", compile_classpath,
                        "-d", str(classes), *map(str, sources)], stdout=log, stderr=subprocess.STDOUT, check=True)
    named_jar = build / "client-test-named.jar"
    with zipfile.ZipFile(named_jar, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\r\n\r\n")
        archive.writestr("pack.mcmeta", json.dumps({"pack": {"pack_format": 15, "description": "Isolated client test resources"}}))
        archive.writestr("META-INF/mods.toml", '''modLoader="javafml"
loaderVersion="[47,)"
license="Isolated local test only"
[[mods]]
modId="refitclienttests"
version="1"
displayName="Isolated CDP client integration probe"
''')
        for path in classes.rglob("*.class"):
            archive.write(path, path.relative_to(classes).as_posix())
    renamer = one((args.gradle_home / "caches/forge_gradle/maven_downloader/net/minecraftforge/ForgeAutoRenamingTool").glob(
        "*/*-all.jar"), "cached FART")
    jar = build / "refit-client-test-bootstrap.jar"
    command = [args.java, "-Xmx2G", "-jar", str(renamer), "--input", str(named_jar), "--output", str(jar),
               "--names", str(PROJECT / "build/createMcpToSrg/output.tsrg"), "--threads", "2", "--strip-sigs"]
    for library in compile_classpath.split(os.pathsep):
        command.extend(["--lib", library])
    with (build / "remap.log").open("w") as log:
        subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, check=True)
    copy_verified(jar, run / "mods" / jar.name)
    manifest["testOnlyMods"] = [entry for entry in manifest["testOnlyMods"] if Path(entry["path"]).name != jar.name]
    manifest["testOnlyMods"].append({"path": str(jar), "sha256": sha256(jar)})
    manifest_path.write_text(json.dumps(manifest, indent=2) + "\n")
    if not target_world.exists():
        shutil.copytree(world, target_world, ignore=shutil.ignore_patterns("session.lock"))
    results = run / "client-probe-runs" / str(time.time_ns())
    results.mkdir(parents=True)
    result_path = results / "result.json"
    command = list(manifest["command"])
    command[1:1] = ["-Dcodpattern.clientTestBootstrap=true", "-Dcodpattern.clientTestResult=" + str(result_path),
                    "-Dcodpattern.clientTestStage=" + args.stage, "-Dcodpattern.clientTestSlot=" + args.slot]
    (run / "client-probe-command.json").write_text(json.dumps(command, indent=2) + "\n")
    (results / "command.json").write_text(json.dumps(command, indent=2) + "\n")
    copy_verified(jar, results / jar.name)
    (results / "probe-run.json").write_text(json.dumps({
        "stage": args.stage, "slot": args.slot, "testBootstrapSha256": sha256(jar),
        "sourceMods": manifest["sourceMods"], "worldSource": str(world), "testOnly": True,
    }, indent=2) + "\n")
    print(f"Prepared client test bootstrap: {jar}", flush=True)
    if args.prepare_only:
        return
    console = results / "console.log"
    with console.open("x") as log:
        child = subprocess.Popen(command, cwd=run, stdout=log, stderr=subprocess.STDOUT)
        try:
            code = child.wait(timeout=540 if args.stage == "duration" else 360)
        except (subprocess.TimeoutExpired, KeyboardInterrupt):
            child.terminate()
            try:
                child.wait(timeout=15)
            except subprocess.TimeoutExpired:
                child.kill()
                child.wait(timeout=10)
            raise RuntimeError(f"Client probe timed out or was interrupted; inspect {console}")
    result = json.loads(result_path.read_text()) if result_path.is_file() else {}
    expected_phase = {"world": "world_ready", "refit": "refit_saved_reopened", "immediate-close": "immediate_close_saved",
                      "duration": "duration_saved", "hot-filter": "hot_filter_refreshed", "module": "module_saved_reopened",
                      "large-catalog": "large_catalog_last_page", "hsv": "hsv_saved_reopened"}[args.stage]
    if code != 0 or result.get("success") is not True or result.get("phase") != expected_phase:
        raise RuntimeError(f"Client probe did not pass (exit {code}); inspect {console} and {result_path}")
    print(json.dumps(result, indent=2))


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, KeyError, subprocess.CalledProcessError) as failure:
        print(f"Client probe failed: {failure}", file=sys.stderr)
        raise SystemExit(1)
