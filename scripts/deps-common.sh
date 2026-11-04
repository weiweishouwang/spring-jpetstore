#!/usr/bin/env bash
# 依赖中间件的注册表与公共工具：deps-start.sh、deps-stop.sh、deps-status.sh 共用这一份实现。
#
# 设计约定（与 scripts/dev-smoke.py、scripts/dev-doctor.py 保持一致）：
#
# * 进程型服务（jar / 命令）只终止本脚本自己记录过 PID 的进程，绝不碰用户已有的进程；
# * “进程在”与“就绪”分开报告：进程活着但端口还没起来时算未就绪，不写成“已启动”；
# * 启动后有界轮询等待就绪，不用固定 sleep 掩盖启动慢的问题；
# * 注册表里不写口令，也不打印口令；机器本地差异写在不入库的 scripts/deps.local.conf。
#
# 注册表（deps_add_service 的八个参数）：
#
#   1 id         服务标识，用于命令行选择与 PID 文件名
#   2 label      展示名
#   3 manager    管理方式：docker | brew | systemd | jar | command
#   4 start      启动命令
#   5 stop       停止命令
#   6 status     状态命令：退出码 0 表示“正在运行”，非 0 表示没在跑
#   7 probe      就绪探针：port:主机:端口 | http:URL | cmd:命令 | none
#   8 ports      要展示的端口列表（逗号分隔）
#
# jar / command 两种方式的 start/stop/status 由脚本自己处理（后台运行并记录 PID），
# 注册表里的对应字段可以写成 true 占位。

# 目录：脚本所在目录、仓库根目录、运行期目录（PID 与日志）。
DEPS_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPS_REPO_ROOT="$(cd "$DEPS_SCRIPT_DIR/.." && pwd)"
DEPS_RUN_DIR="${DEPS_RUN_DIR:-$DEPS_REPO_ROOT/.run}"

# 轮询间隔（秒）与默认超时（秒）。
DEPS_PROBE_INTERVAL="${DEPS_PROBE_INTERVAL:-1}"
DEPS_START_TIMEOUT="${DEPS_START_TIMEOUT:-120}"
DEPS_STOP_TIMEOUT="${DEPS_STOP_TIMEOUT:-30}"

# 颜色只在终端下输出，便于日志与测试阅读。
if [ -t 1 ]; then
    DEPS_C_OK=$'\033[32m'
    DEPS_C_WARN=$'\033[33m'
    DEPS_C_ERR=$'\033[31m'
    DEPS_C_DIM=$'\033[2m'
    DEPS_C_OFF=$'\033[0m'
else
    DEPS_C_OK=''
    DEPS_C_WARN=''
    DEPS_C_ERR=''
    DEPS_C_DIM=''
    DEPS_C_OFF=''
fi

# 服务注册表：并行数组保存（bash 3.2 没有关联数组）。
DEPS_IDS=()
DEPS_LABELS=()
DEPS_MANAGERS=()
DEPS_STARTS=()
DEPS_STOPS=()
DEPS_STATUSES=()
DEPS_PROBES=()
DEPS_PORTS=()

deps_info() { printf '%s\n' "$*"; }
deps_ok() { printf '%s%s%s\n' "$DEPS_C_OK" "$*" "$DEPS_C_OFF"; }
deps_warn() { printf '%s%s%s\n' "$DEPS_C_WARN" "$*" "$DEPS_C_OFF"; }
deps_err() { printf '%s%s%s\n' "$DEPS_C_ERR" "$*" "$DEPS_C_OFF" >&2; }
deps_dim() { printf '%s%s%s\n' "$DEPS_C_DIM" "$*" "$DEPS_C_OFF"; }

# 注册一个服务。
deps_add_service() {
    if [ "$#" -lt 8 ]; then
        deps_err "deps_add_service 需要 8 个参数，收到 $# 个"
        return 2
    fi
    DEPS_IDS+=("$1")
    DEPS_LABELS+=("$2")
    DEPS_MANAGERS+=("$3")
    DEPS_STARTS+=("$4")
    DEPS_STOPS+=("$5")
    DEPS_STATUSES+=("$6")
    DEPS_PROBES+=("$7")
    DEPS_PORTS+=("$8")
}

deps_reset_registry() {
    DEPS_IDS=()
    DEPS_LABELS=()
    DEPS_MANAGERS=()
    DEPS_STARTS=()
    DEPS_STOPS=()
    DEPS_STATUSES=()
    DEPS_PROBES=()
    DEPS_PORTS=()
}

deps_count() { printf '%s' "${#DEPS_IDS[@]}"; }

