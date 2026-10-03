"""Exercise the packaged server mod through a real Fabric server console."""

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
import xml.etree.ElementTree as ET


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("minecraft", choices=("26.1.2", "26.2"))
    parser.add_argument("--gradle", type=Path)
    parser.add_argument("--gradle-user-home", type=Path)
    parser.add_argument("--offline", action="store_true")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    run_dir = root / ".local" / "fabric-smoke" / args.minecraft
    run_dir.mkdir(parents=True, exist_ok=True)
    # Keep downloaded libraries between runs, but each assertion needs a fresh disposable world.
    world_dir = (run_dir / "smoke-world").resolve()
    if world_dir.parent != run_dir.resolve():
        raise RuntimeError("Smoke world resolved outside the fixture directory")
    if world_dir.exists():
        shutil.rmtree(world_dir)
    (run_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (run_dir / "server.properties").write_text(
        "online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n"
        "level-name=smoke-world\nlevel-type=minecraft:flat\n"
        'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"lakes":false,"features":false}\n'
        "generate-structures=false\nspawn-monsters=false\n"
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
    if server_bundle.is_file() and manifest.is_file():
        expected_sha1 = json.loads(manifest.read_text(encoding="utf-8"))["downloads"]["server"]["sha1"]
        with server_bundle.open("rb") as source:
            assert hashlib.file_digest(source, "sha1").hexdigest() == expected_sha1, "Mojang server checksum mismatch"
        installer_cache = run_dir / ".fabric" / "server"
        installer_cache.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(server_bundle, installer_cache / f"{args.minecraft}-server.jar")

    gradle = args.gradle or root / ("gradlew.bat" if os.name == "nt" else "gradlew")
    command = [str(gradle), ":fabric:prodServer", f"-PminecraftVersion={args.minecraft}",
               f"-PfabricRunDirectory={run_dir.as_posix()}", "--console=plain", "--no-daemon"]
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

    threading.Thread(target=reader, daemon=True).start()

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
            if "Exception executing command" in line or "Mixin apply failed" in line:
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

    def verify_production_libraries() -> None:
        # The installer maintains a separate runtime library directory. Check its actual JARs
        # against the same exact coordinates and SHA-256 pins used for the compile classpath.
        metadata = ET.parse(root / "gradle" / "verification-metadata.xml").getroot()
        namespace = {"v": "https://schema.gradle.org/dependency-verification"}
        checksums: dict[tuple[str, str, str, str], set[str]] = {}
        for component in metadata.findall("v:components/v:component", namespace):
            coordinates = tuple(component.attrib[name] for name in ("group", "name", "version"))
            for artifact in component.findall("v:artifact", namespace):
                checksums[(*coordinates, artifact.attrib["name"])] = {
                    checksum.attrib["value"] for checksum in artifact.findall("v:sha256", namespace)
                }
        libraries = run_dir / "libraries"
        for library in libraries.rglob("*.jar"):
            path = library.relative_to(libraries).parts
            coordinate = (".".join(path[:-3]), path[-3], path[-2], path[-1])
            with library.open("rb") as source:
                digest = hashlib.file_digest(source, "sha256").hexdigest()
            if digest not in checksums.get(coordinate, set()):
                raise RuntimeError(f"Production library lacks a matching pinned SHA-256: {library}")

    try:
        wait_for(r"EClean Modern Fabric enabled", 240)
        verify_production_libraries()
        # No clients connect. Automatic cleanup must keep the native game ticking after the
        # configured one-second empty-server pause threshold has elapsed.
        send("time query gametime")
        first_tick = int(wait_for(r"The (?:game )?time is (\d+)").group(1))
        time.sleep(2)
        send("time query gametime")
        second_tick = int(wait_for(r"The (?:game )?time is (\d+)").group(1))
        assert second_tick > first_tick, "Configured cleanup stopped ticking on the empty server"
        send("eclean config validate")
        wait_for(r"Configuration normal and language files passed validation")
        send("eclean status all")
        wait_for(r"The server has .* entities in total")
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
        wait_for(r"Summoned new")
        send("tick sprint 40t")
        wait_for(r"Sprint completed")
        send("eclean clean trash --preview")
        wait_for(r"The trash can contains 5 items")
        send("eclean clean trash")
        wait_for(r"Trash can cleared\. Removed 5 items")
        send('summon minecraft:zombie 1 80 1 {Tags:["eclean_named"],NoAI:1b,Invulnerable:1b,CustomName:{text:"Protected zombie"}}')
        wait_for(r"Summoned new")
        send("eclean entity minecraft:zombie minecraft:overworld")
        wait_for(r"x: 0\.\.15, z: 0\.\.15")
        send('summon minecraft:zombie 1 80 1 {Tags:["eclean_ordinary"],NoAI:1b,Invulnerable:1b}')
        wait_for(r"Summoned new")
        send('summon minecraft:wolf 1 80 1 {Tags:["eclean_tamed"],Owner:[I;0,0,0,1],NoAI:1b,Invulnerable:1b}')
        wait_for(r"Summoned new")
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
        for _ in range(3):
            send('summon minecraft:zombie 1 80 1 {Tags:["eclean_ordinary"],NoAI:1b,Invulnerable:1b}')
            wait_for(r"Summoned new")
        send("eclean clean chunk minecraft:overworld --preview")
        wait_cleanup(2, preview=True)
        send("eclean clean chunk minecraft:overworld")
        wait_cleanup(2)
        send("scoreboard objectives add ecleanSmoke dummy")
        send("execute store result score remaining ecleanSmoke run execute if entity @e[tag=eclean_ordinary]")
        send("execute if score remaining ecleanSmoke matches 1 run say ECLEAN_SMOKE_DENSE_LIMIT_RETAINED")
        wait_for(r"ECLEAN_SMOKE_DENSE_LIMIT_RETAINED")
        send("execute if entity @e[tag=eclean_named] run say ECLEAN_SMOKE_DENSE_NAMED_PROTECTED")
        wait_for(r"ECLEAN_SMOKE_DENSE_NAMED_PROTECTED")
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
        send("stop")
        wait_for(r"EClean Modern Fabric stopped")
        process.wait(timeout=90)
        if process.returncode != 0:
            raise RuntimeError(f"Fabric production run exited with code {process.returncode}")
        print(f"Fabric {args.minecraft} packaged-mod smoke test passed.")
    finally:
        (run_dir / "smoke-console.log").write_text("".join(transcript), encoding="utf-8")
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


if __name__ == "__main__":
    main()
