"""Rename existing EClean Modrinth versions; defaults to a read-only plan.

Only project VW7EmMIj and the name/version_number fields are writable.
API: https://docs.modrinth.com/api/operations/modifyversion/
"""

from __future__ import annotations

import argparse
from dataclasses import dataclass
import json
import os
import re
import sys
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


PROJECT_ID = "VW7EmMIj"
API = "https://api.modrinth.com/v2"
GAMES = {"26.1.2", "26.2"}
USER_AGENT = "EClean-Modern/version-names (https://github.com/MeowCat-Team/EClean-modern)"
GUARDED_FIELDS = (
    "id", "project_id", "files", "dependencies", "loaders", "game_versions", "changelog",
    "version_type", "featured", "status", "requested_status", "environment", "date_published", "author_id",
)


class RenameError(Exception):
    """An error safe to print without credentials or response bodies."""


@dataclass(frozen=True)
class Rename:
    before: dict[str, Any]
    name: str
    number: str

    @property
    def id(self) -> str:
        return self.before["id"]

    @property
    def changed(self) -> bool:
        return self.before["name"] != self.name or self.before["version_number"] != self.number

    def payload(self) -> dict[str, str]:
        return {"name": self.name, "version_number": self.number}


class Modrinth:
    def __init__(self, token: str | None = None):
        self.token = token

    def request(self, method: str, path: str, payload: dict[str, str] | None = None) -> Any:
        # URLs and payload fields cannot be supplied by workflow inputs or API responses.
        if path != f"/project/{PROJECT_ID}/version" and not re.fullmatch(r"/version/[A-Za-z0-9]{1,32}", path):
            raise RenameError("Refusing an unexpected Modrinth API path")
        if method not in {"GET", "PATCH"} or (method == "GET" and payload is not None) or (
            method == "PATCH" and (not path.startswith("/version/") or not self.token or set(payload or {}) != {"name", "version_number"})
        ):
            raise RenameError("Refusing an unexpected Modrinth operation")
        headers = {"User-Agent": USER_AGENT, "Accept": "application/json"}
        if self.token:
            headers["Authorization"] = self.token
        data = None
        if payload is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(payload).encode("utf-8")
        try:
            with urlopen(Request(API + path, data=data, headers=headers, method=method), timeout=30) as response:
                content = response.read()
                return json.loads(content) if content else None
        except HTTPError as failure:
            # Never print response bodies, request headers, tokens, or raw network errors.
            raise RenameError(f"Modrinth {method} failed with HTTP {failure.code}") from None
        except (URLError, TimeoutError, OSError):
            raise RenameError(f"Modrinth {method} could not complete; check connectivity and retry") from None
        except (ValueError, UnicodeError):
            raise RenameError(f"Modrinth {method} returned invalid JSON") from None

    def versions(self) -> Any:
        return self.request("GET", f"/project/{PROJECT_ID}/version")

    def version(self, version_id: str) -> Any:
        return self.request("GET", f"/version/{version_id}")

    def patch(self, rename: Rename) -> None:
        self.request("PATCH", f"/version/{rename.id}", rename.payload())


def validate_base(base: str) -> None:
    if not re.fullmatch(r"[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?", base):
        raise RenameError("base_version must be a release number such as 0.3.4, without platform metadata")