# 仓库自带的默认注册表：目前只有 MySQL 8.4 一个依赖中间件
# （plan §9：首期不引入 Redis、消息队列、分布式事务或独立搜索引擎）。
deps_register_defaults() {
    local env_file=""
    if [ -f "$DEPS_REPO_ROOT/.env.dev" ]; then
        env_file="--env-file .env.dev"
    fi
    deps_add_service \
        "mysql" \
        "MySQL 8.4" \
        "docker" \
        "docker compose $env_file -f infra/compose.dev.yml up -d --wait mysql" \
        "docker compose $env_file -f infra/compose.dev.yml stop mysql" \
        "docker compose $env_file -f infra/compose.dev.yml ps --status running --services mysql | grep -qx mysql" \
        "port:127.0.0.1:3306" \
        "3306"
}

# 装载注册表：本地覆盖文件优先（scripts/deps.local.conf，或 DEPS_CONF 指定的路径）。
# 覆盖文件里定义 deps_register_services() 即完全接管注册表。
deps_load_registry() {
    deps_reset_registry
    local conf="${DEPS_CONF:-$DEPS_SCRIPT_DIR/deps.local.conf}"
    if [ -f "$conf" ]; then
        # shellcheck source=/dev/null
        . "$conf"
    fi
    if command -v deps_register_services >/dev/null 2>&1; then
        deps_register_services
    else
        deps_register_defaults
    fi
}

# 按标识找服务下标，找不到返回 1。
deps_index_of() {
    local want="$1" i=0
    while [ "$i" -lt "${#DEPS_IDS[@]}" ]; do
        if [ "${DEPS_IDS[$i]}" = "$want" ]; then
            printf '%s' "$i"
            return 0
        fi
        i=$((i + 1))
    done
    return 1
}

# 执行注册表里的命令。注册表来自本地配置文件，按项目约定视为可信输入。
deps_run() {
    local cmd="$1"
    deps_dim "\$ $cmd"
    eval "$cmd"
}

deps_run_quiet() {
    eval "$1" >/dev/null 2>&1
}

# 端口是否可连接。优先用 nc（带超时），没有 nc 时退回 bash 的 /dev/tcp。
deps_port_open() {
    local host="$1" port="$2"
    if command -v nc >/dev/null 2>&1; then
        nc -z -w 2 "$host" "$port" >/dev/null 2>&1
        return $?
    fi
    (exec 3<>"/dev/tcp/$host/$port") >/dev/null 2>&1
}

# HTTP 探针：curl 优先，其次 wget。
deps_http_ok() {
    local url="$1"
    if command -v curl >/dev/null 2>&1; then
        curl -fsS -o /dev/null --max-time 5 "$url" >/dev/null 2>&1
        return $?
    fi
    if command -v wget >/dev/null 2>&1; then
        wget -q -T 5 -O /dev/null "$url" >/dev/null 2>&1
        return $?
    fi
    return 1
}

# 就绪探针：输出 ready / not-ready / n/a。
deps_probe_state() {
    local probe="$1" target host port
    case "$probe" in
        ""|none)
            printf 'n/a\n'
            ;;
        port:*)
            target="${probe#port:}"
            host="${target%%:*}"
            port="${target##*:}"
            if deps_port_open "$host" "$port"; then printf 'ready\n'; else printf 'not-ready\n'; fi
            ;;
        http:*)
            if deps_http_ok "${probe#http:}"; then printf 'ready\n'; else printf 'not-ready\n'; fi
            ;;
        cmd:*)
            if eval "${probe#cmd:}" >/dev/null 2>&1; then printf 'ready\n'; else printf 'not-ready\n'; fi
            ;;
        *)
            printf 'not-ready\n'
            ;;
    esac
}

# 进程是否还活着。
deps_pid_alive() {
    local pid="$1"
    [ -n "$pid" ] || return 1
    kill -0 "$pid" 2>/dev/null
}

deps_pid_file() {
    printf '%s/%s.pid' "$DEPS_RUN_DIR" "$1"
}

# 管理方式分发：启动。
deps_manager_start() {
    local i="$1" id="${DEPS_IDS[$1]}" manager="${DEPS_MANAGERS[$1]}" cmd="${DEPS_STARTS[$1]}"
    case "$manager" in
        docker|brew|systemd)
            deps_run "$cmd"
            ;;
        jar|command)
            local pid_file
            pid_file="$(deps_pid_file "$id")"
            if [ -f "$pid_file" ] && deps_pid_alive "$(cat "$pid_file" 2>/dev/null || true)"; then
                return 0
            fi
            mkdir -p "$DEPS_RUN_DIR" || return 1
            local log="$DEPS_RUN_DIR/$id.log"
            deps_dim "\$ $cmd"
            # shellcheck disable=SC2086
            nohup $cmd >"$log" 2>&1 &
            printf '%s\n' "$!" >"$pid_file"
            ;;
        *)
            deps_err "未知的管理方式：$manager"
            return 2
            ;;
    esac
}

