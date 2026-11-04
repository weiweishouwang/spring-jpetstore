#!/usr/bin/env bash
# 本地开发一键启动：检查外部依赖 → 构建后端 → 后台启动后端并等健康检查通过 → 启动前端开发服务器。
#
# 任何一步失败就停下并说明原因；本次启动的进程会被回收，不留半拉子的现场。已经跑着的进程
# 不重复启动。
#
# 用法：
#   ./start.sh                # 用 .env.dev 里的端口与连接信息
#   REQUIRE_REDIS=1 ./start.sh  # 把 Redis 也当成硬性前置条件
#
# 依赖检查的严格程度：MySQL 是应用启动的硬性前置（连不上应用直接起不来），Redis 当前构建并
# 不使用（plan §9 首期不引入），所以默认只提示；需要它时用 REQUIRE_REDIS=1。
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

RUN_DIR="$ROOT/run"
LOG_DIR="$ROOT/logs"

# 载入本机变量（.env.dev 不入库）：已经在环境里的优先，文件只补齐缺的。
if [ -f "$ROOT/.env.dev" ]; then
    while IFS= read -r line || [ -n "$line" ]; do
        case "$line" in
            ''|'#'*) continue ;;
            *=*) ;;
            *) continue ;;
        esac
        key="${line%%=*}"
        value="${line#*=}"
        [ -n "$key" ] || continue
        if ! printenv "$key" >/dev/null 2>&1; then
            export "$key=$value"
        fi
    done < "$ROOT/.env.dev"
fi

BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
REDIS_HOST="${REDIS_HOST:-127.0.0.1}"
REDIS_PORT="${REDIS_PORT:-6379}"
REQUIRE_REDIS="${REQUIRE_REDIS:-0}"
BACKEND_TIMEOUT="${BACKEND_TIMEOUT:-180}"
FRONTEND_TIMEOUT="${FRONTEND_TIMEOUT:-60}"

# PID 记录按端口分文件：不同端口的实例互不覆盖。
BACKEND_PID_FILE="$RUN_DIR/backend-$BACKEND_PORT.pid"
FRONTEND_PID_FILE="$RUN_DIR/frontend-$FRONTEND_PORT.pid"

STARTED_BACKEND=0
STARTED_FRONTEND=0

log() { printf '%s\n' "$*"; }
step() { printf '\n== %s\n' "$*"; }
fail() {
    printf '\n启动失败：%s\n' "$*" >&2
    exit 1
}

# 端口是否可连接。优先用 nc（带超时），没有 nc 时退回 bash 的 /dev/tcp。
port_open() {
    if command -v nc >/dev/null 2>&1; then
        nc -z -w 2 "$1" "$2" >/dev/null 2>&1
        return $?
    fi
    (exec 3<>"/dev/tcp/$1/$2") >/dev/null 2>&1
}

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

# 停掉一个由本脚本记录的进程：先 TERM，等一会儿，还活着才 KILL。
stop_one() {
    local pid_file="$1" label="$2" pid waited=0
    pid="$(read_pid "$pid_file" || true)"
    if [ -z "$pid" ] || ! pid_alive "$pid"; then
        rm -f "$pid_file"
        return 0
    fi
    kill -TERM "$pid" 2>/dev/null || true
    while pid_alive "$pid" && [ "$waited" -lt 15 ]; do
        sleep 1
        waited=$((waited + 1))
    done
    if pid_alive "$pid"; then
        kill -9 "$pid" 2>/dev/null || true
    fi
    rm -f "$pid_file"
    log "  已回收 $label（PID $pid）"
}

on_exit() {
    local status="${1:-0}"
    if [ "$status" -ne 0 ]; then
        printf '\n启动没有走完，正在回收本次启动的进程…\n' >&2
        [ "$STARTED_FRONTEND" -eq 1 ] && stop_one "$FRONTEND_PID_FILE" "前端" || true
        [ "$STARTED_BACKEND" -eq 1 ] && stop_one "$BACKEND_PID_FILE" "后端" || true
    fi
}
trap on_exit EXIT

# ---------------------------------------------------------------- 1. 依赖检查
step "1/5 检查外部依赖"

if ! port_open "$MYSQL_HOST" "$MYSQL_PORT"; then
    fail "MySQL 不可用（$MYSQL_HOST:$MYSQL_PORT）：应用启动即失败，先把数据库起起来（scripts/deps-start.sh 或 docker compose --env-file .env.dev -f infra/compose.dev.yml up -d）"
fi
log "  MySQL：可用（$MYSQL_HOST:$MYSQL_PORT）"

if port_open "$REDIS_HOST" "$REDIS_PORT"; then
    log "  Redis：可用（$REDIS_HOST:$REDIS_PORT）"
elif [ "$REQUIRE_REDIS" = "1" ]; then
    fail "Redis 不可用（$REDIS_HOST:$REDIS_PORT），而 REQUIRE_REDIS=1 要求它必须可用"
else
    log "  Redis：不可用（$REDIS_HOST:$REDIS_PORT）——当前构建不使用 Redis，不阻塞启动；需要它时用 REQUIRE_REDIS=1"
fi

# ---------------------------------------------------------------- 2. 构建后端
step "2/5 构建后端"
if ! ./mvnw -B -ntp -pl backend -DskipTests package; then
    fail "后端构建失败"
