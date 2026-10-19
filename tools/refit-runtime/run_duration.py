#!/usr/bin/env python3
"""Run the opt-in duration test on a normal, isolated production dedicated server.

Requires a ready production_prepare.py --gametest runtime. This deliberately uses
forge.gameTestServer=false: GameTestServer advances ticks faster than wall time.
Only this script's own Java child receives stop/terminate/kill requests.
"""

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import queue
import re
import shutil
import subprocess
import sys
import tempfile
import threading
import time

NAMESPACE = "codpattern_refit_duration"
BOOTSTRAP = "refit-production-test-bootstrap.jar"
TIMEOUT_SECONDS = 420
STOP_GRACE_SECONDS = 30
KILL_GRACE_SECONDS = 10
DURATION_RESULT = re.compile(r"Refit duration verified: elapsedTicks=(\d+) elapsedMillis=(\d+) "
                             r"actual INSTALL/UNLOAD/CANCEL succeeded")


def sha256(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def local_path(root, raw):
    path = (root / raw).resolve()
    if not path.is_relative_to(root) or path == root:
        raise ValueError(f"Expected a file inside the prepared runtime: {raw}")
    return path


def option(command, name):
    if command.count(name) != 1:
        raise ValueError(f"Prepared command must contain exactly one {name}")
    index = command.index(name) + 1
    if index == len(command):
        raise ValueError(f"Missing prepared command value for {name}")
    return index, command[index]


def replace_property(command, name, value):
    prefix = "-D" + name + "="
    matches = [i for i, arg in enumerate(command) if arg.startswith(prefix)]
    if len(matches) != 1:
        raise ValueError(f"Prepared command must contain exactly one {name} property")
    command[matches[0]] = prefix + value


def read_runtime(root):
    manifest_path = root / "production-manifest.json"
    manifest = json.loads(manifest_path.read_text())
    if (manifest.get("preparationOnly") is not True or manifest.get("readyToLaunch") is not True
            or manifest.get("naming") != "srg" or manifest.get("launchTarget") != "forgeserver"
            or not manifest.get("gameTestDifference")):
        raise ValueError("Expected a ready, isolated production/SRG GameTest preparation")
    profile = manifest.get("profiles", {}).get("gametest", {})
    gametest = local_path(root, profile.get("directory", ""))
    if gametest != root / "gametest":
        raise ValueError("GameTest profile is not the prepared runtime's isolated gametest directory")
    command = profile.get("command")
    if not isinstance(command, list) or not command or not all(isinstance(arg, str) for arg in command):
        raise ValueError("Missing prepared GameTest command")
    if json.loads((gametest / "command.json").read_text()) != command:
        raise ValueError("GameTest command differs from the preparation manifest; prepare again")
    if option(command, "--launchTarget")[1] != "forgeserver":
        raise ValueError("Only the production dedicated-server target is supported")
    if Path(option(command, "--gameDir")[1]).resolve() != gametest:
        raise ValueError("Prepared command points outside its isolated GameTest profile")
    if "-Dcodpattern.productionTestBootstrap=true" not in command:
        raise ValueError("The isolated GameTest bootstrap must be explicitly enabled")
    if any(arg in command for arg in ("--universe", "--world")):
        raise ValueError("Custom world paths are not supported by this isolated test runner")

    expected = {}
    for entry in manifest.get("sourceMods", []):
        name = Path(entry["path"]).name
        if name in expected or not name.endswith(".jar"):
            raise ValueError("Invalid or duplicate prepared source mod name")
        expected[name] = entry["sha256"]
    if not expected:
        raise ValueError("The preparation contains no source mods")
    for entry in manifest.get("generatedRuntime", []):
        artifact = local_path(root, entry["path"])
        if sha256(artifact) != entry["sha256"]:
            raise ValueError(f"Prepared runtime hash mismatch: {artifact.name}")
        if artifact.name == BOOTSTRAP:
            expected[BOOTSTRAP] = entry["sha256"]
    if BOOTSTRAP not in expected:
        raise ValueError("The preparation has no recorded test-only bootstrap")
    source_mods = local_path(root, "gametest/mods")
    if {p.name for p in source_mods.iterdir()} != set(expected):
        raise ValueError("Prepared GameTest mods contain missing or unmanaged files")
    for name, digest in expected.items():
        source = local_path(root, str(source_mods / name))
        if sha256(source) != digest:
            raise ValueError(f"Prepared source mod hash mismatch: {name}")
    return manifest_path, gametest, list(command), expected


def prepare_profile(root, manifest_path, gametest, command, expected):
    # Always use a new directory; an existing test or its logs are never overwritten.
    run = root / "duration-normal-server"
    try:
        run.mkdir()
    except FileExistsError:
        run = Path(tempfile.mkdtemp(prefix="duration-normal-server-", dir=root))
    mods = run / "mods"
    mods.mkdir()
    for name, digest in expected.items():
        target = mods / name
        shutil.copyfile(gametest / "mods" / name, target)
        if sha256(target) != digest:
            raise ValueError(f"Source mod changed while copying: {name}")
    gunpacks = gametest / "tacz"
    if gunpacks.is_dir():
        # Copy from the prepared test profile, even if it links to shared test fixtures.
        # This server must not modify the other profiles' gunpack/export state.
        shutil.copytree(gunpacks, run / "tacz")
    (run / "eula.txt").write_text("eula=true\n")
    (run / "server.properties").write_text(
        "online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\nmax-tick-time=0\n"
        "view-distance=2\nsimulation-distance=2\n")
    replace_property(command, "forge.gameTestServer", "false")
    replace_property(command, "forge.enabledGameTestNamespaces", NAMESPACE)
    command[option(command, "--gameDir")[0]] = str(run)
    (run / "command.json").write_text(json.dumps(command, indent=2) + "\n")
    (run / "duration-run.json").write_text(json.dumps({
        "testOnly": True, "createdAt": datetime.now(timezone.utc).isoformat(),
        "productionManifest": str(manifest_path), "productionManifestSha256": sha256(manifest_path),
        "namespace": NAMESPACE, "gameTestServer": False, "mods": expected,
        "timeoutSeconds": TIMEOUT_SECONDS, "command": command,
    }, indent=2) + "\n")
    return run


def request_stop(process):
    if process.poll() is None:
        try:
            process.stdin.write("stop\n")
            process.stdin.flush()
        except (BrokenPipeError, OSError):
            pass


def finish_child(process):
    if process.poll() is None:
        request_stop(process)
        try:
            return process.wait(timeout=STOP_GRACE_SECONDS)
        except subprocess.TimeoutExpired:
            process.terminate()
            try:
                return process.wait(timeout=KILL_GRACE_SECONDS)
            except subprocess.TimeoutExpired:
                process.kill()
    return process.wait(timeout=KILL_GRACE_SECONDS)


def run_test(run, command, log):
    log.parent.mkdir(parents=True, exist_ok=True)
    started = time.monotonic()
    passed = False
    elapsed_ticks = elapsed_millis = None
    reason = "server exited before a verified duration result"
    # Exclusive creation also prevents --log from overwriting an active run's log.
    with log.open("x", encoding="utf-8") as output:
        process = subprocess.Popen(command, cwd=run, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace",
                                   bufsize=1, start_new_session=True)
        lines = queue.Queue()

        def consume():
            try:
                for line in process.stdout:
                    lines.put(line)
            finally:
                lines.put(None)

        reader = threading.Thread(target=consume, daemon=True)
        reader.start()
        command_at = None
        try:
            while time.monotonic() - started < TIMEOUT_SECONDS:
                try:
                    line = lines.get(timeout=1)
                except queue.Empty:
                    if process.poll() is not None:
                        break
                    continue
                if line is None:
                    break
                output.write(line)
                output.flush()
                if "Done (" in line and command_at is None:
                    process.stdin.write("test runall\n")
                    process.stdin.flush()
                    command_at = time.monotonic()
                    print("Normal dedicated server ready; started the isolated duration test.", flush=True)
                if "Refit duration verified:" in line:
                    match = DURATION_RESULT.search(line)
                    if match:
                        elapsed_ticks, elapsed_millis = map(int, match.groups())
                        passed = (command_at is not None and elapsed_ticks > 2400 and elapsed_millis >= 120000
                                  and time.monotonic() - command_at >= 120)
                    reason = "duration verified" if passed else "duration marker did not prove more than 2400 ticks and 120 real seconds"
                    print(line.rstrip(), flush=True)
                    break
            else:
                reason = "duration test exceeded its 420-second startup-and-test deadline"
        except KeyboardInterrupt:
            reason = "interrupted; stopping only this runner's child server"
        except (BrokenPipeError, OSError) as failure:
            reason = f"server console failed: {failure}"
        finally:
            code = finish_child(process)
            reader.join(timeout=1)
            # Keep final shutdown output without allowing logging to extend the deadline.
            while True:
                try:
                    line = lines.get_nowait()
                except queue.Empty:
                    break
                if line is not None:
                    output.write(line)
            process.stdin.close()
            process.stdout.close()
    success = passed and code == 0
    result = {"passed": success, "reason": reason, "exitCode": code,
              "elapsedTicks": elapsed_ticks, "elapsedMillis": elapsed_millis,
              "runnerWallSeconds": round(time.monotonic() - started, 3), "log": str(log)}
    (run / "duration-result.json").write_text(json.dumps(result, indent=2) + "\n")
    print(f"Duration test passed={success}, exit={code}, reason={reason}; log={log}", flush=True)
    return 0 if success else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--runtime-dir", type=Path, required=True, help="Existing production_prepare.py --gametest output")
    parser.add_argument("--log", type=Path, help="New console log file; defaults to the new duration profile's console.log")
    args = parser.parse_args()
    root = args.runtime_dir.resolve(strict=True)
    manifest_path, gametest, command, expected = read_runtime(root)
    run = prepare_profile(root, manifest_path, gametest, command, expected)
    log = args.log.resolve() if args.log else run / "console.log"
    print(f"Starting normal-rate production duration test in {run}", flush=True)
    return run_test(run, command, log)


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, TypeError, subprocess.TimeoutExpired) as failure:
        print(f"Duration runner failed: {failure}", file=sys.stderr)
        raise SystemExit(1)
