#!/usr/bin/env python3
"""依赖中间件管理脚本（deps-start.sh / deps-stop.sh / deps-status.sh）的测试。

用临时注册表、临时假命令与一个真实的本地监听端口覆盖：

* 启动脚本必须等到端口真正可用才返回（“启动了但还没 ready”是最容易漏的坑）；
* 永远起不来的服务必须判失败并返回非零；
* 状态脚本要报出进程状态、就绪状态与端口监听情况；
* 停止脚本只终止本脚本自己记录过 PID 的进程，绝不动用户自己的进程；
* brew services 与 systemd 的管理方式要把命令分发到正确的子命令。

测试不触碰本机真实的中间件：注册表、假命令、监听端口与 PID 目录都在临时目录里。
"""

from __future__ import annotations

import os
import socket
import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parents[1]
REPO_ROOT = SCRIPTS.parent

LISTENER = """\
import socket
import sys
import time

port = int(sys.argv[1])
delay = float(sys.argv[2])
if delay:
    time.sleep(delay)
sock = socket.socket()
sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
sock.bind(("127.0.0.1", port))
sock.listen(8)
while True:
    time.sleep(3600)
"""

FAKE_COMMAND = """\
#!/usr/bin/env bash
# 假的 brew / systemctl：把调用记进日志，并用一个状态文件模拟“跑着/没跑”。
printf '%s\\n' "$*" >> "${FAKE_COMMAND_LOG:?}"
state="${FAKE_COMMAND_STATE:?}"
subcommand="$2"
case "$1" in
  start) subcommand="start" ;;
  stop) subcommand="stop" ;;
  is-active) subcommand="status" ;;
esac
case "$subcommand" in
  start) touch "$state" ;;
  stop) rm -f "$state" ;;
  status|info)
    if [ -f "$state" ]; then exit 0; fi
    exit 1
    ;;
esac
exit 0
"""


def free_port() -> int:
    """借一个空闲端口：脚本要是在监听，用的就是这个端口。"""
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return int(sock.getsockname()[1])


def port_open(port: int) -> bool:
    with socket.socket() as sock:
        sock.settimeout(1)
        return sock.connect_ex(("127.0.0.1", port)) == 0