fi

jar=""
for candidate in backend/target/backend-*.jar; do
    [ -f "$candidate" ] || continue
    case "$candidate" in
        *.original) continue ;;
    esac
    jar="$candidate"
done
[ -n "$jar" ] || fail "构建成功但找不到 backend/target/backend-*.jar"

# ---------------------------------------------------------------- 3. 启动后端
step "3/5 启动后端"
existing_backend="$(read_pid "$BACKEND_PID_FILE" || true)"
if [ -n "$existing_backend" ] && pid_alive "$existing_backend"; then
    log "  后端已经在运行（PID $existing_backend），跳过启动"
else
    rm -f "$BACKEND_PID_FILE"
    mkdir -p "$RUN_DIR" "$LOG_DIR"
    log "  java -jar $jar （端口 $BACKEND_PORT）"
    # shellcheck disable=SC2086
    nohup java ${JAVA_OPTS:-} -jar "$jar" \
        "--spring.profiles.active=${SPRING_PROFILES_ACTIVE:-dev}" \
        "--server.port=$BACKEND_PORT" \
        >"$LOG_DIR/backend.log" 2>&1 &
    backend_pid=$!
    printf '%s\n' "$backend_pid" >"$BACKEND_PID_FILE"
    STARTED_BACKEND=1
fi

# ---------------------------------------------------------------- 4. 等健康检查
step "4/5 等待后端健康检查通过"
backend_pid="$(read_pid "$BACKEND_PID_FILE" || true)"
[ -n "$backend_pid" ] || fail "后端 PID 记录丢失"

waited=0
ready=0
while [ "$waited" -lt "$BACKEND_TIMEOUT" ]; do
    if ! pid_alive "$backend_pid"; then
        tail -n 30 "$LOG_DIR/backend.log" >&2 || true
        fail "后端进程已退出（日志：$LOG_DIR/backend.log）"
    fi
    if curl -fsS --max-time 3 "http://127.0.0.1:$BACKEND_PORT/actuator/health" 2>/dev/null | grep -q '"status":"UP"'; then
        ready=1
        break
    fi
    sleep 1
    waited=$((waited + 1))
done
[ "$ready" -eq 1 ] || { tail -n 30 "$LOG_DIR/backend.log" >&2 || true; fail "后端在 ${BACKEND_TIMEOUT}s 内没有通过健康检查（日志：$LOG_DIR/backend.log）"; }
log "  后端已就绪（PID $backend_pid）"

# ---------------------------------------------------------------- 5. 前端开发服务器
step "5/5 启动前端开发服务器"
existing_frontend="$(read_pid "$FRONTEND_PID_FILE" || true)"
if [ -n "$existing_frontend" ] && pid_alive "$existing_frontend"; then
    log "  前端已经在运行（PID $existing_frontend），跳过启动"
else
    rm -f "$FRONTEND_PID_FILE"
    if [ ! -x "$ROOT/frontend/node_modules/.bin/vite" ]; then
        fail "前端依赖没装好：先执行 cd frontend && npm ci"
    fi
    # 直接跑 vite 而不是 npm run dev：这样记下的 PID 就是 vite 本身，停止时才不会留下孤儿子进程。
    # 后台命令必须自己就是 vite（不能套一层子 shell）：套了的话那层 shell 会一直持有脚本的
    # 标准输出，调用方会等不到脚本结束。
    log "  vite --port $FRONTEND_PORT"
    pushd "$ROOT/frontend" >/dev/null
    nohup ./node_modules/.bin/vite --port "$FRONTEND_PORT" --strictPort \
        >"$LOG_DIR/frontend.log" 2>&1 &
    frontend_pid=$!
    popd >/dev/null
    printf '%s\n' "$frontend_pid" >"$FRONTEND_PID_FILE"
    [ -n "$frontend_pid" ] || fail "前端启动命令没有给出 PID"
    STARTED_FRONTEND=1

    waited=0
    frontend_ready=0
    while [ "$waited" -lt "$FRONTEND_TIMEOUT" ]; do
        if ! pid_alive "$frontend_pid"; then
            tail -n 30 "$LOG_DIR/frontend.log" >&2 || true
            fail "前端进程已退出（日志：$LOG_DIR/frontend.log）"
        fi
        if port_open 127.0.0.1 "$FRONTEND_PORT"; then
            frontend_ready=1
            break
        fi
        sleep 1
        waited=$((waited + 1))
    done
    [ "$frontend_ready" -eq 1 ] || { tail -n 30 "$LOG_DIR/frontend.log" >&2 || true; fail "前端在 ${FRONTEND_TIMEOUT}s 内没有监听端口 $FRONTEND_PORT"; }
    log "  前端已就绪（PID $frontend_pid）"
fi

printf '\n本地开发环境已启动：\n'
printf '  前端开发服务器  http://127.0.0.1:%s\n' "$FRONTEND_PORT"
printf '  后端 API        http://127.0.0.1:%s/api\n' "$BACKEND_PORT"
printf '  健康检查        http://127.0.0.1:%s/actuator/health\n' "$BACKEND_PORT"
printf '  日志            %s/backend.log、%s/frontend.log\n' "$LOG_DIR" "$LOG_DIR"
printf '  停止            ./stop.sh（或 make stop）\n'
