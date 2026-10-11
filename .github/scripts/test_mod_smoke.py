"""Offline checks for the packaged-mod compatibility fixture's integrity guards."""
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("mod_smoke", Path(__file__).with_name("mod-smoke.py"))
smoke = importlib.util.module_from_spec(spec)
spec.loader.exec_module(smoke)


class CompatibilityVerificationTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.run = self.root / "fixture"
        self.home = self.root / "gradle-home"
        self.artifact = self.write("packaged/final.jar", b"final EClean artifact")
        self.api = self.write("packaged/fabric-api-0.161.0+26.2.jar", b"pinned Fabric API")
        self.library = self.write("fixture/libraries/com/example/runtime/1/runtime-1.jar", b"pinned library")
        self.loaded = [self.artifact, self.api]
        namespace = "https://schema.gradle.org/dependency-verification"
        metadata = ET.Element("dependency-verification", xmlns=namespace)
        components = ET.SubElement(metadata, "components")
        for path, group, name, version in [
            (self.api, "net.fabricmc.fabric-api", "fabric-api", "0.161.0+26.2"),
            (self.library, "com.example", "runtime", "1"),
        ]:
            component = ET.SubElement(components, "component", group=group, name=name, version=version)
            artifact = ET.SubElement(component, "artifact", name=path.name)
            ET.SubElement(artifact, "sha256", value=smoke.digest(path))
        path = self.root / "gradle/verification-metadata.xml"
        path.parent.mkdir(parents=True)
        ET.ElementTree(metadata).write(path)

    def write(self, name, payload):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(payload)
        return path

    def verify(self, compatibility=None):
        return smoke.verify_production(
            self.root, self.run, self.home, "fabric", "26.2", self.artifact,
            self.root / "unused-bundle.jar", {"fabric-api-mc262": "0.161.0+26.2"},
            loaded_mods=self.loaded, compatibility_mods=compatibility,
        )

    def test_normal_fixture_has_no_architectury_dependency(self):
        self.assertEqual(set(self.verify()["mods_sha256"]), {self.artifact.name, self.api.name})

    def test_compatibility_fixture_requires_exact_moonrise_pin(self):
        moonrise = self.write("packaged/Moonrise.jar", b"verified Moonrise")
        self.loaded.append(moonrise)
        pin = {moonrise.name: smoke.digest(moonrise)}
        self.assertEqual(len(self.verify(pin)["mods_sha256"]), 3)
        moonrise.write_bytes(b"modified Moonrise")
        with self.assertRaisesRegex(RuntimeError, "compatibility mod differs"):
            self.verify(pin)

    def test_normal_fixture_rejects_extra_moonrise_or_architectury(self):
        for filename in ("Moonrise.jar", "architectury-fabric.jar"):
            with self.subTest(filename=filename):
                path = self.write("packaged/" + filename, b"extra mod")
                self.loaded.append(path)
                with self.assertRaisesRegex(RuntimeError, "Unexpected mod"):
                    self.verify()
                self.loaded.pop()

    def test_missing_compatibility_mod_is_rejected(self):
        with self.assertRaisesRegex(RuntimeError, "compatibility mod differs"):
            self.verify({"Moonrise.jar": hashlib.sha256(b"missing").hexdigest()})

    def test_fabric_api_still_requires_gradle_pin(self):
        self.api.write_bytes(b"modified API")
        with self.assertRaisesRegex(RuntimeError, "pinned SHA-256"):
            self.verify()

    def test_mixin_conflict_and_scheduled_failure_are_fatal(self):
        for message in (
            "Mixin apply for mod architectury failed architectury.mixins.json:MixinChunkMap",
            "Scheduled EClean task failedjava.lang.NullPointerException",
        ):
            self.assertIsNotNone(smoke.FATAL.search(message))


if __name__ == "__main__":
    unittest.main()
