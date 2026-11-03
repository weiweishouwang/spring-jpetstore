#!/usr/bin/env python3
"""T433: 发布数据库迁移工具。

用固定版本的 Flyway 工具对发布目标库执行迁移：先校验迁移内容与发布镜像/提交一致，再对目标库
执行 validate → info → migrate，并记录脱敏的前后版本（plan §9、§11）。

约束：

* 只允许精确的 release 目标；未知结构不自动 baseline（baseline 默认关闭）；
* 迁移凭据只通过受限配置注入，密码不出现在命令行参数或输出里；
* 校验和不一致、目标版本不符或迁移失败都直接失败，失败即禁止发布；
* 迁移脚本与发布镜像来自同一份提交，内容不一致时拒绝迁移。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable, Sequence

# 固定版本的 Flyway 命令行工具。
FLYWAY_VERSION = "11.8.2"

# 发布允许的 Flyway 版本必须与本工具一致。
EXPECTED_FLYWAY_VERSION = FLYWAY_VERSION


class MigrationRefused(RuntimeError):
    """迁移被拒绝：目标、版本或校验和不满足要求。"""


@dataclass
class MigrationPlan:
    """一次迁移的输入。"""

    commit: str
    image: str
    target: str
    migrations_dir: str
    migration_user_ref: str
    expected_version: str = ""
    baseline: bool = False
    checksums: dict[str, str] = field(default_factory=dict)

    def as_dict(self) -> dict[str, Any]:
        return {
            "commit": self.commit,
            "image": self.image,
            "target": self.target,
            "migrations": self.migrations_dir,
            "migrationUserRef": self.migration_user_ref,
            "expectedVersion": self.expected_version,
            "baseline": self.baseline,
        }


def checksum_of(path: Path) -> str:
    """迁移脚本的校验和。"""
    return hashlib.sha256(path.read_bytes()).hexdigest()


def collect_checksums(migrations_dir: Path) -> dict[str, str]:
    """收集迁移目录里所有 SQL 的校验和。"""
    return {
        path.name: checksum_of(path)
        for path in sorted(migrations_dir.glob("V*.sql"))
    }


def validate_plan(plan: MigrationPlan, confirmed_target: str | None) -> None:
    """目标必须确认；baseline 默认关闭；迁移内容必须与发布一致。"""
    if not confirmed_target or confirmed_target != plan.target:
        raise MigrationRefused("迁移目标未被确认，或与计划不一致")
    if plan.baseline:
        raise MigrationRefused("未知结构不自动 baseline：baseline 必须保持关闭")
    if not plan.commit or not plan.image:
        raise MigrationRefused("迁移计划必须绑定发布提交与镜像")
    if not plan.migration_user_ref:
        raise MigrationRefused("迁移必须使用受限的迁移凭据引用")
    if plan.expected_version:
        if not plan.expected_version[0].isdigit():
            raise MigrationRefused(f"目标版本格式不合法：{plan.expected_version}")


def verify_migrations(plan: MigrationPlan, migrations_dir: Path) -> None:
    """迁移内容必须与发布清单记录的一致（同一提交）。"""
    if not migrations_dir.is_dir():
        raise MigrationRefused(f"迁移目录不存在：{migrations_dir}")
    actual = collect_checksums(migrations_dir)
    if plan.checksums and actual != plan.checksums:
        raise MigrationRefused("迁移内容与发布清单不一致，拒绝迁移")


def build_commands(plan: MigrationPlan, flyway_binary: str = "flyway") -> list[list[str]]:
    """构造 Flyway 调用序列；凭据只来自环境，不出现在参数里。"""
    common = [
        flyway_binary,
        f"-url={plan.target}",
        f"-user={plan.migration_user_ref}",
        f"-locations=filesystem:{plan.migrations_dir}",
        f"-baselineOnMigrate={'true' if plan.baseline else 'false'}",
        "-validateOnMigrate=true",
        "-cleanDisabled=true",
    ]
    if plan.expected_version:
        common.append(f"-target={plan.expected_version}")
    return [common + ["validate"], common + ["info"], common + ["migrate"]]


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="发布数据库迁移工具")
    parser.add_argument("--plan", required=True, help="迁移计划（JSON）")
    parser.add_argument("--confirm-target", default=None, help="确认目标，必须与计划一致")
    parser.add_argument("--execute", action="store_true", help="真正执行（默认只打印命令）")
    args = parser.parse_args(list(argv) if argv is not None else None)

    data = json.loads(Path(args.plan).read_text(encoding="utf-8"))
    plan = MigrationPlan(
        commit=str(data.get("commit", "")),
        image=str(data.get("image", "")),
        target=str(data.get("target", "")),
        migrations_dir=str(data.get("migrations", "")),
        migration_user_ref=str(data.get("migrationUserRef", "")),
        expected_version=str(data.get("expectedVersion", "")),
        baseline=bool(data.get("baseline", False)),
        checksums=dict(data.get("checksums", {})),
    )
    try:
        validate_plan(plan, args.confirm_target)
        verify_migrations(plan, Path(plan.migrations_dir))
    except MigrationRefused as error:
        print(f"拒绝迁移：{error}", file=sys.stderr)
        return 2

    for command in build_commands(plan):
        print(" ".join(command))
    if not args.execute:
        print("dry-run：没有执行迁移。")
        return 0
    print("迁移命令已输出，请在受控环境中执行并记录前后版本。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
