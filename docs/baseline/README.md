# 旧系统基线

日期：2026-11-02。原始提交：`f1d6049`。业务需求见根目录 `spec.md`。

本阶段新增测试、运行入口及文档，不改动 `src/main` 或原始 `pom.xml`。原系统已知缺陷通过独立特征测试记录；测试通过不表示缺陷已经修复。

## 1. 交付索引

| 文件 | 用途 |
| --- | --- |
| `spec.md` | 业务规则、输入约束、待确认问题 |
| `docs/baseline/README.md` | 启动方式、验证范围、页面流程及回归清单 |
| `docs/baseline/data-model.md` | 表结构、关系、初始化数据与事务边界 |
| `docs/baseline/decisions.md` | 保留行为、缺陷纠正标准、需要业务决策的变化 |
| `docs/baseline/verification.md` | 本机实测结果及尚未验证的部分 |
| `baseline/pom.xml` | 独立的旧技术栈测试构建入口 |
| `scripts/legacy-baseline.sh` | 选择完整 JDK 8 并运行测试 |
| `src/test/java/ik/am/jpetstore/baseline/` | MVC、安全过滤器、数据库和事务回归用例 |

## 2. 原系统运行环境

| 项目 | 仓库配置/本机情况 |
| --- | --- |
| 编译目标 | Java 6；使用完整 JDK 8 可编译此目标，默认 JDK 21 不支持该目标 |
| 原始打包 | WAR；预期产物 `target/spring-jpetstore.war` |
| 容器 | Tomcat 7，Servlet 3.0 / JSP 2.2；配置了 `tomcat7-maven-plugin:2.1` |
| 框架 | Spring 3.2.3、Security 3.1.4、MyBatis 3.2.2 / MyBatis-Spring 1.2.0 |
| 数据库 | H2 1.3.156；`jdbc:h2:mem:spring-petstore;MODE=PostgreSQL` |
| 数据库账号 | 演示配置 `sa`，空密码 |
| 页面 | JSP；当前 MVC 使用 InternalResourceViewResolver，Tiles 配置已注释 |
| 本机工具 | macOS arm64、Maven 3.8.7、默认 JDK 21.0.7、另有 x86_64 完整 JDK 8u351 |
| 演示账号 | `j2ee/j2ee`、`ACID/ACID`，仅用于隔离演示环境 |

### 2.1 原始启动方式：命令已还原，完整页面启动尚未通过

在已准备好历史依赖的环境中，从仓库根目录执行：

```bash
export JAVA_HOME="/path/to/full-jdk8"
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
mvn package
mvn tomcat7:run
```

默认预期入口为 `http://localhost:8080/spring-jpetstore/`，目录页为 `/spring-jpetstore/catalog`。端口和上下文路径属于插件默认值推导，尚未通过浏览器实测确认；根 `index.jsp` 提供目录入口。

也可以把 WAR 部署到已有的兼容 Tomcat 容器，由 WAR 名称决定上下文路径。项目没有 Spring Boot 启动类，不能直接 `java -jar` 启动。

本次原始 `mvn test` 在依赖解析阶段失败：`jp.sf.amateras.functions:functions:1.1.2` 不在本地缓存中，原 POM 的 Amateras HTTP 仓库被 Maven 3.8 的 HTTP blocker 拦截。没有降低 Maven 的仓库安全限制，也没有替换历史 JSP 标签库。

后续完成浏览器基线前，需要取得可核验来源的历史标签库或单独批准修复该构建依赖，再重新验证完整 WAR。仅将 URL 改成 HTTPS 不保证历史仓库仍可访问。

### 2.2 可执行的业务回归入口

```bash
# macOS 自动寻找完整 JDK 8；其他环境可显式指定：
LEGACY_JAVA_HOME=/path/to/full-jdk8 bash scripts/legacy-baseline.sh

# 使用独立的临时 Maven 缓存：
bash scripts/legacy-baseline.sh -Dmaven.repo.local=/private/tmp/jpetstore-baseline-m2

# 分别执行正常回归和缺陷特征测试：
bash scripts/legacy-baseline.sh -Dtest=LegacyWebRegressionTest,LegacyServiceRegressionTest
bash scripts/legacy-baseline.sh -Dtest=LegacyKnownDefectTest

# 依赖齐备后可离线复跑：
bash scripts/legacy-baseline.sh -o -Dmaven.repo.local=/private/tmp/jpetstore-baseline-m2
```

