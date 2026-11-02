# 基线验证记录

执行日期：2026-11-02，Asia/Shanghai。原始提交：`f1d6049`。

## 1. 环境

| 项目 | 实际值 |
| --- | --- |
| 操作系统 | macOS 15.3.1，arm64 |
| Maven | 3.8.7 |
| 默认 Java | Oracle JDK 21.0.7 |
| 实际测试 Java | 完整 Oracle JDK 8u351，x86_64；Surefire 报告确认 `java.version=1.8.0_351` |
| 测试缓存 | `/private/tmp/jpetstore-baseline-m2`，不要求读者使用同一路径 |
| 数据 | 原始 H2 建表及演示数据；每条测试重建，未额外补业务数据 |

## 2. 已执行的命令与结果

### 2.1 原始构建

```bash
mvn -B -Dmaven.repo.local=/private/tmp/jpetstore-baseline-m2 \
  -Dmaven.wagon.http.connectionTimeout=10000 \
  -Dmaven.wagon.http.readTimeout=10000 test
```

结果：失败，停在依赖解析，未进入应用编译及测试。关键错误：

```text
Failed to collect dependencies at jp.sf.amateras.functions:functions:jar:1.1.2
Blocked mirror for repositories: ... amateras (http://amateras.sourceforge.jp/mvn/ ...)
```

原始 POM 同时包含 HTTP Central 地址；Maven 使用可用的 HTTPS Central 下载了其他依赖，但没有取得该历史标签库。本结果不能证明恢复 HTTP 仓库后即可启动成功。

### 2.2 默认 Java 编译目标检查

```bash
javac -source 6 -target 6 -d /private/tmp/jpetstore-java21-probe \
  src/main/java/ik/am/jpetstore/domain/model/Sequence.java
```

结果：JDK 21 拒绝 source/target 6。测试入口因此选择完整 JDK 8。macOS 的 Java 8 浏览器插件 JRE 不含 `tools.jar`，不能代替完整 JDK；脚本已处理该选择差异。

### 2.3 独立测试构建

首次联网获取依赖后，执行离线复跑：

```bash
bash scripts/legacy-baseline.sh -o \
  -Dmaven.repo.local=/private/tmp/jpetstore-baseline-m2
```

2026-11-02 16:01:28 +08:00 完成，结果：

| 测试类 | 用例数 | 失败 | 错误 | 跳过 |
| --- | --- | --- | --- | --- |
| LegacyWebRegressionTest | 9 | 0 | 0 | 0 |
| LegacyServiceRegressionTest | 4 | 0 | 0 | 0 |
| LegacyKnownDefectTest | 4 | 0 | 0 | 0 |
| 合计 | 17 | 0 | 0 | 0 |

```text
Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

报告生成于 `baseline/target/surefire-reports/`，属于可再生构建产物，不纳入版本控制。当前历史依赖还会打印 SLF4J 多绑定及 Log4j 初始化警告，未导致测试失败；独立测试依赖集合不能视为原 WAR 完整依赖快照。

## 3. 已验证的边界

- 编译全部 39 个原始 Java 源文件，没有替换领域代码。
- 加载原始应用、安全及 MVC XML；测试将它们放在同一个测试 WebApplicationContext 中，并非完整复刻真实容器的父子上下文。
- MockMvc 请求经过真实 Spring Security 过滤链，登录使用实际演示/注册凭据。
- 商品查询、账户写入、订单持久化通过原始 MyBatis Mapper 访问 H2。
- 注册最后一步失败时，账户及偏好写入回滚。
- 下单第二条明细写入失败时，库存、订单头、状态、明细与序列更新全部回滚。
- 两个用户的订单列表与详情访问隔离。
- 已复现 D02、D04a、D05、D06a；特征测试通过表示缺陷仍存在。

## 4. 尚未验证的边界

| 项目 | 当前状态与原因 |
| --- | --- |
| 原始 WAR 完整构建、Tomcat 运行 | 被历史 JSP 标签库依赖阻碍；未取得完整页面运行证据 |
| JSP 编译、渲染、截图、真实页面文案 | MockMvc 不执行 JSP 渲染；按 README 浏览器清单待执行 |
| 浏览器 Cookie、退出/超时、成功下单后的购物车清理 | 需要真实 HTTP 会话及浏览器验证；不由本次作用域测试推断 |
| 非法字段的全部组合、所有不存在资源 | 只覆盖选定关键场景；完整边界矩阵待扩充 |
| 并发库存竞争、订单号分配和重复请求 | 当前仅单线程特征及事务回归；没有负载/并发实测证据 |
| 真实支付及物流 | 当前没有实现，非本次验证范围 |

## 5. 基线结论

可执行的业务层/MVC回归基线已建立：13 条正常回归、4 条已知缺陷特征测试。原系统完整页面启动和浏览器回归仍未完成。后续技术迁移可以使用本套用例对比业务效果，但上线前仍需补齐完整容器、JSP 和浏览器验证。
