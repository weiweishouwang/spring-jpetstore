#!/usr/bin/env python3
"""Read-only project toolchain diagnostics; no installation, shell eval, or secret output.

Example (from the repository root):
  python3 scripts/dev-doctor.py --java-home .tools/jdk-21.0.13+9/Contents/Home \
    --node-bin "$HOME/.nvm/versions/node/v24.22.2/bin"

Use --help for paths and resource thresholds. Docker socket access is required.
"""
import argparse
from dataclasses import dataclass
import json
import os
from pathlib import Path
import platform
import re
import shutil
import socket
import subprocess
import sys

GIB = 1024**3
NPM_VERSION = '11.19.1'
JAVA_BUILD = '21.0.13+9'


class CheckError(Exception):
    """Only deliberately constructed, non-sensitive diagnostics may be displayed."""


@dataclass(frozen=True)
class Result:
    name: str
    ok: bool
    message: str


def command(args, env=None):
    result = subprocess.run([str(a) for a in args], capture_output=True, text=True,
                            check=True, timeout=20, env=env)
    return result.stdout + '\n' + result.stderr


def cpu(value):
    return {'aarch64': 'arm64', 'x86_64': 'amd64', 'x64': 'amd64'}.get(value, value)


def require(condition, message):
    if not condition:
        raise CheckError(message)


def read_ports(env_file):
    ports = {'MYSQL_PORT': 3306, 'BACKEND_PORT': 8080, 'FRONTEND_PORT': 5173}
    seen = set()
    for line in env_file.read_text().splitlines():
        key, separator, value = line.strip().partition('=')
        key = key.strip()
        if not separator or key not in ports:
            continue
        value = value.strip().strip('\'"')
        require(key not in seen and re.fullmatch(r'[0-9]+', value) is not None,
                '端口配置无效或重复；检查.env.dev中的三个*_PORT字段。')
        seen.add(key)
        port = int(value)
        require(1 <= port <= 65535, '端口必须在1～65535范围内；检查.env.dev。')
        ports[key] = port
    require(len(set(ports.values())) == 3, '三个开发端口必须互不相同；修改.env.dev。')
    return ports


