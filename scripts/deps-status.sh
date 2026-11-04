#!/usr/bin/env bash
# 查看依赖中间件的状态：每个服务的运行状态、就绪状态与端口监听情况。
#
# 用法：
#   scripts/deps-status.sh                  # 查看注册表里的全部服务
#   scripts/deps-status.sh --service mysql  # 只看指定服务
#
# 退出码 0 表示全部就绪；有服务没起来或没就绪就返回非零（便于脚本判断）。
set -euo pipefail

DEPS_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=deps-common.sh
. "$DEPS_SCRIPT_DIR/deps-common.sh"
cd "$DEPS_REPO_ROOT"

usage() {
    cat <<'USAGE'
用法：scripts/deps-status.sh [选项]

选项：
  --service ID   只查看指定服务（可重复）。默认查看注册表里的全部服务。
  -h, --help     显示本帮助。
USAGE
}

services=()

while [ "$#" -gt 0 ]; do
    case "$1" in
        --service)
            [ "$#" -ge 2 ] || { deps_err "--service 需要服务标识"; exit 2; }
            services+=("$2")
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
    deps_err "注册表为空，没有可查看的服务"
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

deps_info "依赖中间件状态（$(date '+%Y-%m-%d %H:%M:%S')）"
not_ready=0

for i in ${selected[@]+"${selected[@]}"}; do
    id="${DEPS_IDS[$i]}"
    label="${DEPS_LABELS[$i]}"
    manager="${DEPS_MANAGERS[$i]}"
    probe="${DEPS_PROBES[$i]}"

    if deps_manager_running "$i"; then
        process_state="running"
    else
        process_state="stopped"
    fi

    ready_state="$(deps_probe_state "$probe")"
    if [ "$ready_state" = "not-ready" ]; then
        not_ready=$((not_ready + 1))
    fi

    deps_info ""
    deps_info "$label"
    deps_dim "  标识 $id · 管理方式 $manager"
    if [ "$process_state" = "running" ]; then
        deps_ok "  进程状态 running"
    else
        deps_warn "  进程状态 stopped"
    fi
    case "$ready_state" in
        ready)
            deps_ok "  就绪状态 ready"
            ;;
        not-ready)
            deps_warn "  就绪状态 not-ready"
            ;;
        *)
            deps_dim "  就绪状态 n/a（没有配置就绪探针）"
            ;;
    esac

    ports="$(deps_ports_of "${DEPS_PORTS[$i]}")"
    if [ -z "$ports" ]; then
        deps_dim "  端口：未登记"
        continue
    fi
    for port in $ports; do
        state="$(deps_port_state "$port")"
        owner="$(deps_port_owner "$port")"
        if [ "$state" = "listening" ]; then
            if [ -n "$owner" ]; then
                deps_ok "  端口 ${port}：listening（${owner}）"
            else
                deps_ok "  端口 ${port}：listening"
            fi
        else
            deps_dim "  端口 ${port}：free"
        fi
    done
done

deps_info ""
if [ "$not_ready" -eq 0 ]; then
    deps_ok "全部依赖中间件就绪"
    exit 0
fi
deps_err "$not_ready 个依赖中间件未就绪"
exit 1
