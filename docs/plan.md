# JPetStore 技术实现方案

| 属性 | 内容 |
| --- | --- |
| 方案版本 | 0.1，设计评审稿 |
| 编写日期 | 2026-11-02 |
| 需求依据 | [需求规范 0.2](spec.md)、[项目工程约束](../AGENTS.md)、[旧系统基线](baseline/README.md) |
| 模板 | 未发现 `/templates/plan-template.md` 或仓库 `templates/plan-template.md`，采用本文结构 |
| 固定技术栈 | Java 21、Spring Boot 4.x、Spring Security、MyBatis、MySQL 8、Vue + TypeScript、Maven |
| 架构 | 按业务划分的模块化单体，一个后端进程、一个 MySQL 数据库、本地事务 |
| 当前交付 | 技术设计、接口与数据草案、实施任务及验收门槛；不包含实际迁移代码 |

## 1. 目标、范围与设计边界

### 1.1 改造目标

在保留账户、商品目录、购物车、模拟下单、本人订单和偏好展示的业务效果基础上，建立可构建、可测试、可部署、可观测的新应用，并纠正需求中已识别的实现缺陷。

完成后应具备：

1. Vue 通过 REST API 完成注册登录、浏览、购物车、确认下单及订单查询。
2. 密码安全存储、本人数据隔离、输入验证、统一错误响应和 CSRF 防护。
3. 订单创建与库存变更处于同一事务，重复请求不能重复建单或扣库存。
4. 历史订单金额使用成交快照，不受商品改价影响。
5. Maven Wrapper、MySQL 真实引擎测试、容器镜像、持续集成和运行诊断能力。

### 1.2 不扩展的业务能力

不引入真实支付、后台管理、物流履约、退款、优惠券或手动收藏。Q08已明确扩大范围：账户购物车持久化、跨设备恢复且退出保留。首期不引入微服务、消息队列、Redis、分布式事务或独立搜索引擎。Vue 静态资源不构成第二个业务后端。

### 1.3 需求决策与技术决策

- 本文确定模块边界、DTO、认证机制、版本管理、持久层、事务及交付方式。
- Q01～Q15已逐项确认，现行规则见§1.4和docs/decisions；下文原技术工作包按已确认规则执行。
- 禁止缺货、锁价时长、购物车保留期限、必填地址等不能通过配置默认值偷偷成为业务规则。
- 旧系统 17 条测试是有限范围的证据；完整 WAR/JSP、浏览器及并发场景尚未验证，不能把“测试通过”等同于完整旧系统已可启动。

## 1.4 已确认执行规则（覆盖下文早期候选措辞）

Q01～Q15均已由用户逐项答复，详见 `decisions/Q01.md`～`Q15.md`。此处是实施时的约束：

- 第二地址行选填；其他姓名/地址按Q15必填。密码8～64字符、UTF-8≤72字节，用户名trim且大小写敏感，改密全部会话失效。
- 缺货允许加购，成交必须库存充足，不支持预订。单SKU正数最多9999；数量小于1仍表示移除。
- 确认快照15分钟，价格或内容变化/到期须重新确认；成功同事务消费本次购物车，失败保留。
- **账户购物车是已批准的新范围**：支持账号恢复、跨设备、退出保留；登录合并游客车，同SKU相加，超限不丢双方内容。游客会话30分钟无活动失效，账号购物车保留至成交/手动移除。
- 账户活动购物车需数据库唯一性保证；MySQL可用ACTIVE状态生成列唯一索引，允许已消费历史cart继续存在。不能对所有owner_userid直接唯一而阻止新活动cart。
- 重新登录按已认证账号查询/绑定活动购物车；不以旧Session ID恢复账号数据。普通会话可进程内保存，重启需重新登录；持久化购物车恢复不要求登录会话本身永久有效。
- 停用账户禁止登录/下单；下架商品禁止新加购/成交，已有项提示不可买，历史订单可查。
- 订单最新优先，UTC存储、Asia/Shanghai展示，首期不分页。P为模拟已提交，配送字段可空。
- 币种CNY、两位小数、HALF_UP，总额上限99,999,999.99，售价大于零；模拟卡仅FAKE-演示文本、MM/YYYY格式，详情仅卡型/尾号，保存商品文本快照。
- 仅学习演示，无真实用户/历史订单迁移；原数据迁移和恢复任务使用合成样本演练，不据此发起生产操作。

## 2. 技术栈与工程约束

### 2.1 版本与依赖管理

| 层次 | 方案 | 实施要求 |
| --- | --- | --- |
| Java | 21 | Maven compiler release=21，构建与运行均使用 Java 21；不使用预览特性 |
| Spring Boot | 4.x | S1 锁定稳定补丁版；采用 Boot parent/BOM，Framework、Security、Jackson 等不单独混配 |
| Web | Spring MVC、嵌入式 Servlet 容器 | 可执行 JAR，使用 Boot 4 的 webmvc 模块化 Starter |
| 安全 | Spring Security | 会话认证、服务端授权、CSRF、统一 JSON 失败响应 |
| 持久层 | MyBatis-Spring-Boot-Starter 4.x 兼容线 | Starter 不受所选 Boot BOM 管理时，单独锁定兼容版本；底层不回退至旧 MyBatis-Spring |
| 数据库 | MySQL 8.x、InnoDB、utf8mb4 | 建议选 8.4 子版本；若环境要求 8.0，在 S0 明确；测试与生产必须同一子版本/补丁基线 |
| 迁移 | Flyway | Boot Flyway Starter + `org.flywaydb:flyway-mysql`，配套 MySQL Connector/J |
| 前端 | Vue 3、TypeScript、Vite、Vue Router、Pinia | 前端工具链及 Node 兼容版本在 S1 锁定，提交 lockfile；TypeScript strict |
| 测试 | Jupiter、AssertJ、Mockito、MockMvc、Testcontainers MySQL、Vue Test Utils、Vitest、Playwright | Java 测试版本由 Boot 管理；Boot 4 使用专用技术测试 Starter |
| 运维 | Actuator、Micrometer、JSON 日志 | 请求关联标识、健康探针、HTTP/JVM/连接池及业务指标 |
| 交付 | Maven Wrapper、npm lockfile、OCI 镜像、GitHub Actions | Wrapper 分发校验、确定性安装、镜像不可变标识、环境配置外置 |

