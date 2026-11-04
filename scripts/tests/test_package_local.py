#!/usr/bin/env python3
"""T477: 本地部署打包脚本的测试。

覆盖：包内目录结构、启停脚本的可执行位、包内校验值、版本号推断，以及缺 jar / 缺前端产物
时的明确失败。测试用临时目录里的假产物，不碰仓库里的真实构建输出。
"""

from __future__ import annotations

import hashlib
import importlib.util
import sys
import tarfile
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "package-local.py"
spec = importlib.util.spec_from_file_location("package_local", SCRIPT)
package_local = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = package_local
spec.loader.exec_module(package_local)


class PackageLocalTest(unittest.TestCase):
    """打包脚本的行为。"""

    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

        self.jar = self.root / "backend-1.2.3.jar"
        self.jar.write_bytes(b"fake jar content")

        self.dist = self.root / "dist"
        (self.dist / "assets").mkdir(parents=True)
        (self.dist / "index.html").write_text("<html></html>", encoding="utf-8")
        (self.dist / "assets" / "index.js").write_text("console.log(1)", encoding="utf-8")
        (self.dist / ".DS_Store").write_bytes(b"junk")

        self.scripts = self.root / "scripts"
        self.scripts.mkdir()
        (self.scripts / "start.sh").write_text("#!/usr/bin/env bash\n", encoding="utf-8")
        (self.scripts / "stop.sh").write_text("#!/usr/bin/env bash\n", encoding="utf-8")
        (self.scripts / "application.yml").write_text("spring:\n  application:\n    name: x\n", encoding="utf-8")
        (self.scripts / "README.md").write_text("# 说明\n", encoding="utf-8")

    def build(self, **kwargs) -> Path:
        return package_local.build_package(
            repo_root=self.root,
            jar=str(self.jar),
            dist=str(self.dist),
            scripts=str(self.scripts),
            **kwargs,
        )

    def entries(self, archive: Path) -> list[str]:
        with tarfile.open(archive, "r:gz") as tar:
            return tar.getnames()

    def extract(self, archive: Path) -> Path:
        target = self.root / "extracted"
        with tarfile.open(archive, "r:gz") as tar:
            tar.extractall(target)
        return target

    def test_the_package_contains_the_expected_entries(self) -> None:
        archive = self.build()
        names = self.entries(archive)

        for expected in (
            "jpetstore-1.2.3/backend.jar",
            "jpetstore-1.2.3/dist/index.html",
            "jpetstore-1.2.3/dist/assets/index.js",
            "jpetstore-1.2.3/application.yml",
            "jpetstore-1.2.3/start.sh",
            "jpetstore-1.2.3/stop.sh",
            "jpetstore-1.2.3/README.md",
            "jpetstore-1.2.3/SHA256SUMS",
        ):
            self.assertIn(expected, names, f"包里缺少 {expected}")

    def test_the_version_comes_from_the_jar_name(self) -> None:
        archive = self.build()
        names = self.entries(archive)
        self.assertTrue(all(name.startswith("jpetstore-1.2.3") for name in names), names)

    def test_the_explicit_version_wins(self) -> None:
        archive = self.build(version="9.9.9")
        names = self.entries(archive)
        self.assertTrue(all(name.startswith("jpetstore-9.9.9") for name in names), names)

    def test_the_scripts_keep_the_executable_bit(self) -> None:
        archive = self.build()
        extracted = self.extract(archive)

        for name in ("start.sh", "stop.sh"):
            mode = (extracted / "jpetstore-1.2.3" / name).stat().st_mode
            self.assertTrue(mode & 0o111, f"{name} 应当可执行")

    def test_the_checksums_match_the_packaged_files(self) -> None:
        archive = self.build()
        extracted = self.extract(archive) / "jpetstore-1.2.3"

        lines = (extracted / "SHA256SUMS").read_text(encoding="utf-8").strip().splitlines()
        self.assertTrue(lines)
        for line in lines:
            digest, relative = line.split("  ", 1)
            actual = hashlib.sha256((extracted / relative).read_bytes()).hexdigest()
            self.assertEqual(actual, digest, f"{relative} 的校验值不对")

    def test_editor_junk_is_left_out(self) -> None:
        archive = self.build()
        self.assertNotIn("jpetstore-1.2.3/dist/.DS_Store", self.entries(archive))

    def test_a_missing_jar_fails_with_a_clear_message(self) -> None:
        self.jar.unlink()
        with self.assertRaises(package_local.PackagingError) as caught:
            self.build()
        self.assertIn("jar", str(caught.exception))

    def test_a_missing_frontend_build_fails_with_a_clear_message(self) -> None:
        (self.dist / "index.html").unlink()
        with self.assertRaises(package_local.PackagingError) as caught:
            self.build()
        self.assertIn("index.html", str(caught.exception))

    def test_a_missing_script_fails_with_a_clear_message(self) -> None:
        (self.scripts / "start.sh").unlink()
        with self.assertRaises(package_local.PackagingError) as caught:
            self.build()
        self.assertIn("start.sh", str(caught.exception))

    def test_the_default_output_goes_under_target_package(self) -> None:
        archive = package_local.build_package(
            repo_root=self.root,
            jar=str(self.jar),
            dist=str(self.dist),
            scripts=str(self.scripts),
        )
        self.assertEqual(archive.parent.name, "package")
        self.assertEqual(archive.parent.parent.name, "target")


if __name__ == "__main__":
    unittest.main()
