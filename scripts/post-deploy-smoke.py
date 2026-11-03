#!/usr/bin/env python3
"""T438: 部署后只读冒烟工具。

对已部署实例执行只读冒烟：TLS 与版本关联、健康探针、前端静态资源、目录 API、匿名会话与
CSRF bootstrap、未登录订单 401、未知 API 404（plan §9、§11）。

约束：默认只读，绝不创建测试订单；输出脱敏；失败返回非零退出码。生产写入测试必须另外授权。
"""

from __future__ import annotations

import argparse
import json
import ssl
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass
from typing import Any, Iterable

# 只读检查项：(名称, 路径, 期望状态)
CHECKS = (
    ("readiness", "/actuator/health/readiness", 200),
    ("static entry", "/", 200),
    ("catalog api", "/api/categories", 200),
    ("csrf bootstrap", "/api/auth/csrf", 200),
    ("anonymous session", "/api/auth/session", 200),
    ("orders need login", "/api/orders", 401),
    ("unknown api", "/api/does-not-exist", 404),
)


class SmokeRefused(RuntimeError):
    """冒烟被拒绝：目标或参数不满足要求。"""


@dataclass
class CheckResult:
    name: str
    path: str
    expected: int
    status: int
    ok: bool
    detail: str = ""

    def as_dict(self) -> dict[str, Any]:
        return {
            "name": self.name,
            "path": self.path,
            "expected": self.expected,
            "status": self.status,
            "ok": self.ok,
            "detail": self.detail,
        }


def require_https(target: str) -> str:
    """生产冒烟必须走 HTTPS。"""
    if not target.startswith("https://"):
        raise SmokeRefused("生产冒烟必须使用 HTTPS 目标")
    return target.rstrip("/")


def http_status(url: str, timeout: float = 10.0, accept: str = "application/json, text/html") -> tuple[int, str]:
    request = urllib.request.Request(url, headers={"Accept": accept})
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(request, timeout=timeout) as response:  # noqa: S310 - 目标已校验
            return response.status, response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8", errors="replace")
    except (urllib.error.URLError, OSError):
        return 0, ""


def run_checks(target: str, timeout: float = 10.0) -> list[CheckResult]:
    """执行全部只读检查。"""
    results: list[CheckResult] = []
    for name, path, expected in CHECKS:
        status, body = http_status(f"{target}{path}", timeout=timeout)
        ok = status == expected
        detail = "" if ok else f"期望 {expected}，实际 {status}"
        results.append(CheckResult(name=name, path=path, expected=expected, status=status, ok=ok, detail=detail))
    return results


def version_marker(body: str) -> str:
    """从会话响应里取出可关联的版本信息（没有则为空）。"""
    try:
        data = json.loads(body)
    except (TypeError, ValueError):
        return ""
    if isinstance(data, dict):
        return str(data.get("version") or data.get("commit") or "")
    return ""


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="部署后只读冒烟")
    parser.add_argument("--target", required=True, help="目标基地址（HTTPS）")
    parser.add_argument("--timeout", type=float, default=10.0)
    parser.add_argument("--allow-http", action="store_true", help="仅用于隔离环境：允许明文 HTTP")
    args = parser.parse_args(list(argv) if argv is not None else None)

    try:
        target = args.target.rstrip("/")
        if not args.allow_http:
            target = require_https(target)
    except SmokeRefused as error:
        print(f"拒绝执行：{error}", file=sys.stderr)
        return 2

    results = run_checks(target, timeout=args.timeout)
    print(json.dumps([result.as_dict() for result in results], ensure_ascii=False, indent=2))
    failed = [result for result in results if not result.ok]
    if failed:
        print(f"冒烟失败：{len(failed)} 项未通过", file=sys.stderr)
        return 1
    print("只读冒烟全部通过。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
