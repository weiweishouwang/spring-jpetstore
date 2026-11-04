# 预发布部署与失败回滚演练

> T444：在已确认的预发布主机按清单执行镜像拉取、secret 注入、备份、迁移、停旧起新、
> HTTPS/探针/只读冒烟与完整 E2E；并故意用不健康版本验证中止与兼容镜像回退（plan §9、§11）。

## 1. 状态

**待执行**：没有获授权的预发布主机（见 [部署目标](../operations/deployment-target.md) 的待确认
项）。本文件记录演练步骤与判定标准，实际结果在获得授权后补记。

## 2. 演练步骤

```bash
# 1) 预演：只打印计划，确认目标与顺序
python3 scripts/deploy.py --manifest release-manifest.json --confirm-target staging

# 2) 备份（迁移之前必须完成）
python3 scripts/backup-restore.py --target "$MIGRATION_TARGET" --output-dir target/backup

# 3) 迁移（内容与发布提交一致，baseline 关闭）
python3 scripts/migrate-release.py --plan release-plan.json --confirm-target "$MIGRATION_TARGET" --execute

# 4) 启动与探针
python3 scripts/deploy.py --manifest release-manifest.json --confirm-target staging --execute

# 5) 只读冒烟
python3 scripts/post-deploy-smoke.py --target https://staging.example.com

# 6) 完整 E2E（浏览器）
cd frontend && E2E_BASE_URL=https://staging.example.com npx playwright test
```

## 3. 失败与回退演练

| 场景 | 做法 | 期望结果 | 实际 |
| --- | --- | --- | --- |
| 镜像不健康 | 用一个 readiness 失败的版本发布 | 部署脚本在 readiness 步骤失败并返回非零，保留上一镜像引用，不切流 | 未执行 |
| 迁移失败 | 用不匹配的迁移内容发布 | 迁移工具拒绝迁移，发布中止，旧版本继续服务 | 未执行 |
| 兼容回退 | 用兼容镜像回退 | 停写 → 切回 → readiness → 只读冒烟全绿；新增订单不丢失 | 未执行 |
| 不兼容回退 | schema 已前进且存在新订单 | 回滚工具拒绝自动回退，输出前向修复指引 | 未执行 |
| 冒烟失败 | 指向未知 API 或未登录订单 | 冒烟返回非零并报告失败项 | 未执行 |

## 4. 结论

演练脚本与判定标准已就绪；**预发布部署与失败回滚演练尚未执行**，因此“停旧起新”“兼容回退”
与“新增订单不丢失”这几条生产关键结论仍未验证。
