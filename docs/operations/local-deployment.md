# 本地部署：开发环境启停与部署包

> 本文覆盖两件事：①开发机上怎么一条命令起停本地环境；②怎么打一个能拷到目标机器上的部署包，
> 以及在目标机器上怎么用。
>
> 这与[生产形态部署](deployment-target.md)是两回事：生产走容器、反向代理与不可变镜像，需要授权
> 环境与发布审批；这里说的是单机自包含的部署目录，目标机器自备 JDK，数据库等外部服务不在包里。

## 1. 本地开发环境

### 起停

```bash
make start     # 或 ./start.sh
make stop      # 或 ./stop.sh
make restart   # 停止后重新启动
```

`start.sh` 走五步，任何一步失败就停下并说明原因：

| 步骤 | 做什么 | 失败时 |
| --- | --- | --- |
| 1 | 检查外部依赖（MySQL、Redis） | MySQL 连不上直接失败；Redis 不可用默认只提示 |
| 2 | 构建后端（`./mvnw -pl backend -DskipTests package`） | 打印 Maven 的错误后退出 |
| 3 | 后台启动后端，PID 记进 `run/backend-<端口>.pid` | 进程自己退出时打印日志尾部 |
| 4 | 轮询 `/actuator/health`，健康检查通过才算起好 | 超时打印日志尾部并退出 |
| 5 | 启动前端开发服务器，等它真的监听端口 | 进程退出或端口没起来时退出 |

失败时脚本会**回收本次启动的进程**（只回收这次起的，不碰你原有的进程），不留半拉子的现场。

`stop.sh` 按 `run/` 下的 PID 记录找到进程：先发 SIGTERM 等它自己退出，超时（默认 30 秒，
`STOP_TIMEOUT` 可调）才用 SIGKILL。PID 记录按端口分文件，不同端口的实例互不覆盖；停止时把全部
记录收掉，先停前端再停后端。

### 依赖检查的严格程度

- **MySQL 是硬性前置**：应用启动即失败，连不上就没有继续的必要。
- **Redis 只提示不阻塞**：当前构建没有引入 Redis 依赖（plan §9 首期不引入消息队列与缓存）。
  需要它时用 `REQUIRE_REDIS=1 ./start.sh` 把它变成硬性条件。

### 端口与配置

端口、数据库连接信息取自 `.env.dev`（不入库）；同名环境变量优先于文件里的值，所以临时换端口
不用改文件：

```bash
BACKEND_PORT=8090 FRONTEND_PORT=5199 ./start.sh
```

后端日志在 `logs/backend.log`，前端日志在 `logs/frontend.log`。

### 依赖中间件的一键启停

数据库这类外部依赖另有三个脚本（`scripts/deps-start.sh`、`deps-stop.sh`、`deps-status.sh`），
它们能管 Docker Compose、Homebrew services、systemd 与手动进程四种管理方式，并且启动后会等到
真正就绪才返回。用法见[开发指南](../development/README.md#9-依赖中间件的一键启停)。

## 2. 部署包

### 打包

```bash
make package        # 产出 target/package/jpetstore-<版本>.tar.gz
```

`make package` 先用 release profile 构建（前端产物装配进 jar），再由 `scripts/package-local.py`
组装 tar 包。脚本也可以单独运行：

```bash
python3 scripts/package-local.py --jar backend/target/backend-1.0.0-SNAPSHOT.jar \
  --dist frontend/dist --output target/package/jpetstore-1.0.0-SNAPSHOT.tar.gz
```

### 包里有什么

```
jpetstore-<版本>/
├── backend.jar        后端应用（内含前端静态资源，可自己提供页面）
├── dist/              前端产物（index.html 与 assets/，供前置 Web 服务器使用）
├── application.yml    配置模板
├── start.sh           启动脚本
├── stop.sh            停止脚本
├── README.md          使用说明
├── SHA256SUMS         包内文件的校验值
├── logs/              运行日志（启动后生成）
└── run/               PID 文件（启动后生成）
```

**包里不含 JDK**，也不含 MySQL、Redis、pgvector 这些外部服务。目标机器需要自备 JDK 21 或更高
版本（`start.sh` 会校验版本）。

### 安装到目标机器

```bash
tar xzf jpetstore-1.0.0-SNAPSHOT.tar.gz
cd jpetstore-1.0.0
sha256sum -c SHA256SUMS        # 先核对文件没被改动
```

### 连接信息

两种给法，**环境变量优先**（Spring Boot 的规则）：

```bash
# 方式一：环境变量，写进同目录的 .env（start.sh 会自动载入；.env 不随包分发）
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

# 方式二：直接改 application.yml 里的值。此时不要再导出同名环境变量，否则环境变量会盖掉它。
```

MySQL 必填；Redis 与 pgvector 是预留的注入点——当前构建没有引入这两个依赖，模板里注明了这一点，
以后启用不用改打包与部署方式。口令只写在 `.env` 或配置文件里，不进版本库、不进 tar 包。

### 启动与停止

```bash
./start.sh              # 后台启动，轮询就绪探针直到就绪
./start.sh --port 9090  # 换端口
./start.sh --foreground # 前台运行，给 systemd 之类的监督进程用
./stop.sh               # SIGTERM 优雅停止，等进程退出（上限 40 秒）
./stop.sh --force       # 超时后用 SIGKILL 强制结束
```

`start.sh` 会在 Java 版本低于 21、缺少 MySQL 连接信息、或应用启动后未能在 `STARTUP_TIMEOUT`
（默认 120 秒）内就绪时明确报错并返回非零，日志在 `logs/app.log`。

### 验收

```bash
curl -fsS http://127.0.0.1:8080/actuator/health/readiness
```

返回 `"status":"UP"` 即就绪——就绪探针包含数据库检查，数据库不可用时不会误报就绪。页面入口在
`http://127.0.0.1:8080/`。

### 升级

替换 tar 包里的 `backend.jar` 与 `dist/`，保留 `.env` 与 `application.yml`，然后 `./stop.sh`、
`./start.sh`。数据库迁移由应用启动时的 Flyway 执行（见[运行手册](runbook.md)）。

### 与生产部署的边界

| | 本地部署包 | 生产形态部署 |
| --- | --- | --- |
| 交付物 | tar 包（jar + dist + 脚本） | 不可变镜像（按 sha256 摘要）+ 外部数据库 |
| 运行方式 | 目标机器自备 JDK，`./start.sh` | 容器 + 反向代理，见 [部署目标](deployment-target.md) |
| 凭据 | `.env` 或配置文件 | `secret://` 引用，见 [生产变量模板](../../infra/.env.prod.example) |
| 前置条件 | 无需授权 | 需要目标服务器与发布授权 |

本地产物不构成生产发布授权；生产门禁见[发布清单与验收门槛](release-checklist.md)。