测试入口编译原始 Java 源码，读取原始 XML、Mapper、建表及演示数据脚本；加载真实 Spring Security 过滤器及事务代理，不模拟数据库或认证结果。测试用 POM 只声明执行这些层所需的历史依赖，未包含不可用的 JSP 标签库，不等价于完整原始 WAR 的依赖树。

每条用例执行前，检查连接为 H2 内存数据库，重建全部表和演示数据，清理 MyBatis 二级缓存；每个 Web 场景使用独立会话。不要并行运行这些共享同一内存库的测试类。数据库重建是测试行为，不用于正式数据。

报告：`baseline/target/surefire-reports/`。MockMvc 验证路由、校验、重定向、模型和持久化结果，**不编译/渲染 JSP，不证明页面最终文案、布局或 JavaScript 正确**。

## 3. 页面与业务流程

以下路径均相对应用上下文。旧 Controller 大部分没有限定 HTTP 方法；“提交”仅说明页面当前表单的使用方式，不表示其他方法已被服务端拒绝。

| 流程 | 入口与参数 | 下一步/结果 | 权限与数据变化 |
| --- | --- | --- | --- |
| 首页 | `/` → `/catalog` | 五类商品导航 | 公开，无写入 |
| 分类 | `/catalog/viewCategory?categoryId=FISH` | 产品列表 | 公开 |
| 产品 | `/catalog/viewProduct?productId=FI-SW-01` | 可售项列表 | 公开 |
| 可售项 | `/catalog/viewItem?itemId=EST-1` | 规格、售价、库存、加购 | 公开 |
| 搜索 | `/catalog?keyword=ANGEL` | 产品名包含匹配，不区分大小写 | 公开，空词验证失败 |
| 注册表单 | `/account/newAccountForm` | 提交 `/account/newAccount` | 校验成功写三类账户数据，转登录页 |
| 登录 | `/account/signonForm` | POST `/account/signon`，字段 `j_username/j_password` | 失败回登录页；成功建立认证会话 |
| 退出 | `/account/signoff` | 返回 `/` | 清理登录状态及会话标识 Cookie |
| 账户编辑 | `/account/editAccountForm` → `/account/editAccount` | 成功重定向回编辑页 | 必须登录，只修改本人；用户名不可变 |
| 加购 | `/cart/addItemToCart?workingItemId=EST-1` | 重定向购物车 | 游客可操作；同编号数量加一 |
| 查看购物车 | `/cart/viewCart` | 数量、价格、小计、移除、结算入口 | 会话隔离，不扣库存 |
| 更新数量 | `/cart/updateCartQuantities`，`quantity[EST-1]=3` | 重定向购物车 | 小于 1 删除列表项，但当前有索引不一致缺陷 |
| 移除 | `/cart/removeItemFromCart?cartItem=EST-1` | 重定向购物车 | 删除列表和索引 |
| 结算摘要 | `/cart/checkOut` | `cart/Checkout` 只读摘要 | 非主下单入口；主入口直接指向订单表单 |
| 开始下单 | `/order/newOrderForm` | 默认账户地址与模拟支付表单 | 必须登录 |
| 独立收货地址 | `/order/newOrder`，`shippingAddressRequired=true` | `order/ShippingForm` | 此时未写订单 |
| 核对地址 | `/order/newOrder`，无上述参数 | `order/ConfirmOrder` | 此时未写订单 |
| 确认提交 | `/order/newOrder`，存在 `confirmed` 参数 | 保存后跳 `/order/viewOrder?orderId=...` | 当前用户、当前购物车；事务扣库存和写订单 |
| 我的订单 | `/order/listOrders` | 订单号、日期、总额列表 | 按当前用户名查询，日期升序 |
| 订单详情 | `/order/viewOrder?orderId=1000` | 本人详情，或错误页 | 必须登录并核对订单归属 |

模拟卡信息只用于演示，不能将下单成功解释为真实支付成功。已登录购物车页面的 My List 来自用户偏好分类；页脚横幅也由偏好分类和开关控制。