官方当前列出的 Boot 4.1.2 支持 Java 21；具体补丁仍在实施时锁定，不把文档查询结果当作发布版本承诺。[Spring Boot 系统要求](https://docs.spring.io/spring-boot/system-requirements.html)

MyBatis 官方兼容表列出 Starter 4.0 对应 Boot 4.0+、Java 17+；实施时仍须通过实际依赖树和启动验证所选组合。[MyBatis 兼容表](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)

Vue 官方提供 TypeScript 工程支持；Vite 与 Node 的兼容范围需随锁定版本核对。[Vue TypeScript](https://vuejs.org/guide/typescript/overview.html)、[Vite 环境要求](https://vite.dev/guide/)

### 2.2 预计 Maven 依赖

| 用途 | 依赖坐标/名称 | 管理规则 |
| --- | --- | --- |
| HTTP/JSON | `spring-boot-starter-webmvc` | Boot 管理，使用 Jackson 3 |
| 认证授权 | `spring-boot-starter-security` | Boot 管理 |
| 输入验证 | `spring-boot-starter-validation` | Boot 管理 |
| JDBC 与连接池 | `spring-boot-starter-jdbc` | 一个 DataSource、一个事务管理器 |
| SQL 映射 | `org.mybatis.spring.boot:mybatis-spring-boot-starter` | 实施时锁定兼容的 4.x Starter |
| 数据库驱动 | `com.mysql:mysql-connector-j` | runtime，优先使用 Boot 管理版本 |
| 表结构迁移 | `spring-boot-starter-flyway`、`org.flywaydb:flyway-mysql` | 不仅添加 flyway-core；MySQL 模块名称不是 flyway-database-mysql |
| 运行诊断 | `spring-boot-starter-actuator` | 仅暴露必要端点 |
| MVC 测试 | `spring-boot-starter-webmvc-test` | 显式选择测试切片/自动配置 |
| 安全测试 | `spring-boot-starter-security-test` | 验证真实安全配置、CSRF 和过滤链错误 |
| 数据测试 | JDBC/Flyway 专用测试 Starter、`spring-boot-testcontainers`、MySQL 容器模块 | 按所选 Boot 的测试模块清单及 Testcontainers 主版本核对坐标 |

专用测试 Starter 带入基础测试设施，不以 classic Starter 作为最终方案；受管依赖不手写版本，确属 BOM 外的依赖集中声明并说明原因。[Boot 测试模块](https://docs.spring.io/spring-boot/reference/testing/test-modules.html)

Flyway 的 MySQL 支持模块需单独引入，实施中验证驱动、迁移工具和目标 MySQL 版本组合。[Flyway MySQL 支持](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql)

### 2.3 AGENTS.md 落实方式

- Controller 只处理协议、身份、DTO 和结果映射；事务放在应用服务。
- MyBatis 数据对象、领域对象和外部 DTO 分离，不将凭据对象直接序列化。
- 自定义 JSON 使用 Jackson 3 的 `tools.jackson` API；迁移注解时核对实际包名，不盲目替换所有 `com.fasterxml.jackson` 引用。
- `javax.servlet`、Validation 等相关 API 转为 Jakarta；JDK 自带的 `javax.sql` 等不机械改名。
- 使用 `@MockitoBean` / `@MockitoSpyBean`，MockMvc 集成测试显式自动配置。
- 每次修改结构新增 Flyway migration，不修改已应用脚本。
- 正常验证入口为 `./mvnw test` 和 `./mvnw verify`；本方案不启用 native profile，不引入原生镜像验收要求。

## 3. 目标架构与代码组织

### 3.1 运行结构

浏览器访问同一站点：Vue 静态资源由 Boot 同源提供，REST 位于 `/api/**`。开发阶段 Vite 代理 `/api` 到本地 Boot，避免为本地开发引入宽泛 CORS。生产入口通过现有反向代理完成 HTTPS；应用仍只有一个后端进程。

后端内部调用链：HTTP adapter → application service → domain/mapper；所有业务数据写入同一个 MySQL。SPA 回退只匹配明确的前端页面路由，不能把未知 API、Actuator 或静态资源错误回退为 index.html。

### 3.2 模块边界

| 模块 | 拥有的数据/能力 | 对外服务接口草案 | 允许依赖 |
| --- | --- | --- | --- |
| account | 账户、凭据、偏好 | AccountRegistration、AccountQuery、AccountProfileUpdate | shared |
| identity | 认证适配、当前身份、SecurityFilterChain | CurrentUser、登录/退出处理 | account 的公开接口、shared |
| catalog | 分类、产品、可售项、横幅、供应商只读资料 | CatalogQuery、PricingQuery | shared |
| cart | 会话购物车、版本及明细 | CartCommand、CartQuery、CartCheckoutAccess | catalog 公开接口、shared |
| order | 确认快照、订单、明细、状态、幂等记录、库存 | CheckoutCommand、OrderCommand、OrderQuery、InventoryQuery | account/catalog/cart 公开接口、shared |
| shared | 稳定错误码、金额/时钟等通用值、请求关联基础设施 | 小型公共契约 | 不依赖业务模块 |

库存由 order 模块负责变更。catalog 使用自身只读展示投影查询（可 JOIN inventory）获得库存，不注入 order Mapper，也不调用 order 应用服务；该跨表只读投影属于明确允许的查询边界，不赋予写入权。接口契约放在所属模块公开 API 包；不跨模块注入 Mapper、直接写其他模块表或共享可变领域对象。

订单应用服务调用 cart 的事务性访问接口时使用相同 REQUIRED 事务，不允许为了“模块隔离”开启独立提交。

### 3.3 目标目录草案

```text
pom.xml                         # 根聚合与受管版本
mvnw / mvnw.cmd / .mvn/
backend/
  pom.xml                       # 唯一可执行后端模块
  src/main/java/ik/am/jpetstore/
    JPetStoreApplication.java
    account/{api,application,domain,infrastructure,web}/
    identity/{application,infrastructure,web}/
    catalog/{api,application,domain,infrastructure,web}/
    cart/{api,application,domain,infrastructure,web}/
    order/{api,application,domain,infrastructure,web}/
    shared/{error,web,configuration,observability}/
  src/main/resources/
    application.yml
    db/migration/
    mapper/{account,catalog,cart,order}/
    static/                     # 构建时复制前端 dist，不提交生成文件
  src/test/
frontend/
  package.json / package-lock.json
  src/{app,features,shared}/
  tests/e2e/
legacy/                         # S1 时移动旧应用及其测试入口，保留 Git 历史
  pom.xml / src/ / baseline/
  scripts/legacy-baseline.sh
infra/                          # 开发 Compose、部署参数说明
.github/workflows/
docs/{spec.md,plan.md,baseline/}
```

每个业务模块首先使用包隔离，不拆成多个业务 JAR。用架构测试禁止跨模块访问 internal 包、循环依赖及 domain 对 Web 框架的依赖。无需为包边界强制引入 Spring Modulith。

S1 移动旧目录时同步修改旧 baseline POM、脚本和文档相对路径；旧应用不加入现代根聚合，旧 JDK 8 job 与新 JDK 21 job 分开，防止测试源码或 XML 被新应用扫描。

## 4. 关键技术决策

| 编号 | 决策 | 理由与限制 |
| --- | --- | --- |
| ADR-01 | 包模块化、单后端、单 MySQL、Spring JDBC 本地事务 | 满足范围与运维规模，保持下单强一致性 |
| ADR-02 | HttpSession + Cookie 认证，不引入 JWT | 仅浏览器同源使用，退出、CSRF 与会话管理直接；sessionStorage/localStorage 不保存登录令牌 |
| ADR-03 | 购物车明细放 MySQL，游客绑定会话、登录购物车归属账号 | 保持购物车消费与订单创建同库事务；Q08已批准账户购物车及跨设备恢复，游客合并/保留期限另行确定 |
| ADR-04 | 服务端确认快照 + 单次消费 checkoutId | 防止确认后篡改或重复下单；价格策略仍受 Q03 决定 |
| ADR-05 | MySQL 唯一约束 + 事务内幂等结果 | 处理重复确认和响应丢失，不能仅用 JVM Map 或短期缓存 |
| ADR-06 | 订单/明细成交快照，禁止按当前商品价格算历史金额 | 直接修正 D05，商品文本快照范围受 Q15 决定 |
| ADR-07 | 纯 JSON 成功 DTO + RFC 9457 错误 | 旧系统是 JSP，没有既有 JSON envelope 必须兼容；避免两套错误契约 |
| ADR-08 | 新工程迁移业务模块，旧代码作为对照保留 | 旧工程不是 Boot 应用，避免把 Boot 3.5→4 的升级步骤机械套用到 Spring 3 项目 |

## 5. 后端实现设计

### 5.1 注册、登录与账户

1. 注册 DTO 包含资料、偏好及两次密码；Jakarta Validation + 类级密码一致性校验，字段取值按 spec 和已冻结 Q 策略。
2. 注册服务事务内写 account、profile、signon；用户名唯一约束为最终保证。不要“先查再插”代替约束；唯一冲突映射 `409 USERNAME_CONFLICT`。
3. 使用 Spring Security PasswordEncoder 的带算法标识存储方案，建议首选 BCrypt 并基准测试成本；存储字段 varchar(255)。BCrypt 的输入字节限制必须与 Q13 对齐，不静默截断密码。
4. 若有真实历史用户，在受控迁移中批量哈希已确认明文的旧凭据或要求重置；不在公开登录链路长期保留 NoOp 明文兼容。演示用户仅由 dev/test seed 创建。
5. 认证优先使用 Security 标准过滤器处理表单凭据，`POST /api/auth/session` 使用 form-urlencoded；不为 JSON 登录绕过 session fixation、会话策略或 SecurityContextRepository 保存。
6. JSON 成功/失败处理器返回 200/401，不重定向到 HTML。凭据日志脱敏，登录失败不区分用户不存在或密码错误。
7. Session principal 只保存用户标识和必要权限，不保存密码/整份账户资料。`GET /api/account/me` 从服务读取资料；资料更新后不依赖旧身份内的 Account 副本。
8. 改密不传用户名，由身份解析账户；密码为空的兼容语义在 DTO→command 时明确转换。重复密码不落库、不进入响应。
9. 用户名大小写与数据库唯一键排序规则同 Q13 一致，避免 MySQL 默认不区分大小写规则悄然合并旧账号。

### 5.2 会话、CSRF 与权限

- 生产会话 Cookie：HttpOnly、Secure、SameSite=Lax、host-only；超时参数与 Q08 冻结。HTTPS 终止后的 forwarded headers 仅信任指定代理。
- 保持 CSRF 开启。`GET /api/auth/csrf` 显式读取/物化 deferred token，返回 token 与 headerName；浏览器写请求携带响应给出的 CSRF header。token 只保留在前端内存。
- 允许游客获取 CSRF、注册和维护自己的购物车；这些写操作同样验证 CSRF。GET 不创建购物车记录或订单；CSRF token 初始化属于安全会话基础设施，不是业务写入。
- 登录和退出可能清除旧 CSRF token，前端随后重新获取。登录切换会话标识，测试游客购物车引用能否按 Q08 正确保留。
- `POST /api/auth/logout` 经 Security logout handler 失效会话、清理 Cookie，返回 204；前端清空认证和结算状态，购物车按 Q08 处理。
- 未认证返回 401；CSRF/明确禁止返回 403。过滤链中的异常由 entry point/denied handler 输出相同 Problem Details；ControllerAdvice 不覆盖过滤链。
- 订单详情使用 `WHERE orderid = ? AND userid = ?` 或等价应用授权；对他人和不存在订单统一返回 404，避免泄露存在性，满足 ORD-18 的拒绝要求。
- 无跨域部署需求时不开 CORS；若以后分域，单列域名白名单、credentials、Cookie/CSRF 与版本请求头策略。

上述 CSRF 设计依据 Security 对 unsafe methods 的默认保护及 SPA token 生命周期要求，实施时通过实际登录/退出流程验证。[Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)

### 5.3 商品与偏好

- 保留分类→产品→可售项层级，SQL 采用显式 JOIN 和字段映射，返回 DTO，不复用旧表单对象。
- 搜索默认仍仅匹配产品名称；多词整体匹配。Q14 冻结后处理 trim、空白及 LIKE 转义；只用 `#{}` 参数，不能拼接用户输入到 SQL。
- 先不增加目录分页承诺；返回规模和未来分页接口必须在 Q11/容量评审中确定，不采用静默截断。
- 查询不缓存可变库存/价格，不启用 MyBatis 二级缓存；列表所需商品信息批量读取，避免按订单行逐条查询。
- My List 由偏好分类查询；profile 只保存偏好，账户认证不再通过 bannerdata 内连接。横幅缺失只隐藏该区域。
- 旧 description/banner HTML 转成受控描述和静态图片引用；默认 Vue 文本插值，不使用 `v-html`。若必须保留富文本，明确允许格式并使用经过审查的净化方案。
- 商品状态过滤仅在 Q10 定义后加入；不能仅因字段存在就自动隐藏当前演示商品。

### 5.4 会话购物车

Q08已确认：游客购物车通过会话cartRef定位，登录后按当前账号定位持久购物车，支持跨设备和重启恢复，退出保留。API 不接受客户端指定任意 cartId；cartRef 不作为可绕过身份/会话验证的 bearer 凭据。

建议数据：cart(id, session_binding_hash, owner_userid?, version, state, expires_at)，cart_item(cart_id, itemid, quantity)。`owner_userid` 为登录购物车的权威归属；通过唯一的账户活动购物车约束协调多设备写入，不能信任客户端cartId或username。原始 Session ID 不落业务表和日志。

- GET 读取当前引用；无购物车或已消费返回空 DTO，不因读取新建业务行。
- 首次写入才创建 cart；变更在应用服务事务内锁 cart，检查会话绑定与 expectedVersion，再更新明细及 version。
- cart_item 以 `(cart_id, itemid)` 唯一，只有一份明细集合，消除旧 List/Map 双状态。
- 数量为整数。保留 `quantity < 1` 表示移除时，0 和负数均走删除，不用 `@Positive` 意外拒绝旧交互；改为拒绝负数须先记录 Q15 决策。
- 订单明细数量始终必须为正，购物车 API 的删除语义不传入扣库存 SQL。
- 批量更新先完整校验再写入；非法输入不做半批更新或静默跳过。
- DTO 返回 version、lineCount、totalQuantity、items、subtotal、mergeConflict及当前账号的pendingMerges；金额为字符串，前端不重算权威金额。
- Vite/Pinia 只保存服务端 DTO，修改携带 expectedVersion；冲突返回 409 并提示刷新，不静默覆盖其他标签页。
- 登录保留、绑定、退出清理、过期回收及单进程重启后的恢复按 Q08 决策。登录会话可进程内保存、重启后重新登录；登录后必须从MySQL恢复本人账号购物车。用户未要求恢复登录会话本身，不因此引入Session JDBC。

账户cart现在是用户明确批准的持久业务数据，不能在登录过期或退出时按临时会话数据删除。订单事务只通过 cart 公开接口消费，不直接调用其 Mapper。

### 5.5 确认快照

建议新增 checkout 草稿以支撑 ORD-06、D11：

1. 登录用户提交 `cartVersion + 两组地址 + 模拟支付信息` 创建确认快照。
2. 锁定当前 cart 并检查版本、非空、有效可售项和数量，读取服务端价格，生成 checkoutId、内容摘要、有效期、明细与总额。
3. 快照绑定用户及 cart/version；前端显示快照数据，不使用隐藏字段作为可信成交依据。
4. 客户端最终提交只传 checkoutId 及 Idempotency-Key，不重新传权威价格、归属和成交明细。
5. 提交时锁 checkout 后锁 cart，比较版本、持有权、有效期及是否已消费。
6. Q03 推荐采用“确认价快照 + 提交时检价；变价则 409 要求重新确认”，不形成长期锁价承诺。若最终选择有效期内锁价，明确有效期及成本，不重新采用实时价格覆盖快照。
7. Q03已确认有效期15分钟，由服务端生成到期时间；到期、价格或内容变化要求重新确认，快照不预占库存、不提供锁价。
8. 同一 checkout 只能关联一张订单，即使前端误换 Idempotency-Key，也不能重复消费。

未选独立收货地址时，按 spec 保留初始收货值，不将账单改动自动覆盖收货地址；Vue 以独立模型保存两组地址。

### 5.6 下单事务与库存并发

Q02已确认允许缺货加购、禁止缺货成交和超卖，以下流程按该规则执行，不实现预订分支。

事务外完成协议校验和当前身份解析；事务由独立 `OrderApplicationService.placeOrder` Bean 开启，使用同一 JDBC transaction manager，所有参与服务为 REQUIRED。建议本操作明确 READ_COMMITTED，测试和部署配置一致；不依赖数据库默认隔离级别猜测锁行为。

事务内顺序：

1. 占用当前用户、操作、Idempotency-Key 的唯一记录。
2. 锁 checkout，验证归属、状态和有效期。
3. 锁 cart，验证归属、version 与未消费状态；按已决策的 Q03 验证确认价。
4. 按 itemid 升序处理可售项/库存，统一锁顺序减少死锁。若检价需要锁商品行，也采用同一全局顺序。
5. 对每项执行条件扣减，并要求 Mapper 返回的影响行数为 1。
6. 插入订单头，使用 MySQL AUTO_INCREMENT + generated keys 获得唯一编号；在同事务写初始状态和全部成交明细。
7. 将 checkout 标记已消费；按 Q04 的明确规则消费购物车。推荐先将本次完整 cart 标记 CONSUMED，后续写请求新建 cart；GET 将已消费内容视为空。若 Q04 要保留未购项，另定义基于版本的逐项消费，不能直接覆盖。
8. 保存可重放的订单创建响应、Location 和状态码，与幂等记录一起提交；事务返回后再发送成功响应。

条件扣减示意：

```sql
UPDATE inventory
SET qty = qty - #{quantity}, version = version + 1
WHERE itemid = #{itemId}
  AND qty >= #{quantity};
```

`quantity > 0` 必须在应用层验证。不存在库存行或影响行数为 0 应转为明确业务错误并回滚，不忽略结果。添加数据库 `CHECK (qty >= 0)` 的前提是 Q02 已确定禁止欠货，不能提前把该约束施加于未决业务。

所有业务异常统一采用可触发回滚的类型或显式 rollbackFor；不吞掉内层失败。独立业务模块不使用 REQUIRES_NEW 分别提交库存或订单。MySQL 自增允许跳号，编号连续性不是需求，也不承诺回滚后重用编号。

死锁/锁等待失败让整个事务回滚，外层根据已冻结技术重试参数进行有限重试或返回可重试 503；每次重试必须开启新事务，不能在 rollback-only 事务继续。默认不对所有异常自动重试。

MySQL 行锁与锁定读必须处于有效事务中；锁顺序、索引及实际隔离级别必须用目标 MySQL 验证。[MySQL InnoDB 锁定读](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)

### 5.7 幂等与响应丢失

建议结构：`order_request(user_id, operation, idempotency_key, request_hash, order_id, response_status, response_json, location, created_at, expires_at)`；唯一键 `(user_id, operation, idempotency_key)`。建议 key 使用随机 UUID 字符串，接口最长 128 字符，body/hash 规范化算法固定；哈希基于 checkoutId 和有业务意义的请求字段，不包含关联请求日志标识。

- 先认证/授权再尝试首次执行或重放，不允许跨用户返回结果。
- 首次请求 INSERT 占位、建单、记录结果在同一事务；失败时占位也回滚。
- 并发相同 key 的 INSERT 等待唯一约束。提交成功后，另一请求获得唯一冲突；让该尝试完整结束/回滚，再由外层在新事务读取既有结果，不能捕获任意唯一冲突后继续业务写入。
- 相同 key、相同摘要：返回原 status/body/Location，即使第一次响应在网络中丢失；可附 `Idempotency-Replayed: true`。
- 相同 key、不同摘要：409 `IDEMPOTENCY_CONFLICT`；明确限定是幂等键约束冲突，不能把其他数据库错误都按重放处理。
- 同一 checkout、更换 key：由 checkout 单次消费及 orders.checkout_id 唯一约束保护，返回 409 `CHECKOUT_ALREADY_SUBMITTED`，仅向本人附带已创建 orderId；不创建第二单，不冒充同 key 重放。
- 前一事务未完成且等待超限：503 `ORDER_RETRYABLE` + Retry-After；前端保留同一个 key，不能重生成 key 后盲目下单。
- 幂等重放读取必须包含 user_id；保存响应不含 Set-Cookie、CSRF、密码和完整模拟卡号。
- 保留时长按真实客户端重试窗口决定并写入接口契约。删除 key 后不能宣称无限期 exactly-once；checkout 单次消费约束仍保留。

### 5.8 历史订单

- 明细金额固定为持久化 unit_price × quantity；不得调用当前 Item.listPrice。
- 历史查询以订单和明细快照为主，不因当前商品不存在而失败。默认建议保存 itemid、产品名、规格描述快照，最终字段由 Q15 冻结。
- 金额/数量/本单地址是强制快照，当前商品链接只能作为可选导航。
- 仅本人列表；Q11已确认最新优先，日期倒序并以orderid稳定排序，首期不分页，不能把PageImpl直接暴露为接口契约。
- 暂不增加状态迁移；状态展示名称及遗留 P 映射由 Q07 确定。

## 6. REST API 契约

### 6.1 通用规则

- 成功返回明确 DTO，201 创建附 Location，204 不返回 body；Content-Type 为 application/json。
- 新 API 采用 `/api` 资源路径，以 Framework 7 原生 `API-Version: 1.0` 请求头解析及 mapping version 属性管理版本；初期默认 1.0，明确拒绝不支持版本，不再叠加路径版本。
- Cookie 与 CSRF 负责认证/写保护，API-Version 不承担安全作用。
- JSON 金额为十进制字符串；现有商品/用户名标识保留字符串语义，订单内部使用 BIGINT，自增值在 API 以十进制字符串表示，避免 JavaScript 精度丢失。
- 时间返回 ISO 8601 UTC，显示时区由 Q11 定义；明确 timestamp 与旧 DATE 的不同精度，不能补造历史时间。
- unknown fields 的接受/拒绝策略在 S1 固定；用户名、价格、状态等敏感字段使用允许字段 DTO，禁止通用 BeanUtils 批量绑定。
- 前端类型从版本化 OpenAPI 契约生成或校验；不以运行时反射暴露数据库对象。选用工具前验证 Boot 4/Jackson 3 兼容性。

原生 API 版本解析与映射方案依据 Framework 7 官方能力，实施时固定唯一解析来源。[Spring MVC API Versioning](https://docs.spring.io/spring-framework/reference/web/webmvc-versioning.html)

### 6.2 接口清单草案

| 方法/路径 | 权限 | 请求关键内容 | 成功响应/关键规则 |
| --- | --- | --- | --- |
| GET `/api/auth/csrf` | 公共 | 无 | 200 token/headerName；no-store |
| POST `/api/accounts` | 公共+CSRF | 注册 DTO | 201 用户标识，不自动登录 |
| POST `/api/auth/session` | 公共+CSRF | form-urlencoded 用户名/密码 | 200 当前用户摘要，登录后重新获取 CSRF |
| GET `/api/auth/session` | 公共 | 无 | 200 authenticated 与最小身份信息；未登录 authenticated=false |
| POST `/api/auth/logout` | 会话+CSRF | 无 | 204 清理身份与 Cookie |
| GET `/api/account/me` | 登录 | 无 | 200 本人资料，无凭据 |
| PATCH `/api/account/me` | 登录+CSRF | 资料、偏好、可选新密码 | 200 最新 DTO；用户名不允许更改 |
| GET `/api/categories` | 公共 | 无 | 200 分类集合 |
| GET `/api/categories/{id}/products` | 公共 | 无 | 200 产品集合；分类不存在 404 |
| GET `/api/products` | 公共 | keyword | 200 名称搜索结果；空词 400 |
| GET `/api/products/{id}` | 公共 | 无 | 200 产品和可售项概要 |
| GET `/api/items/{id}` | 公共 | 无 | 200 详情/当前库存；无效编号 404 |
| GET `/api/cart` | 当前会话 | 无 | 200 CartResponse，可能为空，不新建业务行 |
| POST `/api/cart/merge` | 登录+CSRF | expectedVersion | 200 最新购物车；登录合并超限后保留pendingMerges，调整账号车后显式重试；仍超限409不丢数据 |
| POST `/api/cart/items` | 当前会话+CSRF | itemId、expectedVersion | 200 最新购物车；重复点击语义是再次加 1，不自动重试 |
| PUT `/api/cart/items/{itemId}` | 当前会话+CSRF | quantity、expectedVersion | 200 最新购物车；小于 1 按决策移除 |
| PUT `/api/cart/items` | 当前会话+CSRF | 批量数量、expectedVersion | 200 整批更新，不允许部分成功 |
| DELETE `/api/cart/items/{itemId}` | 当前会话+CSRF | expectedVersion | 200 最新购物车；不存在项不影响其他项 |
| GET `/api/account/me/recommendations` | 登录 | 无 | 200 偏好分类产品；无收藏写接口 |
| GET `/api/account/me/banner` | 登录 | 无 | 200 安全展示模型，缺失/关闭以明确空值表达 |
| POST `/api/checkouts` | 登录+CSRF | cartVersion、两组地址、假卡信息 | 201 CheckoutResponse，绑定身份和版本 |
| POST `/api/orders` | 登录+CSRF | checkoutId，Idempotency-Key header | 201 OrderCreatedResponse + Location，支持同结果重放 |
| GET `/api/orders` | 登录 | 分页参数仅 Q11 决策后添加 | 200 本人订单摘要集合/冻结后的分页 DTO |
| GET `/api/orders/{id}` | 登录 | 无 | 200 本人快照详情；他人/不存在均 404 |

安全过滤器处理的登录/退出/CSRF 基础设施端点使用固定路径，其是否接受 API-Version 不改变认证语义；资源 Controller 统一走 1.0 版本映射并纳入契约测试。

### 6.3 DTO 示例

```json
{
  "orderId": "1000",
  "createdAt": "2026-11-02T08:00:00Z",
  "statusCode": "P",
  "totalAmount": "33.00",
  "items": [
    {"itemId": "EST-1", "quantity": 2, "unitPrice": "16.50", "lineAmount": "33.00"}
  ]
}
```

仅为结构示意；币种、状态名称和时间政策须在相关 Q 决策后补齐，不将示例时间、P 或编号1000视为固定业务值。

### 6.4 错误规范

统一 application/problem+json，字段包含 type、title、status、detail、instance，扩展 code、requestId、violations。自定义 type 在实施时确定项目拥有的稳定 URI；未配置时用 about:blank，不伪造不存在的文档链接。

| HTTP | 错误码示例 | 场景 |
| --- | --- | --- |
| 400 | VALIDATION_FAILED、MALFORMED_JSON、API_VERSION_UNSUPPORTED | 字段/JSON/参数/版本错误 |
| 401 | AUTHENTICATION_REQUIRED、INVALID_CREDENTIALS | 未认证或认证失败 |
| 403 | FORBIDDEN、CSRF_INVALID | 权限拒绝或 CSRF 错误 |
| 404 | RESOURCE_NOT_FOUND、ORDER_NOT_FOUND | 不存在资源、他人订单 |
| 409 | USERNAME_CONFLICT、CART_VERSION_CONFLICT、CHECKOUT_STALE、CHECKOUT_ALREADY_SUBMITTED、IDEMPOTENCY_CONFLICT、OUT_OF_STOCK | 唯一冲突、版本变化、库存不足等可识别业务冲突 |
| 405/415 | METHOD_NOT_ALLOWED、UNSUPPORTED_MEDIA_TYPE | 保持框架状态与必要响应头，不转为500 |
| 500 | INTERNAL_ERROR | 不泄露底层 SQL、异常栈或输入凭据 |
| 503 | ORDER_RETRYABLE、DATABASE_UNAVAILABLE | 有界锁等待/瞬时服务不可用，可重试指示明确 |

ControllerAdvice 继承 ResponseEntityExceptionHandler，避免 catch-all 把框架错误全变为500；过滤链401/403单独接入同一序列化器。violations 不返回 rejectedValue 中的密码、假卡号或地址原文。

## 7. MySQL 数据与迁移方案

### 7.1 数据库约束

- 目标引擎 InnoDB，所有业务和幂等表位于同一库；使用一个连接池和 JDBC 事务管理器。
- 数据库字符集 utf8mb4。分类/产品/SKU 编号采用明确的精确比较；用户名列 collation 按 Q13 定义，迁移前检测大小写/重音/长度冲突。
- 保留已有账户、商品和订单标识的映射；内部订单改为 BIGINT 自增不要求改变已存在数值。装载历史 ID 后从最大值之上继续分配，不要求连续。
- 金额精度以 spec 的 decimal(10,2) 为兼容起点；新上限和扩大精度由 Q06 决策，Java 使用 BigDecimal，JSON 使用字符串。
- 时间建议 DATETIME(6) 保存约定 UTC，Java 在适配层与 Instant 转换；SQL/session/JDBC 时区统一。旧 DATE 无精确信息时保留来源精度，不猜测时刻。
- FK/index/check 的引入必须先处理孤儿、负库存、空明细、重复账号等现有异常，不直接在脏数据上加约束使迁移不可恢复。

### 7.2 表变化草案

| 表/组 | 处理方式 | 关键约束与索引 |
| --- | --- | --- |
| account、signon、profile | 保留业务数据并统一用户标识类型/比较规则 | 用户名唯一；password_hash varchar(255)；profile用户唯一；偏好分类合法 |
| category、product、item、supplier | 保留层级及只读资料 | product(category,productid)、item(productid,itemid)，保留商品主键和关联外键 |
| bannerdata | 改为受控描述/图片引用 | favcategory 唯一；认证查询不依赖其存在 |
| inventory | 增 version，改 Mapper 返回更新行数 | itemid 主键；是否添加 qty>=0 检查取决 Q02 |
| orders | 保留历史 ID；扩展快照与 checkout 关联 | orderid BIGINT自增；checkout_id 唯一且历史可空；索引(userid,orderdate,orderid) |
| lineitem | 保留成交数量/单价，修正历史金额计算 | 主键(orderid,linenum)；商品文本快照字段由 Q15 定义；历史展示不强依赖实时商品 |
| orderstatus | 保留初始状态及历史数据 | 独立状态行号语义待 Q07；不扩展多状态前先修正查询假设 |
| sequence | 仅兼容导入，不再用于新订单 | 旧值可用于核对，不作为新并发编号器 |
| cart、cart_item | 新增会话暂存 | 持有权、version、state、expires_at；明细联合唯一；过期索引 |
| checkout、checkout_item | 新增确认快照 | user_id、cart_id/version、状态、expiry；确认明细和地址快照 |
| order_request | 新增事务幂等 | 唯一(user_id,operation,key)、摘要、结果、expiry索引 |

若存在真实历史商品删除，lineitem 可保留 itemid 字符串但不添加要求实时商品永久存在的强外键；订单→明细/状态的 FK 仍可在数据清理后建立。

### 7.3 Flyway 计划

建议初次实现按职责拆为：

```text
V1__create_account_and_catalog.sql
V2__create_orders_and_inventory.sql
V3__create_cart_and_checkout.sql
V4__create_order_idempotency.sql
V5__add_validated_constraints_and_indexes.sql
```

版本号为未应用前的草案；实际发布后只增加新脚本。dev/test 演示账号、商品和库存通过专用 seed/fixture 导入，不放进所有环境执行的版本迁移。Flyway history 作为额外元数据表存在。

- `baseline-on-migrate=false`、`validate-on-migrate=true`、`out-of-order=false`；不静默忽略初始化错误。
- 新库从空 schema migrate；已有库先备份、diff、明确已到版本，再人工执行一次 baseline，不能自动跳过未知结构。
- MySQL 多条 DDL 不能按普通业务事务承诺整体回滚；迁移采用小步骤、expand/migrate/contract，失败有恢复步骤。
- 不复制技能示例中的 PostgreSQL `ON CONFLICT`、`NOT VALID`、`CONCURRENTLY`、TIMESTAMPTZ 或 advisory-lock 参数到 MySQL。
- 开发可由 Boot 启动迁移；生产建议发布阶段使用迁移凭据先 migrate/validate，再以受限 DML账号启动应用，避免运行账号持有长期 DDL 权限。

### 7.4 历史数据迁移与回滚

Q12已确认仅学习演示，无真实用户或订单迁移。迁移工作包使用合成数据演练导出→staging校验→转换→导入，不能据此发起生产数据操作。

校验清单：逐表行数/主键、账户三表关联、大小写唯一冲突、商品父子关系、库存、订单归属、金额总和、明细单价、地址完整性、最大订单号、日期精度和不可恢复字段。旧历史文本缺少快照时标注“迁移时商品文本”，不能声称是当年成交文本。

上线采用一次受控切换，旧应用停止写入后导出最终增量；不允许新旧系统同时维护同一库存。先备份并演练恢复，兼容扩展期可以回退应用；新系统已接受新订单后不能仅切回旧库丢弃新订单，需停写、对账及显式回迁/前向修复方案。

## 8. 前端实现

### 8.1 页面与功能组织

| 页面/路由草案 | 模块 | 主要状态与行为 |
| --- | --- | --- |
| `/`、`/categories/:id` | catalog | 分类、产品列表、空结果 |
| `/products/:id`、`/items/:id`、`/search` | catalog | 规格、库存、名称搜索 |
| `/register`、`/login` | account | 表单校验、通用认证失败、注册后显式登录 |
| `/account` | account | 本人资料、两次新密码、偏好开关 |
| `/cart` | cart | 服务端购物车、version、金额、库存参考、推荐 |
| `/checkout`、`/checkout/confirm/:id` | order | 独立地址模型、假卡信息、确认快照 |
| `/orders`、`/orders/:id` | order | 本人列表、成交快照详情、拒绝/不存在状态 |

### 8.2 状态、错误与交互

- TypeScript strict，组合式 API；Pinia 只保存用户摘要、购物车DTO和短期确认状态，不保存密码、原始卡信息或长期凭据。
- HTTP client 统一发送 Cookie、API-Version、CSRF header，识别 application/problem+json；为请求显示关联 requestId。
- 不自动重试登录、加购等有副作用操作。订单重试复用同一 checkoutId 和 Idempotency-Key；成功前保留该尝试引用，敏感输入在离开流程后清除。
- 路由守卫仅改善体验，不能代替服务端权限。401 引导登录，保留允许的站内 returnTo，拒绝外部 URL 避免开放重定向。
- 409 cart/checkout 冲突展示具体刷新/重新确认提示；不在拦截器里静默重放更新。
- 金额以服务端格式化字符串显示，时间按 Q11 指定时区；用户界面不展示数据库表名、幂等算法或 Spring 配置等实现细节。
- 完整覆盖 loading、空列表、空车、字段错误、401/403/404及可重试失败。已知旧 JSP 的标题/空车/欢迎信息错误通过 Vue 页面验收纠正。
- 界面语言联动、订单排序/分页和模拟卡号展示范围只按冻结的 Q 决策实施。

## 9. 可观测性、配置与交付

### 9.1 配置与探针

环境分为 dev、test、prod；数据库URL/密码、Cookie策略、超时和运行资源通过环境配置注入，使用类型化 ConfigurationProperties 验证。secret 不进入 Git、前端构建变量或镜像层。

仅暴露必要健康端点；管理接口位于内网/独立管理监听配置，不能让 SPA 公开访问 env、configprops、beans、mappings。

- liveness 只反映进程/应用状态，不包含 MySQL，防止数据库故障触发无限重启。
- readiness 包含 MySQL及应用必要初始化状态，探测超时有界，迁移未完成不能接流量。
- 配置 startup probe 以覆盖启动迁移/初始化，具体宽限由实测决定。
- 优雅停机、容器 termination 时间与最大请求/数据库超时一起设置和验证。

探针分组和暴露策略依据 Actuator 官方配置，参数在实施时与所选版本核对。[Actuator Endpoints](https://docs.spring.io/spring-boot/reference/actuator/endpoints.html)

### 9.2 日志与指标

- 使用 Boot 原生 JSON 结构化日志输出 stdout，包含时间、服务、级别、requestId、可用的traceId/spanId及稳定业务结果码。[Boot Logging](https://docs.spring.io/spring-boot/reference/features/logging.html)
- X-Request-ID 只接收受限长度/字符的值，不合规则生成新值；写响应头与 MDC，所有路径finally清理。与幂等键分开，不把用户输入直接塞进日志。
- 默认不记录请求/响应body、密码、Cookie、Authorization、CSRF、假卡号、地址和SQL绑定参数；异常日志保留诊断上下文但脱敏。
- 指标覆盖 HTTP吞吐/延迟/4xx/5xx、JVM、Hikari池、订单成功/失败、库存冲突、幂等重放和数据库锁失败。
- 指标标签只用操作/错误码等低基数值，不用用户、订单、SKU或原始路径。requestId用于日志定位。
- 首期不强制完整链路追踪平台；若选 OTLP 后端，使用 Boot兼容的 Micrometer/Tracing集成，不手工再装一套SDK。
- 根据基线测量确定延迟/错误率/饱和度阈值，不能在Q12/容量未知时承诺任意SLO。

### 9.3 构建、容器与 CI

Maven根聚合只包含backend。Wrapper锁定分发与校验值，Enforcer校验Java21；frontend使用npm ci。前端构建结果通过显式Maven资源步骤复制到后端static；打包前清理旧产物，缺dist时失败，不能生成不完整JAR。

建议一个release profile/流水线任务串联前端构建和资源复制；普通后端测试不每次下载Node。文档明确各命令的作用，默认 `./mvnw verify` 验证后端，完整发布流水线另执行前端及端到端门禁。

| 阶段 | 主要命令/验证 | 产物 |
| --- | --- | --- |
| 前端检查 | npm ci、lint、vue-tsc、Vitest、build | 前端dist及测试报告 |
| 后端检查 | ./mvnw test、./mvnw verify | JAR、单元/切片/集成报告 |
| 契约与迁移 | OpenAPI校验、MySQL容器迁移及升级演练 | 契约diff、迁移报告 |
| E2E | Playwright针对真实新服务与MySQL | 浏览器报告与失败截图 |
| 打包 | 明确资源装配后，Boot build-image | Java21 OCI镜像、版本与commit标识 |
| 发布前 | 依赖树/SBOM/镜像检查、启动与探针验证 | 不可变digest、发布说明 |
| 部署 | 先迁移、启动、readiness、冒烟、切流 | 可回溯发布记录 |

优先使用 Cloud Native Buildpacks，显式设置构建/运行Java21，锁定兼容builder/run镜像族及digest；非root运行，限制内存CPU，运行期注入secret，只为必要目录提供可写空间。镜像扫描或签名工具的版本与平台支持在S1确定。

GitHub Actions 的PR任务执行检查，发布镜像仅在受信任分支/标签与授权环境运行；Docker daemon是Testcontainers和build-image前提。legacy对照另用JDK8运行，不把其下载失败误当新代码回归，但必须清晰报告未执行。

## 10. 测试与需求追溯

### 10.1 分层测试

| 层次 | 范围 | 技术与关键要求 |
| --- | --- | --- |
| 领域/单元 | 金额、数量删除语义、快照、摘要、错误映射 | 无Spring；Jupiter、AssertJ；不写只验证getter/setter的镜像测试 |
| MVC切片 | DTO、400/401/403/404/405/415、版本、CSRF | WebMvcTest导入真实Security配置，MockitoBean替换应用依赖 |
| Mapper | SQL、generated keys、约束、LIKE、collation、时间 | MyBatis兼容切片或显式Context，Testcontainers MySQL，禁止用H2替代 |
| 应用集成 | 注册/更新/下单整体提交、回滚与重放 | SpringBootTest；显式AutoConfigureMockMvc或RestTestClient配置 |
| 并发 | 库存争抢、同key同payload、key冲突、同checkout换key | 独立连接和线程屏障，验证数据库最终状态与返回结果 |
| Vue组件 | 字段错误、空状态、版本冲突、确认及金额显示 | Vitest、Vue Test Utils |
| 浏览器E2E | 两用户、两标签页、登录CSRF刷新、重复确认、真实页面 | Playwright + Boot + 同版本MySQL，替代旧JSP缺少的页面证据 |
| 迁移/交付 | 空库、旧数据导入、升级、启动、探针、镜像 | Flyway validate/migrate；真实打包进程，不仅mock上下文 |

事务测试不能被测试类级@Transactional自动回滚掩盖；通过真实代理调用，并从新的事务/连接验证已提交数据。after-commit副作用不承担必需业务清理，购物车消费必须同库事务完成。

### 10.2 重点回归映射

| 需求/缺陷 | 实现位置 | 必须达到的验收 |
| --- | --- | --- |
| ACC-01～09、AT-01～04、15～17 | account/identity | 注册三表原子性、用户名唯一、密码替换、资料本人、登录失败 |
| CAT-01～08、AT-05～06 | catalog/Vue目录 | 层级、搜索范围、空词、展示、状态/特殊词按Q |
| CRT-01～11、AT-07～08、18、D04a | cart/Vue购物车 | 同SKU合并、会话隔离、改零重加、批量校验、version冲突 |
| ORD-01～13、AT-09～12、D06a/b | order/identity | 登录、双地址、确认快照、空单拒绝、事务回滚 |
| ORD-09、NFR-03、D03、AT-R02/08 | order_request/checkout | 同key重放、异payload冲突、不同key同checkout不重复、编号唯一 |
| INV-01～03、D02、AT-R01 | inventory/order | 禁止缺货条件成立时只一人成功，多SKU任一失败整单回滚 |
| ORD-14～19、D05、AT-R04 | 成交快照/订单查询 | 真实数据库改价后历史金额不变，删除商品可按策略显示 |
| ORD-17～18、AT-13 | OrderQuery/安全接口 | 两个真实用户的列表、详情隔离与404契约 |
| ORD-20、Q07 | orderstatus | 只保留创建时初始状态，不生成未定义的支付/物流状态迁移 |
| PER-01～04、AT-14 | account/catalog/Vue偏好 | 推荐非手动收藏，横幅缺失不影响认证，开关即时生效 |
| D01、D07、D09、AT-R09 | Security/DTO/错误处理/Vue | 无密码回显、CSRF有效、错误脱敏、GET不业务写入 |
| D04b、D11、D12、AT-R03/06 | 按Q03/04/07/11 | 事务消费与确认一致性，明确时间和状态语义 |
| NFR-01～07 | 事务、安全、交付与运维 | 真实回滚、幂等、安全错误、数据恢复及按实测确定的质量目标 |
| Q01～Q15 | 各模块决策记录 | 依赖的业务政策先冻结，不以代码默认值替代决策 |

四个旧缺陷特征测试在旧基线保留为历史证据；在新系统中各写正确不变量测试，不能把“库存=-1”等旧断言复制为新验收。旧AT编号按spec继续追踪，补足其“部分实测”边界。

## 11. 实施阶段、任务与验收门槛

任务序号是实施建议，无工期承诺；只有依赖和退出条件明确后才排期。当前未开始下述工程改造。

| 阶段 | 工作包 | 前置依赖 | 退出条件 |
| --- | --- | --- | --- |
| S0 需求与版本冻结 | 完成Q01～Q15、MySQL子版本、运行数据范围；锁定认证/错误/版本/金额契约 | 本方案评审 | 业务决策有记录，阻塞后续工作的字段与策略明确 |
| S1 工程基础 | 建backend/frontend、Wrapper、Java21/Boot4依赖、MySQL、Flyway、CI；隔离legacy | S0技术和数据范围 | 空库可迁移，./mvnw verify通过，前端可构建，依赖树无旧Spring混入 |
| S2 商品纵向链路 | 分类→产品→SKU→Vue展示/搜索；错误与日志基础设施 | S1、Q10/14 | AT-05/06在API与真实浏览器通过，镜像可启动 |
| S3 账户与安全 | 注册、登录、退出、资料、偏好；同源CSRF全链路 | S1、Q01/08/09/13 | AT-01～04/14～17与401/403/CSRF回归通过，无凭据回显 |
| S4 会话购物车 | MySQL暂存、version、数量变更、偏好展示及生命周期 | S2/S3、Q02/08/15 | AT-07/08/18通过，改零重加修复，两标签页冲突可解释 |
| S5 结算与订单 | 快照、幂等、条件扣库存、原子消费、本人查询 | S4、Q02/03/04/06/07/11/15 | AT-09～13、AT-R01～09适用项通过，数据库并发及真实改价验证完成 |
| S6 数据与交付演练 | 历史导入、对账、受控切换、回滚、探针、指标、负载测量 | S5、Q12 | 无数据丢失演练、E2E全链路、镜像和运行手册齐备 |

S1先建立无业务变化的基础骨架，每个阶段单独提交和评审。先商品只读纵向链路，后账户/购物车，再把订单、库存、幂等作为一个完整可验收工作包，避免上线半套下单事务。

### 11.1 每个工作包的完成定义

- 需求和Q决策可追溯，接口/数据库变化有版本记录。
- DTO与安全/错误契约已验证，Mapper SQL与迁移经过目标MySQL验证。
- 聚焦测试通过后执行 `./mvnw verify`；前端及E2E按影响范围执行，不以重复无关测试代替检查。
- 不引入敏感日志、跨模块Mapper调用、混合Jackson定制或未受管旧依赖。
- 文档列出未完成验证；涉及页面、会话、并发或镜像必须有相应实测证据。

## 12. 待决策事项的实施影响

本表同步用户已确认Q结论；变更来源与边界保存在decisions逐项记录中。

| 决策 | 已确认规则/技术落点 | 实施工作 | 原计划阶段 |
| --- | --- | --- | --- |
| Q01 已确认 | 账户注册、资料修改、账单地址和收货地址的第二行均为选填；页面、服务端 DTO 与数据库约束保持一致。 | 测试先行，详见decisions/Q01.md | 依tasks执行 |
| Q02 已确认 | 缺货商品允许加入购物车，但提交订单时各可售项必须库存充足；本期不支持缺货预订和超卖。 | 测试先行，详见decisions/Q02.md | 依tasks执行 |
| Q03 已确认 | 确认页保存价格、购物车版本和交易内容快照，有效期15分钟；最终提交时价格或内容变化须要求用户重新确认。 | 测试先行，详见decisions/Q03.md | 依tasks执行 |
| Q04 已确认 | 成功后清理本次成交购物车；确认后其他标签页若修改购物车，先要求重新确认；提交失败保留内容。 | 测试先行，详见decisions/Q04.md | 依tasks执行 |
| Q05 已确认 | 本期只做模拟支付，不接真实扣款、退款和支付回调。 | 测试先行，详见decisions/Q05.md | 依tasks执行 |
| Q06 已确认 | 结算币种统一CNY，两位小数，HALF_UP舍入；延续decimal(10,2)范围，订单总额最多99,999,999.99，商品售价必须大于零。 | 测试先行，详见decisions/Q06.md | 依tasks执行 |
| Q07 已确认 | 初始状态P表示“已提交（模拟，未发生真实扣款）”。UPS和CA仅兼容旧演示数据，新订单承运商及地区不硬编码，允许为空；本期不增加物流状态或真实支付状态迁移。 | 测试先行，详见decisions/Q07.md | 依tasks执行 |
| Q08 已确认 | 登录账户的未成交购物车持久化保存，支持服务重启、会话过期后重新登录及跨设备恢复；主动退出不删除账户购物车。 | 测试先行，详见decisions/Q08.md | 依tasks执行 |
| Q09 已确认 | 首期界面统一简体中文，只保存english/japanese偏好，不根据偏好自动切换界面；不增加英日翻译页面。 | 测试先行，详见decisions/Q09.md | 依tasks执行 |
| Q10 已确认 | 停用账户不能登录或下单；下架商品不可新加购或成交。购物车已有下架项保留并提示不可购买，历史订单仍可查看。 | 测试先行，详见decisions/Q10.md | 依tasks执行 |
| Q11 已确认 | 精确时间使用UTC存储，界面按Asia/Shanghai展示；订单按日期倒序、同时间按订单号形成稳定顺序；首期不分页。 | 测试先行，详见decisions/Q11.md | 依tasks执行 |
| Q12 已确认 | 项目仅用于学习和演示，没有真实用户/历史订单需要迁移。 | 测试先行，详见decisions/Q12.md | 依tasks执行 |
| Q13 已确认 | 用户名trim后1～25字符、大小写敏感；其他必填文本拒绝纯空白；邮箱不要求唯一。密码8～64字符且UTF-8不超过72字节，不trim密码，超出拒绝不截断；改密后所有会话失效并重新登录。 | 测试先行，详见decisions/Q13.md | 依tasks执行 |
| Q14 已确认 | 关键词先trim，长度1～100字符；只按产品名称整体包含匹配，不区分大小写；%和_按普通字符转义。 | 测试先行，详见decisions/Q14.md | 依tasks执行 |
| Q15 已确认 | 单SKU成交数量1～9999；账单/收货姓名及地址必填，第二行选填；模拟卡类型三选，卡号使用FAKE-开头演示文本，有效期MM/YYYY格式。订单保存商品名及规格快照，商品删除后仍可展示。 | 测试先行，详见decisions/Q15.md | 依tasks执行 |

## 13. 风险与验证计划

| 风险 | 影响 | 处理与验证 |
| --- | --- | --- |
| Boot4生态版本组合不一致 | 编译成功但运行失效 | 锁定补丁后查看effective-pom/dependency:tree；真实启动、序列化、安全与MyBatis集成测试 |
| 旧HTML页面未完整运行 | 遗漏字段传递、空状态及Cookie效果 | 从spec恢复用户流程，用新服务真实浏览器补齐；旧WAR恢复验证作为可选补证，不虚报已完成 |
| MySQL与H2行为差异 | 排序、大小写、时间、锁及DDL差异 | 使用生产同版本MySQL容器，覆盖collation、索引、迁移与并发 |
| 确认快照/幂等/购物车锁交错 | 重单、死锁、成功后误清空 | 固定锁顺序与唯一约束，事务内消费，独立连接并发测试及响应丢失重放 |
| 事务提交后再做关键清理 | 已建单但购物车仍可重复购买 | 购物车/checkout在数据库同事务消费，session引用清理仅为辅助 |
| 用户名比较方式变化 | 错误合并或无法登录 | 迁移前冲突报告，按Q13选择字段collation和归一化 |
| 历史数据不完整 | 迁移金额、归属或文本失真 | 逐表对账，标注不可恢复字段，不编造历史时间/商品快照 |
| 单进程重启丢失登录会话 | 用户重新登录或购物车不可恢复 | 在Q08明确承诺；必要时用同库Session JDBC，保持会话与业务持有权校验 |
| 真实数据切换回滚不完整 | 新订单丢失/库存错乱 | 停写切换、备份演练，切回前必须对账并处理新写入 |

## 14. 交付清单与评审结论

实施应交付：现代backend/frontend源码、Maven Wrapper、锁定依赖、REST/OpenAPI契约、MySQL Flyway脚本、dev/test数据、分层和并发测试、真实浏览器用例、CI、Java21容器镜像、配置说明、健康/指标/日志说明、迁移与回滚操作记录。

本方案满足已指定技术栈与模块化单体约束。当前开始执行决策与环境准备，具体进度见tasks执行记录；业务代码及新栈测试尚未完成。评审通过及相关Q决策冻结后，按S1～S6逐阶段实施，不据本文件宣称新系统已经通过验证。
