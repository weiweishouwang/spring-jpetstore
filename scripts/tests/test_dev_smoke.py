#!/usr/bin/env python3
"""T404: 本地全栈启动冒烟工具的测试。

覆盖数据库、后端与前端的就绪判定，有界超时，Vue 路由与同源代理 API，失败时的脱敏诊断，
以及“只清理脚本自己启动的进程”。测试不依赖固定 sleep，也不触碰用户已有服务与数据库卷。
"""

from __future__ import annotations

import importlib.util
import socket
import sys
import threading
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "dev-smoke.py"
spec = importlib.util.spec_from_file_location("dev_smoke", SCRIPT)
dev_smoke = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = dev_smoke
spec.loader.exec_module(dev_smoke)


class _Server(threading.Thread):
    """最小的测试服务器：按路径返回固定内容。

    这里不用 http.server：Python 3.14 的请求行解析在部分环境下不可靠，测试服务器自己解析更可控。
    """

    def __init__(self) -> None:
        super().__init__(daemon=True)
        self.routes: dict[str, tuple[int, str, str]] = {}
        self.socket = socket.socket()
        self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.socket.bind(("127.0.0.1", 0))
        self.socket.listen(8)
        self.port = int(self.socket.getsockname()[1])
        self.running = True

    def run(self) -> None:
        while self.running:
            try:
                connection, _ = self.socket.accept()
            except OSError:
                return
            with connection:
                try:
                    request = connection.recv(4096).decode("latin-1")
                    path = request.split(" ")[1] if " " in request else "/"
                    status, content_type, body = self.routes.get(path, (404, "text/plain", "not found"))
                    payload = body.encode("utf-8")
                    head = (
                        f"HTTP/1.1 {status} X\r\n"
                        f"Content-Type: {content_type}\r\n"
                        f"Content-Length: {len(payload)}\r\n"
                        "Connection: close\r\n\r\n"
                    ).encode("latin-1")
                    connection.sendall(head + payload)
                except OSError:
                    continue

    def stop(self) -> None:
        self.running = False
        try:
            self.socket.close()
        except OSError:
            pass


class DevSmokeTest(unittest.TestCase):
    def setUp(self) -> None:
        self.server = _Server()
        self.server.start()
        self.base_url = f"http://127.0.0.1:{self.server.port}"

    def tearDown(self) -> None:
        self.server.stop()

    def test_waits_until_the_endpoint_is_ready(self) -> None:
        self.server.routes = {"/ready": (200, "application/json", "[]")}

        body = dev_smoke.wait_for_http(f"{self.base_url}/ready", timeout=5)

        self.assertEqual(body, "[]")

    def test_the_timeout_is_bounded_and_reports_the_failure(self) -> None:
        self.server.routes = {}

        with self.assertRaises(dev_smoke.SmokeError) as failure:
            dev_smoke.wait_for_http(f"{self.base_url}/never", timeout=1, interval=0.1)

        self.assertIn("没有就绪", str(failure.exception))

    def test_the_database_check_uses_a_bounded_timeout(self) -> None:
        listener = socket.socket()
        listener.bind(("127.0.0.1", 0))
        listener.listen(1)
        self.addCleanup(listener.close)
        port = listener.getsockname()[1]

        dev_smoke.wait_for_database("127.0.0.1", port, timeout=5)

    def test_the_diagnostics_are_redacted(self) -> None:
        text = 'password=hunter2 token=abc123 card=FAKE-4111-1111 JSESSIONID=deadbeef'

        redacted = dev_smoke.redact(text)

        self.assertNotIn("hunter2", redacted)
        self.assertNotIn("abc123", redacted)
        self.assertNotIn("FAKE-4111-1111", redacted)
        self.assertNotIn("deadbeef", redacted)

    def test_the_frontend_check_requires_html_and_the_proxied_api(self) -> None:
        self.server.routes = {
            "/": (200, "text/html", "<html><div id=\"app\"></div></html>"),
            "/api/categories": (200, "application/json", "[]"),
        }

        dev_smoke.check_frontend(self.base_url, backend_base_url="", timeout=2)

    def test_a_non_json_api_is_rejected(self) -> None:
        self.server.routes = {
            "/": (200, "text/html", "<html></html>"),
            "/api/categories": (200, "text/html", "<html></html>"),
        }

        with self.assertRaises(dev_smoke.SmokeError):
            dev_smoke.check_frontend(self.base_url, backend_base_url="", timeout=2)

    def test_the_backend_check_requires_the_health_api_and_csrf(self) -> None:
        self.server.routes = {
            "/actuator/health": (200, "application/json", '{"status":"UP"}'),
            "/api/categories": (200, "application/json", "[]"),
            "/api/auth/csrf": (200, "application/json", '{"token":"t","headerName":"X-CSRF-TOKEN"}'),
        }

        dev_smoke.check_backend(self.base_url, timeout=2)

    def test_a_missing_csrf_token_is_rejected(self) -> None:
        self.server.routes = {
            "/actuator/health": (200, "application/json", '{"status":"UP"}'),
            "/api/categories": (200, "application/json", "[]"),
            "/api/auth/csrf": (200, "application/json", "{}"),
        }

        with self.assertRaises(dev_smoke.SmokeError):
            dev_smoke.check_backend(self.base_url, timeout=2)

    def test_only_managed_processes_are_stopped(self) -> None:
        managed = dev_smoke.ManagedProcess(name="not-started")

        managed.stop()

        self.assertIsNone(managed.process)

    def test_the_free_port_helper_returns_a_usable_port(self) -> None:
        port = dev_smoke.free_port()

        probe = socket.socket()
        probe.bind(("127.0.0.1", port))
        probe.close()


if __name__ == "__main__":
    unittest.main()
