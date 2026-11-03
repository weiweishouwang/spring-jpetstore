#!/usr/bin/env python3
"""T405: 本地全栈启动冒烟工具。

按开发指南启动并检查本地全栈链路：MySQL（复用已运行的实例或只做连通性检查）→ Boot 后端 →
Vite 前端。检查内容包括：后端健康探针、目录 API、CSRF bootstrap，以及通过 Vite 代理访问
同源 /api。

设计约束（plan §9、§11）：

* 只用有界超时与轮询，不用固定 sleep 掩盖启动慢的问题；
* 失败时保留脱敏诊断（进程输出里的口令一律不落盘）；
* 只清理本脚本自己启动的进程，绝不终止用户已有的服务，也不动数据库数据卷；
* 依赖版本与开发指南一致（Node 24.22.2、npm 11.19.1、Java 21）。
"""

from __future__ import annotations

import argparse
import json
import os
import re
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, Sequence

REPO_ROOT = Path(__file__).resolve().parent.parent
BACKEND_DIR = REPO_ROOT / "backend"
FRONTEND_DIR = REPO_ROOT / "frontend"

DEFAULT_TIMEOUT = 90.0
DEFAULT_INTERVAL = 0.5

# 日志脱敏：口令、Cookie、CSRF token 与假卡号不写进诊断文件。
SECRET_PATTERNS = (
    (re.compile(r"(?i)(password|passwd|pwd)([=:\"']+)([^\s\"',}]+)"), r"\1\2***"),
    (re.compile(r"(?i)(secret|token)([=:\"']+)([^\s\"',}]+)"), r"\1\2***"),
    (re.compile(r"FAKE-[0-9\-]+"), "FAKE-***"),
    (re.compile(r"(?i)(JSESSIONID|CSRF)[=:\"']+[^\s\"',}]+"), r"\1=***"),
)


class SmokeError(RuntimeError):
    """冒烟失败；消息里只包含脱敏后的诊断。"""


@dataclass
class ManagedProcess:
    """由本脚本启动的进程：退出时只清理自己。"""

    name: str
    process: subprocess.Popen | None = None
    log_path: Path | None = None
    _log_handle: object | None = field(default=None, repr=False)

    def stop(self) -> None:
        if self.process is None:
            return
        if self.process.poll() is None:
            self.process.terminate()
            try:
                self.process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                self.process.kill()
        if self._log_handle is not None:
            self._log_handle.close()  # type: ignore[attr-defined]
            self._log_handle = None

    def tail(self, lines: int = 40) -> str:
        if self.log_path is None or not self.log_path.exists():
            return ""
        content = self.log_path.read_text(encoding="utf-8", errors="replace").splitlines()
        return redact("\n".join(content[-lines:]))


def redact(text: str) -> str:
    """脱敏：口令、token、假卡号与 Cookie 一律不落盘。"""
    for pattern, replacement in SECRET_PATTERNS:
        text = pattern.sub(replacement, text)
    return text


def http_get(url: str, timeout: float = 5.0) -> tuple[int, str]:
    request = urllib.request.Request(url, headers={"Accept": "application/json, text/html"})
    # 本机检查不走代理：环境里的 http_proxy 只会把请求转给外部代理。
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(request, timeout=timeout) as response:  # noqa: S310 - 固定本机地址
            return response.status, response.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode("utf-8", errors="replace")


def wait_for_http(
    url: str,
    timeout: float = DEFAULT_TIMEOUT,
    interval: float = DEFAULT_INTERVAL,
    expect_status: int = 200,
    description: str = "",
) -> str:
    """轮询直到 HTTP 端点就绪；超时抛出带诊断的 SmokeError。"""
    deadline = time.monotonic() + timeout
    last_error = ""
    while time.monotonic() < deadline:
        try:
            status, body = http_get(url, timeout=min(10.0, timeout))
            if status == expect_status:
                return body
            last_error = f"HTTP {status}"
        except (urllib.error.URLError, OSError, socket.timeout) as error:
            last_error = str(error)
        time.sleep(interval)
    raise SmokeError(f"{description or url} 在 {timeout:.0f} 秒内没有就绪（最后结果：{last_error}）")


def wait_for_database(host: str, port: int, timeout: float = DEFAULT_TIMEOUT) -> None:
    """等待数据库端口可连接。"""
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            with socket.create_connection((host, port), timeout=2):
                return
        except OSError:
            time.sleep(DEFAULT_INTERVAL)
    raise SmokeError(f"数据库 {host}:{port} 在 {timeout:.0f} 秒内没有就绪")


def free_port() -> int:
    """取一个空闲端口，避免与用户已有服务冲突。"""
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def start_process(name: str, command: Sequence[str], cwd: Path, log_dir: Path) -> ManagedProcess:
    """启动一个进程并把输出写入脱敏日志文件。"""
    log_dir.mkdir(parents=True, exist_ok=True)
    log_path = log_dir / f"{name}.log"
    handle = log_path.open("w", encoding="utf-8")
    process = subprocess.Popen(  # noqa: S603 - 命令来自本仓库的固定脚本
        list(command),
        cwd=str(cwd),
        stdout=handle,
        stderr=subprocess.STDOUT,
        env=os.environ.copy(),
    )
    return ManagedProcess(name=name, process=process, log_path=log_path, _log_handle=handle)