## 4. 自动回归用例清单

测试方法名为稳定执行入口，业务编号对应 `spec.md`。实测结果见 `verification.md`。

| 类/方法 | 规则或场景 | 核心断言 |
| --- | --- | --- |
| Web / `registrationCreatesAccountAndRequiresExplicitLogin` | AT-01 | 三类数据齐备、未自动登录、新密码可登录 |
| Web / `mismatchedPasswordsDoNotRegister` | AT-02 | 字段错误，三表无新增 |
| Web / `wrongPasswordDoesNotAuthenticate` | 登录失败 | 失败跳转，无认证会话 |
| Web / `loginProtectsOrdersAndAccountEditing` | AT-09、权限 | 未登录访问结算、列表、详情、账户编辑均转登录 |
| Web / `guestsBrowseCatalogAndSearchByProductName` | AT-05/06 | 分类/产品/可售项路由、大小写搜索、描述不参与搜索、空词拒绝 |
| Web / `guestCartMergesQuantitiesAndCalculatesTotals` | AT-07/08 | 同 SKU 合并、正整数更新、显式删除、金额正确、不扣库存 |
| Web / `separateSessionsDoNotShareCarts` | CRT-01 | 一个会话加购不影响另一个 |
| Web / `checkoutPersistsAddressesLinesAndInventoryWithoutChangingAccount` | AT-10/11 | 地址分支、确认、订单归属与明细、库存扣减、账户地址不变 |
| Web / `usersCanOnlyReadTheirOwnOrders` | AT-13 | 列表不出现他人订单，详情拒绝且模型不携带订单 |
| Service / `duplicateUsernameDoesNotOverwriteExistingAccount` | ACC-02 | 重复账号被拒绝且原账户不被覆盖 |
| Service / `registrationRollsBackWhenCredentialsCannotBeWritten` | ACC-03 | 最后一步凭据插入失败，前两表回滚 |
| Service / `emptyPasswordPreservesCredentialsAndNonemptyPasswordChangesThem` | AT-03/04 | 空密码保留旧凭据，非空密码替换，旧密码失效 |
| Service / `orderFailureRollsBackInventoryHeaderStatusLinesAndSequence` | AT-12 | 第二条明细插入失败后，库存、订单、状态、明细、序列一起回滚 |
| Defect / `d04QuantityZeroLeavesAnInvisibleCartIndexEntry` | D04 | 删除列表项后内部索引残留，重加仍不可见 |
| Defect / `d02OrdersCanDeductMoreThanAvailableStock` | D02 | 购买数量大于库存可使库存变成负数 |
| Defect / `d05HistoricalLineTotalUsesCurrentPriceInsteadOfPaidUnitPrice` | D05 | 关联商品价格变化会改变明细小计，成交单价与总额仍保留旧值 |
| Defect / `d06ServiceAcceptsAnEmptyOrder` | D06 | 服务层可持久化无明细订单 |

## 5. 浏览器回归清单与证据要求

状态均为“待完整 WAR 启动后执行”，不能用 MockMvc 通过替代。

1. 新浏览器会话访问首页，保存首页、分类、产品和可售项截图，核对标题及图片。
2. 注册一个新用户，核对字段错误显示、成功后登录页及真实登录结果。
3. 游客添加同一项两次、另一规格一次，核对数量、小计、库存标识和空车提示。
4. 修改数量、移除商品、将数量改零后重新添加，记录 D04 的实际用户表现。
5. 从购物车开始登录结算，填写假卡片信息；分别执行同地址及独立收货地址分支，核对确认页字段传递。
6. 确认下单，记录订单号、成功提示、库存差值和下单后购物车实际内容。
7. 分别登录两个用户，验证列表隔离、直接输入他人订单号时的页面及提示。
8. 修改资料、保留/修改密码、切换偏好开关，核对会话内容及下一次登录。
9. 退出、重新登录、刷新、多标签页操作，记录购物车生命周期；会话超时另行记录。

每条执行记录应包含原始提交、Java/Maven/容器版本、启动命令、步骤、预期与实际、截图/请求证据、是否复现及关联缺陷编号。涉及账号只用演示数据。
