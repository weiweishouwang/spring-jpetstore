# 基础开发环境验证（T179）

| 属性 | 值 |
| --- | --- |
| 执行日期 | 2026-11-03，Asia/Shanghai |
| 主机 | macOS 15.3.1，arm64（Apple Silicon） |
| 目标运行时 | Temurin JDK 21.0.13+9；Node 24.22.2 / npm 11.19.1；Python 3.14.4；Maven Wrapper 3.9.11 |
| 目标数据库 | MySQL 8.4.13（镜像 `library/mysql@sha256:a0225d1e…`，linux/arm64） |
| 范围 | 从干净状态验证基础开发环境可准备完成；**不含**完整业务页面或全栈启动（属 T406） |

## 1. 开发变量

`.env.dev`（被 `.gitignore` 忽略，仅本地存在）包含且仅包含以下键，本报告不记录任何值：

`MYSQL_DATABASE`、`MYSQL_USER`、`MYSQL_PASSWORD`、`MYSQL_ROOT_PASSWORD`、`MYSQL_PORT`、`BACKEND_PORT`、`FRONTEND_PORT`、`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`SPRING_DATASOURCE_PASSWORD`、`SPRING_PROFILES_ACTIVE`。

模板见 [.env.example](../../.env.example)；`backend` 的 `application.yml` 只引用 `SPRING_DATASOURCE_*` 与 `BACKEND_PORT`，缺任一项时上下文启动即失败（T177 已验证）。

## 2. 逐项命令与结果

### 2.1 环境诊断

```bash
PYTHONDONTWRITEBYTECODE=1 python3 scripts/dev-doctor.py \
  --java-home .tools/jdk-21.0.13+9/Contents/Home \
  --node-bin /Users/zhw/.nvm/versions/node/v24.22.2/bin
```

结果：`10/10 checks passed`，退出码 0。覆盖 python/git/java(含 javac)/node+npm/docker/compose/磁盘(247.6GiB)/镜像架构/数据库健康/端口占用。

### 2.2 Maven Wrapper

```bash
export JAVA_HOME="$PWD/.tools/jdk-21.0.13+9/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"
./mvnw --version
```

结果：`Apache Maven 3.9.11 (3e54c93a704957b63ee3494413a2b544fd3d825b)`，Java 21.0.13 (Eclipse Adoptium)，分发按 `.mvn/wrapper/maven-wrapper.properties` 中锁定的 SHA512 校验后下载。

### 2.3 后端编译与测试（全新 Maven 缓存）

```bash
./mvnw -B -Dmaven.repo.local=/private/tmp/jpetstore-verify-m2 -pl backend test
```

结果：`BUILD SUCCESS`，`Tests run: 7, Failures: 0, Errors: 0, Skipped: 0`，Total time 02:53。

| 测试类 | 用例数 | 结果 |
| --- | --- | --- |
| `ApplicationConfigurationTest` | 4 | 通过（含真实 MySQL 容器连接、MyBatis 缓存关闭、Flyway 严格性、缺配置快速失败） |
| `ApplicationBoundaryTest` | 3 | 通过（启动类锚点、无 legacy XML 导入、基础上下文可启动） |

使用空的 `maven.repo.local` 目录，证明**不依赖旧 Maven 缓存**即可完成构建与测试。

### 2.4 前端依赖安装

```bash
cd frontend && npm ci --no-audit --no-fund
```

结果：安装成功，退出码 0。安装过程未执行任何未批准的 lifecycle 脚本（npm 报告 `core-js`、`fsevents`、`protobufjs` 三个包的脚本未获批准因而未运行），未写入任何受版本控制的文件；安装后 `git status` 无受跟踪文件改动。工具版本实测：`vite 8.3.5`、`vitest 5.1.0`、`vue-tsc`（TypeScript 5.9.3）、`eslint 10.14.0`。

未重复执行"清空 npm 缓存后再安装"的变体：依赖可获取性已由上述安装证明，验收要求的"不依赖旧缓存"针对 Maven 缓存，已在 2.3 单独验证。

### 2.5 前端静态检查与类型检查

```bash
cd frontend && npm run lint      # eslint .            → 0 问题
cd frontend && npm run typecheck # vue-tsc --noEmit     → 退出码 0
```

`eslint` 经探针文件验证确能发现真实问题（`@typescript-eslint/no-explicit-any`、`@typescript-eslint/no-unused-vars`），且不整体排除测试文件。

### 2.6 开发数据库

```bash
docker compose --env-file .env.dev -f infra/compose.dev.yml ps
docker compose --env-file .env.dev -f infra/compose.dev.yml exec -T mysql sh -c \
  'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=tcp -h127.0.0.1 -u"$MYSQL_USER" -D"$MYSQL_DATABASE" -N -e "SELECT VERSION(), DATABASE(), CURRENT_USER();"'
```

结果：容器 `jpetstore-dev-mysql-1` 状态 `Up (healthy)`，端口仅绑定 `127.0.0.1:3306`；应用账号查询返回 `8.4.13 / jpetstore_dev / jpetstore_app@%`。镜像按 digest 固定，未使用 `latest`。

## 3. 边界与未覆盖项

- 验证对象是**基础开发环境**，不是完整应用：业务页面、REST 端点、Vue 页面、真实会话与浏览器流程均未实现或未验证（属 Phase 5～9）。
- `backend` 当前仅有启动类与环境配置，`./mvnw verify` 通过但不含业务测试；Failsafe（`*IT`）尚无集成测试文件。
- 数据库 schema 迁移尚未存在（Phase 4），Flyway 当前无可执行迁移；演示数据仅存在于 `backend/src/test/resources/fixtures/shop.sql`（测试库用）。
- 前端尚无业务页面与组件测试，`vitest` 目前为空测试环境（`--passWithNoTests` 退出码 0）。
- Playwright 浏览器二进制未安装，E2E 未运行（属 Phase 10）。

## 4. 结论

新开发机在具备 Docker、可用 JDK 21/Node 24 与网络的前提下，可按 [.env.example](../../.env.example) → `docker compose up -d` → `./mvnw verify` → `npm ci` 的顺序完成准备，不需要旧 Maven 缓存、已启动的共享数据库或手工修改源码。上述每一步均以本机实测命令与结果为证。
