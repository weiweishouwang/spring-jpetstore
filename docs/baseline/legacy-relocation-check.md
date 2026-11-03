# 旧文件迁移校验（T160）

| 属性 | 值 |
| --- | --- |
| 执行日期 | 2026-11-03，Asia/Shanghai |
| 校验对象 | T027～T159 的 133 个单文件归档（`legacy-manifest.json`，源提交 `3b4799e`/业务基线 `f1d6049`） |
| 执行环境 | macOS 15.3.1 arm64；完整 Oracle JDK 8u351；Maven 3.8.7；离线缓存 `/private/tmp/jpetstore-baseline-m2` |

## 1. 迁移执行与摘要核验

- 每个文件执行独立的 `git mv source destination`，按 manifest 的 source→destination 映射逐项进行；未执行目录级批量移动。
- 预检：133 个源文件全部存在，工作区 SHA256 与 manifest 记录一致（0 缺失、0 不符，无未记录的用户改动）。
- 迁移：133/133 成功。
- 复核：目标位置 SHA256 与 manifest 全部一致；源位置无残留文件。
- Git 重命名检测：133 个 `R100`（相似度 100%，内容未发生任何变化）。
- 文件系统清理：删除迁移后的空目录；`baseline/target/`（已被忽略的历史 surefire 报告）随归档移至 `legacy/baseline/target/`，不改变任何已跟踪文件。

## 2. 归档后路径一致性实测

相对路径关系在 `legacy/` 内保持自洽，无需修改旧构建文件：

- `legacy/baseline/pom.xml` 的 `sourceDirectory=../src/main/java` 解析为 `legacy/src/main/java`（存在）。
- `legacy/scripts/legacy-baseline.sh` 的 `task_root` 解析为 `legacy/`，`mvn -f baseline/pom.xml` 解析为 `legacy/baseline/pom.xml`（存在）；脚本的 JDK8 `tools.jar` 探测逻辑生效。

实测命令与结果：

```bash
bash legacy/scripts/legacy-baseline.sh -o -Dmaven.repo.local=/private/tmp/jpetstore-baseline-m2
```

| 测试类 | 用例数 | 失败 | 错误 | 跳过 |
| --- | --- | --- | --- | --- |
| LegacyWebRegressionTest | 9 | 0 | 0 | 0 |
| LegacyServiceRegressionTest | 4 | 0 | 0 | 0 |
| LegacyKnownDefectTest | 4 | 0 | 0 | 0 |
| 合计 | 17 | 0 | 0 | 0 |

`BUILD SUCCESS`（2026-11-03 14:14:08 +08:00，Total time 8.8s）。报告位于 `legacy/baseline/target/surefire-reports/`（可再生构建产物）。SLF4J 多绑定与 Log4j 初始化警告与归档前一致，未影响结果。

## 3. 边界断言

- `git ls-files` 中 `src/`、`baseline/` 旧路径条目为 0；工作区旧目录已移除。
- `backend/` 尚不存在（Phase 3 创建），无旧源码/测试混入现代构建的可能；现代根聚合 POM 同样尚未创建（T161），当前不存在会扫描 legacy 的构建。
- 133 项任务均只改变各自单一逻辑文件路径，符合执行协议第 1 条。

## 4. 已知阻碍（与迁移前一致，非本次引入）

- 原始 `pom.xml` 的完整 WAR 构建仍被 `jp.sf.amateras.functions:functions:1.1.2` 依赖解析阻碍（历史 HTTP 仓库被 Maven 3.8 拦截），JSP 编译/渲染与浏览器回归仍未执行；详见 [verification.md](verification.md) §4。该阻碍属于旧基线验证状态，不因归档变化。

## 5. 发现的文档缺口（不在本任务内修改）

以下文件仍引用归档前路径，按执行协议需以独立单文件 DOC 任务更新：

- `README.md`：`bash scripts/legacy-baseline.sh` 应为 `bash legacy/scripts/legacy-baseline.sh`。
- `docs/baseline/README.md`：交付索引表引用 `baseline/pom.xml`、`scripts/legacy-baseline.sh`、`src/test/...` 旧路径。
- `docs/spec.md` §12.2 引用 `[旧系统基线](baseline/README.md)` 与脚本路径，需同步核对。

建议在 Phase 3 开始前补充对应 DOC 任务，避免后续会话按旧路径执行失败。
