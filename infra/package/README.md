# JPetStore 本地部署包

一个自包含的部署目录：后端 jar、前端产物、启动与停止脚本、配置模板。
目标机器只需要有 JDK 21 或更高版本；MySQL、Redis、pgvector 都是外部服务，不在包里。

## 目录结构

```
backend.jar        后端应用（内含前端静态资源，可自己提供页面）
dist/              前端产物（index.html 与 assets/，供前置 Web 服务器使用）
application.yml    配置模板：外部服务的连接信息在这里或用环境变量注入
start.sh           启动脚本，等到就绪才返回
stop.sh            停止脚本：SIGTERM 优雅停止并等待进程退出
logs/              运行日志（启动后生成）
run/app.pid        进程号（启动后生成）
```

## 连接信息

MySQL 必填，Redis 与 pgvector 视构建而定（当前构建没有引入 Redis 与 pgvector 依赖，
模板里预留了注入点）。两种给法，环境变量优先：

```bash
# 方式一：环境变量（推荐）。可以写进同目录的 .env 文件，start.sh 会自动载入。
cat > .env <<'ENV'
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/jpetstore?connectionTimeZone=UTC
SPRING_DATASOURCE_USERNAME=jpetstore
SPRING_DATASOURCE_PASSWORD=换成真实口令
REDIS_HOST=127.0.0.1
REDIS_PORT=6379
PGVECTOR_HOST=127.0.0.1
PGVECTOR_PORT=5432
PGVECTOR_DATABASE=vectors
PGVECTOR_USERNAME=vectors
PGVECTOR_PASSWORD=换成真实口令
ENV

# 方式二：直接改 application.yml 里的值。此时不要导出同名环境变量。
```

`.env` 不随包分发，也不会进版本库。

## 启动与停止

```bash
./start.sh              # 后台启动，轮询 /actuator/health/readiness 直到就绪
./start.sh --port 9090  # 换端口
./start.sh --foreground # 前台运行，给 systemd 之类的监督进程用

./stop.sh               # SIGTERM 优雅停止，等待进程退出（上限 40 秒）
./stop.sh --force       # 超时后用 SIGKILL 强制结束
```

`start.sh` 会在 Java 版本低于 21、缺少 MySQL 连接信息、或应用启动后未能在
`STARTUP_TIMEOUT`（默认 120 秒）内就绪时明确报错并返回非零，日志在 `logs/app.log`。

## 验收

```bash
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
```

返回 `"status":"UP"` 即就绪（就绪探针包含数据库检查）。

## 校验包内容

包里有 `SHA256SUMS`，可以核对文件是否被改动：

```bash
sha256sum -c SHA256SUMS
```
