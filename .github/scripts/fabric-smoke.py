"""Compatibility entry point for the shared Fabric/NeoForge production smoke suite."""

from pathlib import Path
import runpy
import sys


if __name__ == "__main__":
    sys.argv.insert(1, "fabric")
    runpy.run_path(str(Path(__file__).with_name("mod-smoke.py")), run_name="__main__")
