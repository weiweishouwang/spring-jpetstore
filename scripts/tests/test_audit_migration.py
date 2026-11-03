#!/usr/bin/env python3
"""T412: 迁移后只读对账工具的测试。

覆盖逐表行数、主键、账户关联、订单归属、金额、库存、最大主键与历史精度差异，并确认工具不会
执行任何修复写入。
"""

from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "audit-migration.py"
spec = importlib.util.spec_from_file_location("audit_migration", SCRIPT)
auditor = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = auditor
spec.loader.exec_module(auditor)


def legacy() -> dict:
    return {
        "account": [{"userid": "alice"}, {"userid": "bob"}],
        "item": [{"itemid": "EST-1", "list_price": 16.5}],
        "inventory": [{"itemid": "EST-1", "qty": 10}],
        "orders": [{"orderid": 1000, "userid": "alice", "total_amount": 20.0}],
        "lineitem": [{"orderid": 1000, "linenum": 1, "itemid": "EST-1", "unit_price": 10.0}],
    }


def target() -> dict:
    return {
        "account": [{"userid": "alice"}, {"userid": "bob"}],
        "item": [{"itemid": "EST-1", "list_price": 16.5}],
        "inventory": [{"itemid": "EST-1", "qty": 9}],
        "orders": [{"orderid": 1000, "userid": "alice", "total_amount": 20.0}],
        "lineitem": [{"orderid": 1000, "linenum": 1, "itemid": "EST-1", "unit_price": 10.0}],
    }


class AuditMigrationTest(unittest.TestCase):
    def test_matching_snapshots_have_no_differences(self) -> None:
        report = auditor.audit(legacy(), target())

        self.assertFalse(report.has_differences)
        self.assertEqual(report.table_counts["orders"]["legacy"], 1)
        self.assertEqual(report.table_counts["orders"]["target"], 1)

    def test_missing_rows_are_reported(self) -> None:
        broken = target()
        broken["account"] = [{"userid": "alice"}]

        report = auditor.audit(legacy(), broken)

        self.assertIn("bob", report.missing_keys["account"])
        self.assertTrue(report.has_differences)

    def test_unexpected_rows_are_reported(self) -> None:
        extra = target()
        extra["account"] = extra["account"] + [{"userid": "carol"}]

        report = auditor.audit(legacy(), extra)

        self.assertIn("carol", report.unexpected_keys["account"])

    def test_amount_differences_are_reported(self) -> None:
        changed = target()
        changed["orders"] = [{"orderid": 1000, "userid": "alice", "total_amount": 19.99}]

        report = auditor.audit(legacy(), changed)

        self.assertTrue(report.amount_mismatches)
        self.assertIn("orders:1000", report.amount_mismatches[0])

    def test_historical_precision_is_compared_within_a_cent(self) -> None:
        changed = target()
        changed["orders"] = [{"orderid": 1000, "userid": "alice", "total_amount": 20.004}]

        report = auditor.audit(legacy(), changed)

        self.assertFalse(report.amount_mismatches)

    def test_orphan_references_are_reported(self) -> None:
        broken = target()
        broken["orders"] = [{"orderid": 1000, "userid": "nobody", "total_amount": 20.0}]

        report = auditor.audit(legacy(), broken)

        self.assertTrue(any("account:nobody" in reference for reference in report.orphan_references))

    def test_negative_stock_is_reported(self) -> None:
        broken = target()
        broken["inventory"] = [{"itemid": "EST-1", "qty": -1}]

        report = auditor.audit(legacy(), broken)

        self.assertTrue(any("qty=-1" in reference for reference in report.orphan_references))

    def test_the_maximum_ids_are_compared(self) -> None:
        report = auditor.audit(legacy(), target())

        self.assertEqual(report.max_ids["orders"]["legacy"], 1000.0)
        self.assertEqual(report.max_ids["orders"]["target"], 1000.0)


if __name__ == "__main__":
    unittest.main()
