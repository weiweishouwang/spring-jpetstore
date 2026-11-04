#!/usr/bin/env bash
# 启动打包后的 JPetStore 应用（后端 jar + 前端 dist）。
#
# 连接信息的给法（两种都能用，环境变量优先）：
#
#   1. 环境变量：MySQL 用 SPRING_DATASOURCE_URL / _USERNAME / _PASSWORD，
#      Redis 用 REDIS_*，pgvector 用 PGVECTOR_*。可以写进同目录的 .env 文件
#      （start.sh 会自动载入；.env 不随包分发），也可以由外部注入。
#   2. 配置文件：直接改 application.yml 里的值。此时不要再导出同名环境变量——
#      Spring Boot 里环境变量的优先级高于配置文件。
#
# 用法：
#   ./start.sh                # 后台启动，等到就绪才返回
#   ./start.sh --foreground   # 前台运行（给 systemd 之类的监督进程用）
#   ./start.sh --port 9090    # 覆盖端口
set -euo pipefail

APP_HOME="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
JAR="$APP_HOME/backend.jar"
CONFIG="$APP_HOME/application.yml"
LOG_DIR="$APP_HOME/logs"
PID_FILE="$APP_HOME/run/app.pid"
LOG_FILE="$LOG_DIR/app.log"

SERVER_PORT="${SERVER_PORT:-8080}"
JAVA_OPTS="${JAVA_OPTS:-}"
STARTUP_TIMEOUT="${STARTUP_TIMEOUT:-120}"
FOREGROUND=0

usage() {
    cat <<'USAGE'
用法：./start.sh [选项]

选项：
  --foreground        前台运行（不写 PID 文件），供 systemd 等监督进程使用。
  --port PORT         覆盖监听端口（默认 8080，或 SERVER_PORT）。
  -h, --help          显示本帮助。

可用环境变量：
  SPRING_DATASOURCE_URL / _USERNAME / _PASSWORD   MySQL 连接信息（必填）
  REDIS_HOST / REDIS_PORT / REDIS_PASSWORD / REDIS_DATABASE / REDIS_URL
  PGVECTOR_URL / _HOST / _PORT / _DATABASE / _USERNAME / _PASSWORD
  SERVER_PORT      监听端口
  JAVA_OPTS        额外的 JVM 参数
  STARTUP_TIMEOUT  等待就绪的上限（秒）
  SPRING_PROFILES_ACTIVE  Spring profile（默认 prod）
USAGE
}

while [ "$#" -gt 0 ]; do
    case "$1" in
        --foreground)
            FOREGROUND=1
            shift
            ;;
        --port)
            [ "$#" -ge 2 ] || { echo "--port 需要端口号" >&2; exit 2; }
            SERVER_PORT="$2"
            shift 2
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

# 可选的 .env：连接信息的本地写法，不随包分发。
if [ -f "$APP_HOME/.env" ]; then
    set -a
    # shellcheck source=/dev/null
    . "$APP_HOME/.env"
    set +a
fi

# MySQL 连接信息必须给：环境变量没给、配置文件里又还是占位符时直接失败，
# 不必等应用启动后再看日志（应用本身也会快速失败）。
if [ -z "${SPRING_DATASOURCE_URL:-}" ] && grep -q '\${SPRING_DATASOURCE_URL}' "$CONFIG" 2>/dev/null; then
    echo "缺少 MySQL 连接信息：请设置 SPRING_DATASOURCE_URL（或在 application.yml 里写死 url）" >&2
    exit 2
fi

JAVA_BIN="java"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA_BIN="$JAVA_HOME/bin/java"
fi
if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
    echo "找不到 java：目标机器需要自备 JDK（21 或更高），或设置 JAVA_HOME" >&2
    exit 2
fi

# 目标机器自带 JDK，这里只校验版本够用。
java_version="$("$JAVA_BIN" -version 2>&1 | head -1)"
java_major="$(printf '%s' "$java_version" | sed -E 's/.*version "([0-9]+)\.([0-9]+).*/\1 \2/' | awk '{ if ($1 == 1) print $2; else print $1 }')"
if [ -n "$java_major" ] && [ "$java_major" -lt 21 ] 2>/dev/null; then
    echo "Java 版本过低（$java_version），需要 21 或更高" >&2
    exit 2
fi

mkdir -p "$LOG_DIR" "$APP_HOME/run"

# 已经在跑就别再起一个。
if [ -f "$PID_FILE" ] && [ "$FOREGROUND" -eq 0 ]; then
    existing="$(cat "$PID_FILE" 2>/dev/null || true)"
    if [ -n "$existing" ] && kill -0 "$existing" 2>/dev/null; then
        echo "已经在运行（PID $existing）"
        exit 0
    fi
    rm -f "$PID_FILE"
fi

# shellcheck disable=SC2086
java_args=(
    -jar "$JAR"
    "--spring.config.additional-location=file:$CONFIG"
    "--server.port=$SERVER_PORT"
    "--spring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}"
)

if [ "$FOREGROUND" -eq 1 ]; then
    exec "$JAVA_BIN" $JAVA_OPTS "${java_args[@]}"
fi

echo "启动 JPetStore（端口 $SERVER_PORT）…"
# shellcheck disable=SC2086
nohup "$JAVA_BIN" $JAVA_OPTS "${java_args[@]}" >"$LOG_FILE" 2>&1 &
pid=$!
printf '%s\n' "$pid" >"$PID_FILE"

# 等就绪：用真实的健康探针轮询，不用固定 sleep 掩盖启动慢的问题。
ready=0
waited=0
while [ "$waited" -lt "$STARTUP_TIMEOUT" ]; do
    if ! kill -0 "$pid" 2>/dev/null; then
        break
    fi
    if command -v curl >/dev/null 2>&1; then
        if curl -fsS --max-time 3 "http://127.0.0.1:$SERVER_PORT/actuator/health/readiness" 2>/dev/null | grep -q '"status":"UP"'; then
            ready=1
            break
        fi
    elif (exec 3<>"/dev/tcp/127.0.0.1/$SERVER_PORT") >/dev/null 2>&1; then
        ready=1
        break
    fi
    sleep 1
    waited=$((waited + 1))
done

if [ "$ready" -eq 1 ]; then
    echo "已就绪（PID $pid，http://127.0.0.1:$SERVER_PORT）"
    exit 0
fi

if kill -0 "$pid" 2>/dev/null; then
    echo "在 ${STARTUP_TIMEOUT}s 内没有就绪，进程仍在运行（PID $pid）；日志：$LOG_FILE" >&2
    exit 1
fi

rm -f "$PID_FILE"
echo "启动失败，日志尾部：" >&2
tail -n 30 "$LOG_FILE" >&2 || true
exit 1
