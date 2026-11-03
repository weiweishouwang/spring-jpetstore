# 可恢复性演练记录

> T436：对隔离 MySQL 做“备份 → 恢复到另一隔离实例 → 只读对账”，记录耗时、校验、订单归属与
> 金额、库存、时间精度与不完整数据（plan §9、§11）。

## 1. 状态

| 项目 | 结果 |
| --- | --- |
| 执行时间 | 2026-11-03 |
| 目标 | **未执行**：需要两个隔离 MySQL 实例（本次环境只有开发实例，无隔离实例可用） |
| 工具 | `scripts/backup-restore.py`（一致性备份 + 校验值 + 受限权限 + 隔离目标强制校验） |
| 自动化 | `scripts/tests/test_backup_restore.py` → 7/7 通过（备份一致性、清单校验、权限、篡改检测、误覆盖拒绝） |

## 2. 演练步骤（待执行）

```bash
# 1) 一致性备份
python3 scripts/backup-restore.py \
  --target "jdbc:mysql://restore-source.internal:3306/jpetstore_source" \
  --output-dir target/backup

# 2) 恢复到隔离实例（目标必须带隔离标记，否则工具直接拒绝）
python3 scripts/backup-restore.py \
  --target "jdbc:mysql://restore-drill.internal:3306/jpetstore_restore" \
  --restore-from target/backup/backup.sql

# 3) 只读对账
python3 scripts/audit-migration.py \
  --legacy legacy/export/legacy-data.json \
  --target target/restore-snapshot.json
```

## 3. 需要记录的结果

| 项目 | 期望 | 实际 |
| --- | --- | --- |
| 备份耗时 | 与数据量成比例 | 未执行 |
| 恢复耗时 | 在 RTO（60 分钟）之内 | 未执行 |
| 备份校验值 | 与清单一致 | 未执行 |
| 行数 | 与源库逐表一致 | 未执行 |
| 订单归属 | 每张订单的账户关联一致 | 未执行 |
| 订单金额 | 与源库一致（含两位小数精度） | 未执行 |
| 库存 | 与源库一致且不为负 | 未执行 |
| 时间精度 | `DATETIME(6)` 微秒精度保留，不被截断或补造 | 未执行 |
| 不完整数据 | 备份工具报告缺失与跳过项，不静默丢弃 | 未执行 |

## 4. 结论

恢复工具本身已通过自动化测试（一致性备份、完整性清单、受限权限、篡改检测、禁止误覆盖现役库）；
**隔离环境的端到端恢复演练尚未执行**，因此恢复时间（RTO）与数据完整性结论仍未验证。
