#!/usr/bin/env python3
"""T477: 本地部署打包脚本。

把后端 jar、前端产物与启停脚本打成一个自包含的 tar 包：目标机器只需要自备 JDK，
MySQL、Redis、pgvector 都是外部服务（不在包里），连接信息由环境变量或配置模板注入。

包内结构（平铺，方便直接解包运行）::

    jpetstore-<版本>/
      backend.jar      后端应用
      dist/            前端产物（index.html 与 assets/）
      application.yml  配置模板
      start.sh         启动脚本（等到就绪才返回）
      stop.sh          停止脚本（优雅停止并等待退出）
      README.md        使用说明
      SHA256SUMS       包内文件的校验值

约束：

* 只打包构建产物，不打包外部服务、不打包任何凭据；
* 缺 jar、缺前端产物或缺脚本时明确失败，不打出半成品；
* 校验值按内容算，脚本里的可执行位保留；
* 输出里只有路径、大小与摘要，不打印口令。
"""

from __future__ import annotations

import argparse
import hashlib
import shutil
import sys
import tarfile
import tempfile
from dataclasses import dataclass
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_JAR_PATTERN = "backend/target/backend-*.jar"
DEFAULT_DIST = "frontend/dist"
DEFAULT_SCRIPTS = "infra/package"
DEFAULT_OUTPUT_DIR = "target/package"
PACKAGE_PREFIX = "jpetstore"
SKIP_NAMES = {".DS_Store", "__MACOSX"}


class PackagingError(Exception):
    """打包前置条件不满足：缺产物、缺脚本或参数不合法。"""


@dataclass(frozen=True)
class PackageSource:
    """打包输入：一次打包需要的全部文件。"""

    jar: Path
    dist: Path
    start: Path
    stop: Path
    config: Path
    readme: Path
    version: str

    @property
    def directory_name(self) -> str:
        return f"{PACKAGE_PREFIX}-{self.version}"


def detect_version(jar: Path) -> str:
    """从 jar 文件名取版本：backend-1.0.0-SNAPSHOT.jar → 1.0.0-SNAPSHOT。"""
    stem = jar.name[: -len(".jar")] if jar.name.endswith(".jar") else jar.stem
    prefix = "backend-"
    return stem[len(prefix):] if stem.startswith(prefix) else stem


def resolve_jar(repo_root: Path, explicit: str | None) -> Path:
    """定位后端 jar；显式指定优先，否则取构建输出里最新的那个。"""
    if explicit:
        jar = Path(explicit)
        if not jar.is_absolute():
            jar = repo_root / jar
        if not jar.is_file():
            raise PackagingError(f"找不到后端 jar：{jar}")
        return jar
    candidates = sorted(repo_root.glob(DEFAULT_JAR_PATTERN), key=lambda path: path.stat().st_mtime)
    if not candidates:
        raise PackagingError(
            f"找不到后端 jar（{DEFAULT_JAR_PATTERN}）；先执行 ./mvnw -Prelease -DskipTests package"
        )
    return candidates[-1]


def load_source(
    repo_root: Path,
    jar: str | None = None,
    dist: str | None = None,
    scripts: str | None = None,
    version: str | None = None,
) -> PackageSource:
    """收集并校验打包输入。"""
    jar_path = resolve_jar(repo_root, jar)
    dist_path = Path(dist) if dist else Path(DEFAULT_DIST)
    scripts_path = Path(scripts) if scripts else Path(DEFAULT_SCRIPTS)
    for path, base in ((dist_path, repo_root), (scripts_path, repo_root)):
        if not path.is_absolute():
            path = base / path
    dist_path = _absolute(dist_path, repo_root)
    scripts_path = _absolute(scripts_path, repo_root)

    if not dist_path.is_dir():
        raise PackagingError(f"找不到前端产物目录：{dist_path}")
    if not (dist_path / "index.html").is_file():
        raise PackagingError(f"前端产物不完整（缺 index.html）：{dist_path}")

    # 仓库里的源文件叫 deploy-*.sh（避免和项目根目录的开发用 start.sh/stop.sh 混淆），
    # 打进包里时恢复成 start.sh/stop.sh。
    start = scripts_path / "deploy-start.sh"
    stop = scripts_path / "deploy-stop.sh"
    config = scripts_path / "application.yml"
    readme = scripts_path / "README.md"
    for path in (start, stop, config, readme):
        if not path.is_file():
            raise PackagingError(f"缺少打包所需文件：{path}")

    return PackageSource(
        jar=jar_path,
        dist=dist_path,
        start=start,
        stop=stop,
        config=config,
        readme=readme,
        version=version or detect_version(jar_path),
    )