def check_backend(base_url: str, timeout: float = DEFAULT_TIMEOUT) -> None:
    """后端就绪：健康探针、目录 API 与 CSRF bootstrap。"""
    wait_for_http(f"{base_url}/actuator/health", timeout=timeout, description="后端健康探针")
    body = wait_for_http(f"{base_url}/api/categories", timeout=timeout, description="目录 API")
    try:
        json.loads(body)
    except json.JSONDecodeError as error:
        raise SmokeError(f"目录 API 没有返回 JSON：{error}") from error
    csrf = wait_for_http(f"{base_url}/api/auth/csrf", timeout=timeout, description="CSRF bootstrap")
    if "token" not in csrf:
        raise SmokeError("CSRF bootstrap 没有返回 token")


def check_frontend(base_url: str, backend_base_url: str, timeout: float = DEFAULT_TIMEOUT) -> None:
    """前端就绪：首页可加载，且 /api 经同源代理可达。"""
    body = wait_for_http(base_url, timeout=timeout, description="前端首页")
    if "<html" not in body.lower():
        raise SmokeError("前端首页没有返回 HTML")
    proxied = wait_for_http(f"{base_url}/api/categories", timeout=timeout, description="Vite 代理的目录 API")
    if not proxied.strip().startswith("["):
        raise SmokeError("Vite 代理没有返回目录数组")
    if backend_base_url:
        wait_for_http(f"{backend_base_url}/actuator/health", timeout=timeout, description="后端健康探针")


def run_smoke(
    database_host: str = "127.0.0.1",
    database_port: int = 3306,
    backend_url: str | None = None,
    frontend_url: str | None = None,
    timeout: float = DEFAULT_TIMEOUT,
    log_dir: Path | None = None,
    start_backend: bool = True,
    start_frontend: bool = True,
) -> int:
    """执行冒烟：返回进程退出码（0 表示通过）。"""
    log_dir = log_dir or (REPO_ROOT / "target" / "dev-smoke")
    managed: list[ManagedProcess] = []
    backend_base = backend_url
    frontend_base = frontend_url
    try:
        wait_for_database(database_host, database_port, timeout=timeout)
        if backend_base is None and start_backend:
            port = free_port()
            backend_base = f"http://127.0.0.1:{port}"
            managed.append(
                start_process(
                    "backend",
                    ["./mvnw", "-q", "spring-boot:run", f"-Dspring-boot.run.arguments=--server.port={port}"],
                    BACKEND_DIR,
                    log_dir,
                )
            )
        if backend_base is None:
            raise SmokeError("没有可用的后端地址：既没有传入地址，也不允许启动后端")
        check_backend(backend_base, timeout=timeout)

        if frontend_base is None and start_frontend:
            port = free_port()
            frontend_base = f"http://127.0.0.1:{port}"
            managed.append(
                start_process(
                    "frontend",
                    ["npm", "run", "dev", "--", "--port", str(port), "--strictPort"],
                    FRONTEND_DIR,
                    log_dir,
                )
            )
        if frontend_base is None:
            raise SmokeError("没有可用的前端地址：既没有传入地址，也不允许启动前端")
        check_frontend(frontend_base, backend_base, timeout=timeout)
        print("全栈冒烟通过：数据库、后端与前端都就绪。")
        return 0
    except SmokeError as error:
        print(f"全栈冒烟失败：{error}", file=sys.stderr)
        for process in managed:
            tail = process.tail()
            if tail:
                print(f"--- {process.name} 日志尾部（已脱敏）---\n{tail}", file=sys.stderr)
        return 1
    finally:
        # 只清理本脚本启动的进程。
        for process in managed:
            process.stop()


def main(argv: Iterable[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="本地全栈启动冒烟检查")
    parser.add_argument("--database-host", default="127.0.0.1")
    parser.add_argument("--database-port", type=int, default=3306)
    parser.add_argument("--backend-url", default=None, help="已运行的后端地址；省略则由脚本启动")
    parser.add_argument("--frontend-url", default=None, help="已运行的前端地址；省略则由脚本启动")
    parser.add_argument("--timeout", type=float, default=DEFAULT_TIMEOUT)
    parser.add_argument("--no-start", action="store_true", help="只检查已运行的服务，不启动任何进程")
    args = parser.parse_args(list(argv) if argv is not None else None)
    return run_smoke(
        database_host=args.database_host,
        database_port=args.database_port,
        backend_url=args.backend_url,
        frontend_url=args.frontend_url,
        timeout=args.timeout,
        start_backend=not args.no_start,
        start_frontend=not args.no_start,
    )


if __name__ == "__main__":
    raise SystemExit(main())
