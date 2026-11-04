#!/usr/bin/env bash
# 停止本地开发的后端与前端进程。
#
# 按 PID 文件找进程：先发 SIGTERM 让它自己收尾，等它退出；只有在超时后才用 SIGKILL。
# PID 记录按端口分文件（run/backend-<端口>.pid、run/frontend-<端口>.pid），这里全部收掉——
# 先停前端（客户端），再停后端。
#
# 用法：
#   ./stop.sh                  # 优雅停止，等待进程退出
#   STOP_TIMEOUT=60 ./stop.sh  # 调整等待上限（秒）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

RUN_DIR="$ROOT/run"
STOP_TIMEOUT="${STOP_TIMEOUT:-30}"

log() { printf '%s\n' "$*"; }

pid_alive() {
    [ -n "$1" ] && kill -0 "$1" 2>/dev/null
}

read_pid() {
    [ -f "$1" ] || return 1
    local pid
    pid="$(cat "$1" 2>/dev/null || true)"
    [ -n "$pid" ] || return 1
    printf '%s' "$pid"
}

# 停止一个进程：SIGTERM → 等待 → 超时才 SIGKILL。
stop_one() {
    local pid_file="$1" label="$2" pid waited=0
    if [ ! -f "$pid_file" ]; then
        log "$label：没有 PID 记录，本来就没在运行"
        return 0
    fi
    pid="$(read_pid "$pid_file" || true)"
    if [ -z "$pid" ] || ! pid_alive "$pid"; then
        log "$label：进程已经不在，清理 PID 记录"
        rm -f "$pid_file"
        return 0
    fi

    log "$label：向 PID $pid 发送 SIGTERM，等待它自己退出…"
    kill -TERM "$pid" 2>/dev/null || true
    while pid_alive "$pid" && [ "$waited" -lt "$STOP_TIMEOUT" ]; do
        sleep 1
        waited=$((waited + 1))
    done

    if pid_alive "$pid"; then
        log "$label：等待 ${STOP_TIMEOUT}s 仍未退出，改用 SIGKILL"
        kill -9 "$pid" 2>/dev/null || true
        sleep 1
        if pid_alive "$pid"; then
            printf '停止失败：%s 的进程 %s 仍然存在\n' "$label" "$pid" >&2
            return 1
        fi
    fi

    rm -f "$pid_file"
    log "$label：已停止（PID $pid，耗时 ${waited}s）"
}

status=0
found=0
for pid_file in "$RUN_DIR"/frontend-*.pid "$RUN_DIR"/backend-*.pid; do
    [ -f "$pid_file" ] || continue
    found=1
    case "$(basename "$pid_file")" in
        frontend-*) stop_one "$pid_file" "前端（$(basename "$pid_file" .pid)）" || status=1 ;;
        backend-*) stop_one "$pid_file" "后端（$(basename "$pid_file" .pid)）" || status=1 ;;
    esac
done
[ "$found" -eq 1 ] || log "没有 PID 记录，本地开发环境本来就没在运行"

if [ "$status" -eq 0 ]; then
    printf '\n本地开发环境已停止\n'
else
    printf '\n有进程没有停下来，请手工确认\n' >&2
fi
exit "$status"
