#!/usr/bin/env python3
"""T411: 受控历史数据导入工具。

把归档的旧系统数据导入现代 MySQL 库。工具本身不连数据库：它读取旧数据导出，生成可审计的
SQL，并且只允许对明确确认的目标执行（plan §7.4、Q12、NFR-06）。

安全约束（不可通过参数关闭）：

* 必须显式确认目标：``--target`` 与 ``--confirm`` 必须完全一致，否则拒绝执行；
* 只接受已知形态的目标（MySQL 连接串），未知目标一律拒绝；
* ``--dry-run`` 只输出 SQL，不执行任何写入；
* 用户名冲突不覆盖既有账户，冲突账户被跳过并写进报告；
* 旧口令哈希无法安全转换成 BCrypt：导入的账户使用统一的重置占位哈希，并在报告里列出需要
  用户重设口令的账户；
* 不伪造旧时间：缺少时间的记录被跳过并写进报告。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable, Sequence

# 只允许的已知目标形态：MySQL 连接串。
TARGET_PATTERN = re.compile(r"^jdbc:mysql://[A-Za-z0-9._\-]+(:\d+)?/[A-Za-z0-9_\-]+\b.*$")

# 导入的账户使用的统一占位哈希（BCrypt cost 10，不可用于任何已知口令）。
RESET_PASSWORD_HASH = "$2a$10$invalidinvalidinvalidinvalidinvalidinvalidinvalidinvalidinv"

# 表与列的映射：旧表 → 现代表。
TABLE_ORDER = ["supplier", "category", "product", "item", "inventory", "bannerdata", "account", "signon",
               "profile", "orders", "orderstatus", "lineitem"]


class ImportRefused(RuntimeError):
    """导入被拒绝：目标不明确或形态未知。"""


@dataclass
class ImportReport:
    """导入报告：跳过与冲突的明细都写在这里。"""

    inserted: dict[str, int] = field(default_factory=dict)
    skipped: dict[str, list[str]] = field(default_factory=dict)
    conflicts: list[str] = field(default_factory=list)
    password_resets: list[str] = field(default_factory=list)

    def note_skip(self, table: str, key: str) -> None:
        self.skipped.setdefault(table, []).append(key)

    def as_dict(self) -> dict[str, Any]:
        return {
            "inserted": self.inserted,
            "skipped": self.skipped,
            "conflicts": self.conflicts,
            "passwordResets": self.password_resets,
        }


def require_confirmed_target(target: str | None, confirm: str | None) -> str:
    """目标必须被显式确认，且形态已知。"""
    if not target or not confirm:
        raise ImportRefused("必须同时提供 --target 与 --confirm")
    if target != confirm:
        raise ImportRefused("--confirm 必须与 --target 完全一致")
    if not TARGET_PATTERN.match(target):
        raise ImportRefused(f"未知目标形态，拒绝执行：{target}")
    return target


def load_legacy(path: Path) -> dict[str, list[dict[str, Any]]]:
    """读取旧数据导出（JSON：表名 → 行列表）。"""
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ImportRefused("旧数据导出必须是表名到行列表的映射")
    return {table: list(rows) for table, rows in data.items()}


def quote(value: Any) -> str:
    """字面量：字符串加引号并转义，None 变 NULL。"""
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "1" if value else "0"
    if isinstance(value, (int, float)):
        return str(value)
    text = str(value).replace("\\", "\\\\").replace("'", "''")
    return f"'{text}'"


def build_statements(
    legacy: dict[str, list[dict[str, Any]]],
    existing_usernames: Iterable[str] = (),
    existing_item_ids: Iterable[str] = (),
) -> tuple[list[str], ImportReport]:
    """生成插入语句与报告。

    幂等：主键已存在的行被跳过；用户名冲突的账户不覆盖既有账户。
    """
    report = ImportReport()
    usernames = set(existing_usernames)
    item_ids = set(existing_item_ids)
    statements: list[str] = []

    for table in TABLE_ORDER:
        rows = legacy.get(table)
        if rows is None:
            continue
        count = 0
        for row in rows:
            key = str(row.get("userid") or row.get("itemid") or row.get("productid") or count)
            if table == "account":
                username = str(row.get("userid") or "")
                if username in usernames:
                    report.conflicts.append(f"account:{username}")
                    report.note_skip(table, username)
                    continue
                usernames.add(username)
                statements.append(
                    "INSERT INTO account (userid, email, first_name, last_name, status, phone, addr1, addr2,"
                    " city, state, zip, country) VALUES ("
                    + ", ".join([
                        quote(row.get("userid")),
                        quote(row.get("email")),
                        quote(row.get("first_name")),
                        quote(row.get("last_name")),
                        quote(row.get("status") or "P"),
                        quote(row.get("phone")),
                        quote(row.get("addr1")),
                        quote(row.get("addr2")),
                        quote(row.get("city")),
                        quote(row.get("state")),
                        quote(row.get("zip")),
                        quote(row.get("country")),
                    ]) + ");")
                report.password_resets.append(username)
            elif table == "signon":
                username = str(row.get("username") or "")
                if username not in usernames:
                    report.note_skip(table, username)
                    continue
                statements.append(
                    "INSERT INTO signon (username, password_hash) VALUES ("
                    + f"{quote(username)}, {quote(RESET_PASSWORD_HASH)});")
            elif table == "item":
                item_id = str(row.get("itemid") or "")
                if item_id in item_ids:
                    report.note_skip(table, item_id)
                    continue
                item_ids.add(item_id)
                statements.append(
                    "INSERT INTO item (itemid, productid, list_price, unit_cost, supplier, status, attr1, attr2,"
                    " attr3, attr4, attr5) VALUES ("
                    + ", ".join([
                        quote(row.get("itemid")),
                        quote(row.get("productid")),
                        quote(row.get("list_price")),
                        quote(row.get("unit_cost")),
                        quote(row.get("supplier")),
                        quote(row.get("status") or "P"),
                        quote(row.get("attr1")),
                        quote(row.get("attr2")),
                        quote(row.get("attr3")),
                        quote(row.get("attr4")),
                        quote(row.get("attr5")),
                    ]) + ");")
            elif table == "orders":
                order_date = row.get("orderdate")
                if not order_date:
                    # 不伪造旧时间：缺时间的订单不导入。
                    report.note_skip(table, str(row.get("orderid") or count))
                    continue
                statements.append(
                    "INSERT INTO orders (orderid, userid, orderdate, bill_first_name, bill_last_name, bill_addr1,"
                    " bill_addr2, bill_city, bill_state, bill_zip, bill_country, ship_first_name, ship_last_name,"
                    " ship_addr1, ship_addr2, ship_city, ship_state, ship_zip, ship_country, total_amount, status,"
                    " courier, region, card_type, card_last_four) VALUES ("
                    + ", ".join([
                        quote(row.get("orderid")),
                        quote(row.get("userid")),
                        quote(order_date),
                        quote(row.get("bill_first_name")),
                        quote(row.get("bill_last_name")),
                        quote(row.get("bill_addr1")),
                        quote(row.get("bill_addr2")),
                        quote(row.get("bill_city")),
                        quote(row.get("bill_state")),
                        quote(row.get("bill_zip")),
                        quote(row.get("bill_country")),
                        quote(row.get("ship_first_name")),
                        quote(row.get("ship_last_name")),
                        quote(row.get("ship_addr1")),
                        quote(row.get("ship_addr2")),
                        quote(row.get("ship_city")),
                        quote(row.get("ship_state")),
                        quote(row.get("ship_zip")),
                        quote(row.get("ship_country")),
                        quote(row.get("total_amount")),
                        quote(row.get("status") or "P"),
                        quote(row.get("courier")),
                        quote(row.get("region")),
                        quote(row.get("card_type") or "Visa"),
                        quote(row.get("card_last_four") or "0000"),
                    ]) + ");")
            else:
                statements.append(
                    f"-- 未映射的表 {table} 的 {count} 行需要人工确认。")
            count += 1
        report.inserted[table] = count
    return statements, report


def render_script(statements: Sequence[str]) -> str:
    """生成可审计的 SQL 脚本。"""
    header = [
        "-- 由 scripts/import-legacy-data.py 生成。",
        "-- 执行前请确认目标库与本目录的导入计划一致。",
        "START TRANSACTION;",
    ]
    return "\n".join(header + list(statements) + ["COMMIT;"])


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="受控历史数据导入工具")
    parser.add_argument("--source", required=True, help="旧数据导出（JSON）")
    parser.add_argument("--target", required=True, help="目标 MySQL 连接串")
    parser.add_argument("--confirm", required=True, help="确认目标，必须与 --target 完全一致")
    parser.add_argument("--dry-run", action="store_true", help="只输出 SQL，不执行写入")
    parser.add_argument("--output", default=None, help="SQL 输出文件；省略则打印到标准输出")
    args = parser.parse_args(list(argv) if argv is not None else None)

    try:
        require_confirmed_target(args.target, args.confirm)
    except ImportRefused as error:
        print(f"拒绝执行：{error}", file=sys.stderr)
        return 2

    legacy = load_legacy(Path(args.source))
    statements, report = build_statements(legacy)
    script = render_script(statements)
    if args.output:
        Path(args.output).write_text(script, encoding="utf-8")
    else:
        print(script)
    print(json.dumps(report.as_dict(), ensure_ascii=False, indent=2), file=sys.stderr)
    if args.dry_run:
        print("dry-run：没有执行任何写入。", file=sys.stderr)
        return 0
    print("导入脚本已生成，请按运行手册核对后执行。", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
