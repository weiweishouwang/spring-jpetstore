# 研发与系统技术验收（生产发布前）

> T425：逐条核对业务规则、非功能要求、验收场景与并发不变量，记录环境、结果、证据与未验证项。
> 本报告是发布候选门槛，不代替 Phase 11 的实际服务器部署与发布后观察（plan §10、§11）。

## 1. 执行环境

| 项目 | 内容 |
| --- | --- |
| 时间 | 2026-11-03 |
| 机器 | macOS（Darwin 24.3.0）、Temurin 21.0.13+9、Maven Wrapper 3.9.11、Node 24.22.2 / npm 11.19.1 |
| 数据库 | MySQL 8.4.13（固定镜像摘要，Testcontainers） |
| 后端 | `./mvnw verify` → 362 个测试通过（0 失败 0 错误），含 7 个并发集成验收 |
| 前端 | `npm test` → 141 个测试通过；`vue-tsc --noEmit` 与 `eslint .` 退出 0；`npm run build` 成功 |
| 脚本 | `python3 -m unittest discover -s scripts/tests` → 69 个测试通过（3 个因缺发布镜像显式跳过） |
| 打包 | `./mvnw -pl backend -Prelease -DskipTests package` → JAR 内含前端入口与静态资源 |

## 2. 业务规则核对（spec §4）

| 规则族 | 覆盖位置 | 结论 |
| --- | --- | --- |
| ACC-01～08 账户与认证 | `AccountRegistrationServiceTest`、`AccountProfileServiceTest`、`AccountUserDetailsServiceTest`、`SecurityConfigurationTest`、`AccountControllerTest` | 通过 |
| CAT-01～08 商品目录 | `CatalogQueryServiceTest`、`CatalogMapperTest`、`CatalogControllerTest` | 通过 |
| CRT-01～11 会话购物车 | `CartApplicationServiceTest`、`SessionCartBindingTest`、`CartMapperTest`、`CartControllerTest` | 通过 |
| ORD-01～20 确认与下单 | `CheckoutApplicationServiceTest`、`OrderTransactionServiceTest`、`IdempotentOrderServiceTest`、`OrderQueryServiceTest`、`OrderControllerTest`、`CheckoutControllerTest` | 通过 |
| INV-01～03 库存 | `InventoryServiceTest`、`InventoryMapperTest` | 通过 |
| PER-01～04 偏好 | `AccountProfileServiceTest`、`Recommendations.vue` 测试 | 通过 |
| D01/D02/D04a/D05/D07/D10/D11/D12 缺陷纠正 | 对应模块的测试与 [全栈验收](docs/development/full-stack-verification.md) | 通过 |

## 3. 非功能要求核对（spec §5）

| 要求 | 证据 | 结论 |
| --- | --- | --- |
| NFR-01 事务一致性 | `OrderTransactionServiceTest`（失败整体回滚）、`AccountRegistrationServiceTest` | 通过 |
| NFR-02 授权与归属 | `OrderQueryServiceTest`（他人订单不可见）、`SpaRoutingConfigurationTest`、前端路由守卫测试 | 通过 |
| NFR-03 幂等 | `IdempotentOrderServiceTest`、`OrderConcurrencyIT` | 通过 |
| NFR-04 凭据保护 | `DefaultAccountAssemblerTest`、`StructuredLoggingIT`、`RequestIdFilterTest` | 通过 |
| NFR-05 错误与日志 | `ProblemDetailsAdvice` 相关测试、`StructuredLoggingIT`、`RequestIdFilterTest` | 通过 |
| NFR-06 迁移安全 | `UpgradeRestoreIT`、`test_import_legacy_data.py`、`test_audit_migration.py` | 通过 |
| NFR-07 可观测 | `OrderMetricsTest`、`OrderMetricsWiringTest`、`production-observability` 相关配置 | 通过 |

## 4. 验收场景核对（spec §6）

| 场景 | 证据 | 结论 |
| --- | --- | --- |
| AT-01～18 业务验收 | 各模块测试与前端页面测试；浏览器级验收见下 | 后端与组件级通过 |
| AT-R01 库存 1 两人抢购 | `OrderConcurrencyIT.onlyOneOfTwoBuyersWinsWhenTheStockIsOne` | 通过 |
| AT-R02 幂等重放 | `OrderConcurrencyIT`、`IdempotentOrderServiceTest` | 通过 |
| AT-R03 失败保留购物车 | `OrderTransactionServiceTest.insufficientStockRollsBackTheWholeTransaction` | 通过 |
| AT-R04 历史金额不随现价变 | `OrderQueryServiceTest.theTradedAmountNeverFollowsTheCurrentPrice` | 通过 |
| AT-R05 无效提交被拒 | `CheckoutApplicationServiceTest`、`InventoryServiceTest` | 通过 |
| AT-R06 变价要求重新确认 | `OrderTransactionServiceTest.aPriceChangeIsRejectedAndAsksForAReconfirmation` | 通过 |
| AT-R07 购物车版本冲突 | `CartApplicationServiceTest.aStaleExpectedVersionIsRejected`、`CartControllerTest` | 通过 |
| AT-R08 并发订单号唯一 | `OrderConcurrencyIT.concurrentOrdersGetDistinctOrderNumbers` | 通过 |
| AT-R09 会话与 CSRF | `SecurityConfigurationTest`、`DefaultIdentityAssemblerTest`、前端 CSRF 状态测试 | 通过 |

## 5. 未验证项（明确阻塞，不计为通过）

| 项目 | 状态 | 原因 |
| --- | --- | --- |
| 真实浏览器端到端验收（Playwright 三个 spec） | 未执行 | 当前环境没有可用的浏览器运行时；用例已写好（`frontend/tests/e2e/`），CI 工作流会在安装浏览器后执行 |
| 发布镜像运行验收 | 阻塞 | 本机没有 Buildpacks（pack），镜像未产出；测试以显式跳过记录该阻塞 |
| 隔离预发环境容量测量 | 未执行 | 见 [性能记录](docs/verification/performance.md) |
| 生产形态部署、TLS、备份恢复与发布后观察 | 未执行 | 属 Phase 11，需要目标服务器与生产授权 |
| 历史数据真实样本导入与对账 | 未执行 | 工具已就绪，只跑过合成数据；真实样本需脱敏导出 |
| 旧系统 JSP 与浏览器回归 | 沿用旧基线结论 | 见 [旧系统基线](docs/baseline/README.md)；旧 WAR 受历史标签库依赖阻碍 |

## 6. 结论

现代实现的需求覆盖、事务一致性、幂等、库存并发不变量与可观测性均有自动化证据支撑；**发布镜像
验收、真实浏览器端到端验收、隔离环境容量测量与生产部署仍未验证**，因此本报告不构成生产发布
授权。生产发布需在 Phase 11 完成上述未验证项并取得授权。
