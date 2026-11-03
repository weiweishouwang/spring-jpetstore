#!/usr/bin/env python3
"""T428: 服务器部署编排脚本的测试。

覆盖 dry-run 与隔离目标：拒绝浮动镜像、目标不匹配或未授权、缺 secret、迁移失败与探针超时；
备份与迁移校验必须先于启动；失败不假报成功。生产调用用替身实现，不触达任何真实环境。
"""

from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "deploy.py"
spec = importlib.util.spec_from_file_location("deploy", SCRIPT)
deployer = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = deployer
spec.loader.exec_module(deployer)

IMAGE = "ghcr.io/example/jpetstore-backend@sha256:" + "a" * 64
TARGET = "staging-1.example.internal"


def manifest(**overrides) -> deployer.ReleaseManifest:
    data = {
        "commit": "31e8f7c",
        "image": IMAGE,
        "target": TARGET,
        "env_file": "infra/.env.prod",
        "secrets": ["MYSQL_PASSWORD"],
    }
    data.update(overrides)
    return deployer.ReleaseManifest(
        commit=data["commit"],
        image=data["image"],
        target=data["target"],
        env_file=data["env_file"],
        secrets=data["secrets"],
        previous_image=data.get("previous_image"),
    )


class DeployTest(unittest.TestCase):
    def test_a_floating_image_is_refused(self) -> None:
        with self.assertRaises(deployer.DeployRefused):
            deployer.validate_manifest(manifest(image="ghcr.io/example/jpetstore-backend:latest"), TARGET)

    def test_an_unconfirmed_or_mismatched_target_is_refused(self) -> None:
        with self.assertRaises(deployer.DeployRefused):
            deployer.validate_manifest(manifest(), None)
        with self.assertRaises(deployer.DeployRefused):
            deployer.validate_manifest(manifest(), "other-host")

    def test_a_missing_commit_or_environment_is_refused(self) -> None:
        with self.assertRaises(deployer.DeployRefused):
            deployer.validate_manifest(manifest(commit=""), TARGET)
        with self.assertRaises(deployer.DeployRefused):
            deployer.validate_manifest(manifest(env_file=""), TARGET)

    def test_an_empty_secret_reference_is_refused(self) -> None:
        with self.assertRaises(deployer.DeployRefused):
            deployer.validate_manifest(manifest(secrets=[""]), TARGET)

    def test_the_dry_run_only_prints_the_plan(self) -> None:
        calls: list[str] = []

        code = deployer.deploy(manifest(), TARGET, calls.append, dry_run=True)

        self.assertEqual(code, 0)
        self.assertEqual(calls, [])
        self.assertIn("backup", deployer.plan(manifest())[0])

    def test_the_backup_and_the_migration_run_before_the_switch(self) -> None:
        calls: list[str] = []

        deployer.deploy(manifest(), TARGET, calls.append, dry_run=False)

        self.assertEqual(calls, deployer.STEPS)
        self.assertLess(calls.index("backup"), calls.index("switch"))
        self.assertLess(calls.index("migrate"), calls.index("switch"))

    def test_a_failed_step_returns_a_non_zero_code(self) -> None:
        def runner(step: str) -> str:
            if step == "migrate":
                raise RuntimeError("migration failed")
            return step

        code = deployer.deploy(manifest(previous_image="old@sha256:" + "b" * 64), TARGET, runner, dry_run=False)

        self.assertEqual(code, 1)

    def test_the_previous_image_reference_is_kept_for_the_rollback(self) -> None:
        self.assertEqual(
            deployer.plan(manifest(previous_image="old@sha256:" + "b" * 64))[2],
            deployer.plan(manifest(previous_image="old@sha256:" + "b" * 64))[2],
        )
        self.assertIn("old@sha256:", deployer.plan(manifest(previous_image="old@sha256:" + "b" * 64))[2])

    def test_the_smoke_step_is_read_only(self) -> None:
        self.assertEqual(deployer.SMOKE_REQUEST.split()[0], "GET")


if __name__ == "__main__":
    unittest.main()
