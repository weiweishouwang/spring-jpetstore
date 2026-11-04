#!/usr/bin/env bash
# 优雅停止打包后的 JPetStore 应用。
#
# 先发 SIGTERM：Spring Boot 的 graceful shutdown 会停止接收新请求、等在途请求做完再退出
# （prod profile 的 server.shutdown=graceful）。退出有上限，超时只报告，不擅自强杀——
# 强杀留给显式的 --force。
#
# 用法：
#   ./stop.sh            # 优雅停止，等到进程退出
#   ./stop.sh --force    # 超时后用 SIGKILL 强制结束
set -euo pipefail

APP_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$APP_HOME/run/app.pid"
STOP_TIMEOUT="${STOP_TIMEOUT:-40}"
FORCE=0

usage() {
    cat <<'USAGE'
用法：./stop.sh [选项]

选项：
  --force        等待超时后用 SIGKILL 强制结束。
  -h, --help     显示本帮助。

可用环境变量：
  STOP_TIMEOUT   等待进程退出的上限（秒，默认 40）。
USAGE
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --force)
            FORCE=1
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            echo "未知参数：$1" >&2
            usage
            exit 2
            ;;
    esac
done

if [ ! -f "$PID_FILE" ]; then
    echo "没有 PID 文件，应用可能没有通过 start.sh 启动"
    exit 0
fi

pid="$(cat "$PID_FILE" 2>/dev/null || true)"
if [ -z "$pid" ] || ! kill -0 "$pid" 2>/dev/null; then
    echo "进程已经不在，清理 PID 记录"
    rm -f "$PID_FILE"
    exit 0
fi

echo "向 PID $pid 发送 SIGTERM，等待其优雅退出…"
kill -TERM "$pid" 2>/dev/null || true

waited=0
while kill -0 "$pid" 2>/dev/null && [ "$waited" -lt "$STOP_TIMEOUT" ]; do
    sleep 1
    waited=$((waited + 1))
done

if kill -0 "$pid" 2>/dev/null; then
    if [ "$FORCE" -eq 1 ]; then
        echo "等待超时，改用 SIGKILL"
        kill -9 "$pid" 2>/dev/null || true
        sleep 1
        if kill -0 "$pid" 2>/dev/null; then
            echo "进程 $pid 仍然存在" >&2
            exit 1
        fi
    else
        echo "进程在 ${STOP_TIMEOUT}s 内没有退出（--force 可以强制结束）" >&2
        exit 1
    fi
fi

rm -f "$PID_FILE"
echo "已停止（PID $pid，耗时 ${waited}s）"
