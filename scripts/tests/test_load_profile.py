#!/usr/bin/env python3
"""T415: 可重复非破坏性负载测量工具的测试。

覆盖固定样本、延迟分位数、错误率、有界并发，以及未知目标与未授权写请求的拒绝。测试用本地
回环服务器，不触碰任何外部系统。
"""

from __future__ import annotations

import importlib.util
import socket
import sys
import threading
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "load-profile.py"
spec = importlib.util.spec_from_file_location("load_profile", SCRIPT)
loader = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = loader
spec.loader.exec_module(loader)


class _Server(threading.Thread):
    """最小回环服务器：按路径返回状态码。"""

    def __init__(self, statuses: dict[str, int]) -> None:
        super().__init__(daemon=True)
        self.statuses = statuses
        self.socket = socket.socket()
        self.socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.socket.bind(("127.0.0.1", 0))
        self.socket.listen(16)
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
                    status = self.statuses.get(path, 200)
                    body = b"ok"
                    head = (
                        f"HTTP/1.1 {status} X\r\nContent-Length: {len(body)}\r\n"
                        "Connection: close\r\n\r\n"
                    ).encode("latin-1")
                    connection.sendall(head + body)
                except OSError:
                    continue

    def stop(self) -> None:
        self.running = False
        try:
            self.socket.close()
        except OSError:
            pass


class LoadProfileTest(unittest.TestCase):
    def test_unknown_targets_are_refused(self) -> None:
        for target in ["https://example.com", "http://10.0.0.1:8080", ""]:
            with self.assertRaises(loader.LoadRefused):
                loader.require_known_target(target)

    def test_write_requests_need_an_explicit_authorization(self) -> None:
        specs = [loader.RequestSpec("POST", "/api/cart/items")]

        with self.assertRaises(loader.LoadRefused):
            loader.require_authorized(specs, allow_writes=False)

        self.assertEqual(len(loader.require_authorized(specs, allow_writes=True)), 1)

    def test_read_requests_are_allowed_by_default(self) -> None:
        specs = [loader.RequestSpec("GET", "/api/categories")]

        self.assertEqual(len(loader.require_authorized(specs, allow_writes=False)), 1)

    def test_the_concurrency_is_bounded(self) -> None:
        with self.assertRaises(loader.LoadRefused):
            loader.run_load("http://127.0.0.1:1", [], iterations=1, concurrency=0)
        with self.assertRaises(loader.LoadRefused):
            loader.run_load("http://127.0.0.1:1", [], iterations=1, concurrency=99)

    def test_the_latency_percentiles_and_error_rate_are_reported(self) -> None:
        server = _Server({"/api/categories": 200, "/api/broken": 500})
        self.addCleanup(server.stop)
        server.start()
        target = f"http://127.0.0.1:{server.port}"

        result = loader.run_load(
            target,
            [loader.RequestSpec("GET", "/api/categories"), loader.RequestSpec("GET", "/api/broken")],
            iterations=2,
            concurrency=2,
        )

        payload = result.as_dict()
        # 2 个线程 × 2 轮 × 2 个请求 = 8 个样本，其中一半是错误响应。
        self.assertEqual(payload["total"], 8)
        self.assertEqual(payload["errors"], 4)
        self.assertAlmostEqual(payload["errorRate"], 0.5, places=3)
        self.assertGreaterEqual(payload["latencyMs"]["p50"], 0.0)
        self.assertGreaterEqual(payload["latencyMs"]["p99"], payload["latencyMs"]["p50"])
        self.assertGreater(payload["throughputPerSecond"], 0.0)

    def test_the_same_request_specification_is_parsed_consistently(self) -> None:
        specs = loader.parse_specs(["get /api/categories", "GET /api/items/EST-1"])

        self.assertEqual([spec.method for spec in specs], ["GET", "GET"])
        self.assertEqual([spec.path for spec in specs], ["/api/categories", "/api/items/EST-1"])

    def test_an_invalid_request_specification_is_refused(self) -> None:
        with self.assertRaises(loader.LoadRefused):
            loader.parse_specs(["GET"])


if __name__ == "__main__":
    unittest.main()
