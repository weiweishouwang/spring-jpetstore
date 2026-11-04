#!/usr/bin/env bash
# 一键启动依赖中间件：逐个启动，并等到每个服务真正就绪（端口可用、探针通过）才返回。
#
# 用法：
#   scripts/deps-start.sh                 # 启动注册表里的全部服务
#   scripts/deps-start.sh --service mysql # 只启动指定服务
#   scripts/deps-start.sh --timeout 180   # 就绪等待上限（秒）
#
# 退出码 0 表示全部就绪；有任何服务没就绪就返回非零。
set -euo pipefail

DEPS_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=deps-common.sh
. "$DEPS_SCRIPT_DIR/deps-common.sh"
cd "$DEPS_REPO_ROOT"

usage() {
    cat <<'USAGE'
用法：scripts/deps-start.sh [选项]

选项：
  --service ID   只处理指定服务（可重复）。默认处理注册表里的全部服务。
  --timeout N    就绪等待上限（秒），默认 120。
  -h, --help     显示本帮助。
USAGE
}

timeout="$DEPS_START_TIMEOUT"
services=()

while [ "$#" -gt 0 ]; do
    case "$1" in
        --service)
            [ "$#" -ge 2 ] || { deps_err "--service 需要服务标识"; exit 2; }
            services+=("$2")
            shift 2
            ;;
        --timeout)
            [ "$#" -ge 2 ] || { deps_err "--timeout 需要秒数"; exit 2; }
            timeout="$2"
            shift 2
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            deps_err "未知参数：$1"
            usage
            exit 2
            ;;
    esac
done

deps_load_registry

if [ "$(deps_count)" = "0" ]; then
    deps_err "注册表为空，没有可启动的服务"
    exit 2
fi

selected=()
if [ "${#services[@]}" -eq 0 ]; then
    i=0
    while [ "$i" -lt "$(deps_count)" ]; do
        selected+=("$i")
        i=$((i + 1))
    done
else
    for wanted in ${services[@]+"${services[@]}"}; do
        index="$(deps_index_of "$wanted" 2>/dev/null || true)"
        if [ -z "$index" ]; then
            deps_err "注册表里没有服务：$wanted"
            exit 2
        fi
        selected+=("$index")
    done
fi

deps_info "启动依赖中间件（就绪等待上限 ${timeout}s）"
failures=0
for i in ${selected[@]+"${selected[@]}"}; do
    id="${DEPS_IDS[$i]}"
    label="${DEPS_LABELS[$i]}"
    manager="${DEPS_MANAGERS[$i]}"
    probe="${DEPS_PROBES[$i]}"

    # 已经跑着并且就绪（或没有就绪探针可查）才跳过；否则照常启动。
    if deps_manager_running "$i" && [ "$(deps_probe_state "$probe")" != "not-ready" ]; then
        deps_ok "${label}：已在运行，跳过"
        continue
    fi

    if deps_manager_running "$i"; then
        deps_info "${label}：进程在运行，等待就绪…"
    else
        deps_info "${label}：启动中…"
        if ! deps_manager_start "$i"; then
            deps_err "${label}：启动命令失败"
            failures=$((failures + 1))
            continue
        fi
    fi

    if deps_wait_ready "$i" "$timeout"; then
        deps_ok "${label}：就绪"
    else
        wait_status=$?
        case "$wait_status" in
            2)
                deps_err "${label}：未就绪（进程已经退出，详见 ${DEPS_RUN_DIR}/${id}.log）"
                ;;
            *)
                deps_err "${label}：未就绪（等待 ${timeout}s 后端口/探针仍不可用）"
                ;;
        esac
        failures=$((failures + 1))
    fi
done

if [ "$failures" -eq 0 ]; then
    deps_ok "全部依赖中间件就绪"
    exit 0
fi
deps_err "$failures 个依赖中间件没有就绪"
exit 1
