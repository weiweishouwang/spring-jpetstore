# 发布清单与验收门槛

> T441：每次发布必须填写的记录项，以及部署、冒烟、回滚脚本与清单的对应关系
> （plan §9、§11）。

## 1. 必填项

| 项目 | 示例 | 校验方式 |
| --- | --- | --- |
| 发布提交 | `31e8f7c` | 必须与镜像内构建一致 |
| 测试报告 | 后端 `surefire-reports`、前端 `playwright-report`、脚本测试输出 | CI 门禁通过，报告随发布记录归档 |
| 镜像摘要 | `ghcr.io/<owner>/jpetstore-backend@sha256:<64位>` | 必须含 `@sha256:`；`scripts/deploy.py` 拒绝浮动标签 |
| 迁移版本与校验和 | Flyway 目标版本 + 每个 `V*.sql` 的 SHA-256 | `scripts/migrate-release.py` 比对发布清单，不一致即拒绝迁移 |
| secret 引用 | `secret://jpetstore/prod/app-password` | 只写引用，值由运行环境注入 |
| 备份校验 | 备份文件校验值、创建时间、大小 | `scripts/backup-restore.py` 生成清单，发布前验证 |
| 应用/schema 回退兼容性 | 可回退的上一镜像摘要与 schema 版本 | `scripts/rollback-release.py` 校验；不兼容即禁止自动回退 |
| 操作者与授权 | 工号 / 授权人 | 流水线要求 `confirm=RELEASE` 且使用受保护环境 |
| 窗口与观察目标 | 起止时间、观察指标与阈值 | 见 [发布后观察](docs/verification/release-observation.md) |

## 2. 脚本与清单的对应

| 步骤 | 命令 | 前置条件 | 失败处理 |
| --- | --- | --- | --- |
| 预演 | `python3 scripts/deploy.py --manifest release-manifest.json --confirm-target <target>` | 清单校验通过 | 只打印计划，不变更 |
| 备份 | `python3 scripts/backup-restore.py --target <db> --output-dir target/backup` | 受限备份凭据可用 | 备份失败即中止发布 |
| 迁移 | `python3 scripts/migrate-release.py --plan release-plan.json --confirm-target <db> --execute` | 备份已完成、迁移内容与提交一致 | 迁移失败即禁止发布，不启动新版本 |
| 启动 | `python3 scripts/deploy.py --manifest release-manifest.json --confirm-target <target> --execute` | 迁移成功 | 任一步失败返回非零，保留上一镜像引用 |
| 冒烟 | `python3 scripts/post-deploy-smoke.py --target https://<site>` | 新版本就绪 | 冒烟失败按回滚流程处理 |
| 回滚 | `python3 scripts/rollback-release.py --plan rollback-plan.json --confirm-target <target> --execute` | 兼容性校验通过 | 不兼容时停止，引导前向修复 |

## 3. 门槛

发布必须同时满足：

1. CI 门禁全绿（后端 verify、前端 lint/typecheck/test/build、契约校验、浏览器 E2E）；
2. 备份已完成并通过校验；
3. 迁移校验通过（内容、目标版本、checksum）；
4. 新版本 readiness 通过且只读冒烟全绿；
5. 发布观察窗口内错误率与延迟不劣化；
6. 回滚路径仍然可用（上一镜像与 schema 兼容，或已明确接受前向修复）。

任一项不满足即不发布。生产发布还需要单独授权，不因 CI 通过而自动进行。
