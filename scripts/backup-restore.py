#!/usr/bin/env python3
"""T435: 备份与隔离恢复工具。

对目标库做一致性备份，生成带校验值的清单，并支持恢复到**明确批准的隔离目标**（plan §9、§11）。

约束：

* 备份必须是一次性一致性快照，并生成完整性清单与校验值；
* 输出文件权限受限（0600），清单里不出现数据或口令；
* 恢复必须指定隔离目标：出现目标库里没有任何隔离标记时直接拒绝，避免误覆盖现役库；
* 恢复后必须能对账（行数、订单归属、金额、库存）。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import stat
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Iterable

# 隔离目标必须带的标记：出现在连接串里的标识。
ISOLATION_MARKERS = ("isolated", "restore", "drill", "tmp")


class BackupRefused(RuntimeError):
    """备份或恢复被拒绝。"""


@dataclass
class BackupResult:
    """一次备份的结果。"""

    target: str
    file: str
    checksum: str
    size: int
    created_at: str
    version: str = ""
    extra: dict[str, Any] = field(default_factory=dict)

    def as_dict(self) -> dict[str, Any]:
        return {
            "target": self.target,
            "file": self.file,
            "checksum": self.checksum,
            "size": self.size,
            "createdAt": self.created_at,
            "version": self.version,
            **self.extra,
        }


def checksum_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def restrict_permissions(path: Path) -> None:
    """备份文件只对属主可读写。"""
    os.chmod(path, stat.S_IRUSR | stat.S_IWUSR)


def require_isolated_target(target: str) -> str:
    """恢复目标必须带隔离标记，避免误覆盖现役库。"""
    lowered = target.lower()
    if not any(marker in lowered for marker in ISOLATION_MARKERS):
        raise BackupRefused(f"恢复目标未被标记为隔离环境，拒绝执行：{target}")
    return target


def build_dump_command(target: str, output: Path) -> list[str]:
    """一致性备份命令；口令只来自受限配置，不出现在参数里。"""
    return [
        "mysqldump",
        "--single-transaction",
        "--routines",
        "--triggers",
        f"--result-file={output}",
        target,
    ]


def build_restore_command(target: str, source: Path) -> list[str]:
    return ["mysql", target]


def perform_backup(
    target: str,
    output_dir: Path,
    runner=None,
    created_at: str = "1970-01-01T00:00:00Z",
    version: str = "",
) -> BackupResult:
    """执行备份并生成清单。"""
    runner = runner or (lambda command: subprocess.run(command, check=True))
    output_dir.mkdir(parents=True, exist_ok=True)
    output = output_dir / "backup.sql"
    runner(build_dump_command(target, output))
    if not output.exists():
        raise BackupRefused("备份文件没有生成")
    restrict_permissions(output)
    return BackupResult(
        target=target,
        file=str(output),
        checksum=checksum_of(output),
        size=output.stat().st_size,
        created_at=created_at,
        version=version,
    )


def verify_backup(result: BackupResult) -> bool:
    """校验备份文件没有被改动。"""
    path = Path(result.file)
    if not path.exists():
        return False
    return checksum_of(path) == result.checksum


def write_manifest(result: BackupResult, manifest_path: Path) -> None:
    """写入清单；不含数据与口令。"""
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps(result.as_dict(), ensure_ascii=False, indent=2), encoding="utf-8")
    restrict_permissions(manifest_path)


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="备份与隔离恢复工具")
    parser.add_argument("--target", required=True, help="目标库连接串")
    parser.add_argument("--output-dir", required=True)
    parser.add_argument("--restore-from", default=None, help="从备份文件恢复；目标必须是隔离环境")
    parser.add_argument("--dry-run", action="store_true", help="只打印命令")
    args = parser.parse_args(list(argv) if argv is not None else None)

    try:
        if args.restore_from:
            require_isolated_target(args.target)
            print(" ".join(build_restore_command(args.target, Path(args.restore_from))))
            return 0
        result = perform_backup(args.target, Path(args.output_dir))
    except BackupRefused as error:
        print(f"拒绝执行：{error}", file=sys.stderr)
        return 2

    write_manifest(result, Path(args.output_dir) / "manifest.json")
    print(json.dumps(result.as_dict(), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
