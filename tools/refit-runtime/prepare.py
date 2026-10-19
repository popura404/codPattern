#!/usr/bin/env python3
"""Prepare local, mapped copies of the actual refit compatibility JARs.

This script never runs Gradle or Minecraft and never changes the source mods.
Arcana's original loader and transformation services are retained; only its
embedded mod JAR is replaced by a Minecraft-name-remapped copy of that same JAR.
"""

import argparse
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import struct
import subprocess
import sys
import zipfile


PROJECT = Path(__file__).resolve().parents[2]
ARCANA_SERVICES = (
    "META-INF/services/cpw.mods.modlauncher.api.ITransformationService",
    "META-INF/services/net.minecraftforge.forgespi.locating.IModLocator",
)


def digest(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def one(paths, description):
    matches = sorted(set(Path(path).resolve() for path in paths if Path(path).is_file()))
    if len(matches) != 1:
        raise RuntimeError(f"Expected one {description}; found {len(matches)}: {matches}")
    return matches[0]


def property_value(name):
    for line in (PROJECT / "gradle.properties").read_text().splitlines():
        if line.startswith(name + "="):
            return line.split("=", 1)[1].strip()
    raise RuntimeError(f"Missing gradle.properties value: {name}")


def validate_mod(path, modid, version):
    with zipfile.ZipFile(path) as archive:
        metadata = archive.read("META-INF/mods.toml").decode()
        if "${file.jarVersion}" in metadata:
            manifest = archive.read("META-INF/MANIFEST.MF").decode()
            declared_version = re.search(r"(?m)^Implementation-Version:\s*(.+)$", manifest)
            if declared_version:
                metadata = metadata.replace("${file.jarVersion}", declared_version.group(1).strip())
    if not re.search(rf'modId\s*=\s*["\']{re.escape(modid)}["\']', metadata):
        raise RuntimeError(f"{path.name} does not declare mod {modid}")
    if not re.search(rf'version\s*=\s*["\']{re.escape(version)}["\']', metadata):
        raise RuntimeError(f"{path.name} does not declare tested version {version}")


def remap(java, renamer, mappings, source, target, libraries, logs):
    command = [java, "-Xmx2G", "-jar", str(renamer), "--input", str(source),
               "--output", str(target), "--names", str(mappings), "--threads", "2",
               "--strip-sigs"]
    for library in dict.fromkeys(libraries):
        command.extend(["--lib", str(library)])
    log = logs / (target.stem + ".log")
    print(f"Remapping {source.name}; log: {log}", flush=True)
    with log.open("w") as output:
        completed = subprocess.run(command, stdout=output, stderr=subprocess.STDOUT, check=False)
    if completed.returncode:
        raise RuntimeError(f"JAR remapping failed ({completed.returncode}); inspect {log}")


def write_refmap_srg(mappings, target):
    """Mixin 0.8.5's RemappingReferenceMapper reads SRG, not TSRG2."""
    lines = mappings.read_text().splitlines()
    classes = {line.split()[0]: line.split()[1] for line in lines[1:]
               if line and not line.startswith("\t")}

    def map_descriptor(descriptor):
        return re.sub(r"L([^;]+);", lambda match: "L" + classes.get(match[1], match[1]) + ";", descriptor)

    owner = mapped_owner = None
    with target.open("w") as output:
        for line in lines[1:]:
            if not line or line.startswith("\t\t"):
                continue
            parts = line.split()
            if not line.startswith("\t"):
                owner, mapped_owner = parts[:2]
                output.write(f"CL: {owner} {mapped_owner}\n")
            elif len(parts) == 2:
                output.write(f"FD: {owner}/{parts[0]} {mapped_owner}/{parts[1]}\n")
            elif len(parts) == 3 and parts[1].startswith("("):
                output.write(f"MD: {owner}/{parts[0]} {parts[1]} {mapped_owner}/{parts[2]} {map_descriptor(parts[1])}\n")
            else:
                raise RuntimeError(f"Unsupported TSRG2 member: {line}")


def remap_mixin_self_references(mapped_jar, mappings):
    """FART cannot infer that a mixin-owned @Shadow member belongs to its target.

    Minecraft SRG member IDs are globally unique. Apply only those verified IDs
    to UTF8 constants in @Mixin classes: this repairs declarations, self-owner
    references and annotation selectors together without changing business names,
    descriptors, constant-pool indices, refmaps, or native code.
    """
    names = {}
    for line in mappings.read_text().splitlines()[1:]:
        if not line.startswith("\t") or line.startswith("\t\t"):
            continue
        parts = line.split()
        if len(parts) not in (2, 3):
            continue
        old, new = parts[0].encode(), parts[-1].encode()
        if not re.fullmatch(rb"[fm]_\d+_", old):
            continue
        if old in names and names[old] != new:
            raise RuntimeError(f"Ambiguous SRG member ID: {old!r}")
        names[old] = new
    member = re.compile(rb"(?<![A-Za-z0-9_$])[fm]_\d+_(?![A-Za-z0-9_$])")
    counts = {}

    def rewrite_class(data, name):
        if b"Lorg/spongepowered/asm/mixin/Mixin;" not in data:
            return data
        if data[:4] != b"\xca\xfe\xba\xbe":
            raise RuntimeError(f"Invalid class header: {name}")
        pool_count = struct.unpack_from(">H", data, 8)[0]
        result = bytearray(data[:10])
        offset, index, changed = 10, 1, 0
        lengths = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4,
                   11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
        while index < pool_count:
            tag = data[offset]
            result.append(tag)
            offset += 1
            if tag == 1:
                length = struct.unpack_from(">H", data, offset)[0]
                offset += 2
                value = data[offset:offset + length]
                rewritten = member.sub(lambda match: names.get(match[0], match[0]), value)
                changed += rewritten != value
                result.extend(struct.pack(">H", len(rewritten)))
                result.extend(rewritten)
                offset += length
            elif tag in lengths:
                length = lengths[tag]
                result.extend(data[offset:offset + length])
                offset += length
                if tag in (5, 6):
                    index += 1
            else:
                raise RuntimeError(f"Unknown constant-pool tag {tag} in {name}")
            index += 1
        result.extend(data[offset:])
        if changed:
            counts[name] = changed
        return bytes(result)

    temporary = mapped_jar.with_suffix(".mixin-tmp.jar")
    try:
        with zipfile.ZipFile(mapped_jar) as original, zipfile.ZipFile(temporary, "w") as rebuilt:
            for entry in original.infolist():
                data = original.read(entry)
                if entry.filename.endswith(".class"):
                    data = rewrite_class(data, entry.filename)
                rebuilt.writestr(entry, data)
        temporary.replace(mapped_jar)
    finally:
        temporary.unlink(missing_ok=True)
    return counts


def repack_arcana(source, mapped_inner, target):
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(target, "w") as rebuilt:
        for entry in original.infolist():
            rebuilt.writestr(entry, mapped_inner.read_bytes() if entry.filename == "META-INF/mod.jar"
                             else original.read(entry))
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(target) as rebuilt:
        if original.namelist() != rebuilt.namelist():
            raise RuntimeError("Arcana loader entries changed unexpectedly")
        for name in original.namelist():
            if name != "META-INF/mod.jar" and original.read(name) != rebuilt.read(name):
                raise RuntimeError(f"Arcana outer loader was changed: {name}")
        for service in ARCANA_SERVICES:
            if not rebuilt.read(service).strip():
                raise RuntimeError(f"Missing Arcana loader service: {service}")


def verify_inner(original_path, mapped_path):
    with zipfile.ZipFile(original_path) as original, zipfile.ZipFile(mapped_path) as mapped:
        required = ["group/taczexpands/dist/jlkX1CVq.class",
                    "group/taczexpands/dist/Gcj8PhOz.class",
                    "group/taczexpands/common/coremod/PluginWrapper.class",
                    "taczexpands.common.mixins.json", "taczexpands.server.mixins.json"]
        for name in required:
            mapped.getinfo(name)
        for name in original.namelist():
            if name.startswith("META-INF/natives/") or name.endswith(".mixins.json") or name.endswith(".refmap.json"):
                if original.read(name) != mapped.read(name):
                    raise RuntimeError(f"Arcana non-bytecode runtime resource changed: {name}")
        manager = mapped.read("group/taczexpands/dist/jlkX1CVq.class")
        # The actual obfuscated business methods must survive the MC-only rename.
        for marker in (b"nwLBQEs8", b"oVcwyFJh", b"giveItemToPlayer", b"getInventory"):
            if marker not in manager:
                # GunManager does not itself call getInventory; the packet handler does.
                if marker == b"getInventory":
                    handler = mapped.read("group/taczexpands/server/mixin/MixinClientMessageRefitGun.class")
                    if marker in handler:
                        continue
                raise RuntimeError(f"Missing expected remapped Arcana reference: {marker!r}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mods", required=True, type=Path, help="Read-only source game mods directory")
    parser.add_argument("--gradle-home", type=Path, default=Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")))
    parser.add_argument("--output", type=Path, default=PROJECT / "build/refit-runtime")
    parser.add_argument("--java", default="java")
    parser.add_argument("--with-addon", action="store_true", help="Also use the actual Addon 1.1.6 JAR")
    args = parser.parse_args()
    source_mods = args.mods.resolve()
    output = args.output.resolve()
    if output == source_mods or output in source_mods.parents or source_mods in output.parents:
        raise RuntimeError("Runtime preparation must be outside the source mods directory")

    tacz = one(source_mods.glob("*tacz-1.20.1-1.1.8-hotfix2.jar"), "TaCZ 1.1.8-hotfix2 JAR")
    arcana = one(source_mods.glob("Arcana-1.1.4-loader-hybrid.jar"), "Arcana 1.1.4 loader JAR")
    kotlin = one(source_mods.glob("kotlinforforge-4.12.0-all.jar"), "KotlinForForge 4.12.0 JAR")
    addon = one(source_mods.glob("taczaddon-1.20.1-1.1.6.jar"), "Addon 1.1.6 JAR") if args.with_addon else None
    validate_mod(tacz, "tacz", "1.1.8-hotfix2")
    if addon:
        validate_mod(addon, "taczaddon", "1.1.6")

    cache = args.gradle_home.resolve() / "caches/forge_gradle"
    mc = property_value("minecraft_version")
    forge = property_value("forge_version")
    mapping_channel = property_value("mapping_channel")
    mapping_version = property_value("mapping_version")
    renamer = one(cache.glob("maven_downloader/net/minecraftforge/ForgeAutoRenamingTool/*/*-all.jar"), "cached FART tool")
    mappings = one(cache.glob(f"minecraft_user_repo/de/oceanlabs/mcp/mcp_config/{mc}-*/srg_to_{mapping_channel}_{mapping_version}.tsrg"), "SRG to development mapping")
    forge_srg = one(cache.glob(f"minecraft_user_repo/net/minecraftforge/forge/{mc}-{forge}/forge-{mc}-{forge}-srg.jar"), "Forge SRG inheritance JAR")
    originals = output / "originals"
    runtime = output / "runtime"
    logs = output / "logs"
    for directory in (originals, runtime, logs):
        directory.mkdir(parents=True, exist_ok=True)
    (output / "manifest.json").unlink(missing_ok=True)  # No partially prepared bundle can pass the init-script gate.
    refmap_srg = output / "refmap-srg-to-development.srg"
    write_refmap_srg(mappings, refmap_srg)

    with zipfile.ZipFile(arcana) as archive:
        for service in ARCANA_SERVICES:
            archive.getinfo(service)
        inner_bytes = archive.read("META-INF/mod.jar")
    original_inner = originals / "arcana-inner-1.1.4.jar"
    original_inner.write_bytes(inner_bytes)
    validate_mod(original_inner, "taczexpands", "1.1.4")
    with zipfile.ZipFile(io.BytesIO(inner_bytes)) as archive:
        nested_libraries = []
        for name in archive.namelist():
            if name.startswith("META-INF/jarjar/") and name.endswith(".jar"):
                path = originals / Path(name).name
                path.write_bytes(archive.read(name))
                nested_libraries.append(path)
    libraries = [forge_srg, tacz, kotlin, *nested_libraries]
    mapped_inner = output / "arcana-inner-1.1.4-mapped.jar"
    remap(args.java, renamer, mappings, original_inner, mapped_inner, libraries, logs)
    mixin_repairs = remap_mixin_self_references(mapped_inner, mappings)
    (logs / "arcana-mixin-member-remap.json").write_text(json.dumps(mixin_repairs, indent=2) + "\n")
    verify_inner(original_inner, mapped_inner)
    mapped_loader = runtime / "Arcana-1.1.4-loader-hybrid-mapped.jar"
    repack_arcana(arcana, mapped_inner, mapped_loader)
    mapped_tacz = runtime / "tacz-1.20.1-1.1.8-hotfix2-mapped.jar"
    remap(args.java, renamer, mappings, tacz, mapped_tacz, [forge_srg], logs)
    runtime_kotlin = runtime / kotlin.name
    shutil.copyfile(kotlin, runtime_kotlin)
    artifacts = [mapped_loader, mapped_tacz, runtime_kotlin]
    addon_target = runtime / "taczaddon-1.20.1-1.1.6-mapped.jar"
    if addon:
        remap(args.java, renamer, mappings, addon, addon_target, [forge_srg, tacz, original_inner], logs)
        artifacts.append(addon_target)
    else:
        addon_target.unlink(missing_ok=True)
    sources = [tacz, arcana, kotlin] + ([addon] if addon else [])
    manifest = {
        "format": 1,
        "minecraft": mc,
        "forge": forge,
        "mapping": f"{mapping_channel}_{mapping_version}",
        "withAddon": bool(addon),
        "sourceJars": [{"path": str(path), "sha256": digest(path)} for path in sources],
        "runtimeJars": [{"path": str(path.relative_to(output)), "sha256": digest(path)} for path in artifacts],
        "originalInnerSha256": hashlib.sha256(inner_bytes).hexdigest(),
        "mappedInnerSha256": digest(mapped_inner),
        "mixinSelfReferenceRepairs": mixin_repairs,
        "mappings": {"path": str(mappings), "sha256": digest(mappings)},
        "refmapMappings": {"path": str(refmap_srg), "sha256": digest(refmap_srg)},
        "renamer": {"path": str(renamer), "sha256": digest(renamer)},
        "preparationOnly": True,
    }
    (output / "manifest.json").write_text(json.dumps(manifest, indent=2) + "\n")
    print(f"Prepared actual-JAR runtime bundle: {output}")
    print("This verifies the bundle structure, not that Minecraft or the GameTests have passed.")
    print("Run from the project directory:")
    print(f"./gradlew --offline -I tools/refit-runtime/runtime.init.gradle runGameTestServer "
          f"-PrefitRuntimeDir='{output}' -PgameTestNamespaces=codpattern_refit "
          "-PgameTestRunDir=build/refit-arcana-gametest -PwithTaczAddon=false --console=plain")


if __name__ == "__main__":
    try:
        main()
    except (OSError, RuntimeError, zipfile.BadZipFile, KeyError) as error:
        print(f"Preparation failed: {error}", file=sys.stderr)
        sys.exit(1)
