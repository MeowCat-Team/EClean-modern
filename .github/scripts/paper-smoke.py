"""Verify a packaged EClean plugin on an actual, checksum-verified Paper server."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import shutil
import signal
import subprocess
import sys
import threading
import time
import urllib.request
import urllib.error
import zipfile


USER_AGENT = "EClean-compatibility-audit/0.3.5 (https://github.com/MeowCat-Team/EClean-modern)"
API = "https://fill.papermc.io/v3/projects/paper"
VERSION = re.compile(r"\d+(?:\.\d+){1,2}")
FATAL = re.compile(
    r"Exception executing command|Error occurred while (?:enabling|disabling) EClean|"
    r"Could not load .*EClean|InvalidPluginException|Unsupported API version|"
    r"NoSuchMethodError|NoSuchFieldError|AbstractMethodError|IncompatibleClassChangeError|VerifyError|UnsupportedClassVersionError|"
    r"Unsupported class file major version|NoClassDefFoundError|"
    r"ClassNotFoundException: org\.meowcat\.eclean"
)
ANSI = re.compile(r"\x1b\[[0-?]*[ -/]*[@-~]")


def checksum(path: Path) -> str:
    with path.open("rb") as source:
        return hashlib.file_digest(source, "sha256").hexdigest()


def api_json(url: str) -> object:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return json.load(response)
        except urllib.error.HTTPError as error:
            if error.code < 500 and error.code != 429 or attempt == 2:
                raise
        except urllib.error.URLError:
            if attempt == 2:
                raise
        time.sleep(attempt + 1)
    raise RuntimeError("Paper metadata request failed")


def resolve_server(args: argparse.Namespace, cache: Path) -> tuple[Path, dict]:
    """Pin the official build, download URL and SHA before starting any server process."""
    cache.mkdir(parents=True, exist_ok=True)
    if args.offline:
        records = []
        for path in cache.glob(f"paper-{args.minecraft}-*.json"):
            metadata = json.loads(path.read_text(encoding="utf-8"))
            if args.build is None or metadata["id"] == args.build:
                records.append(metadata)
    else:
        rows = api_json(f"{API}/versions/{args.minecraft}/builds")
        if not isinstance(rows, list):
            raise RuntimeError(f"Paper returned invalid build metadata: {rows!r}")
        records = [row for row in rows if args.build is None or row["id"] == args.build]
    permitted = [row for row in records if row.get("channel") == "STABLE" or args.allow_unstable]
    if not permitted:
        raise RuntimeError(f"No permitted Paper build for {args.minecraft}; unstable builds require --allow-unstable")
    metadata = max(permitted, key=lambda row: row["id"])
    download = metadata["downloads"]["server:default"]
    name = download["name"]
    if not isinstance(name, str) or Path(name).name != name or "/" in name or "\\" in name:
        raise RuntimeError("Official server metadata download name must be a basename")
    cached_server = (cache / name).resolve()
    if cached_server.parent != cache.resolve():
        raise RuntimeError("Official server metadata download path escaped the server cache")
    expected = download["checksums"]["sha256"]
    if not re.fullmatch(r"[a-f0-9]{64}", expected):
        raise RuntimeError("Official server metadata lacks a valid SHA-256")
    if args.expected_sha256 is not None and expected != args.expected_sha256.lower():
        raise RuntimeError("Official Paper checksum differs from the pinned expected SHA-256")
    server = args.server_jar.resolve() if args.server_jar else cached_server
    if not server.is_file():
        if args.offline or args.server_jar:
            raise FileNotFoundError(server)
        partial = server.with_suffix(".part")
        request = urllib.request.Request(download["url"], headers={"User-Agent": USER_AGENT})
        try:
            with urllib.request.urlopen(request, timeout=90) as response, partial.open("wb") as output:
                shutil.copyfileobj(response, output)
            if checksum(partial) != expected or partial.stat().st_size != download["size"]:
                raise RuntimeError("Downloaded Paper server does not match its official checksum/size")
            partial.replace(server)
        finally:
            partial.unlink(missing_ok=True)
    if checksum(server) != expected or server.stat().st_size != download["size"]:
        raise RuntimeError(f"Paper server checksum/size mismatch: {server}")
    (cache / f"paper-{args.minecraft}-{metadata['id']}.json").write_text(
        json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(f"Verified Paper {args.minecraft} build {metadata['id']} ({metadata['channel']}): {server}", flush=True)
    return server, metadata


def copy_plugin(source: Path, target: Path, override: str | None) -> dict:
    """A lower-bound audit may change the loader gate, never implementation bytecode."""
    with zipfile.ZipFile(source) as original:
        entries = original.infolist()
        if len({entry.filename for entry in entries}) != len(entries):
            raise RuntimeError("Plugin JAR contains duplicate entries")
        descriptor = original.read("plugin.yml").decode("utf-8")
        declared = re.search(r"(?m)^api-version:\s*['\"]?([^'\"\s]+)", descriptor)
        if declared is None:
            raise RuntimeError("Plugin JAR does not declare api-version")
        if override is None:
            shutil.copyfile(source, target)
        else:
            changed, count = re.subn(r"(?m)^api-version:.*$", f"api-version: '{override}'", descriptor)
            if count != 1:
                raise RuntimeError("Expected exactly one api-version declaration")
            with zipfile.ZipFile(target, "w") as result:
                for entry in entries:
                    result.writestr(copy.copy(entry), changed.encode("utf-8") if entry.filename == "plugin.yml"
                                    else original.read(entry.filename))
        with zipfile.ZipFile(target) as result:
            if set(result.namelist()) != set(original.namelist()):
                raise RuntimeError("Audit copy changed the JAR entry set")
            for entry in entries:
                if override is not None and entry.filename == "plugin.yml":
                    continue
                if original.read(entry.filename) != result.read(entry.filename):
                    raise RuntimeError(f"Audit copy changed payload {entry.filename}")
        return {"source": str(source), "source_sha256": checksum(source),
                "tested_sha256": checksum(target), "declared_api_version": declared.group(1),
                "api_version_override": override, "class_payloads_unchanged": True,
                "class_count": sum(entry.filename.endswith(".class") for entry in entries)}


def remove_fixture(path: Path, run_dir: Path) -> None:
    resolved = path.resolve()
    if resolved.parent != run_dir.resolve() or resolved == run_dir.resolve():
        raise RuntimeError(f"Refusing to remove a path outside the smoke fixture: {resolved}")
    if resolved.exists():
        shutil.rmtree(resolved)


def main() -> None:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("minecraft", help="Exact stable Minecraft version, for example 1.21.8 or 26.2")
    parser.add_argument("--build", type=int, help="Pin an official Paper build number")
    parser.add_argument("--plugin", type=Path, help="Packaged shaded Paper plugin; defaults to the newest build artifact")
    parser.add_argument("--server-jar", type=Path, help="Existing official server JAR, still verified against API metadata")
    parser.add_argument("--expected-sha256", help="Optional independently pinned server SHA-256 for CI/audits")
    parser.add_argument("--java", default=shutil.which("java") or "java")
    parser.add_argument("--api-version-override", help="Audited test copy only; never changes original plugin/class bytes")
    parser.add_argument("--offline", action="store_true", help="Use an already verified cached server/build record")
    parser.add_argument("--allow-unstable", action="store_true", help="Explicitly test an experimental Paper build")
    parser.add_argument("--download-only", action="store_true")
    parser.add_argument("--load-only", action="store_true", help="Check startup/status/config validation without entity scenarios")
    args = parser.parse_args()
    if not VERSION.fullmatch(args.minecraft):
        parser.error("minecraft must be an exact numeric release version")
    if args.api_version_override and not VERSION.fullmatch(args.api_version_override):
        parser.error("api-version override must be a numeric version")
    if args.expected_sha256 and not re.fullmatch(r"[a-fA-F0-9]{64}", args.expected_sha256):
        parser.error("expected-sha256 must be a 64-digit hexadecimal checksum")
    root = Path(__file__).resolve().parents[2]
    server, metadata = resolve_server(args, root / ".local" / "paper-compatibility" / "servers")
    if args.download_only:
        return
    if args.plugin is None:
        candidates = list((root / "paper/build/libs").glob("EClean-Modern-*-paper.jar"))
        if not candidates:
            parser.error("No packaged Paper plugin found; build it or pass --plugin")
        args.plugin = max(candidates, key=lambda path: path.stat().st_mtime)
    plugin = args.plugin.resolve()
    if not plugin.is_file():
        parser.error(f"Plugin does not exist: {plugin}")
    run_dir = root / ".local" / "paper-smoke" / args.minecraft
    run_dir.mkdir(parents=True, exist_ok=True)
    for name in ("smoke-world", "smoke-world_nether", "smoke-world_the_end", "plugins"):
        remove_fixture(run_dir / name, run_dir)
    plugins = run_dir / "plugins"
    plugins.mkdir()
    plugin_audit = copy_plugin(plugin, plugins / "EClean-Modern-smoke.jar", args.api_version_override)
    (run_dir / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    (run_dir / "server.properties").write_text(
        "online-mode=false\nserver-ip=127.0.0.1\nserver-port=0\n"
        "level-name=smoke-world\nlevel-type=minecraft:flat\n"
        'generator-settings={"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},'
        '{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],'
        '"lakes":false,"features":false}\n'
        "generate-structures=false\nspawn-monsters=true\ndifficulty=normal\nallow-nether=false\n"
        "view-distance=2\nsimulation-distance=2\nmax-players=1\n"
        "pause-when-empty-seconds=-1\nsync-chunk-writes=false\n",
        encoding="utf-8",
    )
    (run_dir / "bukkit.yml").write_text("settings:\n  allow-end: false\n", encoding="utf-8")
    config_dir = plugins / "EClean-Modern"
    normal_dir = config_dir / "config" / "normal"
    normal_dir.mkdir(parents=True)
    (config_dir / "config.yml").write_text("profile: normal\n", encoding="utf-8")
    normal = (root / "common/src/main/resources/config/normal/config.yml").read_text(encoding="utf-8")
    normal = normal.replace("language: zh_cn", "language: en_us")
    normal = normal.replace("protectLore: false", "protectLore: true")
    normal = normal.replace("clearIntervalSeconds: 600", "clearIntervalSeconds: null")
    normal = normal.replace("trashcan:\n  enabled: true", "trashcan:\n  enabled: true\n  despawnRecovery:\n    enabled: true\n    matchers: [DIAMOND]")
    normal = re.sub(r"(living:.*?  matchers:) \[\]", r'\1 ["ZOMBIE", "WOLF"]', normal, count=1, flags=re.DOTALL)
    normal = normal.replace("  entityLimits: {}", '  entityLimits:\n    "ZOMBIE": 1')
    normal = normal.replace("update:\n    enabled: true", "update:\n    enabled: false\n  bStats:\n    enabled: false")
    (normal_dir / "config.yml").write_text(normal, encoding="utf-8")
    command = [args.java, "-Xms512M", "-Xmx2G", "-Dfile.encoding=UTF-8",
               "-Dterminal.jline=false", "-Dterminal.ansi=false", "-jar", str(server), "--nogui"]
    creation = {"creationflags": subprocess.CREATE_NEW_PROCESS_GROUP} if os.name == "nt" else {"start_new_session": True}
    process = subprocess.Popen(command, cwd=run_dir, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                               stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace", **creation)
    lines: queue.Queue[str | None] = queue.Queue()
    transcript: list[str] = []
    completed: list[str] = []
    report = {"minecraft": args.minecraft, "paper_build": metadata["id"], "channel": metadata["channel"],
              "server_sha256": metadata["downloads"]["server:default"]["checksums"]["sha256"],
              "plugin": plugin_audit, "mode": "load-only" if args.load_only else "full", "passed": False,
              "started_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()), "checks": completed}

    def reader() -> None:
        assert process.stdout
        try:
            for line in process.stdout:
                transcript.append(line)
                print(line, end="", flush=True)
                lines.put(ANSI.sub("", line))
        finally:
            lines.put(None)

    thread = threading.Thread(target=reader, daemon=True)
    thread.start()

    def wait_for(pattern: str, timeout: int = 45) -> re.Match[str]:
        matcher = re.compile(pattern)
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                line = lines.get(timeout=min(1, max(0.01, deadline - time.monotonic())))
            except queue.Empty:
                continue
            if line is None:
                raise RuntimeError(f"Paper exited before matching {pattern!r} (exit {process.poll()})")
            if FATAL.search(line):
                raise RuntimeError(line.strip())
            match = matcher.search(line)
            if match:
                return match
        raise TimeoutError(f"Paper output did not match {pattern!r} within {timeout} seconds")

    def send(text: str) -> None:
        assert process.stdin
        process.stdin.write(text + "\n")
        process.stdin.flush()

    def marker(condition: str, label: str) -> None:
        send(f"execute {condition} run say ECLEAN_PAPER_{label}")
        wait_for("ECLEAN_PAPER_" + label)

    def cleanup(kind: str, count: int, preview: bool = False) -> None:
        send(f"eclean clean {kind} smoke-world" + (" --preview" if preview else ""))
        wait_for(rf"Cleanup preview .*Would remove {count} entities" if preview else
                 rf"Cleanup results .*Removed {count} entities")
        wait_for(r"(?:Failures|Failed removals): 0; skipped 0 chunk scans; status: Complete")

    game_version = tuple(map(int, args.minecraft.split(".")))
    modern_text = game_version >= (1, 21, 5)
    mob_rule = "minecraft:spawn_mobs" if game_version >= (1, 21, 11) else "doMobSpawning"
    lore = '{text:"Protected"}' if modern_text else "'{\"text\":\"Protected\"}'"
    custom = '{text:"EClean smoke"}' if modern_text else "'{\"text\":\"EClean smoke\"}'"
    name = '{text:"Protected zombie"}' if modern_text else "'{\"text\":\"Protected zombie\"}'"
    try:
        wait_for(r"EClean-Modern enabled\. Author: 404E", 360)
        wait_for(r"Done \(", 180)
        completed.append("packaged-plugin-load")
        send("eclean config validate")
        wait_for(r"Configuration normal and language files passed validation")
        send("eclean status all")
        wait_for(r"Server-wide Statistics")
        wait_for(r"The server has \d+ entities in total")
        completed.append("configuration-and-status")
        if not args.load_only:
            # Disable natural spawning while allowing the mobs summoned for protection tests.
            # Legacy Paper rejects COMMAND spawns when spawn-monsters is false.
            send(f"gamerule {mob_rule} false")
            wait_for(r"[Gg]ame ?rule .* is now set to: false")
            send("forceload add 0 0")
            for _ in range(30):
                send("execute if loaded 0 80 0 run say ECLEAN_PAPER_CHUNK_LOADED")
                try:
                    wait_for("ECLEAN_PAPER_CHUNK_LOADED", 2)
                    break
                except TimeoutError:
                    continue
            else:
                raise RuntimeError("Paper fixture chunk did not become available")
            send('summon minecraft:item 0 80 0 {Item:{id:"minecraft:gold_ingot",count:1,components:{"minecraft:lore":[' + lore + ']}},Tags:["eclean_protected"],NoGravity:1b}')
            wait_for(r"Summoned new")
            send('summon minecraft:item 0 80 0 {Item:{id:"minecraft:stone",count:3,components:{"minecraft:custom_name":' + custom + '}},Tags:["eclean_smoke"],NoGravity:1b}')
            wait_for(r"Summoned new")
            marker('if entity @e[tag=eclean_smoke]', "DROP_EXISTS")
            send("eclean entity ITEM smoke-world 0 0")
            wait_for(r"ITEM @")
            send("eclean top entity 5 smoke-world")
            wait_for(r"Ranked by Count")
            cleanup("drop", 1, preview=True)
            marker('if entity @e[tag=eclean_smoke]', "PREVIEW_PRESERVED")
            cleanup("drop", 1)
            marker('unless entity @e[tag=eclean_smoke]', "DROP_REMOVED")
            marker('if entity @e[tag=eclean_protected]', "LORE_PROTECTED")
            send("eclean clean trash --preview")
            wait_for(r"The trash can contains 3 items")
            completed.append("drop-preview-protection-and-recovery")
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
            completed.append("natural-expiry-recovery-and-reload-retention")
            send('summon minecraft:zombie 1 80 1 {Tags:["eclean_named"],NoAI:1b,PersistenceRequired:1b,Invulnerable:1b,CustomName:' + name + '}')
            wait_for(r"Summoned new")
            send('summon minecraft:zombie 1 80 1 {Tags:["eclean_ordinary"],NoAI:1b,PersistenceRequired:1b,Invulnerable:1b}')
            wait_for(r"Summoned new")
            send('summon minecraft:wolf 1 80 1 {Tags:["eclean_tamed"],Owner:[I;0,0,0,1],NoAI:1b,Invulnerable:1b}')
            wait_for(r"Summoned new")
            marker('if entity @e[tag=eclean_ordinary]', "LIVING_FIXTURE_EXISTS")
            marker('if entity @e[tag=eclean_named]', "NAMED_FIXTURE_EXISTS")
            marker('if entity @e[tag=eclean_tamed]', "TAMED_FIXTURE_EXISTS")
            cleanup("entity", 1, preview=True)
            marker('if entity @e[tag=eclean_ordinary]', "LIVING_PREVIEW_PRESERVED")
            cleanup("entity", 1)
            marker('unless entity @e[tag=eclean_ordinary]', "LIVING_REMOVED")
            marker('if entity @e[tag=eclean_named]', "NAMED_PROTECTED")
            marker('if entity @e[tag=eclean_tamed]', "TAMED_PROTECTED")
            completed.append("living-preview-name-and-tame-protection")
            for _ in range(3):
                send('summon minecraft:zombie 1 80 1 {Tags:["eclean_ordinary"],NoAI:1b,PersistenceRequired:1b,Invulnerable:1b}')
                wait_for(r"Summoned new")
            send("scoreboard objectives add ecleanSmoke dummy")
            send("execute store result score initial ecleanSmoke run execute if entity @e[tag=eclean_ordinary]")
            marker("if score initial ecleanSmoke matches 3", "DENSITY_FIXTURE_COUNT")
            cleanup("chunk", 2, preview=True)
            cleanup("chunk", 2)
            send("execute store result score remaining ecleanSmoke run execute if entity @e[tag=eclean_ordinary]")
            marker("if score remaining ecleanSmoke matches 1", "DENSE_LIMIT_RETAINED")
            marker('if entity @e[tag=eclean_named]', "DENSE_NAMED_PROTECTED")
            completed.append("density-limit-and-name-protection")
            send("eclean config effective")
            before = wait_for(r"Active configuration: profile normal, revision (\d+)").group(1)
            (normal_dir / "config.yml").write_text("global: [broken YAML\n", encoding="utf-8")
            send("eclean reload")
            wait_for(r"The previous configuration remains active")
            send("eclean config effective")
            after = wait_for(r"Active configuration: profile normal, revision (\d+)").group(1)
            if before != after:
                raise RuntimeError("Rejected reload changed the active configuration revision")
            (normal_dir / "config.yml").write_text(normal, encoding="utf-8")
            send("eclean reload")
            wait_for(r"Configuration and language files reloaded")
            completed.append("failed-reload-preserves-active-revision")
        send("stop")
        wait_for(r"EClean-Modern disabled")
        process.wait(timeout=90)
        thread.join(timeout=5)
        if process.returncode != 0:
            raise RuntimeError(f"Paper exited with code {process.returncode}")
        unexpected = [line.strip() for line in transcript if FATAL.search(ANSI.sub("", line))]
        if unexpected:
            raise RuntimeError(f"Compatibility errors were logged: {unexpected}")
        completed.append("clean-plugin-shutdown")
        report["passed"] = True
        print(f"Paper {args.minecraft} build {metadata['id']} packaged-plugin smoke passed.", flush=True)
    except BaseException as error:
        report["failure"] = f"{type(error).__name__}: {error}"
        raise
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
        thread.join(timeout=5)
        (run_dir / "smoke-console.log").write_text("".join(transcript), encoding="utf-8")
        report["exit_code"] = process.returncode
        report["finished_utc"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        (run_dir / "smoke-report.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
