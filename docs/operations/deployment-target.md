# 部署目标与发布参数

> T426：确定预发布与生产服务器、平台、镜像仓库、域名与 TLS、数据库部署方式、网络与 runner
> 可达性、secret 来源、备份存储与 RPO/RTO、观察窗口以及发布权限（plan §9、§11）。

## 1. 状态

| 项目 | 结论 |
| --- | --- |
| 目标服务器 | **待确认**——需要用户提供预发布与生产主机（或授权由运维准备） |
| 平台 | 单主机 Docker Compose（已按此编写 `infra/compose.prod.yml`） |
| 引入 Kubernetes | **不引入**：除非另行确认，编排保持单主机 Compose，避免引入未验证的复杂度 |
| 镜像仓库 | GHCR（`ghcr.io/<owner>/jpetstore-backend`），只接受不可变摘要 |
| 域名与 TLS | 站点域名待确认；证书与私钥以文件引用交给同源代理（`infra/nginx.conf`） |
| 数据库 | 外部 MySQL 8.4.13 实例，应用与 Compose 都不再启动第二个数据库 |
| 网络与 runner | 部署 runner 需要能访问目标主机与数据库；备份 runner 需要可达私有数据库 |
| secret 来源 | 受限 secret 引用（见 `infra/.env.prod.example`），值不进仓库、不进镜像、不进日志 |
| 备份存储 | 受限备份目标引用；保留 30 天 |
| RPO / RTO | RPO 15 分钟（每日定时备份 + 迁移前即时备份），RTO 60 分钟 |
| 观察窗口 | 发布后至少 30 分钟只读观察，再决定是否进入下一阶段 |
| 发布权限 | 每次生产发布都需要单独授权；流水线只允许手动触发且要求目标确认 |

## 2. 参数清单（模板）

```bash
# 目标
export DEPLOY_ENVIRONMENT=staging          # staging | production
export DEPLOY_TARGET=prod-1.example.internal
export DEPLOY_IMAGE=ghcr.io/<owner>/jpetstore-backend@sha256:<64位摘要>
export DEPLOY_COMMIT=<发布提交>

# 迁移
export MIGRATION_TARGET=jdbc:mysql://db.internal:3306/jpetstore_prod?connectionTimeZone=UTC
export MIGRATION_USER_REF=secret://jpetstore/prod/migration-user

# 冒烟
export SMOKE_TARGET_URL=https://shop.example.com
```

## 3. 已确定的部分

- 部署编排：备份 → 迁移 → 停旧起新 → readiness → 只读冒烟，顺序固定（`scripts/deploy.py`）；
- 回滚：默认 dry-run，只允许回到兼容的镜像与 schema 组合；存在新订单且 schema 不兼容时禁止自动回退
  （`scripts/rollback-release.py`）；
- 迁移：固定 Flyway 版本、baseline 默认关闭、迁移内容必须与发布提交一致（`scripts/migrate-release.py`）；
- 备份：一致性快照 + 校验值 + 受限权限，恢复只能到隔离目标（`scripts/backup-restore.py`）。

## 4. 待确认（阻塞项）

| 项目 | 需要谁确认 | 影响 |
| --- | --- | --- |
| 预发布与生产主机地址与登录方式 | 用户 / 运维 | T442、T444、T445 |
| 生产域名与证书来源 | 用户 / 运维 | 同上 |
| 数据库实例位置与受限账号 | 用户 / 运维 | 同上 |
| 备份目标与保留策略 | 用户 / 运维 | T436、T446 |
| 生产发布授权 | 用户 | T445 |

未确认前，所有生产相关步骤都停留在“待执行”状态，不会自行发起。
