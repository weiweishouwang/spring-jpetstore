# 全栈开发环境首次启动验收

> T406：记录开发环境从干净检出到“一次模拟下单成功”的可复现证据（plan §9、§11）。
> 记录时间：2026-11-03；执行环境：macOS（Darwin 24.3.0）、Temurin 21.0.13+9、Maven Wrapper 3.9.11、
> Node 24.22.2 / npm 11.19.1、Docker 29.5.3、MySQL 8.4.13（固定镜像摘要）。

## 1. 结论

| 项目 | 结果 | 证据 |
| --- | --- | --- |
| 后端编译与全量测试 | 通过 | `./mvnw verify` → 362 个测试，0 失败 0 错误（含 7 个并发集成验收） |
| 前端构建与测试 | 通过 | `npm run build` → 91 个模块；`npm test` → 141 个测试通过；`vue-tsc --noEmit` 与 `eslint .` 均退出 0 |
| 发布形态打包 | 通过 | `./mvnw -pl backend -Prelease -DskipTests package` → JAR 内含 `BOOT-INF/classes/static/index.html` 与 `assets/` |
| 打包应用真实进程验收 | 通过 | `PackagedApplicationIT` → 4/4：前端入口、API、未知 API 非 HTML、无旧产物残留 |
| 开发工具自检 | 通过 | `python3 -m unittest discover -s scripts/tests` → 40 个测试通过（含新增全栈冒烟工具） |

未验证项与阻塞见第 6 节。

## 2. 从干净检出到可运行

```bash
# 1) 依赖与工具（首次）
./mvnw -q verify            # 后端：编译 + 单元测试 + 集成测试
cd frontend && npm ci       # 前端：按 lockfile 安装

# 2) 数据库（开发用固定版本镜像）
docker compose -f infra/compose.dev.yml up -d mysql

# 3) 后端
cd backend && ../mvnw spring-boot:run

# 4) 前端（开发服务器，/api 代理到 127.0.0.1:8080）
cd frontend && npm run dev
```

后端启动时 Flyway 自动执行 V1～V7 迁移（账户与目录、订单与库存、购物车与确认快照、幂等记录、
校验约束与索引、游客购物车唯一约束、确认快照商品文本快照）。

## 3. 首次启动的可观察行为

| 步骤 | 期望 | 实际 |
| --- | --- | --- |
| 打开 `http://127.0.0.1:5173/` | 分类入口页面 | 通过（首页渲染五个分类） |
| 打开 `/cart` | 购物车页 | 通过（游客会话购物车，空车有明确提示） |
| `GET /api/categories` | 分类 JSON | 通过（开发服务器经 Vite 代理到后端，同源） |
| `GET /api/auth/csrf` | 返回 token 与 header 名称 | 通过 |
| 登录 `POST /api/auth/session` | 200 且会话标识轮换 | 通过（CSRF token 需重新获取） |
| 加购 `POST /api/cart/items` | 200 且版本递增 | 通过（带 expectedVersion） |
| 结算 `POST /api/checkouts` | 201 确认快照 | 通过（15 分钟有效，服务端价格） |
| 下单 `POST /api/orders` | 201 + Location | 通过（必须带 `Idempotency-Key`） |
| 重复提交同一幂等键 | 原结果重放 | 通过（响应带 `Idempotency-Replayed: true`，不新建订单） |

## 4. 热更新与停止重启（Q08）

- 前端：Vite 开发服务器热更新只影响前端资源，不触碰会话与购物车（购物车在数据库里，按会话绑定
  哈希或账号定位）。
- 后端：停止后重启，**登录会话**需要重新登录（会话在内存里）；**购物车**不丢失——账号车持久化在
  数据库，退出登录也保留（Q08）。
- 验证方式：`PackagedApplicationIT` 每次都从空库启动真实进程；`OrderQueryServiceTest` 与
  `CartApplicationServiceTest` 覆盖“重启后按账号恢复”。

## 5. 失败诊断与清理

`scripts/dev-smoke.py` 负责一次性全栈冒烟：

- 只用有界超时与轮询，不用固定 sleep 掩盖启动慢；
- 失败时保留脱敏诊断（口令、token、假卡号与 Cookie 一律不落盘）；
- 只停止本脚本自己启动的进程，不触碰用户已有服务与数据库卷。

```bash
python3 scripts/dev-smoke.py --timeout 120
```

## 6. 未验证项与阻塞

| 项目 | 状态 | 说明 |
| --- | --- | --- |
| 真实浏览器端到端验收（Playwright） | 未执行 | `frontend/playwright.config.ts` 与 Compose 编排已就绪（T402/T403），但当前环境没有可用的浏览器运行时，E2E 用例本身属 Phase 10 |
| 生产形态部署（TLS、备份恢复、晋级） | 未执行 | 属 Phase 11，需要目标服务器与生产授权 |
| 迁移数据回填 | 未执行 | 属 Phase 10，需真实历史数据样本 |
| `npm ci` 在清空 npm 缓存下的复跑 | 未执行 | 本阶段依赖可获取性由首次安装与发布构建证明；旧缓存场景针对 Maven 已单独验证 |

## 7. 补记（2026-11-04）

本文件记录的是 2026-11-03 的首次全栈验收快照，上面的计数与“未执行”都是当时的事实。此后：

- **真实浏览器端到端验收已执行**：`frontend/tests/e2e/` 三个 spec 共 14 个用例，在同版本 MySQL
  8.4.13 与真实 Chrome 上 14/14 通过；CI 的 browser end to end 作业执行同一批用例并通过。验收
  过程中暴露并修掉了两处真实缺陷（游客加购与提交订单在整页导航时丢失写请求）与五处接线缺口。
  证据见[现代化验收报告](../verification/modernization.md)。
- **测试数量随之变化**：后端 374、前端 151、脚本 129（逐项命令与结果见[任务列表](../tasks.md)
  的执行记录）。
- **本地开发与部署有了统一入口**：`make start` / `make stop` / `make package`，说明见
  [本地部署](../operations/local-deployment.md)。
