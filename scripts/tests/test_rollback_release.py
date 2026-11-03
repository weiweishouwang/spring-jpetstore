#!/usr/bin/env python3
"""T439: 应用回滚工具与数据保护的测试。

覆盖前一镜像摘要与兼容迁移版本校验、停写与切换顺序、健康失败、存在新订单时禁止旧库覆盖，
以及不可逆 schema 变化拒绝自动回退。
"""

from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "rollback-release.py"
spec = importlib.util.spec_from_file_location("rollback_release", SCRIPT)
rollbacker = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = rollbacker
spec.loader.exec_module(rollbacker)

TARGET = "prod-1.example.internal"
PREVIOUS = "ghcr.io/example/jpetstore-backend@sha256:" + "b" * 64
CURRENT = "ghcr.io/example/jpetstore-backend@sha256:" + "c" * 64


def plan(**overrides) -> rollbacker.RollbackPlan:
    data = {
        "current_image": CURRENT,
        "previous_image": PREVIOUS,
        "target": TARGET,
        "current_schema": 5,
        "previous_schema": 5,
    }
    data.update(overrides)
    return rollbacker.RollbackPlan(
        current_image=data["current_image"],
        previous_image=data["previous_image"],
        target=data["target"],
        current_schema=data["current_schema"],
        previous_schema=data["previous_schema"],
        orders_after_deploy=data.get("orders_after_deploy", 0),
        applied_migrations=data.get("applied_migrations", ()),
    )


class RollbackReleaseTest(unittest.TestCase):
    def test_the_target_must_be_confirmed(self) -> None:
        with self.assertRaises(rollbacker.RollbackRefused):
            rollbacker.validate_plan(plan(), None)
        with self.assertRaises(rollbacker.RollbackRefused):
            rollbacker.validate_plan(plan(), "other-host")

    def test_only_immutable_images_can_be_rolled_back_to(self) -> None:
        with self.assertRaises(rollbacker.RollbackRefused):
            rollbacker.validate_plan(plan(previous_image="jpetstore-backend:latest"), TARGET)
        with self.assertRaises(rollbacker.RollbackRefused):
            rollbacker.validate_plan(plan(previous_image=""), TARGET)

    def test_an_incompatible_schema_combination_is_refused(self) -> None:
        with self.assertRaises(rollbacker.RollbackRefused):
            rollbacker.validate_plan(plan(current_schema=8, previous_schema=8), TARGET)

    def test_irreversible_migrations_refuse_an_automatic_rollback(self) -> None:
        with self.assertRaises(rollbacker.RollbackRefused):
            rollbacker.validate_plan(plan(applied_migrations=("V6__unique_active_guest_cart.sql",)), TARGET)

    def test_new_orders_forbid_overwriting_with_the_old_database(self) -> None:
        with self.assertRaises(rollbacker.RollbackRefused):
            rollbacker.validate_plan(
                plan(current_schema=7, previous_schema=6, orders_after_deploy=3), TARGET)

    def test_the_writes_stop_before_the_switch(self) -> None:
        calls: list[str] = []

        rollbacker.rollback(plan(), TARGET, calls.append, dry_run=False)

        self.assertLess(calls.index("stop-writes"), calls.index("switch"))
        self.assertIn("readiness", calls)
        self.assertIn("smoke", calls)

    def test_a_failed_health_check_returns_a_non_zero_code(self) -> None:
        def runner(step: str) -> str:
            if step == "readiness":
                raise RuntimeError("not ready")
            return step

        code = rollbacker.rollback(plan(), TARGET, runner, dry_run=False)

        self.assertEqual(code, 1)

    def test_the_dry_run_changes_nothing(self) -> None:
        calls: list[str] = []

        code = rollbacker.rollback(plan(), TARGET, calls.append, dry_run=True)

        self.assertEqual(code, 0)
        self.assertEqual(calls, [])


if __name__ == "__main__":
    unittest.main()
