#!/usr/bin/env python3
"""T416: 可重复非破坏性负载测量工具。

对指定目标执行可重复的只读负载测量，输出延迟分位数、吞吐与错误率（plan §9、§11、NFR-07）。

约束：

* 只用标准库，样本固定，可重复；
* 并发有界，不会把目标打垮；
* 默认只发只读请求：没有显式授权时，写请求一律拒绝（不预设任何 SLO，只报告实测值）；
* 未知目标拒绝执行。
"""

from __future__ import annotations

import argparse
import json
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from typing import Any, Iterable, Sequence

# 只读方法：默认只允许这些。
READ_ONLY_METHODS = ("GET", "HEAD")

# 已知目标的形态：本机或显式允许的测试环境。
ALLOWED_HOST_PREFIXES = ("http://127.0.0.1", "http://localhost", "http://[::1]")


class LoadRefused(RuntimeError):
    """负载测量被拒绝：目标未知或请求没有授权。"""


@dataclass
class RequestSpec:
    """一个请求样本。"""

    method: str
    path: str

    @property
    def is_write(self) -> bool:
        return self.method.upper() not in READ_ONLY_METHODS


@dataclass
class Sample:
    """一次请求的结果。"""

    method: str
    path: str
    status: int
    elapsed_ms: float
    error: str | None = None


@dataclass
class LoadResult:
    """测量结果：延迟分位数、吞吐与错误率。"""

    samples: list[Sample] = field(default_factory=list)
    duration_seconds: float = 0.0
    concurrency: int = 1

    @property
    def total(self) -> int:
        return len(self.samples)

    @property
    def errors(self) -> int:
        return sum(1 for sample in self.samples if sample.error is not None or sample.status >= 400)

    @property
    def error_rate(self) -> float:
        return (self.errors / self.total) if self.total else 0.0

    @property
    def throughput_per_second(self) -> float:
        return (self.total / self.duration_seconds) if self.duration_seconds > 0 else 0.0

    def percentile(self, percent: float) -> float:
        """延迟分位数（毫秒）。"""
        values = sorted(sample.elapsed_ms for sample in self.samples)
        if not values:
            return 0.0
        index = min(len(values) - 1, max(0, int(round(percent / 100 * len(values))) - 1))
        return values[index]

    def as_dict(self) -> dict[str, Any]:
        return {
            "total": self.total,
            "errors": self.errors,
            "errorRate": round(self.error_rate, 4),
            "throughputPerSecond": round(self.throughput_per_second, 2),
            "latencyMs": {
                "p50": round(self.percentile(50), 2),
                "p90": round(self.percentile(90), 2),
                "p95": round(self.percentile(95), 2),
                "p99": round(self.percentile(99), 2),
                "max": round(max((sample.elapsed_ms for sample in self.samples), default=0.0), 2),
                "mean": round(statistics.fmean([s.elapsed_ms for s in self.samples]) if self.samples else 0.0, 2),
            },
            "concurrency": self.concurrency,
        }


def require_known_target(target: str) -> str:
    """只允许本机目标；其他目标需要显式授权。"""
    if not target.startswith(ALLOWED_HOST_PREFIXES):
        raise LoadRefused(f"未知目标，拒绝执行负载：{target}")
    return target.rstrip("/")


def require_authorized(specs: Sequence[RequestSpec], allow_writes: bool) -> list[RequestSpec]:
    """写请求必须显式授权。"""
    writes = [spec for spec in specs if spec.is_write]
    if writes and not allow_writes:
        raise LoadRefused(
            "写请求需要显式授权（--allow-writes），并且只能指向已确认的测试环境")
    return list(specs)


def execute_one(target: str, spec: RequestSpec, timeout: float = 10.0) -> Sample:
    """执行一次请求并记录结果。"""
    request = urllib.request.Request(f"{target}{spec.path}", method=spec.method.upper())
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    started = time.perf_counter()
    try:
        with opener.open(request, timeout=timeout) as response:  # noqa: S310 - 目标已校验
            response.read()
            elapsed = (time.perf_counter() - started) * 1000
            return Sample(spec.method, spec.path, response.status, elapsed)
    except urllib.error.HTTPError as error:
        elapsed = (time.perf_counter() - started) * 1000
        return Sample(spec.method, spec.path, error.code, elapsed, error=f"HTTP {error.code}")
    except (urllib.error.URLError, OSError) as error:
        elapsed = (time.perf_counter() - started) * 1000
        return Sample(spec.method, spec.path, 0, elapsed, error=str(error))


def run_load(
    target: str,
    specs: Sequence[RequestSpec],
    iterations: int = 5,
    concurrency: int = 2,
    timeout: float = 10.0,
) -> LoadResult:
    """执行固定样本的负载测量。"""
    require_known_target(target)
    if iterations < 1:
        raise LoadRefused("迭代次数必须为正")
    if concurrency < 1 or concurrency > 32:
        raise LoadRefused("并发必须在 1～32 之间")

    result = LoadResult(concurrency=concurrency)
    lock = threading.Lock()
    started = time.perf_counter()

    def worker() -> None:
        for _ in range(iterations):
            for spec in specs:
                sample = execute_one(target, spec, timeout)
                with lock:
                    result.samples.append(sample)

    threads = [threading.Thread(target=worker) for _ in range(concurrency)]
    for thread in threads:
        thread.start()
    for thread in threads:
        thread.join()
    result.duration_seconds = time.perf_counter() - started
    return result


def parse_specs(items: Iterable[str]) -> list[RequestSpec]:
    """解析 "METHOD PATH" 形式的请求定义。"""
    specs: list[RequestSpec] = []
    for item in items:
        parts = item.split()
        if len(parts) != 2:
            raise LoadRefused(f"无法解析请求定义：{item}")
        specs.append(RequestSpec(parts[0].upper(), parts[1]))
    return specs


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="可重复非破坏性负载测量")
    parser.add_argument("--target", required=True, help="目标基地址（本机或已确认的测试环境）")
    parser.add_argument("--request", action="append", default=[], help="请求定义，格式 METHOD PATH")
    parser.add_argument("--iterations", type=int, default=5)
    parser.add_argument("--concurrency", type=int, default=2)
    parser.add_argument("--allow-writes", action="store_true", help="显式授权写请求（只用于已确认的测试环境）")
    args = parser.parse_args(argv)

    try:
        target = require_known_target(args.target)
        specs = require_authorized(parse_specs(args.request), args.allow_writes)
    except LoadRefused as error:
        print(f"拒绝执行：{error}", file=sys.stderr)
        return 2

    result = run_load(target, specs, iterations=args.iterations, concurrency=args.concurrency)
    print(json.dumps(result.as_dict(), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
