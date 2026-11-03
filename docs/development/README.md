# 开发指南：启动、调试与重置

面向本地开发的操作手册。命令均以仓库根目录为基准；先准备环境变量，再按 **数据库 → 后端 → 前端** 顺序启动。

> 当前阶段（Phase 3）只交付工程基础：后端仅有启动类与环境配置，前端尚无页面。业务页面、REST 端点与全链路启动分别在后续阶段落地，**完整链路以 T406 的验证为准**，本文不假定它们已经可用。

## 1. 工具链选择

| 工具 | 版本 | 选择方式 |
| --- | --- | --- |
| Java | Temurin 21.0.13+9 | 项目内 `.tools/jdk-21.0.13+9`，或 `.java-version` 声明的版本管理器 |
| Maven | 3.9.11 | 一律使用 `./mvnw`，不要用本机 `mvn` |
| Node / npm | 24.22.2 / 11.19.1 | `.node-version` 声明；本机可用 `~/.nvm/versions/node/v24.22.2/bin` |
| Python | 3.14.x | 仅用于 `scripts/` 下的诊断与测试脚本 |
| Docker | 含 Compose v2 | 仅用于开发数据库与测试容器 |

一次性设置（示例）：

```bash
cd /Users/zhw/Documents/GitHub/spring-jpetstore
export JAVA_HOME="$PWD/.tools/jdk-21.0.13+9/Contents/Home"
export PATH="$JAVA_HOME/bin:$HOME/.nvm/versions/node/v24.22.2/bin:$PATH"
java -version && ./mvnw --version && node --version && npm --version
```

环境自检（只读诊断，不修改系统配置）：

```bash
PYTHONDONTWRITEBYTECODE=1 python3 scripts/dev-doctor.py \
  --java-home .tools/jdk-21.0.13+9/Contents/Home \
  --node-bin "$HOME/.nvm/versions/node/v24.22.2/bin"
```

## 2. 开发变量

```bash
cp .env.example .env.dev    # .env.dev 已被 .gitignore 忽略，不要提交
```

编辑 `.env.dev` 填入本地值：数据库名与应用账号、`MYSQL_PASSWORD`/`MYSQL_ROOT_PASSWORD`、以及后端连接用的 `SPRING_DATASOURCE_URL`/`USERNAME`/`PASSWORD`。

**Boot 不会自动读取 `.env` 文件。** 启动后端前必须把变量导出到进程环境，或在 IDE 的运行配置中填写同一组变量：

```bash
set -a && source .env.dev && set +a
```

前端构建变量一律不得带 `VITE_` 前缀携带秘密：`VITE_*` 会被打进浏览器产物，只有非敏感配置（如 `VITE_API_BASE_URL`）才能使用该前缀。

## 3. 数据库（仅数据库的 Compose）

[infra/compose.dev.yml](../../infra/compose.dev.yml) 只启动 MySQL，不依赖已打包的后端镜像：

```bash
docker compose --env-file .env.dev -f infra/compose.dev.yml up -d
docker compose --env-file .env.dev -f infra/compose.dev.yml ps
```

- 端口只绑定 `127.0.0.1`，镜像按 digest 固定（MySQL 8.4.13）。
- 数据保存在命名卷 `mysql-data`；**普通停止不删除卷**：

```bash
docker compose --env-file .env.dev -f infra/compose.dev.yml stop
```

- 删除数据是独立的显式操作，会永久丢失本地数据库内容：

```bash
docker compose --env-file .env.dev -f infra/compose.dev.yml down -v   # 明确要求时才执行
```

## 4. 后端

```bash
set -a && source .env.dev && set +a
./mvnw -pl backend spring-boot:run
```

- 启动时若 `backend/src/main/resources/db/migration` 中存在迁移，Flyway 会自动执行（当前 Phase 3 尚无迁移脚本）。
- 缺少 `SPRING_DATASOURCE_*` 时应用**启动即失败**，这是有意的快速失败，不要用默认值绕过。
- 演示数据由开发专用 seeder 提供，**不会**随迁移执行，也不会出现在生产环境（Phase 4 落地）。
- 后端默认端口 `8080`，可用 `BACKEND_PORT` 覆盖。

### IDE 调试

在 IDE 中以 `ik.am.jpetstore.JPetStoreApplication` 为主类启动，并在运行配置的环境变量中填入 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD`（以及可选 `BACKEND_PORT`）。调试时不必先执行 `./mvnw spring-boot:run`。

## 5. 前端

```bash
cd frontend
npm ci          # 依据 package-lock.json 精确安装
npm run dev     # Vite 开发服务器（默认 5173）
```

- 开发模式下由 Vite 代理 `/api` 到本地后端，避免宽泛 CORS；代理配置与 Vue 应用一同在 Phase 8 引入，当前 `npm run dev` 尚不提供业务页面。
- 其他脚本：`npm run lint`（ESLint）、`npm run typecheck`（vue-tsc 严格类型检查）、`npm test`（Vitest）、`npm run build`（类型检查 + 生产构建）、`npm run e2e`（Playwright，需先安装浏览器）。

## 6. 测试

```bash
./mvnw test                                     # 后端单元/切片测试
./mvnw -pl backend -Dtest=ClassName test        # 单个测试类
./mvnw -pl backend -Dit.test=ClassNameIT verify # 集成测试（*IT，需要 Docker）
npm --prefix frontend run test -- --run <相对 frontend 的测试路径>
python3 -m unittest discover -s scripts/tests   # 诊断脚本测试
```

后端集成测试使用 Testcontainers 启动与生产同版本的 MySQL；测试不得用 H2 替代。

## 7. 端口冲突排查

```bash
lsof -nP -iTCP:3306 -sTCP:LISTEN   # MySQL
lsof -nP -iTCP:8080 -sTCP:LISTEN   # 后端
lsof -nP -iTCP:5173 -sTCP:LISTEN   # 前端
```

- 3306 被本项目容器占用属正常；被其他程序占用时，改 `.env.dev` 的 `MYSQL_PORT` 并同步 `SPRING_DATASOURCE_URL` 中的端口。
- 8080/5173 应保持空闲；如需改端口，设置 `BACKEND_PORT` / `FRONTEND_PORT`，前端代理目标随之调整。
- 诊断脚本会一次性列出这些端口的占用情况。

## 8. 停止与重置

| 目的 | 命令 | 是否丢失数据 |
| --- | --- | --- |
| 停止后端 | 终止 `spring-boot:run` 进程（Ctrl-C） | 否 |
| 停止前端 | 终止 Vite 进程（Ctrl-C） | 否 |
| 停止数据库 | `docker compose ... stop` | 否 |
| 删除数据库卷 | `docker compose ... down -v` | **是，永久删除** |
| 重新安装前端依赖 | `cd frontend && npm ci` | 否（清空 node_modules） |
| 清空 Maven 本地缓存 | 删除 `~/.m2/repository`（仅在怀疑缓存损坏时） | 否，会重新下载 |

## 9. 相关文档

- [platform.md](platform.md)：支持矩阵与准备清单
- [toolchain-setup.md](toolchain-setup.md)：工具安装实测记录
- [environment-verification.md](environment-verification.md)：基础环境验证证据
- [../../docs/plan.md](../../docs/plan.md)：架构、数据与交付设计