# 管理方式分发：停止。
deps_manager_stop() {
    local i="$1" id="${DEPS_IDS[$1]}" manager="${DEPS_MANAGERS[$1]}"
    case "$manager" in
        docker|brew|systemd)
            deps_run "${DEPS_STOPS[$i]}"
            ;;
        jar|command)
            local pid_file pid waited=0
            pid_file="$(deps_pid_file "$id")"
            if [ ! -f "$pid_file" ]; then
                deps_warn "没有 PID 记录，跳过 ${id}（不终止不是本脚本启动的进程）"
                return 0
            fi
            pid="$(cat "$pid_file" 2>/dev/null || true)"
            if [ -z "$pid" ] || ! deps_pid_alive "$pid"; then
                rm -f "$pid_file"
                deps_info "${id}：进程已经不在，清理 PID 记录"
                return 0
            fi
            kill "$pid" 2>/dev/null || true
            while deps_pid_alive "$pid" && [ "$waited" -lt "$DEPS_STOP_TIMEOUT" ]; do
                sleep 1
                waited=$((waited + 1))
            done
            if deps_pid_alive "$pid"; then
                if [ "${DEPS_FORCE:-0}" = "1" ]; then
                    deps_warn "${id}：进程没有响应 TERM，改用 KILL"
                    kill -9 "$pid" 2>/dev/null || true
                    sleep 1
                else
                    deps_err "${id}：进程 ${pid} 没有响应 TERM（--force 可以强制结束）"
                    return 1
                fi
            fi
            rm -f "$pid_file"
            ;;
        *)
            deps_err "未知的管理方式：$manager"
            return 2
            ;;
    esac
}

# 管理方式分发：进程是否在运行。
deps_manager_running() {
    local i="$1" id="${DEPS_IDS[$1]}" manager="${DEPS_MANAGERS[$1]}"
    case "$manager" in
        docker|brew|systemd)
            deps_run_quiet "${DEPS_STATUSES[$i]}"
            ;;
        jar|command)
            local pid_file
            pid_file="$(deps_pid_file "$id")"
            [ -f "$pid_file" ] || return 1
            deps_pid_alive "$(cat "$pid_file" 2>/dev/null || true)"
            ;;
        *)
            return 1
            ;;
    esac
}

# 等待就绪：返回 0 表示已就绪，1 表示超时，2 表示进程自己退出了。
deps_wait_ready() {
    local i="$1" timeout="$2" waited=0 state
    local probe="${DEPS_PROBES[$1]}" manager="${DEPS_MANAGERS[$1]}" id="${DEPS_IDS[$1]}"
    while [ "$waited" -lt "$timeout" ]; do
        state="$(deps_probe_state "$probe")"
        if [ "$state" != "not-ready" ]; then
            return 0
        fi
        case "$manager" in
            jar|command)
                local pid_file pid
                pid_file="$(deps_pid_file "$id")"
                if [ -f "$pid_file" ]; then
                    pid="$(cat "$pid_file" 2>/dev/null || true)"
                    if [ -z "$pid" ] || ! deps_pid_alive "$pid"; then
                        return 2
                    fi
                fi
                ;;
        esac
        sleep "$DEPS_PROBE_INTERVAL"
        waited=$((waited + DEPS_PROBE_INTERVAL))
    done
    [ "$(deps_probe_state "$probe")" != "not-ready" ]
}

# 端口状态：listening / free。
deps_port_state() {
    local port="$1"
    if deps_port_open 127.0.0.1 "$port"; then printf 'listening'; else printf 'free'; fi
}

# 端口上的监听进程（lsof 可用时给出 COMMAND(PID)）。
# lsof 找不到监听进程时退出码是 1，pipefail 下会让整条管道失败，这里显式忽略。
deps_port_owner() {
    local port="$1"
    command -v lsof >/dev/null 2>&1 || return 0
    lsof -nP -iTCP:"$port" -sTCP:LISTEN -Fpc 2>/dev/null | awk '
        /^p/ { pid = substr($0, 2) }
        /^c/ { cmd = substr($0, 2); if (cmd != "") { printf "%s(%s)", cmd, pid; exit } }
    ' || true
}

# 端口列表按逗号拆开逐个判断。
deps_ports_of() {
    local ports="$1" port
    local old_ifs="$IFS"
    IFS=','
    for port in $ports; do
        IFS="$old_ifs"
        port="$(printf '%s' "$port" | tr -d '[:space:]')"
        [ -n "$port" ] && printf '%s\n' "$port"
        IFS=','
    done
    IFS="$old_ifs"
}