def run_checks(root, *, java_home=None, node_bin=None, env_file=None,
               min_free_gib=10, min_memory_gib=4):
    root = Path(root).resolve()
    env_file = Path(env_file).resolve() if env_file else root / '.env.dev'
    compose_file = root / 'infra/compose.dev.yml'
    compose = ['docker', 'compose', '--env-file', env_file, '-f', compose_file]
    results = []

    def check(name, action, repair):
        try:
            results.append(Result(name, True, action()))
        except CheckError as error:
            results.append(Result(name, False, str(error)))
        except (OSError, ValueError, TypeError, KeyError, IndexError,
                subprocess.SubprocessError):
            # Do not print commands, raw process output, env files, or tracebacks.
            results.append(Result(name, False, repair))

    def python_check():
        require(sys.version_info.major == 3, '使用Python3执行诊断和脚本测试。')
        return platform.python_version()

    def git_check():
        output = command(['git', '--version']).strip()
        match = re.search(r'git version (\d+\.\d+(?:\.\d+)?)', output)
        require(match is not None, '安装Git并确保git在PATH中。')
        return match[1]

    def java_check():
        selected = java_home or os.environ.get('JAVA_HOME')
        if selected:
            java = Path(selected) / 'bin/java'
            javac = Path(selected) / 'bin/javac'
        else:
            java = Path(shutil.which('java') or '/missing/java')
            javac = java.parent / 'javac'
        require(java.is_file() and javac.is_file(),
                '选择完整JDK（包含java和javac），不要使用Java8浏览器JRE；设置--java-home或JAVA_HOME。')
        output = command([java, '-XshowSettings:properties', '-version'])
        settings = dict(re.findall(r'^\s*([\w.]+)\s*=\s*([^\r\n]+)', output, re.M))
        target = (root / '.java-version').read_text().strip()
        require(settings.get('java.version') == target,
                f'项目需要JDK {target}；用--java-home或JAVA_HOME选择目标完整JDK。')
        require(settings.get('java.vendor') == 'Eclipse Adoptium'
                and re.fullmatch(re.escape(JAVA_BUILD) + r'(?:-LTS)?',
                                 settings.get('java.runtime.version', '')) is not None,
                f'使用锁定的Temurin{JAVA_BUILD}；安装依据见docs/development/toolchain-setup.md。')
        require(cpu(settings.get('os.arch')) == cpu(platform.machine()),
                'JDK CPU架构与本机不一致；安装对应arm64/amd64的完整JDK。')
        compiler = command([javac, '-version']).strip()
        require(compiler == 'javac ' + target, 'java与javac版本必须一致；检查项目JAVA_HOME。')
        return f'Temurin {JAVA_BUILD} / {cpu(settings["os.arch"])}，含javac'

    def node_check():
        node = Path(node_bin) / 'node' if node_bin else 'node'
        npm = Path(node_bin) / 'npm' if node_bin else 'npm'
        child_env = os.environ.copy()
        if node_bin:
            child_env['PATH'] = str(Path(node_bin).resolve()) + os.pathsep + child_env.get('PATH', '')
        info = json.loads(command([node, '-p',
                                  'JSON.stringify({version:process.versions.node,arch:process.arch})'],
                                 env=child_env))
        target = (root / '.node-version').read_text().strip()
        require(info.get('version') == target,
                f'项目需要Node {target}；使用--node-bin或项目shell的PATH选择版本。')
        require(cpu(info.get('arch')) == cpu(platform.machine()), 'Node CPU架构与本机不一致。')
        require(command([npm, '--version'], env=child_env).strip() == NPM_VERSION,
                f'使用目标Node配套的npm {NPM_VERSION}；不修改全局.npmrc。')
        return f'Node {target} / npm {NPM_VERSION} / {cpu(info["arch"])}'

    def docker_info():
        info = json.loads(command(['docker', 'info', '--format', '{{json .}}']))
        require(info.get('OSType') == 'linux' and info.get('Architecture'),
                'Docker Desktop需切换到Linux容器，并能读取Server架构。')
        return info

    def docker_check():
        info = docker_info()
        memory = int(info.get('MemTotal', 0)) / GIB
        require(memory >= min_memory_gib,
                f'Docker可用内存{memory:.1f}GiB，低于{min_memory_gib:g}GiB；调整Desktop资源分配或--min-memory-gib。')
        return f'Server {info["ServerVersion"]} / linux/{cpu(info["Architecture"])} / {memory:.1f}GiB内存'

    def compose_check():
        version = command(['docker', 'compose', 'version', '--short']).strip().lstrip('v')
        require(re.fullmatch(r'\d+\.\d+\.\d+(?:[-+].*)?', version) is not None
                and int(version.split('.')[0]) >= 2,
                '安装Docker Compose v2或以上插件；使用docker compose子命令。')
        return version

    def disk_check():
        free = shutil.disk_usage(root)[2] / GIB
        require(free >= min_free_gib,
                f'项目磁盘可用{free:.1f}GiB，低于{min_free_gib:g}GiB；释放空间或调整--min-free-gib。')
        return f'{free:.1f}GiB可用（阈值{min_free_gib:g}GiB）'

    def image_check():
        match = re.search(r'^\s+image:\s*(\S+)\s*$', compose_file.read_text(), re.M)
        require(match is not None and re.fullmatch(r'mysql@sha256:[0-9a-f]{64}', match[1]),
                '开发MySQL镜像必须按已审核digest固定；检查infra/compose.dev.yml。')
        images = json.loads(command(['docker', 'image', 'inspect', match[1]]))
        info = docker_info()
        require(images and images[0].get('Os') == 'linux'
                and cpu(images[0].get('Architecture')) == cpu(info['Architecture']),
                'MySQL镜像CPU架构与Docker Server不兼容；按目标平台拉取已固定digest。')
        return f'本地固定镜像 linux/{cpu(images[0]["Architecture"])}，与Server一致'

    def mysql_state():
        # --format json emits either a JSON array or one JSON object per line.
        output = command(compose + ['ps', '--format', 'json']).strip()
        rows = json.loads(output) if output.startswith('[') else [json.loads(line) for line in output.splitlines()]
        require(isinstance(rows, list), '无法读取开发数据库状态；启动项目MySQL后重试。')
        return next((row for row in rows if row.get('Service') == 'mysql'), {})

    def owns_mysql_port(state, port):
        publishers = state.get('Publishers') or []
        return state.get('State') == 'running' and any(
            p.get('URL') == '127.0.0.1' and p.get('TargetPort') == 3306
            and p.get('PublishedPort') == port and p.get('Protocol') == 'tcp'
            for p in publishers)

    def database_check():
        require(env_file.is_file(), '准备被Git忽略的.env.dev并填写本地凭据；参照.env.example。')
        command(compose + ['config', '--quiet'])
        ports = read_ports(env_file)
        state = mysql_state()
        require(state.get('State') == 'running',
                '启动开发数据库：docker compose --env-file .env.dev -f infra/compose.dev.yml up -d --wait。')
        require(state.get('Health') == 'healthy', '等待或排查开发MySQL，必须达到healthy；不删除数据卷。')
        require(owns_mysql_port(state, ports['MYSQL_PORT'])
                and all(p.get('URL') == '127.0.0.1' for p in state.get('Publishers', [])),
                '开发MySQL只能绑定127.0.0.1及配置的MYSQL_PORT；检查Compose端口映射。')
        return f'项目MySQL healthy / 127.0.0.1:{ports["MYSQL_PORT"]}（应用账号健康检查）'

    def ports_check():
        ports = read_ports(env_file)
        state = None
        for name, port in ports.items():
            try:
                with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as probe:
                    probe.bind(('127.0.0.1', port))
            except OSError:
                if name == 'MYSQL_PORT':
                    state = mysql_state()
                    if owns_mysql_port(state, port):
                        continue
                raise CheckError(f'端口{port}被占用；核对进程或修改.env.dev中的{name}，不自动停止服务。') from None
        return '后端/前端端口空闲；数据库端口空闲或由本项目MySQL占用'

    check('python', python_check, '使用Python3执行诊断。')
    check('git', git_check, '安装Git并确认PATH。')
    check('java', java_check, '无法检查完整JDK；核对--java-home、JAVA_HOME和.java-version。')
    check('node', node_check, '无法检查Node/npm；核对--node-bin、PATH和.node-version。')
    check('docker', docker_check, '无法连接Docker Server；启动Docker Desktop并检查Docker socket权限/当前context。')
    check('compose', compose_check, '安装Docker Compose v2或以上插件并验证docker compose version。')
    check('disk', disk_check, '无法读取项目磁盘空间；检查目录权限。')
    check('image', image_check, '无法检查本地MySQL镜像；连接Docker并拉取infra/compose.dev.yml固定的镜像。')
    check('database', database_check, '开发MySQL检查失败；核对.env.dev必需变量、Compose配置和Docker权限；不输出凭据。')
    check('ports', ports_check, '无法检查开发端口；检查.env.dev、Docker权限及本地socket访问。')
    return results


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--java-home', type=Path, help='项目完整JDK目录，优先于JAVA_HOME')
    parser.add_argument('--node-bin', type=Path, help='包含目标node和npm的目录，优先于PATH')
    parser.add_argument('--env-file', type=Path, help='开发变量文件，默认项目.env.dev；不输出其内容')
    parser.add_argument('--min-free-gib', type=float, default=10, help='项目磁盘空闲阈值，默认10GiB')
    parser.add_argument('--min-memory-gib', type=float, default=4, help='Docker内存阈值，默认4GiB')
    args = parser.parse_args(argv)
    if not (0 < args.min_free_gib < float('inf') and 0 < args.min_memory_gib < float('inf')):
        parser.error('资源阈值必须为正的有限数值。')
    results = run_checks(args.root, java_home=args.java_home, node_bin=args.node_bin,
                         env_file=args.env_file, min_free_gib=args.min_free_gib,
                         min_memory_gib=args.min_memory_gib)
    for result in results:
        print(f'[{"PASS" if result.ok else "FAIL"}] {result.name}: {result.message}')
    failed = sum(not result.ok for result in results)
    print(f'{len(results) - failed}/{len(results)} checks passed')
    return 1 if failed else 0


if __name__ == '__main__':
    sys.exit(main())
