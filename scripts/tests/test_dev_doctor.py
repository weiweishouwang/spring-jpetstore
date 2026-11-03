"""Read-only environment checks: all host and Docker operations are mocked."""
import contextlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

SCRIPT = Path(__file__).resolve().parents[1] / 'dev-doctor.py'
spec = importlib.util.spec_from_file_location('dev_doctor', SCRIPT)
doctor = importlib.util.module_from_spec(spec)
sys.modules[spec.name] = doctor
spec.loader.exec_module(doctor)


class DevDoctorTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        (self.root / 'infra').mkdir()
        (self.root / '.java-version').write_text('21.0.13\n')
        (self.root / '.node-version').write_text('24.22.2\n')
        self.image = 'mysql@sha256:' + 'a' * 64
        (self.root / 'infra/compose.dev.yml').write_text(
            'services:\n  mysql:\n    image: ' + self.image + '\n')
        (self.root / '.env.dev').write_text(
            'MYSQL_PORT=3306\nBACKEND_PORT=8080\nFRONTEND_PORT=5173\n'
            'MYSQL_PASSWORD=DO_NOT_PRINT_THIS_SECRET\n')
        self.java_home = self.root / 'jdk'
        self.node_bin = self.root / 'node/bin'
        for name in ('java', 'javac'):
            path = self.java_home / 'bin' / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.touch(mode=0o755)
        self.node_bin.mkdir(parents=True)
        for name in ('node', 'npm'):
            (self.node_bin / name).touch(mode=0o755)
        self.java_version = '21.0.13'
        self.java_arch = 'aarch64'
        self.java_vendor = 'Eclipse Adoptium'
        self.java_build = '21.0.13+9-LTS'
        self.compiler_version = '21.0.13'
        self.node_version = '24.22.2'
        self.node_arch = 'arm64'
        self.npm_version = '11.19.1'
        self.compose_version = '5.1.4'
        self.daemon = {'ServerVersion': '29.5.3', 'OSType': 'linux',
                       'Architecture': 'aarch64', 'MemTotal': 8 * 1024**3}
        self.image_info = [{'Os': 'linux', 'Architecture': 'arm64'}]
        self.mysql = [{'Service': 'mysql', 'State': 'running', 'Health': 'healthy',
                       'Publishers': [{'URL': '127.0.0.1', 'TargetPort': 3306,
                                       'PublishedPort': 3306, 'Protocol': 'tcp'}]}]
        self.busy = {3306}
        self.free = 100 * 1024**3
        self.command_errors = {}
        self.run_mock = self.patch('subprocess.run', side_effect=self.fake_run)
        self.patch('shutil.which', side_effect=lambda name: '/tools/' + name)
        self.patch('shutil.disk_usage', side_effect=lambda _: (200 * 1024**3, 0, self.free))
        self.patch('platform.machine', return_value='arm64')
        self.patch('socket.socket', side_effect=self.fake_socket)

    def patch(self, target, **kwargs):
        patcher = patch('dev_doctor.' + target, **kwargs)
        value = patcher.start()
        self.addCleanup(patcher.stop)
        return value

    def fake_socket(self, *args, **kwargs):
        test = self
        class Probe:
            def __enter__(self):
                return self
            def __exit__(self, *exc):
                return False
            def bind(self, address):
                if address[1] in test.busy:
                    raise OSError('address already in use')
        return Probe()

    def fake_run(self, argv, **kwargs):
        args = tuple(str(a) for a in argv)
        for prefix, error in self.command_errors.items():
            if args[:len(prefix)] == prefix:
                raise error
        name = Path(args[0]).name
        if name == 'java':
            output = (f'java.version = {self.java_version}\n'
                      f'java.runtime.version = {self.java_build}\n'
                      f'java.vendor = {self.java_vendor}\nos.arch = {self.java_arch}\n')
            return subprocess.CompletedProcess(args, 0, '', output)
        if name == 'javac':
            output = 'javac ' + self.compiler_version
        elif name == 'node':
            output = json.dumps({'version': self.node_version, 'arch': self.node_arch})
        elif name == 'npm':
            output = self.npm_version
        elif name == 'git':
            output = 'git version 2.33.0'
        elif args[:2] == ('docker', 'info'):
            output = json.dumps(self.daemon)
        elif args[:3] == ('docker', 'compose', 'version'):
            output = self.compose_version
        elif args[:3] == ('docker', 'image', 'inspect'):
            output = json.dumps(self.image_info)
        elif name == 'docker' and 'ps' in args:
            output = '\n'.join(json.dumps(item) for item in self.mysql)
        elif name == 'docker' and 'config' in args:
            output = ''
        else:
            self.fail('Unexpected external command: ' + repr(args))
        return subprocess.CompletedProcess(args, 0, output, '')

    def checks(self):
        return doctor.run_checks(self.root, java_home=self.java_home, node_bin=self.node_bin)

    def assert_failed(self, name, hint):
        results = self.checks()
        result = next(r for r in results if r.name == name)
        self.assertFalse(result.ok, result.message)
        self.assertIn(hint, result.message)
        self.assertNotIn('DO_NOT_PRINT_THIS_SECRET', str(results))

    def test_prepared_host_passes_with_project_mysql_occupying_its_port(self):
        results = self.checks()
        self.assertTrue(all(r.ok for r in results), results)
        self.assertGreaterEqual(len(results), 10)
        self.assertEqual({r.name for r in results},
                         {'python', 'git', 'java', 'node', 'docker', 'compose',
                          'disk', 'image', 'database', 'ports'})

    def test_java_patch_mismatch_has_repair_hint(self):
        self.java_version = '21.0.7'
        self.assert_failed('java', '21.0.13')

    def test_java8_browser_jre_is_rejected(self):
        self.java_version = '1.8.0_351'
        (self.java_home / 'bin/javac').unlink()
        self.assert_failed('java', '完整JDK')

    def test_java_and_compiler_must_match(self):
        self.compiler_version = '25.0.2'
        self.assert_failed('java', 'javac')

    def test_java_vendor_and_build_are_locked(self):
        for vendor, build in [('Oracle Corporation', '21.0.13+9-LTS'),
                              ('Eclipse Adoptium', '21.0.13+8-LTS')]:
            with self.subTest(vendor=vendor, build=build):
                self.java_vendor, self.java_build = vendor, build
                self.assert_failed('java', 'Temurin21.0.13+9')

    def test_java_wrong_cpu_is_rejected(self):
        self.java_arch = 'x86_64'
        self.assert_failed('java', 'CPU')

    def test_node_and_npm_versions_are_locked(self):
        self.node_version = '25.8.2'
        self.assert_failed('node', '24.22.2')
        self.node_version = '24.22.2'
        self.npm_version = '11.11.1'
        self.assert_failed('node', '11.19.1')

    def test_node_wrong_cpu_is_rejected(self):
        self.node_arch = 'x64'
        self.assert_failed('node', 'CPU')

    def test_child_npm_uses_selected_node_without_mutating_environment(self):
        with patch.dict(doctor.os.environ, {'PATH': '/global/bin', 'JAVA_HOME': '/global/jdk'}):
            self.checks()
            self.assertEqual(doctor.os.environ['PATH'], '/global/bin')
            self.assertEqual(doctor.os.environ['JAVA_HOME'], '/global/jdk')
        call = next(c for c in self.run_mock.call_args_list if Path(c.args[0][0]).name == 'npm')
        self.assertEqual(Path(call.kwargs['env']['PATH'].split(doctor.os.pathsep)[0]).resolve(), self.node_bin.resolve())
        self.assertFalse(call.kwargs.get('shell', False))

    def test_explicit_java_home_overrides_wrong_global_java(self):
        with patch.dict(doctor.os.environ, {'JAVA_HOME': '/browser-jre'}):
            self.assertTrue(next(r for r in self.checks() if r.name == 'java').ok)
        commands = [str(c.args[0][0]) for c in self.run_mock.call_args_list]
        self.assertIn(str(self.java_home / 'bin/java'), commands)
        self.assertNotIn('/browser-jre/bin/java', commands)

    def test_daemon_unavailable_does_not_leak_subprocess_output(self):
        self.command_errors[('docker', 'info')] = subprocess.CalledProcessError(
            1, 'docker', stderr='DO_NOT_PRINT_THIS_SECRET')
        self.assert_failed('docker', 'Docker Desktop')

    def test_missing_docker_binary_is_actionable(self):
        self.command_errors[('docker', 'info')] = FileNotFoundError('DO_NOT_PRINT_THIS_SECRET')
        self.assert_failed('docker', 'Docker Desktop')

    def test_command_timeout_is_a_failure(self):
        self.command_errors[('docker', 'info')] = subprocess.TimeoutExpired('docker', 20)
        self.assert_failed('docker', 'Docker Desktop')

    def test_compose_missing_or_v1_is_rejected(self):
        self.compose_version = '1.29.2'
        self.assert_failed('compose', 'Compose v2')
        self.command_errors[('docker', 'compose', 'version')] = subprocess.CalledProcessError(1, 'docker')
        self.assert_failed('compose', 'Compose v2')

    def test_low_disk_has_explicit_failure(self):
        self.free = 1024**3
        self.assert_failed('disk', '10')

    def test_low_docker_memory_has_explicit_failure(self):
        self.daemon['MemTotal'] = 1024**3
        self.assert_failed('docker', '4')

    def test_database_image_wrong_cpu_is_rejected(self):
        self.image_info = [{'Os': 'linux', 'Architecture': 'amd64'}]
        self.assert_failed('image', 'CPU')

    def test_docker_cpu_need_not_equal_host_cpu_for_remote_engine(self):
        self.daemon['Architecture'] = 'x86_64'
        self.image_info = [{'Os': 'linux', 'Architecture': 'amd64'}]
        self.assertTrue(next(r for r in self.checks() if r.name == 'image').ok)

    def test_missing_image_is_not_silently_pulled(self):
        self.command_errors[('docker', 'image', 'inspect')] = subprocess.CalledProcessError(1, 'docker')
        self.assert_failed('image', '拉取')
        self.assertFalse(any('pull' in c.args[0] for c in self.run_mock.call_args_list))

    def test_mutable_image_tag_is_rejected(self):
        (self.root / 'infra/compose.dev.yml').write_text('services:\n  mysql:\n    image: mysql:latest\n')
        self.assert_failed('image', 'digest')

    def test_unhealthy_or_missing_database_is_failure(self):
        self.mysql[0]['Health'] = 'unhealthy'
        self.assert_failed('database', 'healthy')
        self.mysql = []
        self.assert_failed('database', '启动')

    def test_backend_and_frontend_port_conflicts_are_detected(self):
        for port in (8080, 5173):
            with self.subTest(port=port):
                self.busy = {port}
                self.assert_failed('ports', str(port))

    def test_unrelated_mysql_port_occupancy_is_not_excused(self):
        self.mysql[0]['Publishers'] = []
        self.assert_failed('ports', '3306')

    def test_database_public_binding_is_rejected(self):
        self.mysql[0]['Publishers'][0]['URL'] = '0.0.0.0'
        self.assert_failed('database', '127.0.0.1')
        self.assert_failed('ports', '3306')

    def test_port_overrides_are_read_without_shell_evaluation(self):
        (self.root / '.env.dev').write_text('MYSQL_PORT=13306\nBACKEND_PORT="18080"\nFRONTEND_PORT=15173\nPASSWORD=$(never-run)\n')
        self.mysql[0]['Publishers'][0]['PublishedPort'] = 13306
        self.busy = {13306}
        self.assertTrue(all(r.ok for r in self.checks()))
        self.busy.add(15173)
        self.assert_failed('ports', '15173')

    def test_invalid_and_duplicate_ports_are_rejected(self):
        for value in ('BACKEND_PORT=not-a-port', 'BACKEND_PORT=70000',
                      'BACKEND_PORT=3306', 'BACKEND_PORT=8080\nBACKEND_PORT=8081'):
            with self.subTest(value=value):
                (self.root / '.env.dev').write_text(value + '\n')
                self.assert_failed('ports', '端口')

    def test_compose_config_error_does_not_print_credentials(self):
        original = self.fake_run
        def fail_config(args, **kwargs):
            if 'config' in args:
                raise subprocess.CalledProcessError(1, args, stderr='MYSQL_PASSWORD=DO_NOT_PRINT_THIS_SECRET')
            return original(args, **kwargs)
        self.run_mock.side_effect = fail_config
        self.assert_failed('database', '.env.dev')

    def test_missing_env_file_is_clear(self):
        (self.root / '.env.dev').unlink()
        self.assert_failed('database', '.env.dev')

    def test_malformed_docker_json_fails_without_traceback(self):
        original = self.fake_run
        def malformed(args, **kwargs):
            if tuple(args[:2]) == ('docker', 'info'):
                return subprocess.CompletedProcess(args, 0, 'not-json', '')
            return original(args, **kwargs)
        self.run_mock.side_effect = malformed
        self.assert_failed('docker', 'Docker Desktop')

    def test_cli_exit_codes_and_secret_free_output(self):
        args = ['--root', str(self.root), '--java-home', str(self.java_home), '--node-bin', str(self.node_bin)]
        with contextlib.redirect_stdout(io.StringIO()) as out:
            self.assertEqual(doctor.main(args), 0)
        self.assertIn('PASS', out.getvalue())
        self.free = 0
        with contextlib.redirect_stdout(io.StringIO()) as out:
            self.assertEqual(doctor.main(args), 1)
        self.assertIn('FAIL', out.getvalue())
        self.assertNotIn('DO_NOT_PRINT_THIS_SECRET', out.getvalue())


if __name__ == '__main__':
    unittest.main()
