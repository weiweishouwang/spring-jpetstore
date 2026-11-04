# 运行手册：迁移、切换与回滚

> T422：面向发布候选的运行手册。每条命令都写明前提、预期结果与恢复步骤；生产操作需要单独
> 授权（plan §7.4、§9、Q12）。

## 0. 前提与角色

| 项目 | 要求 |
| --- | --- |
| 数据 | 现代库与旧库同版本 MySQL 8.4.13；导入目标库必须是临时/预发库 |
| 权限 | 导入脚本只生成 SQL，不代为执行；执行人需显式确认目标 |
| 备份 | 切换前必须有一份可恢复的旧库备份，并验证过恢复 |
| 停写 | 切换窗口内旧系统停止写库存与订单；**新旧系统不得同时写库存** |

## 1. 迁移前

```bash
# 1.1 生成导入计划（dry-run，只输出 SQL 与报告）
python3 scripts/import-legacy-data.py \
  --source legacy/export/legacy-data.json \
  --target "jdbc:mysql://127.0.0.1:3306/jpetstore_dev" \
  --confirm "jdbc:mysql://127.0.0.1:3306/jpetstore_dev" \
  --dry-run --output target/legacy-import.sql
```

预期：退出码 0，报告中 `conflicts` 为空则说明没有用户名冲突；`passwordResets` 列出所有需要用户
重设口令的账户（旧哈希无法安全转换成 BCrypt，工具使用统一占位哈希）。

恢复：dry-run 不写任何数据，无需恢复。

## 2. 最终导入

```bash
# 2.1 停旧写
#     由旧系统负责人执行“停止写库存与订单”，并记录时间点。

# 2.2 生成并执行导入脚本（目标必须与 --confirm 完全一致）
python3 scripts/import-legacy-data.py \
  --source legacy/export/legacy-data.json \
  --target "jdbc:mysql://127.0.0.1:3306/jpetstore_dev" \
  --confirm "jdbc:mysql://127.0.0.1:3306/jpetstore_dev" \
  --output target/legacy-import.sql
mysql --user=<app> --password jpetstore_dev < target/legacy-import.sql
```

预期：导入脚本在单个事务里执行；任一行失败则整体回滚（旧数据不完整时宁可全部不导入）。

恢复：事务回滚即可；必要时从备份恢复。

## 3. 对账

```bash
python3 scripts/audit-migration.py \
  --legacy legacy/export/legacy-data.json \
  --target legacy/export/target-snapshot.json
```

预期：退出码 0（无差异）。逐表核对行数、主键、账户关联、订单归属、金额、库存与最大主键。

恢复：工具是只读的，不做任何修复；出现差异时回到第 2 步重新导入或人工处理。

## 4. 启动现代系统并验证

```bash
# 4.1 启动（Flyway 自动迁移到最新版本）
cd backend && ../mvnw spring-boot:run

# 4.2 就绪与冒烟
curl -fsS http://127.0.0.1:8080/actuator/health
python3 scripts/dev-smoke.py --timeout 120
```

预期：健康探针 `UP`；冒烟覆盖数据库、后端与前端、目录 API 与 CSRF bootstrap。

恢复：停止进程即可，数据不受影响。

## 5. 切流

```bash
# 5.1 切流前再跑一次对账与冒烟
# 5.2 由运维把反向代理指向现代后端；旧系统保持只读
```

预期：新订单只写入现代库。

## 6. 回滚

**切换后尚未产生新订单时**：把反向代理切回旧系统，旧库数据未变，直接恢复服务。

```bash
# 6.1 停止现代后端（保持旧系统只读状态）
# 6.2 切回旧系统入口
# 6.3 核对旧库行数与切换前一致
```

**已经产生新订单之后**：不能简单切回旧系统（旧库没有新订单）。此时按前向修复处理：

1. 保持现代系统对外服务，不切回；
2. 用 `scripts/audit-migration.py` 对比旧库与现代库，导出差异订单；
3. 人工确认这些订单的归属与金额后，按业务决定补偿或冲正；
4. 记录在发布记录里，不允许两边同时写库存。

## 7. 定位与告警

| 现象 | 排查 | 阈值参考 |
| --- | --- | --- |
| 下单大量 409 | 购物车版本冲突或确认快照过期（`CART_VERSION_CONFLICT` / `CHECKOUT_STALE`） | 单分钟超过登录用户的 5% 需关注 |
| 下单 503 | 锁等待超时（`ORDER_RETRYABLE`），客户端应复用同一幂等键重试 | 连续 3 分钟出现即排查锁与库存热点 |
| 库存冲突升高 | `OUT_OF_STOCK` 计数上升，通常是热销 SKU 库存不足 | 与补货节奏对齐 |
| 5xx 升高 | `jpetstore.orders{outcome="failed"}` 与 `outcome="lock-failure"` 计数 | 见 [性能记录](../verification/performance.md) |
| 请求关联排查 | 日志里的 `requestId` 与响应头 `X-Request-Id` 一致，可直接检索 | — |

## 8. 备份与恢复

- 备份：数据库层用一致性快照（`mysqldump --single-transaction` 或等价方式），并保留校验和；
- 恢复演练：定期在临时库演练“恢复 → 迁移校验 → 冒烟”，演练结果记入发布记录；
- 恢复后必须重新执行第 3 步对账与第 4 步冒烟，再考虑切流。
