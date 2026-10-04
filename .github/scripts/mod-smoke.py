"""Exercise a packaged Fabric or NeoForge mod through its real production server."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import signal
import shutil
import subprocess
import sys
import threading
import time
import tomllib
import xml.etree.ElementTree as ET
import zipfile


LABELS = {"fabric": "Fabric", "neoforge": "NeoForge"}
FATAL = re.compile(r"Exception executing command|An unexpected error occurred trying to execute that command|Mixin apply failed|NoSuch(?:Method|Field)Error|AbstractMethodError|NoClassDefFoundError|IncompatibleClassChangeError|VerifyError")


def digest(path: Path, algorithm: str = "sha256") -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, algorithm).hexdigest()


def gradle_checksums(root: Path) -> dict[tuple[str, str, str, str], set[str]]:
    metadata = ET.parse(root / "gradle/verification-metadata.xml").getroot()
    namespace = {"v": "https://schema.gradle.org/dependency-verification"}
    checksums = {}
    for component in metadata.findall("v:components/v:component", namespace):
        coordinates = tuple(component.attrib[name] for name in ("group", "name", "version"))
        for artifact in component.findall("v:artifact", namespace):
            checksums[(*coordinates, artifact.attrib["name"])] = {
                checksum.attrib["value"] for checksum in artifact.findall("v:sha256", namespace)
            }
    return checksums


def require_pinned(path: Path, coordinate: tuple[str, str, str, str], checksums: dict) -> str:
    actual = digest(path)
    if actual not in checksums.get(coordinate, set()):
        raise RuntimeError(f"Production artifact lacks a matching pinned SHA-256: {path}")
    return actual


def library_coordinate(path: Path) -> tuple[str, str, str, str]:
    if len(path.parts) < 4:
        raise RuntimeError(f"Invalid Maven library path: {path}")
    return (".".join(path.parts[:-3]), *path.parts[-3:])


def maven_path(coordinate: str) -> str:
    parts = coordinate.split(":")
    if len(parts) not in (3, 4):
        raise RuntimeError(f"Unsupported installer coordinate: {coordinate}")
    group, name, version = parts[:3]
    classifier = "-" + parts[3] if len(parts) == 4 else ""
    return f"{group.replace('.', '/')}/{name}/{version}/{name}-{version}{classifier}.jar"


def manifest_path(value: str) -> str:
    path = Path(value)
    if any(character in value for character in ("\\", ":")) or value.startswith("/") or path.is_absolute() or ".." in path.parts or not value.endswith(".jar"):
        raise RuntimeError(f"Invalid installer library path: {value}")
    return path.as_posix()


def fabric_mod_paths(transcript: list[str], separator: str = os.pathsep) -> list[Path]:
    commands = [line for line in transcript if "Command:" in line and "-Dfabric.addMods=" in line]
    if len(commands) != 1:
        raise RuntimeError("Expected one actual Fabric production launch command")
    match = re.search(r"-Dfabric\.addMods=(.*?) -cp ", commands[0])
    if not match or not match.group(1):
        raise RuntimeError("Fabric production command did not expose its packaged mod paths")
    paths = [Path(value.strip('"')).resolve() for value in match.group(1).strip('"').split(separator)]
    if len(paths) != len(set(paths)):
        raise RuntimeError("Fabric production command contains duplicate mod paths")
    return paths


def installer_libraries(installer: Path, minecraft: str, neoforge: str) -> tuple[dict, set[str], dict[str, bytes]]:
    expected: dict[str, tuple[str, str]] = {}
    with zipfile.ZipFile(installer) as archive:
        profile = json.loads(archive.read("install_profile.json"))
        version = json.loads(archive.read("version.json"))
        if profile["minecraft"] != minecraft or version["id"] != f"neoforge-{neoforge}":
            raise RuntimeError("NeoForge installer does not match the requested game and loader")
        for library in profile["libraries"] + version["libraries"]:
            artifact = library.get("downloads", {}).get("artifact", {})
            if not artifact.get("url"):
                continue
            path = manifest_path(artifact["path"])
            sha1 = artifact["sha1"]
            if not re.fullmatch(r"[0-9a-f]{40}", sha1):
                raise RuntimeError(f"Invalid installer SHA-1: {path}")
            pin = ("sha1", sha1)
            if path in expected and expected[path] != pin:
                raise RuntimeError(f"Installer has conflicting library hashes: {path}")
            expected[path] = pin
        # PROCESS_MINECRAFT_JAR generates this exact output from verified inputs.
        # Downloaded libraries must retain their official pins; generated output is recorded separately.
        patched = profile["data"]["PATCHED"]["server"]
        if not (patched.startswith("[") and patched.endswith("]")):
            raise RuntimeError("NeoForge installer has an unexpected patched-server output")
        generated = {manifest_path(maven_path(patched[1:-1]))}
        launch_files = {name: archive.read(f"data/{name}") for name in ("win_args.txt", "unix_args.txt")}
    return expected, generated, launch_files


def verify_production(root: Path, run_dir: Path, gradle_home: Path, loader: str,
                      minecraft: str, artifact: Path, bundle: Path, versions: dict,
                      *, loaded_mods: list[Path] | None = None, expected_artifact_sha256: str | None = None) -> dict:
    checksums = gradle_checksums(root)
    actual_artifact_sha256 = digest(artifact)
    if expected_artifact_sha256 is not None and actual_artifact_sha256 != expected_artifact_sha256:
        raise RuntimeError("The packaged EClean artifact changed after smoke preflight")
    if loader == "fabric":
        if loaded_mods is None:
            raise RuntimeError("Fabric requires actual injected mod paths from its production command")
        if any((run_dir / "mods").glob("*.jar")):
            raise RuntimeError("Unexpected mod JARs in the isolated Fabric production fixture")
        mod_paths = {path.name: path.resolve() for path in loaded_mods}
        if len(mod_paths) != len(loaded_mods) or mod_paths.get(artifact.name) != artifact.resolve():
            raise RuntimeError("Fabric did not inject the exact final packaged EClean path")
    else:
        mod_paths = {path.name: path for path in (run_dir / "mods").glob("*.jar")}
    if artifact.name not in mod_paths or digest(mod_paths[artifact.name]) != actual_artifact_sha256:
        raise RuntimeError("Production mods directory does not contain the exact packaged EClean artifact")
    game_key = "mc2612" if minecraft == "26.1.2" else "mc262"
    dependencies = [("dev.architectury", f"architectury-{loader}", versions[f"architectury-api-{game_key}"])]
    if loader == "fabric":
        dependencies.append(("net.fabricmc.fabric-api", "fabric-api", versions[f"fabric-api-{game_key}"]))
    verified_mods = {artifact.name: actual_artifact_sha256}
    for group, name, version in dependencies:
        filename = f"{name}-{version}.jar"
        if filename not in mod_paths:
            raise RuntimeError(f"Production mod dependency is missing: {filename}")
        verified_mods[filename] = require_pinned(mod_paths[filename],
                                                (group, name, version, filename), checksums)
    if set(mod_paths) != set(verified_mods):
        raise RuntimeError("Unexpected mod JARs in the isolated production fixture")
    libraries = run_dir / "libraries"
    expected: dict[str, tuple[str, str]] = {}
    generated: set[str] = set()
    required: set[str] = set()
    installer_sha256 = None
    if loader == "neoforge":
        neoforge = versions[f"neoforge-{game_key}"]
        filename = f"neoforge-{neoforge}-installer.jar"
        cache = gradle_home / "caches/modules-2/files-2.1/net.neoforged/neoforge" / neoforge
        candidates = list(cache.glob(f"*/{filename}"))
        readonly_cache = os.environ.get("GRADLE_RO_DEP_CACHE")
        if not candidates and readonly_cache:
            cache = Path(readonly_cache) / "modules-2/files-2.1/net.neoforged/neoforge" / neoforge
            candidates = list(cache.glob(f"*/{filename}"))
        if not candidates:
            raise RuntimeError("Verified NeoForge production installer is missing from the Gradle cache")
        installer = candidates[0]
        installer_sha256 = require_pinned(installer, ("net.neoforged", "neoforge", neoforge, filename), checksums)
        expected, generated, launch_files = installer_libraries(installer, minecraft, neoforge)
        for name, content in launch_files.items():
            installed = libraries / f"net/neoforged/neoforge/{neoforge}/{name}"
            if installed.read_bytes() != content:
                raise RuntimeError(f"Production launcher differs from the pinned installer: {name}")
            required.update(manifest_path(path) for path in re.findall(r"libraries/([^\s:;]+\.jar)", content.decode("utf-8")))
        with zipfile.ZipFile(bundle) as archive:
            for line in archive.read("META-INF/libraries.list").decode("utf-8").splitlines():
                sha256, _, path = line.split("\t")
                path = manifest_path(path)
                # NeoForge may replace a vanilla library; its installer pin takes precedence.
                expected.setdefault(path, ("sha256", sha256))
        original = libraries / f"net/minecraft/server/{minecraft}/server-{minecraft}.jar"
        if digest(original) != digest(bundle):
            raise RuntimeError("NeoForge installed a different Mojang server bundle")
        expected[original.relative_to(libraries).as_posix()] = ("sha256", digest(bundle))
    checked = 0
    generated_hashes = {}
    found: set[str] = set()
    for library in libraries.rglob("*.jar"):
        relative = library.relative_to(libraries)
        path = relative.as_posix()
        found.add(path)
        if path in generated:
            with zipfile.ZipFile(library) as archive:
                if "net/minecraft/server/MinecraftServer.class" not in archive.namelist() or archive.testzip():
                    raise RuntimeError(f"Invalid generated Minecraft server JAR: {library}")
            generated_hashes[path] = digest(library)
        elif path in expected:
            algorithm, wanted = expected[path]
            if digest(library, algorithm) != wanted:
                raise RuntimeError(f"Production library differs from the installer/Mojang pin: {library}")
        else:
            require_pinned(library, library_coordinate(relative), checksums)
        checked += 1
    if checked == 0 or not generated.issubset(found):
        raise RuntimeError("Production runtime libraries or generated server are missing")
    if not required.issubset(found):
        raise RuntimeError("Production launcher references missing runtime libraries")
    return {"mods_sha256": verified_mods, "mod_paths": {name: str(path) for name, path in mod_paths.items()}, "libraries_checked": checked,
            "installer_sha256": installer_sha256, "generated_server_sha256": generated_hashes}


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("loader", choices=tuple(LABELS))
    parser.add_argument("minecraft", choices=("26.1.2", "26.2"))
    parser.add_argument("--gradle", type=Path)
    parser.add_argument("--gradle-user-home", type=Path)
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    run_dir = root / ".local" / f"{args.loader}-smoke" / args.minecraft
    run_dir.mkdir(parents=True, exist_ok=True)
    (run_dir / "smoke-console.log").write_text("", encoding="utf-8")
    report = {"loader": args.loader, "minecraft": args.minecraft, "passed": False,
              "checks": [], "error": None}
    try:
        exercise(args, root, run_dir, report)
        report["passed"] = True
    except BaseException as failure:
        report["error"] = f"{type(failure).__name__}: {failure}"
        raise
    finally:
        (run_dir / "smoke-report.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")


def exercise(args: argparse.Namespace, root: Path, run_dir: Path, report: dict) -> None:
    label = LABELS[args.loader]
    project_version = re.search(r'(?m)^\s*version = "([^"]+)"',
                                (root / "build.gradle.kts").read_text(encoding="utf-8")).group(1)
    artifact = root / args.loader / "build" / args.minecraft / "libs" / f"EClean-Modern-{project_version}-{args.loader}-mc{args.minecraft}.jar"
    if not artifact.is_file():
        raise RuntimeError(f"Build the final packaged artifact before running the smoke test: {artifact}")
    report["artifact"] = artifact.relative_to(root).as_posix()
    report["artifact_sha256"] = digest(artifact)
    versions = tomllib.loads((root / "gradle/libs.versions.toml").read_text(encoding="utf-8"))["versions"]
    # Keep downloaded libraries between runs, but each assertion needs a fresh disposable world.
    for name in ("smoke-world", "mods"):
        disposable = (run_dir / name).resolve()
        if disposable.parent != run_dir.resolve():
            raise RuntimeError("Disposable smoke directory resolved outside the fixture directory")
        if disposable.exists():
            shutil.rmtree(disposable)
    (run_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    # Native transports are irrelevant to these loopback console-only checks. NIO
    # also avoids Log4j probing unavailable Linux/BSD Netty classes on Windows.
    (run_dir / "server.properties").write_text(
        "online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n"
        "use-native-transport=false\n"
        "level-name=smoke-world\nlevel-type=minecraft:flat\n"
        'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"lakes":false,"features":false}\n'
        "generate-structures=false\nspawn-monsters=true\ndifficulty=normal\n"
        "view-distance=2\nsimulation-distance=2\nmax-players=1\n"
        "pause-when-empty-seconds=1\n"
        "sync-chunk-writes=false\n",
        encoding="utf-8",
    )
    config_dir = run_dir / "config" / "eclean"
    normal_dir = config_dir / "config" / "normal"
    normal_dir.mkdir(parents=True, exist_ok=True)
    (config_dir / "config.yml").write_text("profile: normal\n", encoding="utf-8")
    normal = (root / "common/src/main/resources/config/normal/config.yml").read_text(encoding="utf-8")
    normal = normal.replace("language: zh_cn", "language: en_us")
    normal = normal.replace("update:\n    enabled: true", "update:\n    enabled: false")
    normal = normal.replace("protectLore: false", "protectLore: true")
    normal = normal.replace("trashcan:\n  enabled: true", "trashcan:\n  enabled: true\n  despawnRecovery:\n    enabled: true\n    matchers: [DIAMOND]")
    normal = re.sub(r"(living:.*?  matchers:) \[\]", r'\1 ["ZOMBIE", "WOLF"]', normal, count=1, flags=re.DOTALL)
    normal = normal.replace("  entityLimits: {}", '  entityLimits:\n    "ZOMBIE": 1')
    (normal_dir / "config.yml").write_text(normal, encoding="utf-8")
    # Reuse the exact Mojang server bundle already verified/downloaded for compilation.
    gradle_home = args.gradle_user_home or Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle"))
    game_cache = gradle_home / "caches" / "fabric-loom" / args.minecraft
    server_bundle = game_cache / "minecraft-server.jar"
    manifest = game_cache / "mojang_minecraft_info.json"
    if not server_bundle.is_file() or not manifest.is_file():
        raise RuntimeError("The build's Mojang server bundle and version manifest are missing")
    expected_sha1 = json.loads(manifest.read_text(encoding="utf-8"))["downloads"]["server"]["sha1"]
    if digest(server_bundle, "sha1") != expected_sha1:
        raise RuntimeError("Mojang server checksum mismatch")
    if args.loader == "fabric":
        installer_cache = run_dir / ".fabric" / "server"
        installer_cache.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(server_bundle, installer_cache / f"{args.minecraft}-server.jar")
    else:
        installer_cache = run_dir / "libraries" / "net" / "minecraft" / "server" / args.minecraft
        installer_cache.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(server_bundle, installer_cache / f"server-{args.minecraft}.jar")

    gradle = args.gradle or root / ("gradlew.bat" if os.name == "nt" else "gradlew")
    command = [str(gradle), f":{args.loader}:prodServer", f"-PminecraftVersion={args.minecraft}",
               f"-P{args.loader}RunDirectory={run_dir.as_posix()}", "--console=plain", "--no-daemon"]
    if args.loader == "fabric":
        command.append("--info")  # Capture the actual -Dfabric.addMods paths used by Loom's launcher.
    if args.gradle_user_home:
        command.extend(("-g", str(args.gradle_user_home.resolve())))
    if args.offline:
        command.append("--offline")
    creation = {"creationflags": subprocess.CREATE_NEW_PROCESS_GROUP} if os.name == "nt" else {"start_new_session": True}
    process = subprocess.Popen(command, cwd=root, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace", **creation)
    lines: queue.Queue[str | None] = queue.Queue()
    transcript: list[str] = []

    def reader() -> None:
        assert process.stdout
        try:
            for line in process.stdout:
                transcript.append(line)
                print(line, end="", flush=True)
                lines.put(line)
        finally:
            lines.put(None)

    reader_thread = threading.Thread(target=reader, daemon=True)
    reader_thread.start()

    def wait_for(pattern: str, timeout: int = 45) -> re.Match[str]:
        matcher = re.compile(pattern)
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                line = lines.get(timeout=min(1, max(0.01, deadline - time.monotonic())))
            except queue.Empty:
                continue
            if line is None:
                raise RuntimeError(f"Server exited before matching {pattern!r}")
            if FATAL.search(line):
                raise RuntimeError(line.strip())
            match = matcher.search(line)
            if match:
                return match
        raise TimeoutError(f"Server output did not match {pattern!r} within {timeout} seconds")

    def send(command_text: str) -> None:
        assert process.stdin
        process.stdin.write(command_text + "\n")
        process.stdin.flush()

    def wait_cleanup(count: int, *, preview: bool = False) -> None:
        if preview:
            wait_for(rf"Cleanup preview .*Would remove {count} entities")
        else:
            wait_for(rf"Cleanup results .*Removed {count} entities")
        wait_for(r"(?:Failures|Failed removals): 0; skipped 0 chunk scans; status: Complete")

    try:
        wait_for(rf"EClean Modern {label} enabled", 360 if args.loader == "neoforge" else 240)
        report["production"] = verify_production(root, run_dir, gradle_home, args.loader,
                                                  args.minecraft, artifact, server_bundle, versions,
                                                  loaded_mods=fabric_mod_paths(transcript) if args.loader == "fabric" else None,
                                                  expected_artifact_sha256=report["artifact_sha256"])
        report["checks"].append("packaged_artifact_and_pinned_dependencies")
        # No clients connect. Automatic cleanup must keep the native game ticking after the
        # configured one-second empty-server pause threshold has elapsed.
        send("time query gametime")
        first_tick = int(wait_for(r"The (?:game )?time is (\d+)").group(1))
        time.sleep(2)
        send("time query gametime")
        second_tick = int(wait_for(r"The (?:game )?time is (\d+)").group(1))
        assert second_tick > first_tick, "Configured cleanup stopped ticking on the empty server"
        report["checks"].append("empty_server_keeps_ticking")
        send("gamerule minecraft:spawn_mobs false")
        wait_for(r"spawn_mobs.*false")
        send("eclean config validate")
        wait_for(r"Configuration normal and language files passed validation")
        send("eclean status all")
        wait_for(r"The server has .* entities in total")
        report["checks"].append("commands_and_config_validation")
        send("eclean clean trash")
        wait_for(r"Trash can cleared")
        send("forceload add 0 0")
        wait_for(r"Marked chunk|already marked|No chunks were marked")
        # A force-load ticket is asynchronous on a fresh world. Check the actual chunk without
        # loading it through EClean, then begin entity assertions only after vanilla exposes it.
        for _ in range(30):
            send("execute if loaded 0 80 0 run say ECLEAN_SMOKE_CHUNK_LOADED")
            try:
                wait_for(r"ECLEAN_SMOKE_CHUNK_LOADED", 2)
                break
            except TimeoutError:
                continue
        else:
            raise RuntimeError("Vanilla force-loaded fixture chunk never became available")
        send("kill @e[tag=eclean_smoke]")
        send("kill @e[tag=eclean_protected]")
        send("kill @e[tag=eclean_natural_expiry]")
        send("kill @e[tag=eclean_named]")
        send("kill @e[tag=eclean_ordinary]")
        send("kill @e[tag=eclean_tamed]")
        send('summon minecraft:item 0 80 0 {Item:{id:"minecraft:gold_ingot",count:1,components:{"minecraft:lore":[{text:"Protected"}]}},Tags:["eclean_protected"],NoGravity:1b}')
        wait_for(r"Summoned new")
        send('summon minecraft:item 0 80 0 {Item:{id:"minecraft:stone",count:3,components:{"minecraft:custom_name":{text:"EClean smoke"}}},Tags:["eclean_smoke"],NoGravity:1b}')
        wait_for(r"Summoned new")
        send("execute if entity @e[tag=eclean_smoke] run say ECLEAN_SMOKE_DROP_EXISTS")
        wait_for(r"ECLEAN_SMOKE_DROP_EXISTS")
        send("eclean entity minecraft:item minecraft:overworld 0 0")
        wait_for(r"ITEM @|minecraft:item @")
        send("eclean top entity 5 minecraft:overworld")
        wait_for(r"Ranked by Count")
        send("eclean clean drop minecraft:overworld --preview")
        wait_cleanup(1, preview=True)
        send("execute if entity @e[tag=eclean_smoke] run say ECLEAN_SMOKE_PREVIEW_PRESERVED")
        wait_for(r"ECLEAN_SMOKE_PREVIEW_PRESERVED")
        send("eclean clean drop minecraft:overworld")
        wait_cleanup(1)
        send("execute unless entity @e[tag=eclean_smoke] run say ECLEAN_SMOKE_DROP_REMOVED")
        wait_for(r"ECLEAN_SMOKE_DROP_REMOVED")
        send("execute if entity @e[tag=eclean_protected] run say ECLEAN_SMOKE_LORE_PROTECTED")
        wait_for(r"ECLEAN_SMOKE_LORE_PROTECTED")
        send("eclean clean trash --preview")
        wait_for(r"The trash can contains 3 items")
        send("eclean reload")
        wait_for(r"Configuration and language files reloaded")
        send("eclean clean trash --preview")
        wait_for(r"The trash can contains 3 items")
        send('summon minecraft:item 0 80 0 {Item:{id:"minecraft:diamond",count:2},Tags:["eclean_natural_expiry"],Age:5999s,NoGravity:1b}')
        report["checks"].append("preview_cleanup_lore_protection_metadata_and_reload_storage")
        wait_for(r"Summoned new")
        send("tick sprint 40t")
        wait_for(r"Sprint completed")
        send("execute unless entity @e[tag=eclean_natural_expiry] run say ECLEAN_SMOKE_DEFAULT_EXPIRY_RECOVERED")
        wait_for(r"ECLEAN_SMOKE_DEFAULT_EXPIRY_RECOVERED")
        send("eclean clean trash --preview")
        wait_for(r"The trash can contains 5 items")
        clear_count = 5
        if args.loader == "neoforge":
            # Freeze before summoning so the three-tick fixture can be asserted before expiry.
            send("tick freeze")
            wait_for(r"(?i)frozen")
            send('summon minecraft:item 0 80 0 {Item:{id:"minecraft:diamond",count:2},Tags:["eclean_short_expiry"],Age:1s,Lifespan:3,NoGravity:1b}')
            wait_for(r"Summoned new")
            send("execute if entity @e[tag=eclean_short_expiry] run say ECLEAN_SMOKE_SHORT_LIFESPAN_EXISTS")
            wait_for(r"ECLEAN_SMOKE_SHORT_LIFESPAN_EXISTS")
            send("tick unfreeze")
            wait_for(r"(?i)running normally")
            send("tick sprint 40t")
            wait_for(r"Sprint completed")
            send("execute unless entity @e[tag=eclean_short_expiry] run say ECLEAN_SMOKE_SHORT_LIFESPAN_RECOVERED")
            wait_for(r"ECLEAN_SMOKE_SHORT_LIFESPAN_RECOVERED")
            send("eclean clean trash --preview")
            wait_for(r"The trash can contains 7 items")
            send('summon minecraft:item 0 80 0 {Item:{id:"minecraft:diamond",count:2},Tags:["eclean_long_expiry"],Age:5999s,Lifespan:7000,NoGravity:1b}')
            wait_for(r"Summoned new")
            send("tick sprint 40t")
            wait_for(r"Sprint completed")
            send("execute if entity @e[tag=eclean_long_expiry] run say ECLEAN_SMOKE_EXTENDED_LIFESPAN_PRESERVED")
            wait_for(r"ECLEAN_SMOKE_EXTENDED_LIFESPAN_PRESERVED")
            send("eclean clean trash --preview")
            wait_for(r"The trash can contains 7 items")
            send("tick sprint 1200t")
            wait_for(r"Sprint completed")
            send("execute unless entity @e[tag=eclean_long_expiry] run say ECLEAN_SMOKE_EXTENDED_LIFESPAN_RECOVERED")
            wait_for(r"ECLEAN_SMOKE_EXTENDED_LIFESPAN_RECOVERED")
            send("eclean clean trash --preview")
            wait_for(r"The trash can contains 9 items")
            report["checks"].append("custom_lifespan_and_delayed_expiry")
            clear_count = 9
        send("eclean clean trash")
        wait_for(rf"Trash can cleared\. Removed {clear_count} items")
        report["checks"].append("natural_expiry_recovery_and_stack_counts")
        send('summon minecraft:zombie 1 80 1 {Tags:["eclean_named"],NoAI:1b,Invulnerable:1b,CustomName:{text:"Protected zombie"}}')
        wait_for(r"Summoned new")
        send("eclean entity minecraft:zombie minecraft:overworld")
        wait_for(r"x: 0\.\.15, z: 0\.\.15")
        send('summon minecraft:zombie 1 80 1 {Tags:["eclean_ordinary"],NoAI:1b,Invulnerable:1b}')
        wait_for(r"Summoned new")
        send('summon minecraft:wolf 1 80 1 {Tags:["eclean_tamed"],Owner:[I;0,0,0,1],NoAI:1b,Invulnerable:1b}')
        wait_for(r"Summoned new")
        send("execute if entity @e[tag=eclean_ordinary] if entity @e[tag=eclean_named] if entity @e[tag=eclean_tamed] run say ECLEAN_SMOKE_LIVING_FIXTURE_EXISTS")
        wait_for(r"ECLEAN_SMOKE_LIVING_FIXTURE_EXISTS")
        send("eclean clean entity minecraft:overworld --preview")
        wait_cleanup(1, preview=True)
        send("execute if entity @e[tag=eclean_ordinary] run say ECLEAN_SMOKE_LIVING_PREVIEW_PRESERVED")
        wait_for(r"ECLEAN_SMOKE_LIVING_PREVIEW_PRESERVED")
        send("eclean clean entity minecraft:overworld")
        wait_cleanup(1)
        send("execute unless entity @e[tag=eclean_ordinary] run say ECLEAN_SMOKE_LIVING_REMOVED")
        wait_for(r"ECLEAN_SMOKE_LIVING_REMOVED")
        send("execute if entity @e[tag=eclean_named] run say ECLEAN_SMOKE_NAMED_PROTECTED")
        wait_for(r"ECLEAN_SMOKE_NAMED_PROTECTED")
        send("execute if entity @e[tag=eclean_tamed] run say ECLEAN_SMOKE_TAMED_PROTECTED")
        wait_for(r"ECLEAN_SMOKE_TAMED_PROTECTED")
        report["checks"].append("living_preview_cleanup_named_and_tamed_protection")
        for _ in range(3):
            send('summon minecraft:zombie 1 80 1 {Tags:["eclean_ordinary"],NoAI:1b,Invulnerable:1b}')
            wait_for(r"Summoned new")
        send("scoreboard objectives add ecleanSmoke dummy")
        send("execute store result score fixture ecleanSmoke run execute if entity @e[tag=eclean_ordinary]")
        send("execute if score fixture ecleanSmoke matches 3 run say ECLEAN_SMOKE_DENSE_FIXTURE_EXISTS")
        wait_for(r"ECLEAN_SMOKE_DENSE_FIXTURE_EXISTS")
        send("eclean clean chunk minecraft:overworld --preview")
        wait_cleanup(2, preview=True)
        send("eclean clean chunk minecraft:overworld")
        wait_cleanup(2)
        send("execute store result score remaining ecleanSmoke run execute if entity @e[tag=eclean_ordinary]")
        send("execute if score remaining ecleanSmoke matches 1 run say ECLEAN_SMOKE_DENSE_LIMIT_RETAINED")
        wait_for(r"ECLEAN_SMOKE_DENSE_LIMIT_RETAINED")
        send("execute if entity @e[tag=eclean_named] run say ECLEAN_SMOKE_DENSE_NAMED_PROTECTED")
        wait_for(r"ECLEAN_SMOKE_DENSE_NAMED_PROTECTED")
        report["checks"].append("density_preview_limit_and_named_protection")
        send("eclean config effective")
        before_revision = wait_for(r"Active configuration: profile normal, revision (\d+)").group(1)
        (normal_dir / "config.yml").write_text("global: [broken YAML\n", encoding="utf-8")
        send("eclean reload")
        wait_for(r"The previous configuration remains active")
        send("eclean config effective")
        after_revision = wait_for(r"Active configuration: profile normal, revision (\d+)").group(1)
        assert after_revision == before_revision, "A rejected reload changed the active configuration revision"
        (normal_dir / "config.yml").write_text(normal, encoding="utf-8")
        send("eclean reload")
        wait_for(r"Configuration and language files reloaded")
        report["checks"].append("rejected_reload_preserves_active_revision")
        send("stop")
        wait_for(rf"EClean Modern {label} stopped")
        process.wait(timeout=90)
        report["exit_code"] = process.returncode
        if process.returncode != 0:
            raise RuntimeError(f"{label} production run exited with code {process.returncode}")
        reader_thread.join(timeout=2)
        report["artifact_sha256_after"] = digest(artifact)
        if report["artifact_sha256_after"] != report["artifact_sha256"]:
            raise RuntimeError("The final packaged artifact changed during the production smoke")
        failures = [line.strip() for line in transcript if FATAL.search(line)]
        if failures:
            raise RuntimeError(f"Production console reported a fatal error: {failures[0]}")
        report["checks"].append("clean_shutdown")
        print(f"{label} {args.minecraft} packaged-mod smoke test passed.")
    finally:
        if process.poll() is None:
            try:
                send("stop")
                process.wait(timeout=20)
            except (OSError, subprocess.TimeoutExpired):
                if os.name == "nt":
                    subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], check=False,
                                   stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                else:
                    os.killpg(process.pid, signal.SIGTERM)
                process.wait(timeout=10)
        report["exit_code"] = process.returncode
        reader_thread.join(timeout=2)
        (run_dir / "smoke-console.log").write_text("".join(transcript), encoding="utf-8")


if __name__ == "__main__":
    main()
