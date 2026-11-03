#!/usr/bin/env python3
"""T432: 发布数据库迁移工具的测试。

覆盖只允许精确 release 的迁移：固定 Flyway 版本、只用迁移凭据引用、baseline 默认关闭、
校验和或目标版本不符时失败、迁移失败禁止发布。
"""

from __future__ import annotations

import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "migrate-release.py"
spec = importlib.util.spec_from_file_location("migrate_release", SCRIPT)
migrator = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = migrator
spec.loader.exec_module(migrator)

TARGET = "jdbc:mysql://db.example.internal:3306/jpetstore_prod"


def plan(**overrides) -> migrator.MigrationPlan:
    data = {
        "commit": "31e8f7c",
        "image": "ghcr.io/example/jpetstore-backend@sha256:" + "a" * 64,
        "target": TARGET,
        "migrations_dir": "backend/src/main/resources/db/migration",
        "migration_user_ref": "JPetStore_MIGRATION_USER",
    }
    data.update(overrides)
    return migrator.MigrationPlan(
        commit=data["commit"],
        image=data["image"],
        target=data["target"],
        migrations_dir=data["migrations_dir"],
        migration_user_ref=data["migration_user_ref"],
        expected_version=data.get("expected_version", ""),
        baseline=data.get("baseline", False),
        checksums=data.get("checksums", {}),
    )


class MigrateReleaseTest(unittest.TestCase):
    def test_the_target_must_be_confirmed_exactly(self) -> None:
        with self.assertRaises(migrator.MigrationRefused):
            migrator.validate_plan(plan(), None)
        with self.assertRaises(migrator.MigrationRefused):
            migrator.validate_plan(plan(), "jdbc:mysql://other/db")

    def test_baseline_is_refused_by_default(self) -> None:
        with self.assertRaises(migrator.MigrationRefused):
            migrator.validate_plan(plan(baseline=True), TARGET)

    def test_the_plan_must_bind_the_commit_the_image_and_the_credentials(self) -> None:
        with self.assertRaises(migrator.MigrationRefused):
            migrator.validate_plan(plan(commit=""), TARGET)
        with self.assertRaises(migrator.MigrationRefused):
            migrator.validate_plan(plan(image=""), TARGET)
        with self.assertRaises(migrator.MigrationRefused):
            migrator.validate_plan(plan(migration_user_ref=""), TARGET)

    def test_an_invalid_target_version_is_refused(self) -> None:
        with self.assertRaises(migrator.MigrationRefused):
            migrator.validate_plan(plan(expected_version="latest"), TARGET)

    def test_the_flyway_version_is_pinned(self) -> None:
        self.assertEqual(migrator.FLYWAY_VERSION, migrator.EXPECTED_FLYWAY_VERSION)

    def test_the_migration_content_must_match_the_release_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            migrations = Path(folder)
            (migrations / "V1__a.sql").write_text("SELECT 1;", encoding="utf-8")
            checksums = migrator.collect_checksums(migrations)

            migrator.verify_migrations(plan(checksums=checksums), migrations)

            with self.assertRaises(migrator.MigrationRefused):
                migrator.verify_migrations(plan(checksums={"V1__a.sql": "0" * 64}), migrations)

    def test_a_missing_migration_directory_is_refused(self) -> None:
        with self.assertRaises(migrator.MigrationRefused):
            migrator.verify_migrations(plan(), Path("/nonexistent/migrations"))

    def test_the_commands_validate_then_info_then_migrate(self) -> None:
        commands = migrator.build_commands(plan(expected_version="7"))

        self.assertEqual([command[-1] for command in commands], ["validate", "info", "migrate"])
        for command in commands:
            joined = " ".join(command)
            self.assertIn("-validateOnMigrate=true", joined)
            self.assertIn("-baselineOnMigrate=false", joined)
            self.assertNotIn("password", joined.lower())

    def test_the_password_is_never_a_visible_argument(self) -> None:
        for command in migrator.build_commands(plan()):
            for argument in command:
                self.assertNotIn("PASSWORD", argument.upper())


if __name__ == "__main__":
    unittest.main()
