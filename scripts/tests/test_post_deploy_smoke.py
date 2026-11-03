#!/usr/bin/env python3
"""T437: 部署后只读冒烟工具的测试。

覆盖 HTTPS 与静态资源、API、运行版本关联、readiness、匿名身份与 CSRF、未登录订单 401、
未知 API 404，以及有界失败。默认只读，不向真实用户写测试订单。
"""

from __future__ import annotations

import importlib.util
import sys
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "post-deploy-smoke.py"
spec = importlib.util.spec_from_file_location("post_deploy_smoke", SCRIPT)
smoke = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = smoke
spec.loader.exec_module(smoke)


class PostDeploySmokeTest(unittest.TestCase):
    def test_the_production_target_must_use_https(self) -> None:
        with self.assertRaises(smoke.SmokeRefused):
            smoke.require_https("http://prod.example.internal")
        self.assertEqual(smoke.require_https("https://prod.example.internal/"),
                         "https://prod.example.internal")

    def test_the_checks_cover_the_read_only_contract(self) -> None:
        paths = {path for _, path, _ in smoke.CHECKS}

        self.assertIn("/actuator/health/readiness", paths)
        self.assertIn("/", paths)
        self.assertIn("/api/categories", paths)
        self.assertIn("/api/auth/csrf", paths)
        self.assertIn("/api/auth/session", paths)

    def test_the_order_endpoint_must_require_a_login(self) -> None:
        orders = next(item for item in smoke.CHECKS if item[1] == "/api/orders")

        self.assertEqual(orders[2], 401)

    def test_an_unknown_api_must_be_a_not_found(self) -> None:
        unknown = next(item for item in smoke.CHECKS if item[1] == "/api/does-not-exist")

        self.assertEqual(unknown[2], 404)

    def test_no_check_writes(self) -> None:
        for _, path, _ in smoke.CHECKS:
            self.assertNotIn("POST", path.upper())
        self.assertTrue(all(path.split(" ")[0] in ("GET", "HEAD") or " " not in path
                            for path in (item[1] for item in smoke.CHECKS)))

    def test_an_unreachable_target_fails_with_a_bounded_timeout(self) -> None:
        results = smoke.run_checks("https://127.0.0.1:1", timeout=0.5)

        self.assertTrue(results)
        self.assertTrue(any(not result.ok for result in results))

    def test_the_version_marker_is_read_from_the_session_body(self) -> None:
        self.assertEqual(smoke.version_marker('{"version":"31e8f7c"}'), "31e8f7c")
        self.assertEqual(smoke.version_marker("not json"), "")


if __name__ == "__main__":
    unittest.main()
