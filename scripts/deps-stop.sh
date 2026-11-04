#!/usr/bin/env bash
# 一键停止依赖中间件：逐个停止，并确认端口已经释放。
#
# 用法：
#   scripts/deps-stop.sh                  # 停止注册表里的全部服务
#   scripts/deps-stop.sh --service mysql  # 只停止指定服务
#   scripts/deps-stop.sh --force          # 进程不响应 TERM 时强制结束
#
# 退出码 0 表示全部停止；有任何服务停不掉就返回非零。
set -euo pipefail

DEPS_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=deps-common.sh
. "$DEPS_SCRIPT_DIR/deps-common.sh"
cd "$DEPS_REPO_ROOT"

usage() {
    cat <<'USAGE'
用法：scripts/deps-stop.sh [选项]

选项：
  --service ID   只处理指定服务（可重复）。默认处理注册表里的全部服务。
  --timeout N    停止等待上限（秒），默认 30。
  --force        进程不响应 TERM 时用 KILL 结束（只对脚本自己启动的进程有效）。
  -h, --help     显示本帮助。
USAGE
}

timeout="$DEPS_STOP_TIMEOUT"
services=()
force=0

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
        --force)
            force=1
            shift
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

DEPS_FORCE="$force"
export DEPS_FORCE

deps_load_registry

if [ "$(deps_count)" = "0" ]; then
    deps_err "注册表为空，没有可停止的服务"
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

deps_info "停止依赖中间件"
failures=0
for i in ${selected[@]+"${selected[@]}"}; do
    id="${DEPS_IDS[$i]}"
    label="${DEPS_LABELS[$i]}"
    manager="${DEPS_MANAGERS[$i]}"

    if ! deps_manager_running "$i"; then
        deps_info "${label}：本来就没在运行"
        continue
    fi

    if ! deps_manager_stop "$i"; then
        deps_err "${label}：停止失败"
        failures=$((failures + 1))
        continue
    fi
    deps_ok "${label}：已停止"

    # 脚本自己拉起的进程要确认端口真的释放了；docker/brew/systemd 的停止由各自的管理器负责。
    case "$manager" in
        jar|command)
            for port in $(deps_ports_of "${DEPS_PORTS[$i]}"); do
                waited=0
                while [ "$waited" -lt "$timeout" ] && [ "$(deps_port_state "$port")" = "listening" ]; do
                    sleep 1
                    waited=$((waited + 1))
                done
                if [ "$(deps_port_state "$port")" = "listening" ]; then
                    deps_warn "${label}：端口 ${port} 仍在监听"
                    failures=$((failures + 1))
                fi
            done
            ;;
    esac
done

if [ "$failures" -eq 0 ]; then
    deps_ok "全部依赖中间件已停止"
    exit 0
fi
deps_err "$failures 个依赖中间件没有干净停止"
exit 1