class DepsScriptsTest(unittest.TestCase):
    """三个脚本的共同行为。"""

    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.bin = self.root / "bin"
        self.bin.mkdir()
        self.run_dir = self.root / "run"
        self.port = free_port()
        self.listener = self.root / "listener.py"
        self.listener.write_text(LISTENER, encoding="utf-8")
        self.command_log = self.root / "fake-command.log"
        self.command_log.write_text("", encoding="utf-8")
        self.command_state = self.root / "fake-command.state"
        for name in ("brew", "systemctl"):
            fake = self.bin / name
            fake.write_text(FAKE_COMMAND, encoding="utf-8")
            fake.chmod(0o755)

    def write_registry(self, body: str) -> Path:
        conf = self.root / "deps.conf"
        conf.write_text(textwrap.dedent(body), encoding="utf-8")
        return conf

    def command_service_registry(self, delay: float = 0.0) -> str:
        """一个由脚本自己拉起、自己记录 PID 的命令型服务。"""
        return f"""
            deps_register_services() {{
              deps_add_service "fake" "假中间件" "command" \\
                "python3 {self.listener} {self.port} {delay}" \\
                "true" \\
                "true" \\
                "port:127.0.0.1:{self.port}" \\
                "{self.port}"
            }}
        """

    def run_script(self, name: str, conf: Path, *args: str, extra_env: dict | None = None):
        env = dict(os.environ)
        env["DEPS_CONF"] = str(conf)
        env["DEPS_RUN_DIR"] = str(self.run_dir)
        env["PATH"] = f"{self.bin}{os.pathsep}{env['PATH']}"
        env["FAKE_COMMAND_LOG"] = str(self.command_log)
        env["FAKE_COMMAND_STATE"] = str(self.command_state)
        env.update(extra_env or {})
        return subprocess.run(
            ["bash", str(SCRIPTS / name), *args],
            capture_output=True,
            text=True,
            timeout=120,
            env=env,
            cwd=str(REPO_ROOT),
        )

    # ---------------------------------------------------------------- 启动

    def test_start_returns_only_after_the_service_is_ready(self) -> None:
        """启动脚本必须等到端口可用才返回。"""
        conf = self.write_registry(self.command_service_registry(delay=2.0))

        result = self.run_script("deps-start.sh", conf, "--timeout", "30")

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(port_open(self.port), "脚本返回时端口应当已经可用")
        self.assertIn("假中间件", result.stdout)

    def test_start_reports_a_service_that_never_becomes_ready(self) -> None:
        """永远起不来的服务必须判失败，不能报“已启动”。"""
        conf = self.write_registry(f"""
            deps_register_services() {{
              deps_add_service "dead" "起不来的中间件" "command" \\
                "true" \\
                "true" \\
                "true" \\
                "port:127.0.0.1:{self.port}" \\
                "{self.port}"
            }}
        """)

        result = self.run_script("deps-start.sh", conf, "--timeout", "3")

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("起不来的中间件", result.stdout + result.stderr)
        self.assertIn("未就绪", result.stdout + result.stderr)

    def test_start_is_idempotent_for_an_already_ready_service(self) -> None:
        """已经在跑的服务不重复启动。"""
        conf = self.write_registry(self.command_service_registry())
        first = self.run_script("deps-start.sh", conf, "--timeout", "30")
        self.assertEqual(first.returncode, 0, first.stdout + first.stderr)
        pid_file = self.run_dir / "fake.pid"
        pid = pid_file.read_text(encoding="utf-8").strip()

        second = self.run_script("deps-start.sh", conf, "--timeout", "30")

        self.assertEqual(second.returncode, 0, second.stdout + second.stderr)
        self.assertIn("已在运行", second.stdout)
        self.assertEqual(pid_file.read_text(encoding="utf-8").strip(), pid, "PID 不该被换掉")

    # ---------------------------------------------------------------- 状态

    def test_status_reports_process_state_and_port_listening(self) -> None:
        """状态脚本要报出运行状态与端口监听情况。"""
        conf = self.write_registry(self.command_service_registry())
        self.run_script("deps-start.sh", conf, "--timeout", "30")

        result = self.run_script("deps-status.sh", conf)

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn("假中间件", result.stdout)
        self.assertIn("running", result.stdout)
        self.assertIn("ready", result.stdout)
        self.assertIn(f"{self.port}", result.stdout)
        self.assertIn("listening", result.stdout)

    def test_status_fails_when_a_service_is_down(self) -> None:
        """没起来的服务必须让状态脚本返回非零。"""
        conf = self.write_registry(self.command_service_registry())

        result = self.run_script("deps-status.sh", conf)

        self.assertNotEqual(result.returncode, 0)
        self.assertIn("stopped", result.stdout)
        self.assertIn("free", result.stdout)

    # ---------------------------------------------------------------- 停止

    def test_stop_terminates_the_process_and_frees_the_port(self) -> None:
        """停止脚本要真的停掉进程并释放端口。"""
        conf = self.write_registry(self.command_service_registry())
        self.run_script("deps-start.sh", conf, "--timeout", "30")
        self.assertTrue(port_open(self.port))

        result = self.run_script("deps-stop.sh", conf, "--timeout", "30")

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertFalse(port_open(self.port), "端口应当已经释放")
        self.assertFalse((self.run_dir / "fake.pid").exists(), "PID 文件应当被清掉")

    def test_stop_never_kills_processes_it_did_not_start(self) -> None:
        """没有 PID 记录时不得终止任何进程。"""
        conf = self.write_registry(self.command_service_registry())
        self.run_script("deps-start.sh", conf, "--timeout", "30")
        pid_file = self.run_dir / "fake.pid"
        pid = pid_file.read_text(encoding="utf-8").strip()
        pid_file.unlink()  # 模拟“不是本脚本启动的进程”

        result = self.run_script("deps-stop.sh", conf, "--timeout", "10")

        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertTrue(port_open(self.port), "别人的进程不该被杀掉")
        os.kill(int(pid), 15)

    # ------------------------------------------------------- 管理方式分发

    def test_brew_services_manager_is_dispatched(self) -> None:
        """brew 方式要走 brew services 的三个子命令。"""
        conf = self.write_registry("""
            deps_register_services() {
              deps_add_service "mysql" "MySQL 8.4" "brew" \\
                "brew services start mysql@8.4" \\
                "brew services stop mysql@8.4" \\
                "brew services info mysql@8.4" \\
                "none" \\
                "3306"
            }
        """)

        self.run_script("deps-start.sh", conf, "--timeout", "5")
        self.run_script("deps-stop.sh", conf, "--timeout", "5")
        self.run_script("deps-status.sh", conf)

        calls = self.command_log.read_text(encoding="utf-8").splitlines()
        self.assertIn("services start mysql@8.4", calls)
        self.assertIn("services stop mysql@8.4", calls)
        self.assertIn("services info mysql@8.4", calls)

    def test_systemd_manager_is_dispatched(self) -> None:
        """systemd 方式要走 systemctl 的三个子命令。"""
        conf = self.write_registry("""
            deps_register_services() {
              deps_add_service "redis" "Redis" "systemd" \\
                "systemctl start redis-server" \\
                "systemctl stop redis-server" \\
                "systemctl is-active --quiet redis-server" \\
                "none" \\
                "6379"
            }
        """)

        self.run_script("deps-start.sh", conf, "--timeout", "5")
        self.run_script("deps-stop.sh", conf, "--timeout", "5")

        calls = self.command_log.read_text(encoding="utf-8").splitlines()
        self.assertIn("start redis-server", calls)
        self.assertIn("stop redis-server", calls)
        self.assertIn("is-active --quiet redis-server", calls)

    # ------------------------------------------------------------ 脚本形态

    def test_scripts_are_executable_and_share_the_common_engine(self) -> None:
        """三个脚本要可执行，并且共用同一份公共实现。"""
        for name in ("deps-start.sh", "deps-stop.sh", "deps-status.sh"):
            path = SCRIPTS / name
            self.assertTrue(path.exists(), f"{name} 不存在")
            self.assertTrue(os.access(path, os.X_OK), f"{name} 不可执行")
            body = path.read_text(encoding="utf-8")
            self.assertIn("deps-common.sh", body, f"{name} 应当引用公共实现")


if __name__ == "__main__":
    unittest.main()
