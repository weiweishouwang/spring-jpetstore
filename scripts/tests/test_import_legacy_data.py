#!/usr/bin/env python3
"""T410: 受控历史数据导入工具的测试。

覆盖显式目标确认、dry-run、幂等主键映射、用户名冲突、口令哈希重置策略、不造旧时间与拒绝未知
目标。测试只用合成数据，不连接任何数据库。
"""

from __future__ import annotations

import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "import-legacy-data.py"
spec = importlib.util.spec_from_file_location("import_legacy_data", SCRIPT)
importer = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = importer
spec.loader.exec_module(importer)

TARGET = "jdbc:mysql://127.0.0.1:3306/jpetstore_dev"


def legacy_fixture() -> dict:
    return {
        "supplier": [{"suppid": 1, "name": "S", "status": "P", "addr1": "a", "addr2": None, "city": "c",
                      "state": "s", "zip": "z", "country": "C", "phone": "p"}],
        "category": [{"catid": "FISH", "name": "Fish", "description": "d"}],
        "product": [{"productid": "FI-SW-01", "category": "FISH", "name": "Angelfish", "description": "d"}],
        "item": [{"itemid": "EST-1", "productid": "FI-SW-01", "list_price": 16.5, "unit_cost": 10.0, "supplier": 1,
                  "status": "P", "attr1": "Large"}],
        "inventory": [{"itemid": "EST-1", "qty": 100, "version": 0}],
        "signon": [{"username": "alice", "password_hash": "old-md5-hash"}],
        "account": [{"userid": "alice", "email": "alice@example.com", "first_name": "Alice", "last_name": "Ishikawa",
                     "phone": "090", "addr1": "a1", "addr2": None, "city": "c", "state": "s", "zip": "z",
                     "country": "JP"}],
        "profile": [{"userid": "alice", "language_preference": "english", "favourite_category": "FISH",
                     "list_option": True, "banner_option": True}],
        "orders": [{"orderid": 1000, "userid": "alice", "orderdate": "2026-10-01 10:00:00", "total_amount": 20.0,
                    "status": "P"}],
        "lineitem": [{"orderid": 1000, "linenum": 1, "itemid": "EST-1", "quantity": 2, "unit_price": 10.0,
                      "product_name": "Angelfish"}],
    }


class ImportLegacyDataTest(unittest.TestCase):
    def test_the_target_must_be_confirmed_exactly(self) -> None:
        with self.assertRaises(importer.ImportRefused):
            importer.require_confirmed_target(TARGET, None)
        with self.assertRaises(importer.ImportRefused):
            importer.require_confirmed_target(TARGET, "jdbc:mysql://other/db")
        self.assertEqual(importer.require_confirmed_target(TARGET, TARGET), TARGET)

    def test_unknown_targets_are_refused(self) -> None:
        for target in ["postgres://host/db", "file:///tmp/db", "jdbc:h2:mem:test", ""]:
            with self.assertRaises(importer.ImportRefused):
                importer.require_confirmed_target(target, target)

    def test_the_dry_run_writes_nothing(self) -> None:
        with tempfile.TemporaryDirectory() as folder:
            source = Path(folder) / "legacy.json"
            source.write_text(json.dumps(legacy_fixture()), encoding="utf-8")
            output = Path(folder) / "import.sql"

            code = importer.main([
                "--source", str(source),
                "--target", TARGET,
                "--confirm", TARGET,
                "--dry-run",
                "--output", str(output),
            ])

            self.assertEqual(code, 0)
            script = output.read_text(encoding="utf-8")
            self.assertIn("INSERT INTO account", script)
            self.assertIn("START TRANSACTION;", script)

    def test_existing_primary_keys_are_skipped(self) -> None:
        statements, report = importer.build_statements(legacy_fixture(), existing_item_ids=["EST-1"])

        self.assertFalse(any("INSERT INTO item" in statement for statement in statements))
        self.assertIn("EST-1", report.skipped.get("item", []))

    def test_username_conflicts_never_overwrite(self) -> None:
        statements, report = importer.build_statements(legacy_fixture(), existing_usernames=["alice"])

        self.assertFalse(any("INSERT INTO account" in statement for statement in statements))
        self.assertIn("account:alice", report.conflicts)

    def test_password_hashes_are_reset_not_converted(self) -> None:
        statements, report = importer.build_statements(legacy_fixture())

        signon = next(statement for statement in statements if "INSERT INTO signon" in statement)
        self.assertNotIn("old-md5-hash", signon)
        self.assertIn(importer.RESET_PASSWORD_HASH, signon)
        self.assertIn("alice", report.password_resets)

    def test_orders_without_a_timestamp_are_skipped(self) -> None:
        legacy = legacy_fixture()
        legacy["orders"] = [{"orderid": 1001, "userid": "alice", "total_amount": 20.0, "status": "P"}]

        statements, report = importer.build_statements(legacy)

        self.assertFalse(any("INSERT INTO orders" in statement for statement in statements))
        self.assertIn("1001", report.skipped.get("orders", []))

    def test_the_generated_script_is_wrapped_in_a_transaction(self) -> None:
        statements, _ = importer.build_statements(legacy_fixture())

        script = importer.render_script(statements)

        self.assertTrue(script.startswith("-- 由 scripts/import-legacy-data.py 生成。"))
        self.assertIn("START TRANSACTION;", script)
        self.assertTrue(script.rstrip().endswith("COMMIT;"))

    def test_quoting_escapes_single_quotes(self) -> None:
        self.assertEqual(importer.quote("O'Brien"), "'O''Brien'")
        self.assertEqual(importer.quote(None), "NULL")
        self.assertEqual(importer.quote(True), "1")


if __name__ == "__main__":
    unittest.main()
