# 发布后观察与交接

> T447：按已确认窗口观察错误率、延迟、连接池、资源、订单与库存冲突及幂等指标，核对备份任务
> 与告警接收，记录异常处置、是否回滚以及运维交接（plan §9、§11）。

## 1. 当前状态

| 项目 | 状态 |
| --- | --- |
| 生产发布 | 未执行（见 [生产发布记录](production-release.md)） |
| 观察窗口 | 未开始 |
| 备份调度 | 已在 `.github/workflows/backup.yml` 配置定时任务，但未在生产环境验证 |

## 2. 观察项与判据

| 观察项 | 来源 | 判据 |
| --- | --- | --- |
| 5xx 比例 | HTTP 访问日志与 `http.server.requests` 指标 | 不高于发布前基线；出现持续上升即回滚评估 |
| 下单失败率 | `jpetstore.orders{outcome="failed"}` | 与发布前基线比较 |
| 库存冲突 | `jpetstore.orders{outcome="stock-conflict"}` | 与补货节奏对齐；突然升高说明库存被超卖消耗 |
| 幂等重放 | `jpetstore.orders{outcome="replay"}` | 升高说明客户端在重复提交，需要排查前端 |
| 锁失败 | `jpetstore.orders{outcome="lock-failure"}` | 连续出现即排查热点 SKU 与锁顺序 |
| 延迟 | `http.server.requests` 的 p95/p99 | 不高于发布前基线 |
| 连接池 | Hikari 的 active/pending/max | pending 长期接近 max 即排查慢查询与锁等待 |
| 资源 | 容器内存与 CPU | 无持续增长趋势；接近限制即评估扩容 |
| 备份 | 定时任务结果与产物校验值 | 每日备份成功且校验通过；失败立即人工介入 |
| 告警接收 | 告警通道与值班确认 | 明确到人，并确认演练过一次 |

## 3. 异常处置

1. 出现回滚判据时，按[发布清单](../operations/release-checklist.md)执行回滚：
   `python3 scripts/rollback-release.py --plan rollback-plan.json --confirm-target <target> --execute`；
2. 兼容回退失败时停止自动操作，改走前向修复（不要用旧库覆盖已有新订单的库）；
3. 每次处置都记录：现象、指标、时间、处置动作、结果、是否回滚。

## 4. 运维交接

| 交接项 | 内容 |
| --- | --- |
| 服务构成 | 单主机 Compose：同源代理 + 后端容器；数据库为外部实例 |
| 日常操作 | 日志查看、指标查询、备份核对、证书续期、容量关注点 |
| 故障手册 | [运行手册](../operations/runbook.md) 与本文件的异常处置 |
| 关键脚本 | `deploy.py`、`migrate-release.py`、`backup-restore.py`、`post-deploy-smoke.py`、`rollback-release.py` |
| 未完成项 | 生产发布、预发布演练、恢复演练与发布镜像验收仍未执行（浏览器端到端验收已执行，见 [现代化验收报告](modernization.md)） |

## 5. 结论

观察项、判据与处置流程已定义；**实际观察窗口未开始**（尚无生产发布），因此发布后稳定性结论
仍未验证。