def build_plan(versions: Any, base: str) -> list[Rename]:
    validate_base(base)
    if not isinstance(versions, list):
        raise RenameError("Modrinth did not return a complete project version list")
    ids: set[str] = set()
    numbers: dict[str, set[str]] = {}
    selected: list[tuple[dict[str, Any], str | None]] = []
    for version in versions:
        if not isinstance(version, dict) or version.get("project_id") != PROJECT_ID:
            raise RenameError("Project version list contains an unexpected project or response shape")
        version_id, number = version.get("id"), version.get("version_number")
        if not isinstance(version_id, str) or not re.fullmatch(r"[A-Za-z0-9]{1,32}", version_id) or version_id in ids:
            raise RenameError("Project version list contains an invalid or duplicate version ID")
        if not isinstance(number, str):
            raise RenameError("Project version list contains an invalid version number")
        ids.add(version_id)
        numbers.setdefault(number, set()).add(version_id)
        suffix = None
        if number == base:
            selected.append((version, suffix))
        elif number.startswith(base + "+paper."):
            selected.append((version, "paper"))
        elif number.startswith(base + "+fabric."):
            selected.append((version, "fabric"))
    if not selected:
        raise RenameError(f"No existing versions match base_version {base}; nothing was modified")

    plan: list[Rename] = []
    destinations: set[str] = set()
    for version, suffix in selected:
        version_id = version["id"]
        if not isinstance(version.get("name"), str):
            raise RenameError(f"Version {version_id} has an invalid display name")
        games, loaders = version.get("game_versions"), version.get("loaders")
        if not isinstance(games, list) or len(games) != 1 or not isinstance(games[0], str) or games[0] not in GAMES:
            raise RenameError(f"Version {version_id} must have exactly one supported game version (26.1.2 or 26.2)")
        if not isinstance(loaders, list) or not loaders or any(not isinstance(loader, str) for loader in loaders):
            raise RenameError(f"Version {version_id} has invalid loaders")
        loader_set = set(loaders)
        if len(loader_set) != len(loaders):
            raise RenameError(f"Version {version_id} has duplicate loaders")
        if loader_set == {"fabric"}:
            platform, label = "fabric", "Fabric"
        elif loader_set <= {"paper", "folia"}:
            platform, label = "paper", "Paper / Folia"
        else:
            raise RenameError(f"Version {version_id} is not exclusively Paper/Folia or Fabric")
        game = games[0]
        target_number = f"{base}+{platform}.{game}"
        if suffix is not None and (suffix != platform or version["version_number"] != target_number):
            raise RenameError(f"Version {version_id} platform suffix does not match its loaders and game version")
        if target_number in destinations or numbers.get(target_number, set()) - {version_id}:
            raise RenameError(f"Target version_number {target_number} collides with another existing or planned version")
        destinations.add(target_number)
        plan.append(Rename(version, f"{base} ({label}, MC {game})", target_number))
    return sorted(plan, key=lambda rename: rename.number)


def guard_snapshot(current: Any, rename: Rename, *, renamed: bool) -> None:
    if not isinstance(current, dict) or any(current.get(field) != rename.before.get(field) for field in GUARDED_FIELDS):
        raise RenameError(f"Version {rename.id} changed protected metadata; stop and inspect before retrying")
    expected = rename.payload() if renamed else {field: rename.before[field] for field in ("name", "version_number")}
    if any(current.get(field) != value for field, value in expected.items()):
        raise RenameError(f"Version {rename.id} did not match the expected names; stop and inspect before retrying")


def execute(client: Modrinth, base: str, apply: bool = False) -> list[Rename]:
    # Validate every target and every collision before the first PATCH.
    plan = build_plan(client.versions(), base)
    print(json.dumps({"project_id": PROJECT_ID, "base_version": base, "mode": "apply" if apply else "check", "plan": [
        {"id": rename.id, "before": {field: rename.before[field] for field in ("name", "version_number")},
         "after": rename.payload(), "changed": rename.changed} for rename in plan
    ]}, indent=2))
    if not apply:
        print("Read-only plan complete. Pass --apply with MODRINTH_TOKEN to update these two fields.")
        return plan
    # Re-read the whole project, then every selected version, before making any mutation.
    fresh = build_plan(client.versions(), base)
    if [(item.id, item.payload()) for item in fresh] != [(item.id, item.payload()) for item in plan]:
        raise RenameError("Project version targets changed after planning; retry from a new plan")
    for rename in plan:
        guard_snapshot(client.version(rename.id), rename, renamed=False)
    for rename in plan:
        if rename.changed:
            client.patch(rename)
        guard_snapshot(client.version(rename.id), rename, renamed=True)
        print(f"Verified {rename.id}: {rename.number}" + (" (already correct)" if not rename.changed else ""))
    return plan


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-version", required=True, help="Existing release number, for example 0.3.4")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--check", "--dry-run", action="store_true", help="Print a read-only plan (the default)")
    mode.add_argument("--apply", action="store_true", help="Apply only name and version_number, then verify")
    args = parser.parse_args()
    try:
        validate_base(args.base_version)
        token = os.environ.get("MODRINTH_TOKEN") if args.apply else None
        if args.apply and not token:
            raise RenameError("MODRINTH_TOKEN is required for --apply")
        execute(Modrinth(token), args.base_version, args.apply)
        return 0
    except RenameError as failure:
        print(f"Error: {failure}", file=sys.stderr)
        return 1
    except Exception:
        print("Error: unexpected failure; request details and credentials are suppressed", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
