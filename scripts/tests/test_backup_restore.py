#!/usr/bin/env python3
"""T434: 备份与隔离恢复工具的测试。

覆盖一致性备份、完整性清单、受保护输出、错误检测，以及“只恢复到明确批准的隔离目标”——
禁止误覆盖现役库。
"""

from __future__ import annotations

import importlib.util
import stat
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "backup-restore.py"
spec = importlib.util.spec_from_file_location("backup_restore", SCRIPT)
backup = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = backup
spec.loader.exec_module(backup)

PRODUCTION = "jdbc:mysql://db.example.internal:3306/jpetstore_prod"
ISOLATED = "jdbc:mysql://restore-drill.internal:3306/jpetstore_restore"


class BackupRestoreTest(unittest.TestCase):
    def test_the_backup_is_a_consistent_snapshot(self) -> None:
        command = backup.build_dump_command(PRODUCTION, Path("/tmp/backup.sql"))

        self.assertIn("--single-transaction", command)
        self.assertTrue(any(argument.startswith("--result-file=") for argument in command))

    def test_the_restore_target_must_be_marked_as_isolated(self) -> None:
        with self.assertRaises(backup.BackupRefused):
            backup.require_isolated_target(PRODUCTION)
        self.assertEqual(backup.require_isolated_target(ISOLATED), ISOLATED)

    def test_the_backup_writes_a_manifest_with_a_checksum(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            output_dir = Path(folder)

            def runner(command: list[str]) -> None:
                target = Path(next(argument.split("=", 1)[1] for argument in command
                                   if argument.startswith("--result-file=")))
                target.write_text("-- dump\n", encoding="utf-8")

            result = backup.perform_backup(PRODUCTION, output_dir, runner=runner, created_at="2026-11-03T00:00:00Z")
            manifest = output_dir / "manifest.json"
            backup.write_manifest(result, manifest)

            self.assertTrue(manifest.exists())
            self.assertIn("checksum", manifest.read_text(encoding="utf-8"))
            self.assertTrue(backup.verify_backup(result))

    def test_the_output_permissions_are_restricted(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            output_dir = Path(folder)

            def runner(command: list[str]) -> None:
                target = Path(next(argument.split("=", 1)[1] for argument in command
                                   if argument.startswith("--result-file=")))
                target.write_text("-- dump\n", encoding="utf-8")

            result = backup.perform_backup(PRODUCTION, output_dir, runner=runner)
            mode = Path(result.file).stat().st_mode

            self.assertEqual(stat.S_IMODE(mode), stat.S_IRUSR | stat.S_IWUSR)

    def test_a_tampered_backup_is_detected(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            output_dir = Path(folder)

            def runner(command: list[str]) -> None:
                target = Path(next(argument.split("=", 1)[1] for argument in command
                                   if argument.startswith("--result-file=")))
                target.write_text("-- dump\n", encoding="utf-8")

            result = backup.perform_backup(PRODUCTION, output_dir, runner=runner)
            Path(result.file).write_text("-- tampered\n", encoding="utf-8")

            self.assertFalse(backup.verify_backup(result))

    def test_a_missing_backup_file_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaises(backup.BackupRefused):
                backup.perform_backup(PRODUCTION, Path(folder), runner=lambda command: None)

    def test_the_manifest_contains_no_data_or_password(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            output_dir = Path(folder)

            def runner(command: list[str]) -> None:
                target = Path(next(argument.split("=", 1)[1] for argument in command
                                   if argument.startswith("--result-file=")))
                target.write_text("-- dump\n", encoding="utf-8")

            result = backup.perform_backup(PRODUCTION, output_dir, runner=runner)
            manifest = output_dir / "manifest.json"
            backup.write_manifest(result, manifest)
            content = manifest.read_text(encoding="utf-8").lower()

            self.assertNotIn("password", content)
            self.assertNotIn("insert into", content)


if __name__ == "__main__":
    unittest.main()
