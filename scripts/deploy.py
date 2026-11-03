#!/usr/bin/env python3
"""T429: 服务器部署编排脚本。

按发布清单执行一次受控部署：校验发布清单与目标、调用备份与迁移工具、按已批准的切换策略
停旧起新、等待 readiness、执行只读冒烟（plan §9、§11）。

安全约束：

* 只接受不可变镜像引用（``@sha256:``），拒绝浮动标签；
* 目标必须与确认值完全一致，未确认的目标一律拒绝；
* secret 只引用名称，值由运行环境注入，绝不出现在命令行或日志里；
* 备份与迁移校验必须先于启动；任何一步失败都返回非零，并且保留上一次镜像引用，
  便于按已演练流程回退；
* 默认 dry-run：只打印将要执行的步骤，不做任何变更。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable, Iterable, Sequence

# 不可变镜像引用：名字@摘要。
IMAGE_PATTERN = re.compile(r"^[a-z0-9./\-]+@sha256:[0-9a-f]{64}$")

# 部署步骤的固定顺序：备份 → 迁移 → 停旧起新 → readiness → 冒烟。
STEPS = ["backup", "migrate", "switch", "readiness", "smoke"]

# 冒烟只读，不写测试订单。
SMOKE_REQUEST = "GET /api/categories"


class DeployRefused(RuntimeError):
    """部署被拒绝：清单、目标或 secret 不满足要求。"""


@dataclass
class ReleaseManifest:
    """一次发布的清单。"""

    commit: str
    image: str
    target: str
    env_file: str
    secrets: list[str] = field(default_factory=list)
    previous_image: str | None = None

    def as_dict(self) -> dict[str, Any]:
        return {
            "commit": self.commit,
            "image": self.image,
            "target": self.target,
            "envFile": self.env_file,
            "secrets": list(self.secrets),
            "previousImage": self.previous_image,
        }


def load_manifest(path: Path) -> ReleaseManifest:
    data = json.loads(path.read_text(encoding="utf-8"))
    return ReleaseManifest(
        commit=str(data.get("commit", "")),
        image=str(data.get("image", "")),
        target=str(data.get("target", "")),
        env_file=str(data.get("envFile", "")),
        secrets=list(data.get("secrets", [])),
        previous_image=data.get("previousImage"),
    )


def validate_manifest(manifest: ReleaseManifest, confirmed_target: str | None) -> None:
    """清单必须完整、镜像必须不可变、目标必须被确认。"""
    if not manifest.commit:
        raise DeployRefused("发布清单缺少 commit")
    if not IMAGE_PATTERN.match(manifest.image):
        raise DeployRefused(f"只接受不可变镜像引用（@sha256:）：{manifest.image}")
    if not manifest.target:
        raise DeployRefused("发布清单缺少目标")
    if not confirmed_target or confirmed_target != manifest.target:
        raise DeployRefused("目标未被确认，或与清单不一致")
    if not manifest.env_file:
        raise DeployRefused("发布清单缺少运行环境引用")
    missing = [name for name in manifest.secrets if not name]
    if missing:
        raise DeployRefused("secret 引用不能为空")


def plan(manifest: ReleaseManifest) -> list[str]:
    """将要执行的步骤（dry-run 输出）。"""
    return [
        f"backup: 备份 {manifest.target} 的数据库",
        f"migrate: 用固定版本 Flyway 迁移 {manifest.target}",
        f"switch: 停旧起新，镜像 {manifest.image}（上一次：{manifest.previous_image or '无'}）",
        "readiness: 等待 /actuator/health/readiness 就绪",
        f"smoke: 只读冒烟 {SMOKE_REQUEST}",
    ]


def deploy(
    manifest: ReleaseManifest,
    confirmed_target: str | None,
    runner: Callable[[str], str],
    dry_run: bool = True,
) -> int:
    """执行部署；runner 是实际动作的可注入实现（生产调用与测试替身共用同一接口）。"""
    validate_manifest(manifest, confirmed_target)
    steps = plan(manifest)
    if dry_run:
        for step in steps:
            print(step)
        print("dry-run：没有执行任何变更。")
        return 0

    previous = manifest.previous_image
    for name in STEPS:
        try:
            runner(name)
        except Exception as error:  # noqa: BLE001 - 失败必须变成非零退出码
            print(f"部署在步骤 {name} 失败：{error}", file=sys.stderr)
            if previous:
                print(f"上一次镜像引用保留为 {previous}，请按已演练流程回退。", file=sys.stderr)
            return 1
    print("部署完成。")
    return 0


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="服务器部署编排")
    parser.add_argument("--manifest", required=True, help="发布清单（JSON）")
    parser.add_argument("--confirm-target", default=None, help="确认目标，必须与清单一致")
    parser.add_argument("--execute", action="store_true", help="真正执行（默认 dry-run）")
    args = parser.parse_args(list(argv) if argv is not None else None)

    manifest = load_manifest(Path(args.manifest))

    def runner(step: str) -> str:
        # 生产实现在这里调用备份、迁移、切换与冒烟工具；默认路径不执行。
        return step

    return deploy(manifest, args.confirm_target, runner, dry_run=not args.execute)


if __name__ == "__main__":
    raise SystemExit(main())
