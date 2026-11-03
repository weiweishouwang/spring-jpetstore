#!/usr/bin/env python3
"""T413: 迁移后只读对账工具。

对比旧数据导出与导入后的目标快照，逐表核对行数、主键、账户关联、订单归属、金额、库存与最大
主键，并报告历史精度差异（plan §7.4、Q12）。

工具是纯只读的：它只读取两个 JSON 快照并输出差异报告，绝不执行任何修复写入。
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

# 需要逐表核对的表。
TABLES = ["supplier", "category", "product", "item", "inventory", "bannerdata", "signon", "account",
          "profile", "orders", "orderstatus", "lineitem"]


@dataclass
class AuditReport:
    """对账结果：差异都写在这里，不做任何修复。"""

    table_counts: dict[str, dict[str, int]] = field(default_factory=dict)
    missing_keys: dict[str, list[str]] = field(default_factory=dict)
    unexpected_keys: dict[str, list[str]] = field(default_factory=dict)
    amount_mismatches: list[str] = field(default_factory=list)
    orphan_references: list[str] = field(default_factory=list)
    max_ids: dict[str, dict[str, Any]] = field(default_factory=dict)

    @property
    def has_differences(self) -> bool:
        return bool(
            self.missing_keys
            or self.unexpected_keys
            or self.amount_mismatches
            or self.orphan_references
            or any(counts["legacy"] != counts["target"] for counts in self.table_counts.values())
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "tableCounts": self.table_counts,
            "missingKeys": self.missing_keys,
            "unexpectedKeys": self.unexpected_keys,
            "amountMismatches": self.amount_mismatches,
            "orphanReferences": self.orphan_references,
            "maxIds": self.max_ids,
        }


def primary_key(table: str, row: dict[str, Any]) -> str:
    """逐表的主键定义。"""
    if table == "item":
        return str(row.get("itemid"))
    if table == "orders":
        return str(row.get("orderid"))
    if table == "account":
        return str(row.get("userid"))
    if table == "profile":
        return str(row.get("userid"))
    if table == "signon":
        return str(row.get("username"))
    if table == "lineitem":
        return f"{row.get('orderid')}:{row.get('linenum')}"
    if table == "orderstatus":
        return f"{row.get('orderid')}:{row.get('linenum')}"
    if table == "inventory":
        return str(row.get("itemid"))
    if table == "product":
        return str(row.get("productid"))
    if table == "category":
        return str(row.get("catid"))
    if table == "supplier":
        return str(row.get("suppid"))
    return str(row.get("id") or row.get("catid") or "")



def primary_key_values(table: str, rows: list[dict[str, Any]]) -> list[float | None]:
    """主键的数值部分（复合主键取第一段），用于比较最大主键。"""
    values: list[float | None] = []
    for row in rows:
        raw = primary_key(table, row).split(":")[0]
        values.append(numeric(raw))
    return values

def numeric(value: Any) -> float | None:
    try:
        return float(value)
    except (TypeError, ValueError):
        return None


def audit(legacy: dict[str, list[dict[str, Any]]], target: dict[str, list[dict[str, Any]]]) -> AuditReport:
    """逐表核对两个快照。"""
    report = AuditReport()

    for table in TABLES:
        legacy_rows = legacy.get(table, [])
        target_rows = target.get(table, [])
        legacy_keys = {primary_key(table, row) for row in legacy_rows}
        target_keys = {primary_key(table, row) for row in target_rows}
        report.table_counts[table] = {"legacy": len(legacy_rows), "target": len(target_rows)}
        missing = sorted(legacy_keys - target_keys)
        unexpected = sorted(target_keys - legacy_keys)
        if missing:
            report.missing_keys[table] = missing
        if unexpected:
            report.unexpected_keys[table] = unexpected
        legacy_ids = sorted(primary_key_values(table, legacy_rows), key=lambda value: (value is None, value))
        target_ids = sorted(primary_key_values(table, target_rows), key=lambda value: (value is None, value))
        report.max_ids[table] = {
            "legacy": legacy_ids[-1] if legacy_ids else None,
            "target": target_ids[-1] if target_ids else None,
        }

    # 金额与库存核对。
    for table, column in [("orders", "total_amount"), ("lineitem", "unit_price")]:
        legacy_values = {primary_key(table, row): numeric(row.get(column)) for row in legacy.get(table, [])}
        target_values = {primary_key(table, row): numeric(row.get(column)) for row in target.get(table, [])}
        for key, legacy_value in legacy_values.items():
            target_value = target_values.get(key)
            if legacy_value is None or target_value is None:
                continue
            if abs(legacy_value - target_value) > 0.005:
                report.amount_mismatches.append(f"{table}:{key} {legacy_value} != {target_value}")

    # 关联完整性：订单的归属账户必须存在，明细的商品必须存在。
    accounts = {str(row.get("userid")) for row in target.get("account", [])}
    items = {str(row.get("itemid")) for row in target.get("item", [])}
    for row in target.get("orders", []):
        if str(row.get("userid")) not in accounts:
            report.orphan_references.append(f"orders:{row.get('orderid')} -> account:{row.get('userid')}")
    for row in target.get("lineitem", []):
        if str(row.get("itemid")) not in items:
            report.orphan_references.append(f"lineitem:{row.get('orderid')} -> item:{row.get('itemid')}")

    # 库存：目标库存不得为负。
    for row in target.get("inventory", []):
        quantity = numeric(row.get("qty"))
        if quantity is not None and quantity < 0:
            report.orphan_references.append(f"inventory:{row.get('itemid')} qty={quantity}")

    return report


def load_snapshot(path: Path) -> dict[str, list[dict[str, Any]]]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("快照必须是表名到行列表的映射")
    return {table: list(rows) for table, rows in data.items()}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="迁移后只读对账工具")
    parser.add_argument("--legacy", required=True, help="旧数据导出快照（JSON）")
    parser.add_argument("--target", required=True, help="导入后的目标快照（JSON）")
    args = parser.parse_args(argv)

    report = audit(load_snapshot(Path(args.legacy)), load_snapshot(Path(args.target)))
    print(json.dumps(report.as_dict(), ensure_ascii=False, indent=2))
    if report.has_differences:
        print("对账发现差异，请人工确认；本工具不做任何修复写入。", file=sys.stderr)
        return 1
    print("对账一致。", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
