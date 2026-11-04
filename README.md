spring-jpetstore
================

JPetStore 6 is a full web application built on top of Spring, Spring MVC, MyBatis 3, and Spring Security. 
This is another implementation of MyBatis sample application (https://github.com/mybatis/jpetstore-6).

Original application is available for downloading in the downloads section of MyBatis project site. 
In this section we will walk through this sample to understand how is it built and learn how to run it.

现代重写版（Java 21 / Spring Boot 4 / Vue 3）
-------------------------------------------

仓库同时包含归档后的旧系统（`legacy/`）与正在重写的现代实现（`backend/` + `frontend/`）。现代版是模块化单体：
account、identity、catalog、cart、order 六个业务模块在同一进程内按包隔离，交易与库存变更在单个 MySQL 事务里完成。

### 环境要求

| 工具 | 版本 | 说明 |
| --- | --- | --- |
| JDK | Temurin 21.0.13+9 | 项目用 `.java-version` 固定；不要用系统默认 JDK |
| Maven | 通过 `./mvnw` | Wrapper 3.9.11，脚本已校验 SHA512 |
| Node / npm | 24.22.2 / 11.19.1 | `.node-version` 固定 |
| Docker / Compose | 29.x | 只用于固定版本的 MySQL 8.4.13 与镜像 |

### 从干净检出到运行

```bash
# 后端：编译 + 全量测试（含 Testcontainers 集成测试）
./mvnw verify

# 前端：按 lockfile 安装并跑全部检查
cd frontend && npm ci && npm run lint && npm run typecheck && npm test && npm run build
```

一条命令起本地开发环境（检查依赖 → 构建后端 → 起后端并等健康检查 → 起前端开发服务器）：

```bash
make start     # 停止用 make stop
```

手动分三个终端也可以：

```bash
# 1) 数据库（固定镜像摘要）
docker compose -f infra/compose.dev.yml up -d mysql

# 2) 后端（默认 8080）
cd backend && ../mvnw spring-boot:run

# 3) 前端（默认 5173，/api 代理到 127.0.0.1:8080，同源）
cd frontend && npm run dev
```

一次性全栈冒烟（自带就绪等待、脱敏诊断，只清理自己启动的进程）：

```bash
python3 scripts/dev-smoke.py --timeout 120
```

### 发布形态打包

```bash
# 构建前端并装配进可执行 JAR；缺少前端产物时打包失败
./mvnw -pl backend -Prelease -DskipTests package
```

本地部署包（一个 tar 包：后端 jar、前端产物、启停脚本、配置模板）：

```bash
make package        # 产出 target/package/jpetstore-<版本>.tar.gz
```

包里不含 JDK，也不含 MySQL、Redis、pgvector 这些外部服务——目标机器自备 JDK，连接信息由环境变量
或包内的 `application.yml` 注入。解包后 `./start.sh` 启动（等到就绪才返回），`./stop.sh` 优雅停止
并等待进程退出。

镜像与发布流水线见 [.github/workflows/release.yml](.github/workflows/release.yml) 与
[发布镜像验收](scripts/tests/test_release_image.py)。

### 旧系统业务与回归基线

- [业务需求规范](docs/spec.md)：业务规则、字段约束、验收场景和待确认决策。
- [旧系统基线](docs/baseline/README.md)：启动方式、页面流程、回归用例及浏览器检查清单。
- [数据结构](docs/baseline/data-model.md) 与 [行为保留/缺陷纠正](docs/baseline/decisions.md)。
- [实测记录](docs/baseline/verification.md)：17 条自动用例通过；原始 WAR 受历史标签库依赖阻碍，完整 JSP/浏览器验证待完成。

使用完整 JDK 8 运行原技术栈回归：

```bash
bash legacy/scripts/legacy-baseline.sh
```

脚本使用独立的 `legacy/baseline/pom.xml`，不改动原应用构建及业务代码。测试重建 H2 内存数据库；MockMvc 不渲染 JSP，不能代替完整页面验收。

### 文档索引

| 文档 | 内容 |
| --- | --- |
| [技术方案](docs/plan.md) | 模块边界、REST 契约、数据库方案、测试策略 |
| [任务列表](docs/tasks.md) | T001～T499 的执行协议、进度与执行记录 |
| [开发指南](docs/development/README.md) | 本地开发、调试与重置 |
| [环境验证报告](docs/development/environment-verification.md) | 工具链与数据库实测结果 |
| [全栈首次启动验收](docs/development/full-stack-verification.md) | 从干净检出到一次模拟下单的可复现证据 |
| [本地部署](docs/operations/local-deployment.md) | 本地开发环境启停与可分发部署包的使用 |
| [运行手册](docs/operations/runbook.md) | 迁移、切换与回滚步骤 |
| [发布清单](docs/operations/release-checklist.md) | 发布前必填项与验收门槛 |
| [现代化验收报告](docs/verification/modernization.md) | 逐条核对业务规则与验收场景的结果 |

### 已知限制

- 历史数据导入与对账工具只生成可审计的 SQL 与报告，不代为执行写入；
- 发布镜像的运行验收、隔离环境的容量测量与生产部署尚未验证，需要授权环境；
- 生产部署、TLS 与备份恢复属发布阶段，需要单独授权与目标服务器。