def _absolute(path: Path, base: Path) -> Path:
    return path if path.is_absolute() else base / path


def stage_package(source: PackageSource, staging: Path) -> Path:
    """把打包内容放进暂存目录，返回包内根目录。"""
    root = staging / source.directory_name
    root.mkdir(parents=True)

    shutil.copy2(source.jar, root / "backend.jar")
    shutil.copytree(source.dist, root / "dist", ignore=shutil.ignore_patterns(*SKIP_NAMES))

    for name, origin in (("start.sh", source.start), ("stop.sh", source.stop)):
        target = root / name
        shutil.copy2(origin, target)
        target.chmod(0o755)

    shutil.copy2(source.config, root / "application.yml")
    shutil.copy2(source.readme, root / "README.md")

    (root / "logs").mkdir()
    (root / "run").mkdir()

    _write_checksums(root)
    return root


def _write_checksums(root: Path) -> None:
    """写包内文件的 SHA256 校验值（可执行位之外的内容摘要）。"""
    lines = []
    for path in sorted(root.rglob("*")):
        if path.is_dir() or path.name == "SHA256SUMS":
            continue
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        lines.append(f"{digest}  {path.relative_to(root).as_posix()}")
    (root / "SHA256SUMS").write_text("\n".join(lines) + "\n", encoding="utf-8")


def create_archive(root: Path, output: Path) -> Path:
    """把暂存目录打成 tar.gz。"""
    output.parent.mkdir(parents=True, exist_ok=True)
    with tarfile.open(output, "w:gz") as archive:
        archive.add(root, arcname=root.name, recursive=True, filter=_skip_junk)
    return output


def _skip_junk(info: tarfile.TarInfo) -> tarfile.TarInfo | None:
    """跳过编辑器与系统垃圾文件。"""
    if any(part in SKIP_NAMES for part in Path(info.name).parts):
        return None
    return info


def sha256_of(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def build_package(
    repo_root: Path = REPO_ROOT,
    jar: str | None = None,
    dist: str | None = None,
    scripts: str | None = None,
    version: str | None = None,
    output: str | None = None,
) -> Path:
    """打包并返回生成的 tar 包路径。"""
    source = load_source(repo_root, jar=jar, dist=dist, scripts=scripts, version=version)
    with tempfile.TemporaryDirectory(prefix="jpetstore-package-") as tmp:
        root = stage_package(source, Path(tmp))
        if output:
            target = Path(output)
            if not target.is_absolute():
                target = repo_root / target
        else:
            target = repo_root / DEFAULT_OUTPUT_DIR / f"{source.directory_name}.tar.gz"
        create_archive(root, target)
    return target


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="打包本地部署用的 JPetStore 发布包")
    parser.add_argument("--jar", help="后端 jar 路径（默认取构建输出里最新的一个）")
    parser.add_argument("--dist", help="前端产物目录（默认 frontend/dist）")
    parser.add_argument("--scripts", help="启停脚本与配置模板所在目录（默认 infra/package）")
    parser.add_argument("--version", help="包名里的版本号（默认从 jar 文件名推断）")
    parser.add_argument("--output", help="输出 tar 包路径（默认 target/package/jpetstore-<版本>.tar.gz）")
    args = parser.parse_args(argv)

    try:
        archive = build_package(
            jar=args.jar,
            dist=args.dist,
            scripts=args.scripts,
            version=args.version,
            output=args.output,
        )
    except PackagingError as failure:
        print(f"打包失败：{failure}", file=sys.stderr)
        return 2

    size_mb = archive.stat().st_size / (1024 * 1024)
    print(f"已生成 {archive}")
    print(f"大小 {size_mb:.1f} MiB")
    print(f"SHA256 {sha256_of(archive)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
