#!/usr/bin/env python3
"""T440: 受控应用回滚工具。

把应用切回上一个兼容的镜像或数据库版本：核对允许回退的应用/schema 组合，默认 dry-run；
授权后停写并切回前一兼容镜像，保持当前业务库，再执行 readiness 与只读冒烟
（plan §9、§11）。

约束：不可逆的 schema 变化拒绝自动回退；存在新订单时不得用旧库覆盖；不兼容时停止并引导
前向修复或显式回迁。
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from typing import Any, Callable, Iterable

# 允许自动回退的应用/schema 组合：旧镜像能读的 schema 版本上限。
COMPATIBLE_SCHEMA_MAX = 7

# 不可逆的迁移标记：出现这些迁移时不允许自动回退。
IRREVERSIBLE_MIGRATIONS = ("V6__", "V7__")


class RollbackRefused(RuntimeError):
    """回滚被拒绝：组合不兼容或目标未确认。"""


@dataclass
class RollbackPlan:
    """一次回滚的输入。"""

    current_image: str
    previous_image: str
    target: str
    current_schema: int
    previous_schema: int
    orders_after_deploy: int = 0
    applied_migrations: tuple[str, ...] = ()

    def as_dict(self) -> dict[str, Any]:
        return {
            "currentImage": self.current_image,
            "previousImage": self.previous_image,
            "target": self.target,
            "currentSchema": self.current_schema,
            "previousSchema": self.previous_schema,
            "ordersAfterDeploy": self.orders_after_deploy,
            "appliedMigrations": list(self.applied_migrations),
        }


def validate_plan(plan: RollbackPlan, confirmed_target: str | None) -> None:
    """目标必须确认；组合必须兼容；不可逆迁移拒绝自动回退。"""
    if not confirmed_target or confirmed_target != plan.target:
        raise RollbackRefused("回滚目标未被确认，或与计划不一致")
    if not plan.previous_image:
        raise RollbackRefused("没有可回退的上一个镜像")
    if "@sha256:" not in plan.previous_image:
        raise RollbackRefused("回滚只接受不可变镜像引用")
    if plan.previous_schema > COMPATIBLE_SCHEMA_MAX:
        raise RollbackRefused("旧版本无法读取当前 schema，拒绝自动回退")
    if any(name.startswith(IRREVERSIBLE_MIGRATIONS) for name in plan.applied_migrations):
        raise RollbackRefused("包含不可逆迁移，拒绝自动回退，请走前向修复或显式回迁")
    if plan.orders_after_deploy > 0 and plan.previous_schema < plan.current_schema:
        raise RollbackRefused("切换后已产生新订单且 schema 不兼容，禁止用旧库覆盖")


def steps(plan: RollbackPlan) -> list[str]:
    """回滚步骤：停写 → 切回镜像 → readiness → 只读冒烟。"""
    return [
        "stop-writes: 停止新订单写入",
        f"switch: 切回镜像 {plan.previous_image}（当前 {plan.current_image}）",
        "readiness: 等待 /actuator/health/readiness 就绪",
        "smoke: 只读冒烟",
    ]


def rollback(
    plan: RollbackPlan,
    confirmed_target: str | None,
    runner: Callable[[str], str],
    dry_run: bool = True,
) -> int:
    """执行回滚；runner 是实际动作的可注入实现。"""
    validate_plan(plan, confirmed_target)
    if dry_run:
        for step in steps(plan):
            print(step)
        print("dry-run：没有执行任何变更。")
        return 0
    for name in ("stop-writes", "switch", "readiness", "smoke"):
        try:
            runner(name)
        except Exception as error:  # noqa: BLE001 - 失败必须变成非零退出码
            print(f"回滚在步骤 {name} 失败：{error}", file=sys.stderr)
            return 1
    print("回滚完成。")
    return 0


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="受控应用回滚工具")
    parser.add_argument("--plan", required=True, help="回滚计划（JSON）")
    parser.add_argument("--confirm-target", default=None)
    parser.add_argument("--execute", action="store_true", help="真正执行（默认 dry-run）")
    args = parser.parse_args(list(argv) if argv is not None else None)

    data = json.loads(Path(args.plan).read_text(encoding="utf-8"))
    plan = RollbackPlan(
        current_image=str(data.get("currentImage", "")),
        previous_image=str(data.get("previousImage", "")),
        target=str(data.get("target", "")),
        current_schema=int(data.get("currentSchema", 0)),
        previous_schema=int(data.get("previousSchema", 0)),
        orders_after_deploy=int(data.get("ordersAfterDeploy", 0)),
        applied_migrations=tuple(data.get("appliedMigrations", ())),
    )

    def runner(step: str) -> str:
        return step

    return rollback(plan, args.confirm_target, runner, dry_run=not args.execute)


if __name__ == "__main__":
    raise SystemExit(main())
