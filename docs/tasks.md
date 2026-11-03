# JPetStore 研发任务列表

| 属性 | 内容 |
| --- | --- |
| 版本/日期 | 1.5 / 2026-11-03 |
| 输入 | [spec.md](spec.md)、[plan.md](plan.md)、[AGENTS.md](../AGENTS.md) |
| 当前状态 | 已完成T001～T265、T166及T448～T452、T453～T468；Phase 9完成，下一项为Phase 10的T407；详见执行记录 |
| 技术范围 | Java 21 / Boot 4 / Security / MyBatis / MySQL 8 / Vue + TypeScript；模块化单体 |
| 阶段规则 | 按需求确认、开发环境搭建、旧系统隔离、工程基础、数据、后端、前端、运行装配和系统交付的研发流程组织 |

## 当前执行记录

- T001～T015已逐项获得用户明确答复并完成，详见 `docs/decisions/Q01.md`～`Q15.md`；spec和plan已同步执行规则。
- 关键变更：CNY；账户购物车持久化/跨设备/退出保留，登录合并游客；订单最新优先；仅学习演示。
- T016已完成版本/元数据选择（非现代依赖运行验证）；T019支持矩阵、T020/T021版本声明已完成。2026-11-03复核已安装的目标JDK与Node，版本和CPU架构均匹配，详见支持矩阵及T024报告。
- T017已完成：OpenAPI3.1共19路径/24操作/33schema，固定Redocly2.3.0 lint退出0，1项许可证元信息警告已记录；新增合并重试契约源自已批准Q08，不代表端点已实现。
- T018已完成：133个Git已跟踪文件的迁移摘要清单。业务源码尚未归档，现代工程尚未创建。
- 已获授权启动Docker Desktop并复核Server29.5.3/linux-arm64可连接。T022环境模板、T166本地secret/产物忽略规则完成。T023 Compose config、独立MySQL8.4.13启动/健康及应用账号连接均已验证。
- T024已完成（2026-11-03）：原OracleJDK21.0.7保留，Temurin21.0.13+9通过官方Release直链取得并校验SHA256，安装到忽略的`.tools`；项目java/javac/Maven识别同一目标JDK。Node24.22.2/npm11.19.1通过独立路径验证，不修改全局JDK或.npmrc。Python3.14.4、Git2.33.0、Docker/Compose和本地MySQL均已复核，见`docs/development/toolchain-setup.md`。
- T025 RED：先创建`scripts/tests/test_dev_doctor.py`，执行`python3 -m unittest discover -s scripts/tests -p test_dev_doctor.py`，因目标`scripts/dev-doctor.py`不存在而退出1（1个导入错误），记录预期首轮失败；未先创建实现。测试覆盖JDK/JRE/Node/npm版本与架构、Docker/Compose/超时、磁盘与内存、固定镜像、数据库健康、端口冲突、秘密输出和退出码。
- T026 GREEN：完成只读诊断脚本后，`PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s scripts/tests`共30个测试全部通过、无跳过。获授权终端执行`PYTHONDONTWRITEBYTECODE=1 python3 scripts/dev-doctor.py --java-home .tools/jdk-21.0.13+9/Contents/Home --node-bin /Users/zhw/.nvm/versions/node/v24.22.2/bin`，退出0、10/10项通过；磁盘243.9GiB、Docker内存7.7GiB。默认资源阈值为可配置的10GiB/4GiB开发准备检查值，不是生产容量承诺。
- 诊断的受限沙箱实测因Docker socket权限退出1、6/10项通过，明确提示权限，未把未验证项记成功；获授权后同一命令10/10通过。脚本不安装工具、不停止服务、不拉取镜像、不修改全局环境、不输出凭据，识别本项目MySQL的3306占用；后端8080、前端5173空闲。
- Phase 1结束时尝试`./mvnw verify`返回127（Wrapper尚不存在，计划在T163～T165创建）。当前只完成工具与数据库准备，现代工程/业务尚未实现；T179、T406及现代栈完整verify留待对应阶段。
- Phase 2（T027～T160）已完成（2026-11-03）：133个旧文件按legacy-manifest逐文件`git mv`至`legacy/`，预检与复核SHA256全部一致，git记录133个R100重命名，无目录级批量移动；归档后`bash legacy/scripts/legacy-baseline.sh -o`离线复跑17/17通过（BUILD SUCCESS）；校验报告见`docs/baseline/legacy-relocation-check.md`。README、baseline/README、spec中旧路径引用缺口已记录在报告§5，待新增单文件DOC任务修正。
- 按T160报告§5全仓排查后新增补遗DOC任务T448～T452：除报告所列README、baseline/README、spec §12.3外，另覆盖data-model.md依据路径与verification.md历史记录注记；plan.md §3.3为目标结构草案，无需修改。各任务单文件修正、互不依赖，建议在T161前执行；本次仅新增任务定义，未改动这五份文件。
- T448～T452已完成（2026-11-03）：五个文件按任务定义逐项修正，diff复核仅路径前缀变化、无语义改动。验证：README/baseline README/data-model零残留旧路径；spec §12.3说明改为相对legacy/目录；verification头部补归档注记、正文历史记录零改写；所引6个路径（legacy脚本/POM/报告目录/测试目录/H2脚本/迁移校验报告）全部实际存在。
- Phase 3（T161～T180）已完成（2026-11-03）：①T161根聚合POM（Boot4.1.2 parent，仅backend子模块，legacy不入现代构建，BOM外集中mybatis4.0.0与archunit1.5.1，archunit版本已登记versions.md）；②T162 backend POM（webmvc/security/validation/jdbc/MyBatis/Flyway+flyway-mysql/actuator + webmvc-test/security-test/jdbc-test/flyway-test/testcontainers/archunit，Surefire=*Test、Failsafe=*IT），test-compile通过，dependency:tree无旧Spring，Jackson 3经tools.jackson（jackson-annotations 2.21为Jackson 3官方复用注解包，非混用）；③T163～T165 Maven Wrapper 3.9.11 only-script分发并锁定SHA512，`./mvnw --version`实测3.9.11+Temurin21.0.13；④T167～T172前端工具链（package.json精确版本、npm ci生成lockfile并实测安装、eslint flat config/vue-tsc严格配置/env.d.ts/vitest jsdom配置），lint实测检出no-explicit-any与no-unused-vars、vue-tsc退出0、vitest空环境退出0；⑤T173/T174测试容器fixture与确定性业务数据（两用户含缺横幅、多SKU、低库存1与零库存、历史订单快照）；⑥T175/T176启动类TDD（RED编译红灯→GREEN 3/3）；⑦T177/T178环境配置TDD（RED因MyBatis二级缓存未关闭→GREEN 4/4，真实MySQL8.4.13容器连接验证）；⑧T179全新Maven缓存下`-pl backend test` 7/7通过（2:53），doctor 10/10，容器healthy且应用账号可连；⑨T180开发指南。根目录`./mvnw verify` BUILD SUCCESS并产出可执行JAR。
- Phase 4（T181～T230）已完成（2026-11-03）：①T181/T182 Money值对象TDD（RED编译红灯→GREEN 11/11：两位小数HALF_UP、精确乘加、币种一致、无隐含税费、上限99,999,999.99、售价为正）；②T183统一UTC Clock与Asia/Shanghai展示时区；③T184～T189六模块领域模型契约（AccountModels/CatalogModels/OrderModels/CheckoutModels/OrderRequestModels/CartModels，含成交快照、幂等键、持有权与version）；④T190～T200十一个模块端口契约（AccountQuery/AccountRegistration/AccountProfileUpdate/CatalogQuery/PricingQuery/CartQuery/CartCommand/CartCheckoutAccess/CheckoutCommand/OrderCommand/OrderQuery）；⑤T216 BusinessFailure（稳定错误码+HTTP状态+字段级violation，运行时异常以触发事务回滚）；⑥T201～T215五模块DTO与Assembler（account/identity/catalog/cart/order，Jackson 3序列化、金额与ID为字符串、枚举与必填校验、密码不入响应、未知字段拒绝；identity/catalog的RED证据通过临时移出实现取得）；⑦T217由openapi.yaml生成frontend/src/shared/api/schema.d.ts（Money/OrderId均为string，password仅出现在请求类型）；⑧T218～T227五个Flyway迁移TDD（V1账户与目录、V2订单与库存、V3购物车与确认快照含ACTIVE生成列唯一索引、V4幂等表与checkout唯一、V5校验约束与索引并验证未知schema不静默baseline）；⑨T228/T229开发演示数据seeder（仅dev/test profile、幂等、运行时BCrypt编码不存明文）；⑩T230 ArchUnit模块边界守卫（无环、domain不依赖Web、跨模块只走api、shared不依赖业务模块、六模块非空断言防空洞）。全量`./mvnw test` 75/75通过。
- Phase 5（T231～T265）已完成（2026-11-03）：①T231/T232 ProblemDetailsWriter（T231 RED编译红灯→GREEN 6/6：RFC9457字段、code/requestId、violations不含原值、500脱敏）；②T233/T234 ProblemDetailsAdvice（RED→GREEN 6/6：业务400/404/409、Malformed JSON 400、405/415保留框架状态与Allow头、500脱敏）；③T235/T236原生API-Version（RED→GREEN 4/4：@RequestMapping(version) + configureApiVersioning，默认1.0，仅请求头，未知版本400）；④T237～T239 SessionPrincipal与CurrentUser（RED→GREEN 7/7：匿名/Spring匿名令牌/主体类型识别、requireUsername 401）；⑤T240/T242 AccountMapper契约与参数化SQL（RED→GREEN 7/7：三表查询不依赖横幅、用户名唯一冲突、只更新目标账户、密码哈希非空）；⑥T243/T245 CatalogMapper契约与参数化SQL（RED→GREEN 8/8：分类/产品/SKU关联、名称包含搜索、%与_按字面转义、横幅缺失返回空、实时库存投影）；⑦T246/T247 CatalogQueryService（RED→GREEN 12/12：层级/空结果/搜索范围/缺失资源/横幅缺失/实时价格，含Q10下架商品不可成交）；⑧T248/T249 PasswordConfiguration（RED→GREEN 6/6：哈希非明文、算法标识可升级、72字节上限明确拒绝不截断、按UTF-8字节计数）；⑨T250/T251 AccountRegistrationService（RED→GREEN 6/6，真实事务代理：三表齐写、凭据写入失败整体回滚、重复用户名不覆盖、不自动登录）；⑩T252/T253 AccountProfileService（RED→GREEN 6/6：空密码保留、有效新密码替换且旧密码失效、失败整体回滚、偏好刷新、请求无法指定他人账户）；⑪T254/T255 AccountUserDetailsService（RED→GREEN 6/6：存在/不存在、停用账户与未知用户同样失败不泄露、无横幅仍可认证、主体不缓存账户资料）；⑫T256/T257 SecurityJsonHandlers（RED→GREEN 6/6：200/204/401/403统一Problem Details、不回显凭据与异常细节）；⑬T258/T259 SecurityConfiguration（RED→GREEN 8/8：表单登录、会话标识轮换、401/403、CSRF写保护、Cookie策略HttpOnly/SameSite=Lax/Secure按环境、退出204、无宽泛CORS）；⑭T260/T261 IdentityController（RED→GREEN 4/4：匿名会话标记、CSRF token显式物化与no-store）；⑮T262/T263 CatalogController（RED→GREEN 7/7：分类/产品/SKU/搜索路由、404、空词400、不回退HTML）；⑯T264/T265 AccountController（RED→GREEN 6/6：注册201且未登录、me/PATCH仅本人、响应无凭据、推荐与横幅按偏好与开关）。全量`./mvnw test` 180/180通过。本阶段修正与补充：V1补入account.status（Q10停用账户禁登录，V1尚未发布属初次实现草案）；AccountQuery端口此前无实现，AccountProfileService同时实现AccountQuery以支撑me端点；Mapper记录改用包装类型以匹配MyBatis构造器映射；会话Cookie属性在MockMvc中不可验证，改为校验配置。遗留：真实Assembler仍属Phase 7，故各启动完整上下文的测试按协议第11条替换三个Assembler端口。遗留：T174的shop.sql按V1外键补入supplier行；各迁移测试用target版本隔离，V1测试去掉了对orders表的越界断言。遗留：T168的npm ci未在清空npm缓存下复跑（依赖可获取性已由首次安装证明，验收的"旧缓存"针对Maven已单独验证）；T174 shop.sql列名待Phase 4 V1迁移定稿后核对。

- Phase 6（T266～T304）已完成（2026-11-03）：①T266～T268 CartMapper契约与参数化SQL（RED“无法加载 Mapper XML”→GREEN 9/9：游客会话持有权、账号活动车唯一与跨设备恢复、锁定读FOR UPDATE、version CAS、同SKU唯一、冻结合并双方内容不丢、CONSUMED不可重复消费）；②T269～T271 CheckoutMapper（RED→GREEN 6/6：归属/版本/有效期、明细价格快照、锁定读、单次消费、无原始卡号列）；③T272～T274 InventoryMapper（RED→GREEN 5/5：条件扣减、影响行数必须为1、缺货拒绝不改数据、库存永不为负）；④T275～T277 OrderMapper（RED→GREEN 5/5：generated keys唯一、成交快照、只读本人订单、最新优先且首期不分页、同一确认快照只能一单）；⑤T278～T280 OrderRequestMapper（RED→GREEN 5/5：用户/操作/key联合唯一、响应与摘要持久化、跨用户隔离、过期与重放内容不含Cookie/CSRF/卡号）；⑥T281/T282 SessionCartBinding（RED编译红灯→GREEN 7/7：绑定稳定、会话ID轮换后不变、原始会话ID不落表、游客与账号持有权、读取不创建业务行）；⑦T283/T284 CartApplicationService（RED编译红灯→GREEN 14/14：同SKU加一、规格分行、改零/负值删除后可重加、批量非法整体拒绝、version 409、未加购不扣库存、游客隔离、账号跨设备一致、下架拒绝而缺货可加购、小计来自服务端、游客30分钟失效、登录合并与超限冻结）；⑧T285/T286 CartController（GREEN 10/10：空DTO、游客CSRF、加购、单项与整批、删除不存在项、版本409、pendingMerges、显式合并重试）；⑨T287/T288 CheckoutApplicationService（RED编译红灯→GREEN 11/11：服务端价格、购物车版本、两地址独立、有效期15分钟、归属校验、原始卡号不落库、非法卡输入拒绝、下架拒绝、不预占库存）；⑩T289/T290 InventoryService（RED编译红灯→GREEN 5/5：正数量校验、影响行数必须1、SKU升序锁顺序、任一不足整单回滚）；⑪T291/T292 OrderRequestHasher（RED→GREEN 7/7：同字段同摘要、不同快照不同摘要、规范化、摘要不含明文、键长度上限）；⑫T293/T294 OrderTransactionService（RED编译红灯→GREEN 7/7：幂等键占用→快照锁→购物车锁→升序扣库存→订单/明细/状态→消费→保存响应同一事务，失败整体回滚，订单号唯一且允许跳号，成交明细保留确认时商品文本）；⑬T295/T296 IdempotentOrderService（GREEN 6/6：同键同摘要重放、异摘要409、跨用户不重放、同快照换键409、失败后可复用同键、锁超时503提示复用键）；⑭T297/T298 OrderQueryService（GREEN 6/6：本人列表最新优先、他人与不存在统一不可见、成交金额不随现价变化、地址与支付摘要、无N+1）；⑮T299/T300 CheckoutController（GREEN 7/7：201、认证与CSRF、字段400、版本409、客户端不能覆盖服务端内容、控制器不开事务）；⑯T301/T302 OrderController（GREEN 7/7：201+Location、幂等键必填、重放响应一致与重放头、本人列表与详情、他人404、金额字符串）；⑰T303 OrderConcurrencyIT（GREEN 7/7，独立连接+屏障：库存1两人抢购最多一单且库存不为负、同键同载荷只建一单、同键异摘要409、同快照换键409、多SKU部分失败整体回滚、响应丢失可用同键重试、并发订单号唯一）。T304按验收条款记录“无需变更”：并发红灯未暴露锁顺序/消费/回滚边界问题，事务实现保持T294状态，证据为OrderConcurrencyIT 7/7通过。全量`./mvnw verify` 311/311通过（单元304 + 集成7）。本阶段补正与遗留：按协议新增T453～T465共13项追加式修正（契约/迁移/安全链/构建），详见“补遗任务：Phase 6 实施暴露的契约与迁移缺口”；Phase 5遗留的三个Assembler占位在本阶段扩展为五个（新增CartAssembler与OrderAssembler），真实转换器仍属Phase 7；购物车合并在读取路径自动进行（登录后首次读车即合并），显式重试用POST /api/cart/merge；订单响应体在事务内保存为领域层JSON，重放按同一订单快照重建DTO，body/status/Location与首次一致。遗留：迁移链断言随V6/V7更新（见T459）。


- Phase 7（T305～T334）已完成（2026-11-03）：①T305/T306 DefaultAccountAssembler（RED编译红灯→GREEN 5/5：只映射允许字段、响应绝不含凭据与哈希、地址第二行按原样、更新命令无用户名字段）；②T307/T308 DefaultIdentityAssembler（RED→GREEN 4/4：会话状态与最小身份、未登录用户名为空、CSRF token与请求头名称）；③T309/T310 DefaultCatalogAssembler（RED→GREEN 6/6：金额两位小数字符串、规格保序、详情带明细、横幅只含受控图片路径与纯文本）；④T311/T312 DefaultCartAssembler（RED→GREEN 4/4：行数与数量总和分别统计、明细金额=服务端单价×数量、待合并内容带明细、可售项视图取自目录只读查询）；⑤T313/T314 DefaultOrderAssembler（RED→GREEN 4/4：确认内容与成交金额取自快照、订单号十进制字符串、状态P固定文案、支付只暴露卡型与尾号、courier/region可空）；⑥T315/T316 前端内存CSRF状态（GREEN 5/5：显式更新token与headerName、登录/退出清除旧token、不落localStorage/sessionStorage/Cookie）；⑦T317/T318 统一同源HTTP客户端（RED“无法解析./http”→GREEN 7/7：Cookie同源、API-Version头、写请求带CSRF、Problem Details与requestId、副作用绝不自动重试、401/409不静默重放、baseUrl、Location等响应元数据）；⑧T319/T320 identity前端API（GREEN 6/6：session、CSRF、表单编码登录并刷新token、退出清理、401错误）；⑨T321/T322 account前端API（GREEN 5/5：注册、me、PATCH无用户名字段、推荐与横幅、缺失横幅为null）；⑩T323/T324 catalog前端API（GREEN 5/5：分类/产品/SKU/名称搜索参数编码、金额保持字符串）；⑪T325/T326 cart前端API（GREEN 5/5：GET不写、加购带版本、单项与批量、删除用查询参数带版本、合并冲突展示pendingMerges并显式重试）；⑫T327/T328 order前端API（GREEN 5/5：确认快照、提交带Idempotency-Key并返回Location、同键重试带replayed标记、本人列表、详情404）；⑬T329/T330 展示金额转换（RED→GREEN 6/6：补足两位小数、不经Number、不二次舍入、异常值原样返回、币种显式）；⑭T331/T332 展示时间转换（RED→GREEN 5/5：UTC按Asia/Shanghai展示、旧DATE不编造时间、毫秒精度不影响展示）；⑮T333/T334 展示错误转换（RED→GREEN 5/5：字段级错误、409提示刷新、requestId、不含SQL或密码原文、无法识别时通用提示）。前端`npm test` 53/53通过、`vue-tsc --noEmit`退出0、`eslint .`退出0；后端`./mvnw verify` 334/334通过。完整上下文现在用真实Assembler装配（ApplicationBoundaryTest不再提供转换端口占位），应用可启动。补遗：新增T466（前端lint允许下划线前缀未用参数）。遗留：前端页面与状态仓库属Phase 8；各启动完整上下文的测试仍按协议第11条替换转换端口，真实转换器由各自的单元测试与边界测试覆盖。


- Phase 8（T335～T380）已完成（2026-11-03）：①T335/T336 identity状态仓库（GREEN 6/6：会话引导、登录后刷新CSRF、退出清身份与token、绝不保存口令、returnTo仅站内路径）；②T337/T338 cart状态仓库（GREEN 5/5：服务端DTO为准、写操作带当前版本、409提示刷新且失败不丢内容、合并超限展示pendingMerges、错误成功后清除）；③T339/T340 order状态仓库（GREEN 5/5：保存同次确认快照与幂等键、重复提交复用同一个键、失败保留键可重试、成功才结束尝试、离开清敏感输入）；④T341/T342 FieldErrors组件（RED“无法解析./FieldErrors.vue”→GREEN 4/4：按字段关联错误、role=alert与aria-live、无违规时为空、不显示rejectedValue）；⑤T343/T344 AddressForm组件（GREEN 5/5：两组地址独立模型、改账单不覆盖收货、第二行选填、必填字段标记）；⑥T345/T346 FakePaymentForm组件（GREEN 5/5：三卡型、FAKE-演示提示、MM/YYYY、不设自动填充）；⑦T347/T348 CartTable组件（GREEN 6/6：行数/总量/小计、零或负数按决策移除、数量变更事件、空车与加载状态、商品名纯文本）；⑧T349/T350 Recommendations组件（GREEN 4/4：偏好分类推荐来源、无内容空状态、文本不执行脚本）；⑨T351～T360 catalog五页面（GREEN 15/15：分类五入口、加载/空/错误状态、分类不存在不显示伪页面、可售项与规格分行、售价库存缺货提示且不代表预留、名称搜索与空词与空结果）；⑩T361/T362 RegisterPage（GREEN 4/4：注册字段、两次密码一致、用户名冲突提示、成功后引导登录且不自动登录）；⑪T363/T364 LoginPage（GREEN 3/3：凭据输入、通用失败提示不泄露用户是否存在、仅站内returnTo）；⑫T365/T366 AccountPage（GREEN 4/4：用户名只读、两种语言、两次密码校验、保存后刷新）；⑬T367/T368 CartPage（GREEN 3/3：当前会话购物车、推荐、版本冲突提示）；⑭T369/T370 CheckoutPage（GREEN 2/2：两组地址与模拟支付、服务端确认快照）；⑮T371/T372 ConfirmPage（GREEN 2/2：显示确认内容与总额、无快照不建单）；⑯T373/T374 OrdersPage（GREEN 2/2：本人摘要、最新优先、金额字符串）；⑰T375/T376 OrderDetailPage（GREEN 3/3：成交金额与商品快照、只显示卡型与尾号、他人订单按不存在处理）；⑱T377/T378 路由与守卫（RED“无法解析./router”→GREEN 4/4：公开与受保护路由、未登录引导登录且returnTo仅站内、路由参数编码、守卫不代替后端授权）；⑲T379/T380 App布局与导航（GREEN 4/4：站点标题、公共导航、匿名登录入口、不渲染未知脚本）。前端`npm test` 139/139通过、`vue-tsc --noEmit`退出0、`eslint .`退出0；后端`./mvnw verify` 334/334通过。补遗：新增T467（lint豁免任务清单固定的组件名）。遗留：SPA入口与静态资源装配属Phase 9；页面的“加入购物车/结算”按钮尚未接线到购物车仓库（T338已提供接口，T368按清单只验证展示与冲突提示，接线留给E2E验收阶段）。


- Phase 9（T381～T406）已完成（2026-11-03）：①T381/T382 请求关联过滤器（RED编译红灯→GREEN 6/6：合法标识沿用、缺失生成、过长或恶意标识丢弃、响应回带、正常与异常路径都清MDC、与幂等键独立）；②T383/T384 低基数订单指标（RED→GREEN 3/3：语义事件各计一次、失败不计成交、标签只有outcome）；③T385/T386 协调器接入指标（RED“MeterNotFound”→GREEN 3/3：成交成功、幂等重放、库存冲突分别计数）；④T387/T388 管理端点专用安全链（GREEN 4/4：健康探针公开、env与beans不公开、API链语义不变）；⑤T389/T390 生产profile配置（application-prod.yml：存活探针不依赖数据库、就绪探针含数据库、只暴露health/info/prometheus/metrics、env/beans/mappings不暴露、优雅停机）；⑥T391/T392 结构化日志（logback-spring.xml + StructuredLoggingIT GREEN 2/2：请求关联标识进入日志上下文、日志不含口令/Cookie/CSRF/假卡号）；⑦T393/T394 同源SPA回退（SpaRoutingConfiguration GREEN 4/4：前端路由回index、未知API与缺失静态资源不回退成HTML、API路由照常）；⑧T395/T401 前端资源与JAR装配（vite.config.ts、index.html、main.ts、styles.css、release profile复制dist并缺dist即失败；PackagedApplicationIT 4/4通过——真实JAR同时提供前端入口与API，未知API是错误响应不是HTML，无旧产物残留）；⑨T402/T403 E2E与打包应用编排（infra/compose.yml 固定镜像与单后端、frontend/playwright.config.ts 独立上下文与失败截图追踪）；⑩T404/T405 本地全栈启动冒烟工具（scripts/dev-smoke.py + test_dev_smoke.py：RED“脚本不存在”→GREEN 10/10，数据库/后端/前端就绪、有界超时、Vue路由与同源代理API、脱敏诊断、只清理自有进程）；⑪T406 全栈首次启动验收记录（docs/development/full-stack-verification.md：后端362/362、前端141/141、脚本40/40、发布形态打包与真实进程验收通过，并列出未验证项）。补遗：新增T468（安全链区分业务API与前端页面）。遗留：Playwright真实浏览器验收、生产部署、迁移数据回填属Phase 10/11，已在验收记录中明确标为未验证；PackagedApplicationIT由release profile触发（默认构建不装配前端，缺dist按设计打包失败）。


## 执行协议

1. 一项任务只修改“主要文件”指定的一个逻辑文件。MOVE 是一个文件从旧路径到新路径的重命名，不是移动整个目录。创建目录不算第二业务文件；运行测试产生的忽略产物不计入源码变更。
2. 实现需要额外文件时，先新增独立任务及依赖，不能顺手扩展当前任务。不得覆盖现有未提交用户修改；不得自动提交、push 或生产部署。
3. `[P]` 表示与同阶段至少一个任务没有相互依赖且不写同一文件；只有列出的依赖均完成时才可并行，不等于没有前置。没有 `[P]` 的串行链按依赖执行。共享同一文件的任务已有顺序边，不并行编辑。并行分支宜使用隔离工作树，只包含已完成的前置，防止其他分支未实现测试污染Java全量test-compile；合并后执行完整回归。
4. TDD：每个行为实现先执行对应 `[TEST]` 任务，保存预期失败证据，再完成 `[IMPL]` 并用同一测试转绿。禁止先写实现再补测试；首轮目标类不存在可记编译红灯，但最终必须验证实际行为，不能只验证类存在。
5. CONTRACT 只声明类型/签名，CONFIG 只提供构建/配置，FIXTURE 只提供测试基础数据，MOVE/DOC/DECISION 不实现业务算法；不为它们编写无意义的getter或配置文本镜像测试。具有验证/转换/SQL/运行行为的文件已安排先行测试。配置交付效果由真实应用/镜像验收覆盖。
6. 每次TEST任务只写一个测试文件；使用Jupiter/AssertJ、Boot4专用测试Starter、MockitoBean及显式自动配置。Mapper、事务、并发使用同版MySQL容器；禁止以H2代替或测试级自动回滚隐藏提交效果。
7. Java聚焦测试：`./mvnw -pl backend -Dtest=ClassName test`。`*IT` 使用Failsafe：`./mvnw -pl backend -Dit.test=ClassName verify`。基础Wrapper尚未完成时用固定版本Maven执行等价命令；所有最终验证以 `./mvnw verify` 为准，不能因选择器误跑0条记通过。
8. Vue聚焦测试：`npm --prefix frontend run test -- --run <相对frontend的测试路径>`；E2E使用已配置Playwright命令并启动真实后端/MySQL。脚本测试使用 `python3 -m unittest discover -s scripts/tests`；测试自己隔离临时库/镜像，不触碰生产。
9. Q01～Q15没有用户结论时，相应DECISION任务不能勾选，依赖它的分支保持阻塞；不依赖该Q的工具基础和旧代码归档可继续。本次会话已逐项确认Q01～Q15，后续变更仍须记录。Q若改变范围，补充spec/plan单文件更新任务再向下执行。
10. 任务编号与正文顺序一致，按 T001 起自上而下执行是一条有效的默认串行路径；每项任务的前置都位于它之前。标记 [P] 的任务在依赖已满足时可并行，无需等待无关分支；共享文件仍须串行。依赖中的 Txxx～Tyyy 包含两端的全部任务。所有业务实现完成真实装配前，不得把聚焦测试通过写成完整服务已可运行。
11. 后端HTTP测试可以MockitoBean替换已声明的Assembler端口；真实转换器在Phase 7落地。因此控制器切片不依赖未来转换实现，完整上下文/浏览器门禁明确依赖真实Assembler，禁止将测试mock带入生产配置。
12. 每项完成需记录命令、测试数、结果或文件校验；四个旧缺陷特征测试只能当历史证据，新系统应使用正确不变量。外部条件缺失应写明阻碍，不能跳过后标完成。

## 任务类型与路径约定

- TEST：测试先行；IMPL：业务/交付行为实现；CONTRACT：纯接口/类型；CONFIG：工程配置；FIXTURE：测试支撑；MOVE：单文件归档；DECISION：业务决策；OPS：获授权环境操作，仅以一个报告文件记录证据；DOC/VERIFY：单文件文档/验证报告。
- Java路径完整写到文件，模块限定为 account、identity、catalog、cart、order、shared；不存在的目标路径由其任务创建。
- 任务涉及的 `*Models.java` / `*Dtos.java` 是单个模块相关的不可变嵌套record族，不是把多个业务服务塞进一个文件；Assembler只有抽象签名，DefaultAssembler独立负责显式转换。
- Q02禁止缺货成交、Q03检价快照15分钟、Q04消费成交车、Q08账号持久化跨设备恢复已确认；计划中的旧会话限定和升序候选不得继续执行。

## 阶段总览

| 阶段 | 执行编号 | 主要结果 | 退出证据 |
| --- | --- | --- | --- |
| 0 | T001～T018 | Q决策、版本、API契约、旧文件清单 | 用户结论和可校验输入；未决分支标阻塞 |
| 1 | T019～T026 | 开发环境搭建：工具安装、变量模板、早期MySQL与诊断 | 工具与数据库实测、doctor测试转绿；环境阻碍有记录 |
| 2 | T027～T160 | 旧系统隔离：逐文件归档与迁移校验 | 133个旧文件摘要一致、旧基线验证结果与阻碍有记录 |
| 3 | T161～T180 | 工程基础：Maven/Wrapper、前端工具链、测试设施与应用骨架 | 现代依赖就绪、骨架测试通过、干净检出验证及开发指南 |
| 4 | T181～T230 | 纯契约、领域金额、5个MySQL迁移 | 字段/约束/类型与已决Q一致，迁移真实引擎验证 |
| 5 | T231～T265 | 通用错误/版本、目录、账户、认证 | 切片与数据库层TDD通过，CSRF和权限有效 |
| 6 | T266～T304 | 会话购物车、确认、原子下单、幂等 | 回滚、并发、归属及历史金额不变量通过 |
| 7 | T305～T334 | 真实Assembler、TS API与显示转换 | JSON/金额/时区/敏感字段契约通过 |
| 8 | T335～T380 | Vue页面/组件/状态与路由 | 每个页面测试通过、无静默重放或敏感存储 |
| 9 | T381～T406 | 全栈本地启动验证、日志/指标/探针、SPA和JAR装配 | 开发机启动证据及真实资源/路由/运行日志 |
| 10 | T407～T425 | E2E、迁移工具、CI/镜像、候选验收 | 研发技术门禁、真实容器与迁移验收 |
| 11 | T426～T447 | 服务器、TLS/secret、部署、备份恢复、预发布、生产晋级与观察 | 真实目标演练；生产授权、冒烟、回滚和发布后记录 |
| 补遗 | T448～T452 | 归档后文档路径修正 | README/baseline/spec 引用与仓库实际结构一致 |
| 补遗 | T453～T468 | Phase 6～9 实施中暴露的契约、迁移与工具缺口补正 | 追加式修正，契约与实现一致且全量回归通过 |

## 开发环境与部署发布覆盖索引

清单按“需求决策 → 开发环境 → 工程与数据基础 → 业务实现 → 全栈验证 → 发布候选 → 服务器部署 → 生产观察”组织。开发环境与发布任务已经纳入各自的实际执行位置，以下索引可直接定位。

| 环节 | 主要任务 | 说明 |
| --- | --- | --- |
| 开发工具安装与诊断 | T019～T026 | 版本、实际安装、daemon、变量及早期数据库，不等待应用打包 |
| 干净检出与全栈首次启动 | T179、T180、T404～T406 | 基础环境验证、调试指南、前后端/MySQL真实启动冒烟 |
| 部署目标与服务器准备 | T426、T427、T442 | 主机、域名/TLS、secret、账号、持久存储及网络 |
| 部署脚本与运行配置 | T428～T431、T443 | 测试先行、不可变digest、单后端、代理和受控部署 |
| 数据库迁移与备份恢复 | T432～T436、T446 | 迁移账号、备份完整性、隔离恢复及调度 |
| 发布验证与回滚 | T437～T441、T444 | 只读冒烟、兼容应用回退、预发布故障演练 |
| 生产晋级与观察 | T445、T447 | 授权后上线，记录实际版本、运行指标、备份与交接 |

OPS任务允许在已授权环境执行安装/启动等操作，但仓库只修改指定的一个证据报告；它们不以“单文件报告”掩盖批量源码改动。本次只补任务，未安装工具、创建服务器或部署应用。T425是发布前技术门槛，T447才是生产发布后验收；未获生产授权时T445/T447保持待执行。

## Phase 0：需求决策与契约冻结

执行编号：**T001～T018**；本阶段 18 项任务。按正文顺序推进，只有依赖已完成的 [P] 任务可并行；Q决策或环境条件不足时明确标阻塞。

- [x] **T001** [P] **[DECISION] 确认 Q01：地址第二行**
  - 主要文件：`docs/decisions/Q01.md`；依赖：无；依据：ACC-01、Q01。
  - 执行：读取 spec 对应问题，列出选项和影响；取得用户明确结论后记录取值、批准来源、日期及关联规则。没有结论时保持阻塞，不能把 plan 建议当答案。
  - 验收：只有结论已确认且影响/验收可执行才勾选；若范围改变，先另列 spec/plan 单文件更新任务。

- [x] **T002** [P] **[DECISION] 确认 Q02：缺货加购与预订**
  - 主要文件：`docs/decisions/Q02.md`；依赖：无；依据：CRT-04～05、INV-01～03、Q02。
  - 执行：读取 spec 对应问题，列出选项和影响；取得用户明确结论后记录取值、批准来源、日期及关联规则。没有结论时保持阻塞，不能把 plan 建议当答案。
  - 验收：只有结论已确认且影响/验收可执行才勾选；若范围改变，先另列 spec/plan 单文件更新任务。

- [x] **T003** [P] **[DECISION] 确认 Q03：确认价及快照有效期**
  - 主要文件：`docs/decisions/Q03.md`；依赖：无；依据：ORD-06～07、Q03。
  - 执行：读取 spec 对应问题，列出选项和影响；取得用户明确结论后记录取值、批准来源、日期及关联规则。没有结论时保持阻塞，不能把 plan 建议当答案。
  - 验收：只有结论已确认且影响/验收可执行才勾选；若范围改变，先另列 spec/plan 单文件更新任务。

- [x] **T004** [P] **[DECISION] 确认 Q04：成交购物车消费**
  - 主要文件：`docs/decisions/Q04.md`；依赖：无；依据：ORD-12、Q04。
  - 执行：读取 spec 对应问题，列出选项和影响；取得用户明确结论后记录取值、批准来源、日期及关联规则。没有结论时保持阻塞，不能把 plan 建议当答案。
  - 验收：只有结论已确认且影响/验收可执行才勾选；若范围改变，先另列 spec/plan 单文件更新任务。

- [x] **T005** [P] **[DECISION] 确认 Q05：模拟支付范围**
  - 主要文件：`docs/decisions/Q05.md`；依赖：无；依据：ORD-04～05、Q05。
  - 执行：读取 spec 对应问题，列出选项和影响；取得用户明确结论后记录取值、批准来源、日期及关联规则。没有结论时保持阻塞，不能把 plan 建议当答案。
  - 验收：只有结论已确认且影响/验收可执行才勾选；若范围改变，先另列 spec/plan 单文件更新任务。

- [x] **T006** [P] **[DECISION] 确认 Q06：币种精度及金额上限**
  - 主要文件：`docs/decisions/Q06.md`；依赖：无；依据：ORD-14～16、Q06。
  - 执行：读取 spec 对应问题，列出选项和影响；取得用户明确结论后记录取值、批准来源、日期及关联规则。没有结论时保持阻塞，不能把 plan 建议当答案。
  - 验收：只有结论已确认且影响/验收可执行才勾选；若范围改变，先另列 spec/plan 单文件更新任务。

- [x] **T007** [P] **[DECISION] 确认 Q07：初始状态与配送字段**
  - 主要文件：`docs/decisions/Q07.md`；依赖：无；依据：ORD-08、20、Q07。
  - 执行：读取 spec 对应问题，列出选项和影响；取得用户明确结论后记录取值、批准来源、日期及关联规则。没有结论时保持阻塞，不能把 plan 建议当答案。
  - 验收：只有结论已确认且影响/验收可执行才勾选；若范围改变，先另列 spec/plan 单文件更新任务。

- [x] **T008** [P] **[DECISION] 确认 Q08：会话与购物车生命周期**
  - 主要文件：`docs/decisions/Q08.md`；依赖：无；依据：CRT-01、11、ACC-06、Q08。
  - 执行：读取 spec 对应问题，列出选项和影响；取得用户明确结论后记录取值、批准来源、日期及关联规则。没有结论时保持阻塞，不能把 plan 建议当答案。
  - 验收：只有结论已确认且影响/验收可执行才勾选；若范围改变，先另列 spec/plan 单文件更新任务。

- [x] **T009** [P] **[DECISION] 确认 Q09：语言联动**
  - 主要文件：`docs/decisions/Q09.md`；依赖：无；依据：PER-04、Q09。
  - 执行：读取 spec 对应问题，列出选项和影响；取得用户明确结论后记录取值、批准来源、日期及关联规则。没有结论时保持阻塞，不能把 plan 建议当答案。
  - 验收：只有结论已确认且影响/验收可执行才勾选；若范围改变，先另列 spec/plan 单文件更新任务。

- [x] **T010** [P] **[DECISION] 确认 Q10：用户及商品状态**
  - 主要文件：`docs/decisions/Q10.md`；依赖：无；依据：CAT-08、Q10。
  - 执行：读取 spec 对应问题，列出选项和影响；取得用户明确结论后记录取值、批准来源、日期及关联规则。没有结论时保持阻塞，不能把 plan 建议当答案。
  - 验收：只有结论已确认且影响/验收可执行才勾选；若范围改变，先另列 spec/plan 单文件更新任务。

- [x] **T011** [P] **[DECISION] 确认 Q11：时区精度与列表分页**
  - 主要文件：`docs/decisions/Q11.md`；依赖：无；依据：ORD-17、Q11。
  - 执行：读取 spec 对应问题，列出选项和影响；取得用户明确结论后记录取值、批准来源、日期及关联规则。没有结论时保持阻塞，不能把 plan 建议当答案。
  - 验收：只有结论已确认且影响/验收可执行才勾选；若范围改变，先另列 spec/plan 单文件更新任务。

- [x] **T012** [P] **[DECISION] 确认 Q12：真实数据及运行目标**
  - 主要文件：`docs/decisions/Q12.md`；依赖：无；依据：NFR-06～07、Q12。
  - 执行：读取 spec 对应问题，列出选项和影响；取得用户明确结论后记录取值、批准来源、日期及关联规则。没有结论时保持阻塞，不能把 plan 建议当答案。
  - 验收：只有结论已确认且影响/验收可执行才勾选；若范围改变，先另列 spec/plan 单文件更新任务。

- [x] **T013** [P] **[DECISION] 确认 Q13：账户归一化与密码政策**
  - 主要文件：`docs/decisions/Q13.md`；依赖：无；依据：ACC-10、Q13。
  - 执行：读取 spec 对应问题，列出选项和影响；取得用户明确结论后记录取值、批准来源、日期及关联规则。没有结论时保持阻塞，不能把 plan 建议当答案。
  - 验收：只有结论已确认且影响/验收可执行才勾选；若范围改变，先另列 spec/plan 单文件更新任务。

- [x] **T014** [P] **[DECISION] 确认 Q14：搜索特殊输入**
  - 主要文件：`docs/decisions/Q14.md`；依赖：无；依据：CAT-07、Q14。
  - 执行：读取 spec 对应问题，列出选项和影响；取得用户明确结论后记录取值、批准来源、日期及关联规则。没有结论时保持阻塞，不能把 plan 建议当答案。
  - 验收：只有结论已确认且影响/验收可执行才勾选；若范围改变，先另列 spec/plan 单文件更新任务。

- [x] **T015** [P] **[DECISION] 确认 Q15：数量/地址/模拟卡及历史快照字段**
  - 主要文件：`docs/decisions/Q15.md`；依赖：无；依据：CRT-07、ORD-13、19、Q15。
  - 执行：读取 spec 对应问题，列出选项和影响；取得用户明确结论后记录取值、批准来源、日期及关联规则。没有结论时保持阻塞，不能把 plan 建议当答案。
  - 验收：只有结论已确认且影响/验收可执行才勾选；若范围改变，先另列 spec/plan 单文件更新任务。

- [x] **T016** [P] **[DOC] 锁定兼容版本与工程参数**
  - 主要文件：`docs/decisions/versions.md`；依赖：无；依据：AGENTS；plan §2。
  - 执行：核对本地技能与官方资料，选择 Java21、Boot4稳定补丁、MyBatis兼容Starter、MySQL8子版本/补丁、Node/Vite、测试模块和镜像族；记录BOM外版本与校验值。
  - 验收：列出精确版本与兼容依据；不修改业务Q结论，不虚构依赖已安装。

- [x] **T017** [P] **[CONTRACT] 冻结 REST/OpenAPI 契约**
  - 主要文件：`docs/contracts/openapi.yaml`；依赖：T001～T015；依据：ACC、CAT、CRT、ORD、INV、PER；plan §6。
  - 执行：逐项声明 plan §6 的端点、DTO、API-Version、Cookie/CSRF、Idempotency-Key、金额字符串、身份404和Problem Details；依据已决Q定义必填/可空/排序。
  - 验收：契约lint通过；无范围外真实支付或后台管理业务；字段与spec一致。

- [x] **T018** [P] **[DOC] 记录旧文件迁移清单和内容摘要**
  - 主要文件：`docs/baseline/legacy-manifest.json`；依赖：无；依据：plan §3.3；旧基线。
  - 执行：枚举当前Git已跟踪pom、src、baseline和旧脚本的路径与SHA256；记录原始提交和已有修改；仅记录，不移动文件。
  - 验收：清单逐文件可校验；不包含构建产物、凭据或用户未跟踪文件。

## Phase 1：开发环境搭建

执行编号：**T019～T026**；本阶段 8 项任务。按正文顺序推进，只有依赖已完成的 [P] 任务可并行；Q决策或环境条件不足时明确标阻塞。

- [x] **T019** [P] **[DOC] 定义开发机支持矩阵与准备清单**
  - 主要文件：`docs/development/platform.md`；依赖：T016；依据：plan §9、§11；开发与交付补充。
  - 执行：记录当前OS/CPU架构，确认JDK21、可选legacy完整JDK8、锁定Node/npm、Python3、Git、Docker及Compose v2、网络/端口/磁盘要求；给出各平台安装与验证命令，镜像需支持实际CPU架构。
  - 验收：工具版本与T016一致；明确Python3为脚本测试的最小引导前提；不要求本机安装第二份MySQL服务。

- [x] **T020** [P] **[CONFIG] 固定开发用 Java 版本声明**
  - 主要文件：`.java-version`；依赖：T016；依据：plan §9、§11；开发与交付补充。
  - 执行：仅声明T016选择的Java21分发版本，说明由版本管理器或显式项目环境选择，不修改全局默认Java。
  - 验收：声明与构建/runtime的Java21一致；legacy单独使用完整JDK8。

- [x] **T021** [P] **[CONFIG] 固定开发用 Node 版本声明**
  - 主要文件：`.node-version`；依赖：T016；依据：plan §9、§11；开发与交付补充。
  - 执行：仅声明与所选Vite兼容的锁定Node版本；沿用已决npm/lockfile策略，不引入第二包管理器。
  - 验收：Node版本与frontend engines和CI一致，可由已采用的版本管理器读取。

- [x] **T022** [P] **[CONFIG] 提供开发环境变量模板**
  - 主要文件：`.env.example`；依赖：T016；依据：plan §9、§11；开发与交付补充。
  - 执行：定义与应用、MySQL Compose一致的变量名及非敏感示例：数据库名、开发端口、应用账号、JDBC配置；密码等字段只放显式占位符，注明本地文件不可提交，不能作为prod配置。
  - 验收：必需字段/来源清楚；无真实密钥，无可直接误用的生产凭据；T166忽略本地.env及.env.dev。

- [x] **T023** [P] **[CONFIG] 提供早期数据库开发环境**
  - 主要文件：`infra/compose.dev.yml`；依赖：T016、T022；依据：plan §9、§11；开发与交付补充。
  - 执行：只配置固定版本MySQL、持久卷、healthcheck、localhost端口和开发变量注入；不依赖已打包backend镜像，使数据库可在Phase1启动；不与E2E Compose同时占用相同端口/卷。
  - 验收：docker compose --env-file .env.dev -f infra/compose.dev.yml config与up -d可用；不自动清空已有卷，不把root凭据给应用。

- [x] **T024** [P] **[OPS] 安装并准备开发机工具**
  - 主要文件：`docs/development/toolchain-setup.md`；依赖：T019～T021；依据：plan §9、§11；开发与交付补充。
  - 执行：在获授权的开发机核对已有工具，使用已有版本管理器/系统安装方式准备缺少的完整JDK21、Node/npm、Python3、Git、Docker/Compose；启动daemon并记录实际命令/版本；legacy需要时另装完整JDK8。
  - 验收：命令行实际可用、daemon可连接、CPU架构匹配；本文件只记录脱敏安装证据，权限或安装条件缺失明确标阻塞。

- [x] **T025** [P] **[TEST] RED：开发环境诊断脚本**
  - 主要文件：`scripts/tests/test_dev_doctor.py`；依赖：T019、T024；依据：plan §9、§11；开发与交付补充。
  - 执行：先写并运行诊断测试：版本不符、误选Java8 JRE、Docker daemon不可用、Compose缺失、端口占用、磁盘不足和CPU镜像不兼容应有明确失败；mock系统调用，不强改用户环境。
  - 验收：目标脚本缺失/诊断不正确时有失败证据，测试不输出环境秘密，不跳过错误。

- [x] **T026** [P] **[IMPL] GREEN：开发环境诊断脚本**
  - 主要文件：`scripts/dev-doctor.py`；依赖：T019、T024、T025；依据：plan §9、§11；开发与交付补充。
  - 执行：仅实现此脚本：只读检测工具、版本、daemon、Compose、端口与资源，输出可操作修复提示和非零退出码；不自动覆盖JAVA_HOME或删除已有服务。
  - 验收：test_dev_doctor.py转绿；在已准备主机运行结果与真实状态相符，诊断不把本机旧默认Java误当项目Java。

## Phase 2：旧系统隔离

执行编号：**T027～T160**；本阶段 134 项任务。按正文顺序推进，只有依赖已完成的 [P] 任务可并行；Q决策或环境条件不足时明确标阻塞。

- [x] **T027** [P] **[MOVE] 归档旧文件：pom.xml**
  - 主要文件：`legacy/pom.xml`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `pom.xml` 迁移为 `legacy/pom.xml`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T028** [P] **[MOVE] 归档旧文件：baseline/pom.xml**
  - 主要文件：`legacy/baseline/pom.xml`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `baseline/pom.xml` 迁移为 `legacy/baseline/pom.xml`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T029** [P] **[MOVE] 归档旧文件：scripts/legacy-baseline.sh**
  - 主要文件：`legacy/scripts/legacy-baseline.sh`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `scripts/legacy-baseline.sh` 迁移为 `legacy/scripts/legacy-baseline.sh`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T030** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/app/account/AccountController.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/app/account/AccountController.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/app/account/AccountController.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/app/account/AccountController.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T031** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/app/account/AccountForm.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/app/account/AccountForm.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/app/account/AccountForm.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/app/account/AccountForm.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T032** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/app/account/AccountHelper.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/app/account/AccountHelper.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/app/account/AccountHelper.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/app/account/AccountHelper.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T033** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/app/account/PasswordEqualsValidator.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/app/account/PasswordEqualsValidator.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/app/account/PasswordEqualsValidator.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/app/account/PasswordEqualsValidator.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T034** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/app/cart/CartController.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/app/cart/CartController.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/app/cart/CartController.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/app/cart/CartController.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T035** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/app/cart/CartForm.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/app/cart/CartForm.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/app/cart/CartForm.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/app/cart/CartForm.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T036** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/app/cart/CartHelper.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/app/cart/CartHelper.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/app/cart/CartHelper.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/app/cart/CartHelper.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T037** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/app/catalog/CatalogController.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/app/catalog/CatalogController.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/app/catalog/CatalogController.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/app/catalog/CatalogController.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T038** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/app/catalog/ProductSearchForm.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/app/catalog/ProductSearchForm.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/app/catalog/ProductSearchForm.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/app/catalog/ProductSearchForm.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T039** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/app/common/session/EnableSynchronizeOnSessionPostProcessor.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/app/common/session/EnableSynchronizeOnSessionPostProcessor.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/app/common/session/EnableSynchronizeOnSessionPostProcessor.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/app/common/session/EnableSynchronizeOnSessionPostProcessor.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T040** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/app/common/session/HttpSessionEventLoggingListener.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/app/common/session/HttpSessionEventLoggingListener.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/app/common/session/HttpSessionEventLoggingListener.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/app/common/session/HttpSessionEventLoggingListener.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T041** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/app/order/OrderController.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/app/order/OrderController.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/app/order/OrderController.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/app/order/OrderController.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T042** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/app/order/OrderForm.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/app/order/OrderForm.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/app/order/OrderForm.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/app/order/OrderForm.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T043** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/app/order/OrderHelper.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/app/order/OrderHelper.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/app/order/OrderHelper.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/app/order/OrderHelper.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T044** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/common/exception/ResourceNotFoundException.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/common/exception/ResourceNotFoundException.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/common/exception/ResourceNotFoundException.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/common/exception/ResourceNotFoundException.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T045** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/model/Account.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/model/Account.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/model/Account.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/model/Account.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T046** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/model/Cart.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/model/Cart.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/model/Cart.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/model/Cart.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T047** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/model/CartItem.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/model/CartItem.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/model/CartItem.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/model/CartItem.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T048** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/model/Category.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/model/Category.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/model/Category.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/model/Category.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T049** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/model/Item.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/model/Item.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/model/Item.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/model/Item.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T050** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/model/LineItem.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/model/LineItem.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/model/LineItem.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/model/LineItem.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T051** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/model/Order.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/model/Order.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/model/Order.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/model/Order.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T052** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/model/Product.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/model/Product.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/model/Product.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/model/Product.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T053** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/model/Sequence.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/model/Sequence.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/model/Sequence.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/model/Sequence.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T054** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/model/UserDetails.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/model/UserDetails.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/model/UserDetails.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/model/UserDetails.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T055** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/repository/account/AccountRepository.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/repository/account/AccountRepository.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/repository/account/AccountRepository.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/repository/account/AccountRepository.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T056** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/repository/category/CategoryRepository.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/repository/category/CategoryRepository.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/repository/category/CategoryRepository.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/repository/category/CategoryRepository.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T057** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/repository/item/ItemRepository.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/repository/item/ItemRepository.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/repository/item/ItemRepository.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/repository/item/ItemRepository.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T058** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/repository/item/LineItemRepository.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/repository/item/LineItemRepository.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/repository/item/LineItemRepository.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/repository/item/LineItemRepository.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T059** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/repository/order/OrderRepository.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/repository/order/OrderRepository.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/repository/order/OrderRepository.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/repository/order/OrderRepository.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T060** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/repository/product/ProductRepository.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/repository/product/ProductRepository.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/repository/product/ProductRepository.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/repository/product/ProductRepository.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T061** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/repository/sequence/SequenceRepository.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/repository/sequence/SequenceRepository.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/repository/sequence/SequenceRepository.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/repository/sequence/SequenceRepository.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T062** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/service/account/AccountService.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/service/account/AccountService.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/service/account/AccountService.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/service/account/AccountService.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T063** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/service/account/AccountServiceImpl.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/service/account/AccountServiceImpl.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/service/account/AccountServiceImpl.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/service/account/AccountServiceImpl.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T064** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/service/catalog/CatalogService.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/service/catalog/CatalogService.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/service/catalog/CatalogService.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/service/catalog/CatalogService.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T065** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/service/catalog/CatalogServiceImpl.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/service/catalog/CatalogServiceImpl.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/service/catalog/CatalogServiceImpl.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/service/catalog/CatalogServiceImpl.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T066** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/service/order/OrderService.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/service/order/OrderService.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/service/order/OrderService.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/service/order/OrderService.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T067** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/service/order/OrderServiceImpl.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/service/order/OrderServiceImpl.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/service/order/OrderServiceImpl.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/service/order/OrderServiceImpl.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T068** [P] **[MOVE] 归档旧文件：src/main/java/ik/am/jpetstore/domain/service/user/UserDetailsServiceImpl.java**
  - 主要文件：`legacy/src/main/java/ik/am/jpetstore/domain/service/user/UserDetailsServiceImpl.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/java/ik/am/jpetstore/domain/service/user/UserDetailsServiceImpl.java` 迁移为 `legacy/src/main/java/ik/am/jpetstore/domain/service/user/UserDetailsServiceImpl.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T069** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/dozer/account-mapping.xml**
  - 主要文件：`legacy/src/main/resources/META-INF/dozer/account-mapping.xml`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/dozer/account-mapping.xml` 迁移为 `legacy/src/main/resources/META-INF/dozer/account-mapping.xml`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T070** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/css/jpetstore.css**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/css/jpetstore.css`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/css/jpetstore.css` 迁移为 `legacy/src/main/resources/META-INF/resources/css/jpetstore.css`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T071** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/banner_birds.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/banner_birds.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/banner_birds.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/banner_birds.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T072** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/banner_cats.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/banner_cats.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/banner_cats.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/banner_cats.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T073** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/banner_dogs.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/banner_dogs.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/banner_dogs.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/banner_dogs.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T074** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/banner_fish.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/banner_fish.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/banner_fish.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/banner_fish.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T075** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/banner_reptiles.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/banner_reptiles.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/banner_reptiles.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/banner_reptiles.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T076** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/bird1.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/bird1.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/bird1.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/bird1.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T077** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/bird2.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/bird2.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/bird2.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/bird2.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T078** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/birds_icon.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/birds_icon.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/birds_icon.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/birds_icon.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T079** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/cart.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/cart.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/cart.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/cart.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T080** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/cat1.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/cat1.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/cat1.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/cat1.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T081** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/cat2.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/cat2.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/cat2.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/cat2.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T082** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/cats_icon.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/cats_icon.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/cats_icon.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/cats_icon.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T083** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/dog1.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/dog1.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/dog1.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/dog1.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T084** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/dog2.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/dog2.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/dog2.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/dog2.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T085** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/dog3.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/dog3.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/dog3.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/dog3.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T086** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/dog4.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/dog4.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/dog4.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/dog4.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T087** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/dog5.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/dog5.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/dog5.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/dog5.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T088** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/dog6.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/dog6.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/dog6.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/dog6.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T089** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/dogs.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/dogs.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/dogs.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/dogs.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T090** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/dogs_icon.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/dogs_icon.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/dogs_icon.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/dogs_icon.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T091** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/fish.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/fish.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/fish.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/fish.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T092** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/fish1.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/fish1.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/fish1.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/fish1.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T093** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/fish2.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/fish2.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/fish2.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/fish2.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T094** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/fish3.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/fish3.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/fish3.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/fish3.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T095** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/fish4.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/fish4.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/fish4.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/fish4.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T096** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/fish_icon.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/fish_icon.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/fish_icon.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/fish_icon.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T097** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/lizard1.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/lizard1.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/lizard1.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/lizard1.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T098** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/logo-topbar.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/logo-topbar.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/logo-topbar.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/logo-topbar.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T099** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/poweredby.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/poweredby.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/poweredby.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/poweredby.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T100** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/reptiles_icon.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/reptiles_icon.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/reptiles_icon.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/reptiles_icon.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T101** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/separator.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/separator.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/separator.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/separator.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T102** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/sm_birds.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/sm_birds.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/sm_birds.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/sm_birds.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T103** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/sm_cats.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/sm_cats.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/sm_cats.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/sm_cats.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T104** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/sm_dogs.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/sm_dogs.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/sm_dogs.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/sm_dogs.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T105** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/sm_fish.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/sm_fish.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/sm_fish.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/sm_fish.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T106** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/sm_reptiles.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/sm_reptiles.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/sm_reptiles.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/sm_reptiles.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T107** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/snake1.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/snake1.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/snake1.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/snake1.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T108** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/resources/images/splash.gif**
  - 主要文件：`legacy/src/main/resources/META-INF/resources/images/splash.gif`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/resources/images/splash.gif` 迁移为 `legacy/src/main/resources/META-INF/resources/images/splash.gif`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T109** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/spring/application.properties**
  - 主要文件：`legacy/src/main/resources/META-INF/spring/application.properties`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/spring/application.properties` 迁移为 `legacy/src/main/resources/META-INF/spring/application.properties`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T110** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/spring/applicationContext.xml**
  - 主要文件：`legacy/src/main/resources/META-INF/spring/applicationContext.xml`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/spring/applicationContext.xml` 迁移为 `legacy/src/main/resources/META-INF/spring/applicationContext.xml`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T111** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/spring/spring-jpetstore-domain.xml**
  - 主要文件：`legacy/src/main/resources/META-INF/spring/spring-jpetstore-domain.xml`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/spring/spring-jpetstore-domain.xml` 迁移为 `legacy/src/main/resources/META-INF/spring/spring-jpetstore-domain.xml`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T112** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/spring/spring-jpetstore-env.xml**
  - 主要文件：`legacy/src/main/resources/META-INF/spring/spring-jpetstore-env.xml`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/spring/spring-jpetstore-env.xml` 迁移为 `legacy/src/main/resources/META-INF/spring/spring-jpetstore-env.xml`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T113** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/spring/spring-jpetstore-infra.properties**
  - 主要文件：`legacy/src/main/resources/META-INF/spring/spring-jpetstore-infra.properties`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/spring/spring-jpetstore-infra.properties` 迁移为 `legacy/src/main/resources/META-INF/spring/spring-jpetstore-infra.properties`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T114** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/spring/spring-jpetstore-infra.xml**
  - 主要文件：`legacy/src/main/resources/META-INF/spring/spring-jpetstore-infra.xml`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/spring/spring-jpetstore-infra.xml` 迁移为 `legacy/src/main/resources/META-INF/spring/spring-jpetstore-infra.xml`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T115** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/spring/spring-mvc.xml**
  - 主要文件：`legacy/src/main/resources/META-INF/spring/spring-mvc.xml`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/spring/spring-mvc.xml` 迁移为 `legacy/src/main/resources/META-INF/spring/spring-mvc.xml`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T116** [P] **[MOVE] 归档旧文件：src/main/resources/META-INF/spring/spring-security.xml**
  - 主要文件：`legacy/src/main/resources/META-INF/spring/spring-security.xml`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/META-INF/spring/spring-security.xml` 迁移为 `legacy/src/main/resources/META-INF/spring/spring-security.xml`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T117** [P] **[MOVE] 归档旧文件：src/main/resources/database/H2-dataload.sql**
  - 主要文件：`legacy/src/main/resources/database/H2-dataload.sql`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/database/H2-dataload.sql` 迁移为 `legacy/src/main/resources/database/H2-dataload.sql`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T118** [P] **[MOVE] 归档旧文件：src/main/resources/database/H2-schema.sql**
  - 主要文件：`legacy/src/main/resources/database/H2-schema.sql`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/database/H2-schema.sql` 迁移为 `legacy/src/main/resources/database/H2-schema.sql`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T119** [P] **[MOVE] 归档旧文件：src/main/resources/i18n/application-messages.properties**
  - 主要文件：`legacy/src/main/resources/i18n/application-messages.properties`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/i18n/application-messages.properties` 迁移为 `legacy/src/main/resources/i18n/application-messages.properties`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T120** [P] **[MOVE] 归档旧文件：src/main/resources/ik/am/jpetstore/domain/repository/account/AccountRepository.xml**
  - 主要文件：`legacy/src/main/resources/ik/am/jpetstore/domain/repository/account/AccountRepository.xml`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/ik/am/jpetstore/domain/repository/account/AccountRepository.xml` 迁移为 `legacy/src/main/resources/ik/am/jpetstore/domain/repository/account/AccountRepository.xml`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T121** [P] **[MOVE] 归档旧文件：src/main/resources/ik/am/jpetstore/domain/repository/category/CategoryRepository.xml**
  - 主要文件：`legacy/src/main/resources/ik/am/jpetstore/domain/repository/category/CategoryRepository.xml`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/ik/am/jpetstore/domain/repository/category/CategoryRepository.xml` 迁移为 `legacy/src/main/resources/ik/am/jpetstore/domain/repository/category/CategoryRepository.xml`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T122** [P] **[MOVE] 归档旧文件：src/main/resources/ik/am/jpetstore/domain/repository/item/ItemRepository.xml**
  - 主要文件：`legacy/src/main/resources/ik/am/jpetstore/domain/repository/item/ItemRepository.xml`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/ik/am/jpetstore/domain/repository/item/ItemRepository.xml` 迁移为 `legacy/src/main/resources/ik/am/jpetstore/domain/repository/item/ItemRepository.xml`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T123** [P] **[MOVE] 归档旧文件：src/main/resources/ik/am/jpetstore/domain/repository/item/LineItemRepository.xml**
  - 主要文件：`legacy/src/main/resources/ik/am/jpetstore/domain/repository/item/LineItemRepository.xml`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/ik/am/jpetstore/domain/repository/item/LineItemRepository.xml` 迁移为 `legacy/src/main/resources/ik/am/jpetstore/domain/repository/item/LineItemRepository.xml`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T124** [P] **[MOVE] 归档旧文件：src/main/resources/ik/am/jpetstore/domain/repository/order/OrderRepository.xml**
  - 主要文件：`legacy/src/main/resources/ik/am/jpetstore/domain/repository/order/OrderRepository.xml`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/ik/am/jpetstore/domain/repository/order/OrderRepository.xml` 迁移为 `legacy/src/main/resources/ik/am/jpetstore/domain/repository/order/OrderRepository.xml`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T125** [P] **[MOVE] 归档旧文件：src/main/resources/ik/am/jpetstore/domain/repository/product/ProductRepository.xml**
  - 主要文件：`legacy/src/main/resources/ik/am/jpetstore/domain/repository/product/ProductRepository.xml`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/ik/am/jpetstore/domain/repository/product/ProductRepository.xml` 迁移为 `legacy/src/main/resources/ik/am/jpetstore/domain/repository/product/ProductRepository.xml`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T126** [P] **[MOVE] 归档旧文件：src/main/resources/ik/am/jpetstore/domain/repository/sequence/SequenceRepository.xml**
  - 主要文件：`legacy/src/main/resources/ik/am/jpetstore/domain/repository/sequence/SequenceRepository.xml`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/ik/am/jpetstore/domain/repository/sequence/SequenceRepository.xml` 迁移为 `legacy/src/main/resources/ik/am/jpetstore/domain/repository/sequence/SequenceRepository.xml`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T127** [P] **[MOVE] 归档旧文件：src/main/resources/log4jdbc.properties**
  - 主要文件：`legacy/src/main/resources/log4jdbc.properties`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/log4jdbc.properties` 迁移为 `legacy/src/main/resources/log4jdbc.properties`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T128** [P] **[MOVE] 归档旧文件：src/main/resources/logback.xml**
  - 主要文件：`legacy/src/main/resources/logback.xml`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/resources/logback.xml` 迁移为 `legacy/src/main/resources/logback.xml`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T129** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/account/EditAccountForm.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/account/EditAccountForm.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/account/EditAccountForm.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/account/EditAccountForm.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T130** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/account/IncludeAccountFields.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/account/IncludeAccountFields.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/account/IncludeAccountFields.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/account/IncludeAccountFields.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T131** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/account/NewAccountForm.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/account/NewAccountForm.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/account/NewAccountForm.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/account/NewAccountForm.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T132** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/account/SignonForm.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/account/SignonForm.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/account/SignonForm.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/account/SignonForm.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T133** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/cart/Cart.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/cart/Cart.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/cart/Cart.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/cart/Cart.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T134** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/cart/Checkout.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/cart/Checkout.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/cart/Checkout.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/cart/Checkout.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T135** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/cart/IncludeMyList.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/cart/IncludeMyList.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/cart/IncludeMyList.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/cart/IncludeMyList.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T136** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/catalog/Category.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/catalog/Category.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/catalog/Category.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/catalog/Category.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T137** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/catalog/Item.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/catalog/Item.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/catalog/Item.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/catalog/Item.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T138** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/catalog/Main.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/catalog/Main.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/catalog/Main.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/catalog/Main.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T139** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/catalog/Product.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/catalog/Product.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/catalog/Product.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/catalog/Product.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T140** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/catalog/SearchProducts.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/catalog/SearchProducts.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/catalog/SearchProducts.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/catalog/SearchProducts.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T141** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/common/Error.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/common/Error.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/common/Error.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/common/Error.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T142** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/common/IncludeBottom.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/common/IncludeBottom.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/common/IncludeBottom.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/common/IncludeBottom.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T143** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/common/IncludeTop.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/common/IncludeTop.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/common/IncludeTop.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/common/IncludeTop.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T144** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/common/include.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/common/include.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/common/include.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/common/include.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T145** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/notFoundError.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/notFoundError.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/notFoundError.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/notFoundError.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T146** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/order/ConfirmOrder.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/order/ConfirmOrder.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/order/ConfirmOrder.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/order/ConfirmOrder.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T147** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/order/ListOrders.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/order/ListOrders.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/order/ListOrders.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/order/ListOrders.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T148** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/order/NewOrderForm.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/order/NewOrderForm.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/order/NewOrderForm.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/order/NewOrderForm.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T149** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/order/ShippingForm.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/order/ShippingForm.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/order/ShippingForm.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/order/ShippingForm.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T150** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/order/ViewOrder.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/order/ViewOrder.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/order/ViewOrder.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/order/ViewOrder.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T151** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/views/systemError.jsp**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/views/systemError.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/views/systemError.jsp` 迁移为 `legacy/src/main/webapp/WEB-INF/views/systemError.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T152** [P] **[MOVE] 归档旧文件：src/main/webapp/WEB-INF/web.xml**
  - 主要文件：`legacy/src/main/webapp/WEB-INF/web.xml`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/WEB-INF/web.xml` 迁移为 `legacy/src/main/webapp/WEB-INF/web.xml`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T153** [P] **[MOVE] 归档旧文件：src/main/webapp/index.jsp**
  - 主要文件：`legacy/src/main/webapp/index.jsp`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/main/webapp/index.jsp` 迁移为 `legacy/src/main/webapp/index.jsp`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T154** [P] **[MOVE] 归档旧文件：src/test/java/ik/am/jpetstore/baseline/LegacyKnownDefectTest.java**
  - 主要文件：`legacy/src/test/java/ik/am/jpetstore/baseline/LegacyKnownDefectTest.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/test/java/ik/am/jpetstore/baseline/LegacyKnownDefectTest.java` 迁移为 `legacy/src/test/java/ik/am/jpetstore/baseline/LegacyKnownDefectTest.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T155** [P] **[MOVE] 归档旧文件：src/test/java/ik/am/jpetstore/baseline/LegacyServiceRegressionTest.java**
  - 主要文件：`legacy/src/test/java/ik/am/jpetstore/baseline/LegacyServiceRegressionTest.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/test/java/ik/am/jpetstore/baseline/LegacyServiceRegressionTest.java` 迁移为 `legacy/src/test/java/ik/am/jpetstore/baseline/LegacyServiceRegressionTest.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T156** [P] **[MOVE] 归档旧文件：src/test/java/ik/am/jpetstore/baseline/LegacyTestSupport.java**
  - 主要文件：`legacy/src/test/java/ik/am/jpetstore/baseline/LegacyTestSupport.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/test/java/ik/am/jpetstore/baseline/LegacyTestSupport.java` 迁移为 `legacy/src/test/java/ik/am/jpetstore/baseline/LegacyTestSupport.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T157** [P] **[MOVE] 归档旧文件：src/test/java/ik/am/jpetstore/baseline/LegacyWebRegressionTest.java**
  - 主要文件：`legacy/src/test/java/ik/am/jpetstore/baseline/LegacyWebRegressionTest.java`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/test/java/ik/am/jpetstore/baseline/LegacyWebRegressionTest.java` 迁移为 `legacy/src/test/java/ik/am/jpetstore/baseline/LegacyWebRegressionTest.java`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T158** [P] **[MOVE] 归档旧文件：src/test/resources/baseline-logback.xml**
  - 主要文件：`legacy/src/test/resources/baseline-logback.xml`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/test/resources/baseline-logback.xml` 迁移为 `legacy/src/test/resources/baseline-logback.xml`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T159** [P] **[MOVE] 归档旧文件：src/test/resources/baseline-reset.sql**
  - 主要文件：`legacy/src/test/resources/baseline-reset.sql`；依赖：T018；依据：plan §3.3；legacy-manifest。
  - 执行：将唯一逻辑文件 `src/test/resources/baseline-reset.sql` 迁移为 `legacy/src/test/resources/baseline-reset.sql`，保留内容和Git可追溯性；校对manifest摘要，遇到用户改动先保留。
  - 验收：只有这一项逻辑文件路径改变，内容摘要一致；不执行目录级批量移动。

- [x] **T160** **[VERIFY] 校验旧文件逐项迁移**
  - 主要文件：`docs/baseline/legacy-relocation-check.md`；依赖：T027～T159；依据：plan §10、§11。
  - 执行：核对清单与实际文件、逐项摘要；确认没有旧源码/测试进入backend；尝试 bash legacy/scripts/legacy-baseline.sh，说明历史依赖阻碍；只在本报告记录环境、结果、证据及未验证项，发现实现缺口则新增单文件任务。
  - 验收：相关断言均通过或明确标记阻塞；不能将未执行、跳过或缺陷特征测试通过写成目标已完成。

## Phase 3：工程基础

执行编号：**T161～T180**；本阶段 20 项任务。按正文顺序推进，只有依赖已完成的 [P] 任务可并行；Q决策或环境条件不足时明确标阻塞。

- [x] **T161** [P] **[CONFIG] 创建现代根 Maven 聚合**
  - 主要文件：`pom.xml`；依赖：T016、T027；依据：AGENTS；plan §2、§3。
  - 执行：创建Java21/Boot4 parent/BOM与仅backend子模块，声明BOM外集中版本；legacy不加入现代构建。
  - 验收：effective-pom可解析；无Spring3/旧测试参与现代构建。

- [x] **T162** [P] **[CONFIG] 声明后端编译与专用测试依赖**
  - 主要文件：`backend/pom.xml`；依赖：T161；依据：AGENTS；plan §2。
  - 执行：配置release21、webmvc/security/validation/jdbc/MyBatis/Flyway+flyway-mysql/Actuator及专用测试Starter及版本集中锁定的ArchUnit；配置Surefire/Failsafe命名；不引入classic桥接Starter。
  - 验收：mvn -f backend/pom.xml test-compile可用；dependency:tree无混合Spring/Jackson定制。

- [x] **T163** [P] **[CONFIG] 锁定 Maven Wrapper 分发**
  - 主要文件：`.mvn/wrapper/maven-wrapper.properties`；依赖：T016；依据：plan §9.3。
  - 执行：只创建Wrapper分发/校验配置，采用支持的only-script分发策略，记录固定校验值。
  - 验收：配置可被平台脚本读取，不下载未经锁定分发。

- [x] **T164** [P] **[CONFIG] 创建 Unix Maven Wrapper**
  - 主要文件：`mvnw`；依赖：T163；依据：AGENTS。
  - 执行：从所选官方Wrapper取得Unix脚本，仅落此文件并设置可执行位。
  - 验收：./mvnw --version使用固定分发；不自动修改POM或Windows脚本。

- [x] **T165** [P] **[CONFIG] 创建 Windows Maven Wrapper**
  - 主要文件：`mvnw.cmd`；依赖：T163；依据：AGENTS。
  - 执行：取得同一Wrapper版本的Windows脚本，仅创建此文件。
  - 验收：Windows语法及分发路径可复核；与Unix使用同一properties。

- [x] **T166** [P] **[CONFIG] 更新构建产物忽略规则**
  - 主要文件：`.gitignore`；依赖：无；依据：plan §9.3。
  - 执行：仅补充backend/target、frontend/node_modules/dist、Playwright报告、IDE及本地环境文件忽略；保留现有规则。
  - 验收：不忽略源码、lockfile、迁移或正式文档。

- [x] **T167** [P] **[CONFIG] 声明前端工具链和脚本**
  - 主要文件：`frontend/package.json`；依赖：T016；依据：plan §2、§8。
  - 执行：配置Vue/TS/Vite/Router/Pinia、Vitest/Vue Test Utils/Playwright、vue-tsc、lint、OpenAPI类型生成与契约lint工具；声明typecheck/test/build脚本。
  - 验收：脚本语义明确，未引入服务器端第二业务后端。

- [x] **T168** [P] **[CONFIG] 生成前端确定性锁文件**
  - 主要文件：`frontend/package-lock.json`；依赖：T167；依据：plan §9.3。
  - 执行：按已锁Node/npm生成唯一lockfile；此任务不得同时改package.json。
  - 验收：npm ci可复现；无安装脚本写入其他受跟踪文件。

- [x] **T169** [P] **[CONFIG] 配置前端静态检查**
  - 主要文件：`frontend/eslint.config.js`；依赖：T167、T168；依据：plan §8、§9.3。
  - 执行：仅配置与Vue/TS兼容的已锁lint规则和构建产物忽略；不把格式检查当业务测试。
  - 验收：npm lint实际发现Vue/TS问题，测试文件不被全部排除。

- [x] **T170** [P] **[CONFIG] 启用 TypeScript 严格检查**
  - 主要文件：`frontend/tsconfig.json`；依赖：T167；依据：plan §8。
  - 执行：只配置strict、DOM/Vue编译上下文、测试类型及路径别名。
  - 验收：vue-tsc能识别项目，不用any绕过契约错误。

- [x] **T171** [P] **[CONTRACT] 声明Vite环境与Vue模块编译类型**
  - 主要文件：`frontend/src/env.d.ts`；依赖：T170；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T172** [P] **[CONFIG] 建立组件测试配置**
  - 主要文件：`frontend/vitest.config.ts`；依赖：T168、T170；依据：plan §10。
  - 执行：配置jsdom、Vue插件、测试匹配与必要隔离；不在此添加业务mock。
  - 验收：空测试环境可启动，随后组件测试由该配置执行。

- [x] **T173** [P] **[FIXTURE] 提供目标 MySQL 测试容器**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/support/MySqlTestDatabase.java`；依赖：T016、T023、T024、T026、T162；依据：plan §10。
  - 执行：定义同版本MySQL容器及JDBC参数，提供独立schema/连接获取方式；不自动使用H2，不吞Docker缺失。
  - 验收：可供迁移/Mapper/并发测试复用；没有业务断言或正式库写入。

- [x] **T174** [P] **[FIXTURE] 准备新系统可重复业务测试数据**
  - 主要文件：`backend/src/test/resources/fixtures/shop.sql`；依赖：T016；依据：AT-01～18；plan §7。
  - 执行：仅写隔离测试库账号、分类、SKU、库存等确定性数据；密码使用测试hash；数据与正式迁移分开。
  - 验收：包含两用户、多SKU、低库存、缺横幅和历史订单测试所需关联；不向生产导入。

- [x] **T175** [P] **[TEST] RED：Boot21启动类与扫描边界**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/ApplicationBoundaryTest.java`；依赖：T162；依据：plan §2、§3。
  - 执行：先编写目标行为测试并运行聚焦命令；断言backend应用扫描限定新包且不导入legacy XML；基础上下文可通过显式配置启动。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T176** [P] **[IMPL] GREEN：Boot21启动类与扫描边界**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/JPetStoreApplication.java`；依赖：T162、T175；依据：plan §2、§3。
  - 执行：仅在此文件实现Boot21启动类与扫描边界，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T177** [P] **[TEST] RED：应用环境配置**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/configuration/ApplicationConfigurationTest.java`；依赖：T162、T173；依据：plan §7、§9。
  - 执行：先编写目标行为测试并运行聚焦命令；验证真实MySQL配置、MyBatis缓存关闭、Flyway严格验证、无生产演示seed、缺必要配置失败；避免仅比对YAML文本。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T178** [P] **[IMPL] GREEN：应用环境配置**
  - 主要文件：`backend/src/main/resources/application.yml`；依赖：T162、T173、T177；依据：plan §7、§9。
  - 执行：仅在此文件实现应用环境配置，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T179** [P] **[VERIFY] 从干净检出验证基础开发环境**
  - 主要文件：`docs/development/environment-verification.md`；依赖：T023、T024、T026、T162、T164～T166、T168、T173、T178；依据：plan §9、§11；开发与交付补充。
  - 执行：按模板在忽略的本地文件配置开发变量，启动开发MySQL并检查健康/受限账号连接；运行doctor、mvnw --version、后端test-compile、npm ci和MySQL容器最小连接验证；记录端口和命令，暂不声称完整业务页面已可运行。
  - 验收：新开发机不依赖旧Maven缓存、已启动共享数据库或手工改源码即可准备完成；不能把缺Docker、依赖下载失败或零测试记成功。

- [x] **T180** [P] **[DOC] 编写开发启动、调试与重置指南**
  - 主要文件：`docs/development/README.md`；依赖：T179；依据：plan §9、§11；开发与交付补充。
  - 执行：记录项目Java/Node选择、开发变量、仅数据库Compose、backend本地run、Vite代理、IDE调试、测试和停止命令；首次启动自动迁移、可选dev seed及端口冲突排查明确；明确Boot不会自动读取.env，需要环境导出或IDE运行配置，secret不得加VITE_前缀；删除数据单独显式操作。
  - 验收：按“数据库→后端→前端”可操作；普通停止不删卷；标注全链路启动将在T406验证，不假定尚未开发的页面已就绪。

## Phase 4：数据契约、领域规则与数据库

执行编号：**T181～T230**；本阶段 50 项任务。按正文顺序推进，只有依赖已完成的 [P] 任务可并行；Q决策或环境条件不足时明确标阻塞。

- [x] **T181** [P] **[TEST] RED：十进制金额值及边界**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/shared/domain/MoneyTest.java`；依赖：T006、T162；依据：ORD-14～16；Q06。
  - 执行：先编写目标行为测试并运行聚焦命令；覆盖精确乘加、范围、舍入和无隐含税费；输入范围按Q06。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T182** [P] **[IMPL] GREEN：十进制金额值及边界**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/shared/domain/Money.java`；依赖：T006、T162、T181；依据：ORD-14～16；Q06。
  - 执行：仅在此文件实现十进制金额值及边界，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T183** [P] **[CONFIG] 注入统一 UTC Clock**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/shared/configuration/ClockConfiguration.java`；依赖：T011、T162；依据：ORD-08；Q11。
  - 执行：仅提供Clock bean和可测试替换点；不在配置中猜测旧DATE时刻。
  - 验收：业务可注入固定时钟，展示时区依Q11。

- [x] **T184** [P] **[CONTRACT] 定义账户资料、偏好与凭据内部数据类型**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/account/domain/AccountModels.java`；依赖：T001、T009、T010、T013、T162；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T185** [P] **[CONTRACT] 定义分类、产品、SKU及受控展示数据类型**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/catalog/domain/CatalogModels.java`；依赖：T010、T014、T162；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T186** [P] **[CONTRACT] 定义订单及成交明细不可变快照类型**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/domain/OrderModels.java`；依赖：T006、T007、T011、T015、T162、T182；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T187** [P] **[CONTRACT] 定义确认快照、归属、版本和状态类型**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/domain/CheckoutModels.java`；依赖：T003、T004、T015、T162、T182；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T188** [P] **[CONTRACT] 定义幂等键、摘要和可重放结果类型**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/domain/OrderRequestModels.java`；依赖：T015、T162；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T189** [P] **[CONTRACT] 定义会话持有权、版本和明细数据类型**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/cart/domain/CartModels.java`；依赖：T008、T015、T162、T182；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T190** [P] **[CONTRACT] 定义仅本人可用账户查询端口**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/account/api/AccountQuery.java`；依赖：T184；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T191** [P] **[CONTRACT] 定义账户注册命令端口**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/account/api/AccountRegistration.java`；依赖：T184；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T192** [P] **[CONTRACT] 定义资料和可选密码更新端口**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/account/api/AccountProfileUpdate.java`；依赖：T184；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T193** [P] **[CONTRACT] 定义商品及偏好展示查询端口**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/catalog/api/CatalogQuery.java`；依赖：T185；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T194** [P] **[CONTRACT] 定义服务端价格读取契约**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/catalog/api/PricingQuery.java`；依赖：T182、T185；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T195** [P] **[CONTRACT] 定义会话购物车读取契约**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/cart/api/CartQuery.java`；依赖：T189；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T196** [P] **[CONTRACT] 定义单项及整批数量命令契约**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/cart/api/CartCommand.java`；依赖：T189；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T197** [P] **[CONTRACT] 定义同事务锁定及消费购物车端口**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/cart/api/CartCheckoutAccess.java`；依赖：T189；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T198** [P] **[CONTRACT] 定义创建与读取本人确认快照端口**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/api/CheckoutCommand.java`；依赖：T187；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T199** [P] **[CONTRACT] 定义带幂等键的订单提交端口**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/api/OrderCommand.java`；依赖：T186、T188；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T200** [P] **[CONTRACT] 定义本人订单列表/详情端口**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/api/OrderQuery.java`；依赖：T186；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T201** [P] **[TEST] RED：account HTTP请求/响应契约及验证**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/account/web/AccountDtosTest.java`；依赖：T001、T013、T017、T162；依据：plan §6；spec §6。
  - 执行：先编写目标行为测试并运行聚焦命令；验证该模块JSON结构、金额/ID字符串、必填/可空、拒绝敏感字段及合法枚举；不写getter镜像测试。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T202** [P] **[IMPL] GREEN：account HTTP请求/响应契约及验证**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/account/web/AccountDtos.java`；依赖：T001、T013、T017、T162、T201；依据：plan §6；spec §6。
  - 执行：仅在此文件实现account HTTP请求/响应契约及验证，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T203** [P] **[CONTRACT] account显式DTO/领域转换接口**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/account/web/AccountAssembler.java`；依赖：T184、T202；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T204** [P] **[TEST] RED：identity HTTP请求/响应契约及验证**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/identity/web/IdentityDtosTest.java`；依赖：T008、T013、T017、T162；依据：plan §6；spec §6。
  - 执行：先编写目标行为测试并运行聚焦命令；验证该模块JSON结构、金额/ID字符串、必填/可空、拒绝敏感字段及合法枚举；不写getter镜像测试。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T205** [P] **[IMPL] GREEN：identity HTTP请求/响应契约及验证**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/identity/web/IdentityDtos.java`；依赖：T008、T013、T017、T162、T204；依据：plan §6；spec §6。
  - 执行：仅在此文件实现identity HTTP请求/响应契约及验证，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T206** [P] **[CONTRACT] identity显式DTO/领域转换接口**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/identity/web/IdentityAssembler.java`；依赖：T184、T205；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T207** [P] **[TEST] RED：catalog HTTP请求/响应契约及验证**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/catalog/web/CatalogDtosTest.java`；依赖：T010、T014、T017、T162；依据：plan §6；spec §6。
  - 执行：先编写目标行为测试并运行聚焦命令；验证该模块JSON结构、金额/ID字符串、必填/可空、拒绝敏感字段及合法枚举；不写getter镜像测试。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T208** [P] **[IMPL] GREEN：catalog HTTP请求/响应契约及验证**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/catalog/web/CatalogDtos.java`；依赖：T010、T014、T017、T162、T207；依据：plan §6；spec §6。
  - 执行：仅在此文件实现catalog HTTP请求/响应契约及验证，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T209** [P] **[CONTRACT] catalog显式DTO/领域转换接口**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/catalog/web/CatalogAssembler.java`；依赖：T185、T208；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T210** [P] **[TEST] RED：cart HTTP请求/响应契约及验证**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/cart/web/CartDtosTest.java`；依赖：T002、T008、T015、T017、T162；依据：plan §6；spec §6。
  - 执行：先编写目标行为测试并运行聚焦命令；验证该模块JSON结构、金额/ID字符串、必填/可空、拒绝敏感字段及合法枚举；不写getter镜像测试。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T211** [P] **[IMPL] GREEN：cart HTTP请求/响应契约及验证**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/cart/web/CartDtos.java`；依赖：T002、T008、T015、T017、T162、T210；依据：plan §6；spec §6。
  - 执行：仅在此文件实现cart HTTP请求/响应契约及验证，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T212** [P] **[CONTRACT] cart显式DTO/领域转换接口**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/cart/web/CartAssembler.java`；依赖：T185、T189、T211；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T213** [P] **[TEST] RED：order HTTP请求/响应契约及验证**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/order/web/OrderDtosTest.java`；依赖：T003、T004、T006、T007、T011、T015、T017、T162；依据：plan §6；spec §6。
  - 执行：先编写目标行为测试并运行聚焦命令；验证该模块JSON结构、金额/ID字符串、必填/可空、拒绝敏感字段及合法枚举；不写getter镜像测试。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T214** [P] **[IMPL] GREEN：order HTTP请求/响应契约及验证**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/web/OrderDtos.java`；依赖：T003、T004、T006、T007、T011、T015、T017、T162、T213；依据：plan §6；spec §6。
  - 执行：仅在此文件实现order HTTP请求/响应契约及验证，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T215** [P] **[CONTRACT] order显式DTO/领域转换接口**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/web/OrderAssembler.java`；依赖：T186～T188、T214；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T216** [P] **[CONTRACT] 定义带稳定错误码的业务失败类型**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/shared/error/BusinessFailure.java`；依赖：T017；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T217** [P] **[CONTRACT] 生成 TypeScript API 类型**
  - 主要文件：`frontend/src/shared/api/schema.d.ts`；依赖：T017、T168；依据：plan §6、§8。
  - 执行：使用锁定的OpenAPI工具仅输出此声明文件；不生成业务行为或修改其他源码。
  - 验收：类型与OpenAPI一致；金额和大整数ID为string，不包含password响应字段。

- [x] **T218** [P] **[TEST] RED：MySQL迁移 V1__create_account_and_catalog.sql**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/migration/Schema1MigrationTest.java`；依赖：T001、T010、T013、T173、T178；依据：plan §7；NFR-01。
  - 执行：先编写目标行为测试并运行聚焦命令；从空schema验证账户凭据/偏好、商品关系、用户名比较、密码hash长度和外键。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T219** [P] **[IMPL] GREEN：MySQL迁移 V1__create_account_and_catalog.sql**
  - 主要文件：`backend/src/main/resources/db/migration/V1__create_account_and_catalog.sql`；依赖：T001、T010、T013、T173、T178、T218；依据：plan §7；NFR-01。
  - 执行：仅在此文件实现MySQL迁移 V1__create_account_and_catalog.sql，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T220** [P] **[TEST] RED：MySQL迁移 V2__create_orders_and_inventory.sql**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/migration/Schema2MigrationTest.java`；依赖：T002、T006、T007、T011、T015、T173、T178、T219；依据：plan §7；NFR-01。
  - 执行：先编写目标行为测试并运行聚焦命令；验证订单头/状态/明细/库存、金额与历史ID导入；约束按Q02/Q15。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T221** [P] **[IMPL] GREEN：MySQL迁移 V2__create_orders_and_inventory.sql**
  - 主要文件：`backend/src/main/resources/db/migration/V2__create_orders_and_inventory.sql`；依赖：T002、T006、T007、T011、T015、T173、T178、T219、T220；依据：plan §7；NFR-01。
  - 执行：仅在此文件实现MySQL迁移 V2__create_orders_and_inventory.sql，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T222** [P] **[TEST] RED：MySQL迁移 V3__create_cart_and_checkout.sql**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/migration/Schema3MigrationTest.java`；依赖：T003、T004、T008、T173、T178、T221；依据：plan §7；NFR-01。
  - 执行：先编写目标行为测试并运行聚焦命令；验证会话绑定、version、联合唯一、确认快照及消费状态。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T223** [P] **[IMPL] GREEN：MySQL迁移 V3__create_cart_and_checkout.sql**
  - 主要文件：`backend/src/main/resources/db/migration/V3__create_cart_and_checkout.sql`；依赖：T003、T004、T008、T173、T178、T221、T222；依据：plan §7；NFR-01。
  - 执行：仅在此文件实现MySQL迁移 V3__create_cart_and_checkout.sql，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T224** [P] **[TEST] RED：MySQL迁移 V4__create_order_idempotency.sql**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/migration/Schema4MigrationTest.java`；依赖：T173、T178、T223；依据：plan §7；NFR-01。
  - 执行：先编写目标行为测试并运行聚焦命令；验证(user,operation,key)唯一、hash/结果存储、checkout_id唯一以及过期索引。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T225** [P] **[IMPL] GREEN：MySQL迁移 V4__create_order_idempotency.sql**
  - 主要文件：`backend/src/main/resources/db/migration/V4__create_order_idempotency.sql`；依赖：T173、T178、T223、T224；依据：plan §7；NFR-01。
  - 执行：仅在此文件实现MySQL迁移 V4__create_order_idempotency.sql，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T226** [P] **[TEST] RED：MySQL迁移 V5__add_validated_constraints_and_indexes.sql**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/migration/Schema5MigrationTest.java`；依赖：T173、T178、T225；依据：plan §7；NFR-01。
  - 执行：先编写目标行为测试并运行聚焦命令；验证已清理数据上的约束与查询索引；未知/脏数据应明确失败，不自动baseline。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T227** [P] **[IMPL] GREEN：MySQL迁移 V5__add_validated_constraints_and_indexes.sql**
  - 主要文件：`backend/src/main/resources/db/migration/V5__add_validated_constraints_and_indexes.sql`；依赖：T173、T178、T225、T226；依据：plan §7；NFR-01。
  - 执行：仅在此文件实现MySQL迁移 V5__add_validated_constraints_and_indexes.sql，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T228** [P] **[TEST] RED：隔离的开发演示数据导入**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/configuration/DevDataSeederTest.java`；依赖：T176、T219、T221、T223、T225、T227；依据：plan §7.3。
  - 执行：先编写目标行为测试并运行聚焦命令；验证仅dev/test执行且幂等；prod无演示账号、无默认明文密码。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T229** [P] **[IMPL] GREEN：隔离的开发演示数据导入**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/shared/configuration/DevDataSeeder.java`；依赖：T176、T219、T221、T223、T225、T227、T228；依据：plan §7.3。
  - 执行：仅在此文件实现隔离的开发演示数据导入，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T230** [P] **[TEST] 建立模块依赖与分层架构守卫**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/architecture/ModuleBoundariesTest.java`；依赖：T162、T190～T200；依据：AGENTS；plan §3。
  - 执行：在实现业务服务前声明ArchUnit规则：无模块循环、domain不依赖Web、禁止跨模块Mapper/internal访问；catalog只读投影是明确允许项；规则对后来新增类持续生效。
  - 验收：测试可运行且规则覆盖所有六模块；无empty包就直接跳过整个规则的漏洞；此任务只有测试，不制造违规生产代码。

## Phase 5：通用 HTTP、商品、账户与认证

执行编号：**T231～T265**；本阶段 35 项任务。按正文顺序推进，只有依赖已完成的 [P] 任务可并行；Q决策或环境条件不足时明确标阻塞。

- [x] **T231** [P] **[TEST] RED：统一安全与MVC错误序列化**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/shared/web/ProblemDetailsWriterTest.java`；依赖：T176、T216；依据：plan §6.4；D07/D09。
  - 执行：先编写目标行为测试并运行聚焦命令；验证RFC9457字段、status一致、requestId、violations无敏感值和tools.jackson输出；过滤链/MVC复用同一工厂。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T232** [P] **[IMPL] GREEN：统一安全与MVC错误序列化**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/shared/web/ProblemDetailsWriter.java`；依赖：T176、T216、T231；依据：plan §6.4；D07/D09。
  - 执行：仅在此文件实现统一安全与MVC错误序列化，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T233** [P] **[TEST] RED：统一 Problem Details 映射**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/shared/web/ProblemDetailsAdviceTest.java`；依赖：T232；依据：plan §6.4；D07/D09。
  - 执行：先编写目标行为测试并运行聚焦命令；覆盖业务400/404/409、Malformed JSON、405/415、500脱敏及Content-Type，禁止全变500。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T234** [P] **[IMPL] GREEN：统一 Problem Details 映射**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/shared/web/ProblemDetailsAdvice.java`；依赖：T232、T233；依据：plan §6.4；D07/D09。
  - 执行：仅在此文件实现统一 Problem Details 映射，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T235** [P] **[TEST] RED：原生 API-Version 解析**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/shared/web/ApiVersionConfigurationTest.java`；依赖：T176、T234；依据：plan §6.1。
  - 执行：先编写目标行为测试并运行聚焦命令；默认1.0、支持1.0、未知版本400；只用请求头，不叠加路径版本。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T236** [P] **[IMPL] GREEN：原生 API-Version 解析**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/shared/web/ApiVersionConfiguration.java`；依赖：T176、T234、T235；依据：plan §6.1。
  - 执行：仅在此文件实现原生 API-Version 解析，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T237** [P] **[CONTRACT] 定义不含密码的会话身份**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/identity/domain/SessionPrincipal.java`；依赖：T184；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T238** [P] **[TEST] RED：当前身份读取与未认证处理**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/identity/application/CurrentUserTest.java`；依赖：T216、T237；依据：ORD-01；NFR-02。
  - 执行：先编写目标行为测试并运行聚焦命令；覆盖匿名、认证主体及错误主体类型；不接受请求username替换当前身份。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T239** [P] **[IMPL] GREEN：当前身份读取与未认证处理**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/identity/application/CurrentUser.java`；依赖：T216、T237、T238；依据：ORD-01；NFR-02。
  - 执行：仅在此文件实现当前身份读取与未认证处理，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T240** [P] **[CONTRACT] Account Mapper签名与类型约定**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/account/infrastructure/AccountMapper.java`；依赖：T184、T219、T221、T223、T225、T227；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T241** [P] **[TEST] RED：Account参数化 SQL**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/account/infrastructure/AccountMapperTest.java`；依赖：T173、T174、T240；依据：plan §5、§7。
  - 执行：先编写目标行为测试并运行聚焦命令；三表查询不依赖横幅；注册唯一与更新目标限定，密码hash非空。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T242** [P] **[IMPL] GREEN：Account参数化 SQL**
  - 主要文件：`backend/src/main/resources/mapper/account/AccountMapper.xml`；依赖：T173、T174、T240、T241；依据：plan §5、§7。
  - 执行：仅在此文件实现Account参数化 SQL，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T243** [P] **[CONTRACT] Catalog Mapper签名与类型约定**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/catalog/infrastructure/CatalogMapper.java`；依赖：T185、T219、T221、T223、T225、T227；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T244** [P] **[TEST] RED：Catalog参数化 SQL**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/catalog/infrastructure/CatalogMapperTest.java`；依赖：T173、T174、T243；依据：plan §5、§7。
  - 执行：先编写目标行为测试并运行聚焦命令；分类/产品/SKU关联、名称包含搜索、字面通配符按Q14、缺横幅正常、实时库存投影；SQL参数化。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T245** [P] **[IMPL] GREEN：Catalog参数化 SQL**
  - 主要文件：`backend/src/main/resources/mapper/catalog/CatalogMapper.xml`；依赖：T173、T174、T243、T244；依据：plan §5、§7。
  - 执行：仅在此文件实现Catalog参数化 SQL，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T246** [P] **[TEST] RED：商品和偏好展示查询服务**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/catalog/application/CatalogQueryServiceTest.java`；依赖：T193、T194、T216、T245；依据：CAT-01～08；PER-01～03；AT-05/06。
  - 执行：先编写目标行为测试并运行聚焦命令；覆盖层级/无结果、名称搜索范围、缺失资源、横幅缺失、实时价格；不写库存。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T247** [P] **[IMPL] GREEN：商品和偏好展示查询服务**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/catalog/application/CatalogQueryService.java`；依赖：T193、T194、T216、T245、T246；依据：CAT-01～08；PER-01～03；AT-05/06。
  - 执行：仅在此文件实现商品和偏好展示查询服务，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T248** [P] **[TEST] RED：密码编码与输入字节保护**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/identity/infrastructure/PasswordConfigurationTest.java`；依赖：T013、T162；依据：ACC-05/08；NFR-04；D01。
  - 执行：先编写目标行为测试并运行聚焦命令；编码不等于明文、算法标识可升级、错误密码失败、超字节限制明确拒绝；不静默截断。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T249** [P] **[IMPL] GREEN：密码编码与输入字节保护**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/identity/infrastructure/PasswordConfiguration.java`；依赖：T013、T162、T248；依据：ACC-05/08；NFR-04；D01。
  - 执行：仅在此文件实现密码编码与输入字节保护，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T250** [P] **[TEST] RED：账户原子注册服务**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/account/application/AccountRegistrationServiceTest.java`；依赖：T191、T216、T242、T249；依据：ACC-01～04；AT-01/02/15/16。
  - 执行：先编写目标行为测试并运行聚焦命令；真实代理下验证三表成功及最后凭据插入失败整体回滚、重复用户名不覆盖；不自动登录。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T251** [P] **[IMPL] GREEN：账户原子注册服务**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/account/application/AccountRegistrationService.java`；依赖：T191、T216、T242、T249、T250；依据：ACC-01～04；AT-01/02/15/16。
  - 执行：仅在此文件实现账户原子注册服务，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T252** [P] **[TEST] RED：本人资料与偏好更新服务**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/account/application/AccountProfileServiceTest.java`；依赖：T190、T192、T242、T249；依据：ACC-07～10；PER-04；AT-03/04/14。
  - 执行：先编写目标行为测试并运行聚焦命令；空密码保留、有效新密码替换/旧密码失效、原子回滚、偏好刷新、他人username不能改账户。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T253** [P] **[IMPL] GREEN：本人资料与偏好更新服务**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/account/application/AccountProfileService.java`；依赖：T190、T192、T242、T249、T252；依据：ACC-07～10；PER-04；AT-03/04/14。
  - 执行：仅在此文件实现本人资料与偏好更新服务，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T254** [P] **[TEST] RED：真实凭据认证适配**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/identity/infrastructure/AccountUserDetailsServiceTest.java`；依赖：T010、T237、T242；依据：ACC-05；D01。
  - 执行：先编写目标行为测试并运行聚焦命令；用户存在/不存在、状态按Q10；无banner仍可认证；principal不缓存密码/整账户。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T255** [P] **[IMPL] GREEN：真实凭据认证适配**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/identity/infrastructure/AccountUserDetailsService.java`；依赖：T010、T237、T242、T254；依据：ACC-05；D01。
  - 执行：仅在此文件实现真实凭据认证适配，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T256** [P] **[TEST] RED：安全过滤器 JSON 处理器**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/identity/web/SecurityJsonHandlersTest.java`；依赖：T232、T237；依据：plan §5.2；NFR-04～05。
  - 执行：先编写目标行为测试并运行聚焦命令；成功200/退出204/登录失败401/CSRF403、统一Problem Details、无异常详情或凭据回显。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T257** [P] **[IMPL] GREEN：安全过滤器 JSON 处理器**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/identity/web/SecurityJsonHandlers.java`；依赖：T232、T237、T256；依据：plan §5.2；NFR-04～05。
  - 执行：仅在此文件实现安全过滤器 JSON 处理器，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T258** [P] **[TEST] RED：会话认证和 CSRF 安全链**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/identity/infrastructure/SecurityConfigurationTest.java`；依赖：T008、T239、T249、T255、T257；依据：ACC-05～06；ORD-01；AT-09/17；AT-R09。
  - 执行：先编写目标行为测试并运行聚焦命令；真实过滤器登录、会话标识轮换、401/403、Cookie属性、游客CSRF写保护、登录/退出后刷新token、无宽泛CORS。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T259** [P] **[IMPL] GREEN：会话认证和 CSRF 安全链**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/identity/infrastructure/SecurityConfiguration.java`；依赖：T008、T239、T249、T255、T257、T258；依据：ACC-05～06；ORD-01；AT-09/17；AT-R09。
  - 执行：仅在此文件实现会话认证和 CSRF 安全链，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T260** [P] **[TEST] RED：认证状态和 CSRF 端点**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/identity/web/IdentityControllerTest.java`；依赖：T205、T206、T259；依据：plan §6.2。
  - 执行：先编写目标行为测试并运行聚焦命令；测试GET session匿名标记和当前身份、显式物化CSRF token/headerName、no-store；转换端口用MockitoBean。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T261** [P] **[IMPL] GREEN：认证状态和 CSRF 端点**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/identity/web/IdentityController.java`；依赖：T205、T206、T259、T260；依据：plan §6.2。
  - 执行：仅在此文件实现认证状态和 CSRF 端点，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T262** [P] **[TEST] RED：Catalog REST适配**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/catalog/web/CatalogControllerTest.java`；依赖：T209、T234、T236、T247、T259；依据：CAT-01～08；AT-05/06。
  - 执行：先编写目标行为测试并运行聚焦命令；验证分类、产品、SKU、搜索路由和404/空词400；不回退为HTML。 仅mock应用端口和转换端口，不禁用安全过滤器。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T263** [P] **[IMPL] GREEN：Catalog REST适配**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/catalog/web/CatalogController.java`；依赖：T209、T234、T236、T247、T259、T262；依据：CAT-01～08；AT-05/06。
  - 执行：仅在此文件实现Catalog REST适配，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T264** [P] **[TEST] RED：Account REST适配**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/account/web/AccountControllerTest.java`；依赖：T203、T234、T236、T239、T247、T251、T253、T259；依据：ACC-01～09；PER-01～04；AT-01～04/14/16。
  - 执行：先编写目标行为测试并运行聚焦命令；注册201且未登录，me/PATCH仅本人，敏感字段不返回，偏好推荐/横幅开关。 仅mock应用端口和转换端口，不禁用安全过滤器。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T265** [P] **[IMPL] GREEN：Account REST适配**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/account/web/AccountController.java`；依赖：T203、T234、T236、T239、T247、T251、T253、T259、T264；依据：ACC-01～09；PER-01～04；AT-01～04/14/16。
  - 执行：仅在此文件实现Account REST适配，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

## Phase 6：购物车、确认快照与订单事务

执行编号：**T266～T304**；本阶段 39 项任务。按正文顺序推进，只有依赖已完成的 [P] 任务可并行；Q决策或环境条件不足时明确标阻塞。

- [x] **T266** [P] **[CONTRACT] Cart Mapper签名**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/cart/infrastructure/CartMapper.java`；依赖：T189、T219、T221、T223、T225、T227；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T267** [P] **[TEST] RED：Cart MySQL写入/查询**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/cart/infrastructure/CartMapperTest.java`；依赖：T173、T174、T266；依据：plan §5.4～5.8；§7。
  - 执行：先编写目标行为测试并运行聚焦命令；游客会话持有权、账号活动车唯一/跨设备恢复、锁cart、version CAS、同SKU唯一、合并超限不丢数据、CONSUMED不可重复消费。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T268** [P] **[IMPL] GREEN：Cart MySQL写入/查询**
  - 主要文件：`backend/src/main/resources/mapper/cart/CartMapper.xml`；依赖：T173、T174、T266、T267；依据：plan §5.4～5.8；§7。
  - 执行：仅在此文件实现Cart MySQL写入/查询，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T269** [P] **[CONTRACT] Checkout Mapper签名**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/infrastructure/CheckoutMapper.java`；依赖：T187、T219、T221、T223、T225、T227；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T270** [P] **[TEST] RED：Checkout MySQL写入/查询**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/order/infrastructure/CheckoutMapperTest.java`；依赖：T173、T174、T269；依据：plan §5.4～5.8；§7。
  - 执行：先编写目标行为测试并运行聚焦命令；归属/版本/有效期、明细快照、锁快照、单次消费。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T271** [P] **[IMPL] GREEN：Checkout MySQL写入/查询**
  - 主要文件：`backend/src/main/resources/mapper/order/CheckoutMapper.xml`；依赖：T173、T174、T269、T270；依据：plan §5.4～5.8；§7。
  - 执行：仅在此文件实现Checkout MySQL写入/查询，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T272** [P] **[CONTRACT] Inventory Mapper签名**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/infrastructure/InventoryMapper.java`；依赖：T186、T219、T221、T223、T225、T227；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T273** [P] **[TEST] RED：Inventory MySQL写入/查询**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/order/infrastructure/InventoryMapperTest.java`；依赖：T173、T174、T272；依据：plan §5.4～5.8；§7。
  - 执行：先编写目标行为测试并运行聚焦命令；返回扣减影响行数，缺库存/不足拒绝；按Q02约束验证，禁止负数量放大库存。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T274** [P] **[IMPL] GREEN：Inventory MySQL写入/查询**
  - 主要文件：`backend/src/main/resources/mapper/order/InventoryMapper.xml`；依赖：T173、T174、T272、T273；依据：plan §5.4～5.8；§7。
  - 执行：仅在此文件实现Inventory MySQL写入/查询，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T275** [P] **[CONTRACT] Order Mapper签名**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/infrastructure/OrderMapper.java`；依赖：T186、T219、T221、T223、T225、T227；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T276** [P] **[TEST] RED：Order MySQL写入/查询**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/order/infrastructure/OrderMapperTest.java`；依赖：T173、T174、T275；依据：plan §5.4～5.8；§7。
  - 执行：先编写目标行为测试并运行聚焦命令；generated keys唯一、成交快照、本人列表/详情过滤、最新优先且首期不分页（Q11）。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T277** [P] **[IMPL] GREEN：Order MySQL写入/查询**
  - 主要文件：`backend/src/main/resources/mapper/order/OrderMapper.xml`；依赖：T173、T174、T275、T276；依据：plan §5.4～5.8；§7。
  - 执行：仅在此文件实现Order MySQL写入/查询，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T278** [P] **[CONTRACT] OrderRequest Mapper签名**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/infrastructure/OrderRequestMapper.java`；依赖：T188、T219、T221、T223、T225、T227；依据：plan §3、§6。
  - 执行：仅定义该文件职责内的不可变数据/接口签名、类型和空值约定；行为或校验另建测试先行任务。
  - 验收：编译可用；不包含默认业务算法，不暴露密码或持久层对象。

- [x] **T279** [P] **[TEST] RED：OrderRequest MySQL写入/查询**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/order/infrastructure/OrderRequestMapperTest.java`；依赖：T173、T174、T278；依据：plan §5.4～5.8；§7。
  - 执行：先编写目标行为测试并运行聚焦命令；用户/操作/key联合唯一、响应/摘要持久化、不同用户隔离、过期与重放不含Cookie。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T280** [P] **[IMPL] GREEN：OrderRequest MySQL写入/查询**
  - 主要文件：`backend/src/main/resources/mapper/order/OrderRequestMapper.xml`；依赖：T173、T174、T278、T279；依据：plan §5.4～5.8；§7。
  - 执行：仅在此文件实现OrderRequest MySQL写入/查询，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T281** [P] **[TEST] RED：会话购物车持有权绑定**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/cart/infrastructure/SessionCartBindingTest.java`；依赖：T008、T189、T237；依据：CRT-01/11；AT-18；Q08。
  - 执行：先编写目标行为测试并运行聚焦命令；无任意cartId输入、会话ID不落日志/表；登录轮换、退出/过期按已决Q08：账号恢复/跨设备/退出保留、游客30分钟失效、登录合并；GET无车不创建业务行。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T282** [P] **[IMPL] GREEN：会话购物车持有权绑定**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/cart/infrastructure/SessionCartBinding.java`；依赖：T008、T189、T237、T281；依据：CRT-01/11；AT-18；Q08。
  - 执行：仅在此文件实现会话购物车持有权绑定，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T283** [P] **[TEST] RED：会话购物车命令与查询服务**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/cart/application/CartApplicationServiceTest.java`；依赖：T002、T015、T195～T197、T247、T268、T282；依据：CRT-01～11；AT-07/08/18；AT-R07；D04a。
  - 执行：先编写目标行为测试并运行聚焦命令；同SKU加一、规格分行、改零/负值按决策删除后重加、批量非法整体拒绝、version409、未加购不扣库存；游客会话隔离、账号跨设备一致且隔离他人。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T284** [P] **[IMPL] GREEN：会话购物车命令与查询服务**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/cart/application/CartApplicationService.java`；依赖：T002、T015、T195～T197、T247、T268、T282、T283；依据：CRT-01～11；AT-07/08/18；AT-R07；D04a。
  - 执行：仅在此文件实现会话购物车命令与查询服务，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T285** [P] **[TEST] RED：购物车 REST适配**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/cart/web/CartControllerTest.java`；依赖：T212、T234、T236、T259、T284；依据：CRT-01～11；plan §6.2。
  - 执行：先编写目标行为测试并运行聚焦命令；游客CSRF、GET空DTO、POST增量加一、PUT批量/单项、DELETE不存在项、expectedVersion冲突、仅本人pendingMerges及POST /api/cart/merge原子重试；仅mock转换。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T286** [P] **[IMPL] GREEN：购物车 REST适配**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/cart/web/CartController.java`；依赖：T212、T234、T236、T259、T284、T285；依据：CRT-01～11；plan §6.2。
  - 执行：仅在此文件实现购物车 REST适配，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T287** [P] **[TEST] RED：服务端确认快照应用服务**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/order/application/CheckoutApplicationServiceTest.java`；依赖：T003、T004、T015、T183、T198、T247、T253、T271、T284；依据：ORD-02～07/13；D11；AT-10；AT-R05/06。
  - 执行：先编写目标行为测试并运行聚焦命令；锁cart/校验版本非空、两地址独立、价格来自服务端、无库存预占、有效期/归属/变化按Q03；不能伪造快照。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T288** [P] **[IMPL] GREEN：服务端确认快照应用服务**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/application/CheckoutApplicationService.java`；依赖：T003、T004、T015、T183、T198、T247、T253、T271、T284、T287；依据：ORD-02～07/13；D11；AT-10；AT-R05/06。
  - 执行：仅在此文件实现服务端确认快照应用服务，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T289** [P] **[TEST] RED：同事务库存条件扣减**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/order/application/InventoryServiceTest.java`；依赖：T002、T274；依据：INV-01～03；D02；AT-R01。
  - 执行：先编写目标行为测试并运行聚焦命令；正数量校验、影响行数必须1、SKU排序锁顺序、任一不足抛可回滚错误；依Q02，不引入独立提交。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T290** [P] **[IMPL] GREEN：同事务库存条件扣减**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/application/InventoryService.java`；依赖：T002、T274、T289；依据：INV-01～03；D02；AT-R01。
  - 执行：仅在此文件实现同事务库存条件扣减，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T291** [P] **[TEST] RED：稳定下单摘要**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/order/domain/OrderRequestHasherTest.java`；依赖：T187、T188；依据：D03；plan §5.7。
  - 执行：先编写目标行为测试并运行聚焦命令；规范化相同业务字段同hash，不同checkout不同hash，requestId不参与，key长度限制；不记录明文敏感内容。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T292** [P] **[IMPL] GREEN：稳定下单摘要**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/domain/OrderRequestHasher.java`；依赖：T187、T188、T291；依据：D03；plan §5.7。
  - 执行：仅在此文件实现稳定下单摘要，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T293** [P] **[TEST] RED：下单本地事务**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/order/application/OrderTransactionServiceTest.java`；依赖：T004、T199、T277、T280、T284、T288、T290、T292；依据：ORD-09～13；NFR-01/03；AT-11/12；AT-R03/08。
  - 执行：先编写目标行为测试并运行聚焦命令；真实代理及新连接验证key占用→checkout锁→cart锁→升序库存→订单/明细/状态→消费→保存响应全部同一事务；故障每步回滚；自增允许跳号。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T294** [P] **[IMPL] GREEN：下单本地事务**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/application/OrderTransactionService.java`；依赖：T004、T199、T277、T280、T284、T288、T290、T292、T293；依据：ORD-09～13；NFR-01/03；AT-11/12；AT-R03/08。
  - 执行：仅在此文件实现下单本地事务，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T295** [P] **[TEST] RED：订单幂等外层协调**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/order/application/IdempotentOrderServiceTest.java`；依赖：T239、T280、T292、T294；依据：D03；NFR-03；AT-R02/08。
  - 执行：先编写目标行为测试并运行聚焦命令；同key同摘要重放原结果，异摘要409，跨用户不重放；唯一冲突在失败事务结束后新事务读；同checkout换key409；锁超时503可重试，复用key。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T296** [P] **[IMPL] GREEN：订单幂等外层协调**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/application/IdempotentOrderService.java`；依赖：T239、T280、T292、T294、T295；依据：D03；NFR-03；AT-R02/08。
  - 执行：仅在此文件实现订单幂等外层协调，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T297** [P] **[TEST] RED：本人历史订单快照查询**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/order/application/OrderQueryServiceTest.java`；依赖：T011、T015、T200、T239、T277；依据：ORD-14～20；AT-13；AT-R04；D05。
  - 执行：先编写目标行为测试并运行聚焦命令；按身份查列表/详情；他人和不存在统一404；最新订单优先且首期不分页；真实DB改价/删除后成交金额不变、无N+1；只初始状态。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T298** [P] **[IMPL] GREEN：本人历史订单快照查询**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/application/OrderQueryService.java`；依赖：T011、T015、T200、T239、T277、T297；依据：ORD-14～20；AT-13；AT-R04；D05。
  - 执行：仅在此文件实现本人历史订单快照查询，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T299** [P] **[TEST] RED：确认快照 REST适配**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/order/web/CheckoutControllerTest.java`；依赖：T215、T234、T236、T259、T288；依据：ORD-01～07；AT-09/10；AT-R05/06。
  - 执行：先编写目标行为测试并运行聚焦命令；认证+CSRF、字段错误400、版本/变价409、成功201、输入价格/用户名不可覆盖；不在Controller开事务。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T300** [P] **[IMPL] GREEN：确认快照 REST适配**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/web/CheckoutController.java`；依赖：T215、T234、T236、T259、T288、T299；依据：ORD-01～07；AT-09/10；AT-R05/06。
  - 执行：仅在此文件实现确认快照 REST适配，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T301** [P] **[TEST] RED：订单提交与查询 REST适配**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/order/web/OrderControllerTest.java`；依赖：T215、T234、T236、T259、T296、T298；依据：ORD-01～20；AT-11/13；AT-R02/04/08。
  - 执行：先编写目标行为测试并运行聚焦命令；201+Location，Idempotency-Key必填，重放响应一致，GET本人/他人404，错误状态及金额字符串。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T302** [P] **[IMPL] GREEN：订单提交与查询 REST适配**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/web/OrderController.java`；依赖：T215、T234、T236、T259、T296、T298、T301；依据：ORD-01～20；AT-11/13；AT-R02/04/08。
  - 执行：仅在此文件实现订单提交与查询 REST适配，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T303** [P] **[TEST] 补齐独立连接并发验收**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/order/application/OrderConcurrencyIT.java`；依赖：T173、T174、T294、T296、T298；依据：AT-R01/02/03/04/05/06/07/08；plan §10。
  - 执行：先运行并记录目标失败：库存1双人抢购、同key同payload、同key异payload、同checkout换key、多SKU部分失败、响应丢失重试、编号唯一；用屏障+独立连接，不靠sleep排序。
  - 验收：所有不变量转绿后才完成；若失败新增单文件修正任务，不能改为旧缺陷断言或弱化并发。

- [x] **T304** [P] **[IMPL] 收敛订单并发事务边界**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/application/OrderTransactionService.java`；依赖：T294、T303；依据：AT-R01/02/03/08。
  - 执行：仅在该文件修正由并发红灯定位出的锁顺序/消费/回滚边界；若根因在其他文件，先添加对应单文件任务而非顺带修改。
  - 验收：OrderConcurrencyIT所有场景及此前下单回归通过；若无需修改记录无需变更且提供测试证据。

## Phase 7：显式数据转换与前端 API

执行编号：**T305～T334**；本阶段 30 项任务。按正文顺序推进，只有依赖已完成的 [P] 任务可并行；Q决策或环境条件不足时明确标阻塞。

- [x] **T305** [P] **[TEST] RED：account 显式DTO/领域转换**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/account/web/DefaultAccountAssemblerTest.java`；依赖：T203、T251、T253；依据：plan §5～§6；D01/D05/D11。
  - 执行：先编写目标行为测试并运行聚焦命令；验证请求只映射允许字段、金额/ID字符串、UTC时间、两地址独立、敏感字段排除及null策略；用tools.jackson核对JSON，不暴露数据行。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T306** [P] **[IMPL] GREEN：account 显式DTO/领域转换**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/account/web/DefaultAccountAssembler.java`；依赖：T203、T251、T253、T305；依据：plan §5～§6；D01/D05/D11。
  - 执行：仅在此文件实现account 显式DTO/领域转换，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T307** [P] **[TEST] RED：identity 显式DTO/领域转换**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/identity/web/DefaultIdentityAssemblerTest.java`；依赖：T206、T239；依据：plan §5～§6；D01/D05/D11。
  - 执行：先编写目标行为测试并运行聚焦命令；验证请求只映射允许字段、金额/ID字符串、UTC时间、两地址独立、敏感字段排除及null策略；用tools.jackson核对JSON，不暴露数据行。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T308** [P] **[IMPL] GREEN：identity 显式DTO/领域转换**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/identity/web/DefaultIdentityAssembler.java`；依赖：T206、T239、T307；依据：plan §5～§6；D01/D05/D11。
  - 执行：仅在此文件实现identity 显式DTO/领域转换，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T309** [P] **[TEST] RED：catalog 显式DTO/领域转换**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/catalog/web/DefaultCatalogAssemblerTest.java`；依赖：T209、T247；依据：plan §5～§6；D01/D05/D11。
  - 执行：先编写目标行为测试并运行聚焦命令；验证请求只映射允许字段、金额/ID字符串、UTC时间、两地址独立、敏感字段排除及null策略；用tools.jackson核对JSON，不暴露数据行。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T310** [P] **[IMPL] GREEN：catalog 显式DTO/领域转换**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/catalog/web/DefaultCatalogAssembler.java`；依赖：T209、T247、T309；依据：plan §5～§6；D01/D05/D11。
  - 执行：仅在此文件实现catalog 显式DTO/领域转换，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T311** [P] **[TEST] RED：cart 显式DTO/领域转换**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/cart/web/DefaultCartAssemblerTest.java`；依赖：T212、T284；依据：plan §5～§6；D01/D05/D11。
  - 执行：先编写目标行为测试并运行聚焦命令；验证请求只映射允许字段、金额/ID字符串、UTC时间、两地址独立、敏感字段排除及null策略；用tools.jackson核对JSON，不暴露数据行。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T312** [P] **[IMPL] GREEN：cart 显式DTO/领域转换**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/cart/web/DefaultCartAssembler.java`；依赖：T212、T284、T311；依据：plan §5～§6；D01/D05/D11。
  - 执行：仅在此文件实现cart 显式DTO/领域转换，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T313** [P] **[TEST] RED：order 显式DTO/领域转换**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/order/web/DefaultOrderAssemblerTest.java`；依赖：T215、T288、T298；依据：plan §5～§6；D01/D05/D11。
  - 执行：先编写目标行为测试并运行聚焦命令；验证请求只映射允许字段、金额/ID字符串、UTC时间、两地址独立、敏感字段排除及null策略；用tools.jackson核对JSON，不暴露数据行。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T314** [P] **[IMPL] GREEN：order 显式DTO/领域转换**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/web/DefaultOrderAssembler.java`；依赖：T215、T288、T298、T313；依据：plan §5～§6；D01/D05/D11。
  - 执行：仅在此文件实现order 显式DTO/领域转换，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T315** [P] **[TEST] RED：前端内存 CSRF 状态**
  - 主要文件：`frontend/src/shared/api/csrf.test.ts`；依赖：T172、T217；依据：plan §5.2、§8。
  - 执行：先编写目标行为测试并运行聚焦命令；登录/退出清除旧token，显式更新token/headerName，不持久化浏览器存储。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T316** [P] **[IMPL] GREEN：前端内存 CSRF 状态**
  - 主要文件：`frontend/src/shared/api/csrf.ts`；依赖：T172、T217、T315；依据：plan §5.2、§8。
  - 执行：仅在此文件实现前端内存 CSRF 状态，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T317** [P] **[TEST] RED：统一同源 HTTP 客户端**
  - 主要文件：`frontend/src/shared/api/http.test.ts`；依赖：T172、T217、T316；依据：plan §6、§8。
  - 执行：先编写目标行为测试并运行聚焦命令；Cookie、API-Version、写请求CSRF、Problem Details、requestId；禁止自动重试副作用；401/409不静默重放。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T318** [P] **[IMPL] GREEN：统一同源 HTTP 客户端**
  - 主要文件：`frontend/src/shared/api/http.ts`；依赖：T172、T217、T316、T317；依据：plan §6、§8。
  - 执行：仅在此文件实现统一同源 HTTP 客户端，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T319** [P] **[TEST] RED：identity 前端API调用**
  - 主要文件：`frontend/src/features/identity/api.test.ts`；依赖：T217、T318；依据：plan §6.2、§8。
  - 执行：先编写目标行为测试并运行聚焦命令；mock传输仅验证session/CSRF/login表单/logout生命周期；响应严格按OpenAPI类型，不在client重复金额计算。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T320** [P] **[IMPL] GREEN：identity 前端API调用**
  - 主要文件：`frontend/src/features/identity/api.ts`；依赖：T217、T318、T319；依据：plan §6.2、§8。
  - 执行：仅在此文件实现identity 前端API调用，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T321** [P] **[TEST] RED：account 前端API调用**
  - 主要文件：`frontend/src/features/account/api.test.ts`；依赖：T217、T318；依据：plan §6.2、§8。
  - 执行：先编写目标行为测试并运行聚焦命令；mock传输仅验证注册/me/PATCH/推荐/横幅，无凭据持久化；响应严格按OpenAPI类型，不在client重复金额计算。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T322** [P] **[IMPL] GREEN：account 前端API调用**
  - 主要文件：`frontend/src/features/account/api.ts`；依赖：T217、T318、T321；依据：plan §6.2、§8。
  - 执行：仅在此文件实现account 前端API调用，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T323** [P] **[TEST] RED：catalog 前端API调用**
  - 主要文件：`frontend/src/features/catalog/api.test.ts`；依赖：T217、T318；依据：plan §6.2、§8。
  - 执行：先编写目标行为测试并运行聚焦命令；mock传输仅验证分类/产品/SKU/名称搜索参数编码；响应严格按OpenAPI类型，不在client重复金额计算。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T324** [P] **[IMPL] GREEN：catalog 前端API调用**
  - 主要文件：`frontend/src/features/catalog/api.ts`；依赖：T217、T318、T323；依据：plan §6.2、§8。
  - 执行：仅在此文件实现catalog 前端API调用，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T325** [P] **[TEST] RED：cart 前端API调用**
  - 主要文件：`frontend/src/features/cart/api.test.ts`；依赖：T217、T318；依据：plan §6.2、§8。
  - 执行：先编写目标行为测试并运行聚焦命令；mock传输仅验证GET无写、add增量、PUT数量、批量、DELETE和版本，以及登录合并冲突展示/显式重试；响应严格按OpenAPI类型，不在client重复金额计算。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T326** [P] **[IMPL] GREEN：cart 前端API调用**
  - 主要文件：`frontend/src/features/cart/api.ts`；依赖：T217、T318、T325；依据：plan §6.2、§8。
  - 执行：仅在此文件实现cart 前端API调用，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T327** [P] **[TEST] RED：order 前端API调用**
  - 主要文件：`frontend/src/features/order/api.test.ts`；依赖：T217、T318；依据：plan §6.2、§8。
  - 执行：先编写目标行为测试并运行聚焦命令；mock传输仅验证checkout/订单提交同key重试、Location、列表/详情404；响应严格按OpenAPI类型，不在client重复金额计算。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T328** [P] **[IMPL] GREEN：order 前端API调用**
  - 主要文件：`frontend/src/features/order/api.ts`；依赖：T217、T318、T327；依据：plan §6.2、§8。
  - 执行：仅在此文件实现order 前端API调用，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T329** [P] **[TEST] RED：展示转换 amountformat**
  - 主要文件：`frontend/src/shared/presentation/amount.test.ts`；依赖：T006、T172、T217；依据：plan §8；D05/D07/D12。
  - 执行：先编写目标行为测试并运行聚焦命令；字符串金额显示两位小数，不经Number丢精度；币种/舍入按Q06。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T330** [P] **[IMPL] GREEN：展示转换 amountformat**
  - 主要文件：`frontend/src/shared/presentation/amount.ts`；依赖：T006、T172、T217、T329；依据：plan §8；D05/D07/D12。
  - 执行：仅在此文件实现展示转换 amountformat，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T331** [P] **[TEST] RED：展示转换 timeformat**
  - 主要文件：`frontend/src/shared/presentation/time.test.ts`；依赖：T011、T172、T217；依据：plan §8；D05/D07/D12。
  - 执行：先编写目标行为测试并运行聚焦命令；UTC输入、Q11展示时区、旧DATE精度缺失不编造时间。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T332** [P] **[IMPL] GREEN：展示转换 timeformat**
  - 主要文件：`frontend/src/shared/presentation/time.ts`；依赖：T011、T172、T217、T331；依据：plan §8；D05/D07/D12。
  - 执行：仅在此文件实现展示转换 timeformat，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T333** [P] **[TEST] RED：展示转换 errorformat**
  - 主要文件：`frontend/src/shared/presentation/problem.test.ts`；依赖：T172、T217；依据：plan §8；D05/D07/D12。
  - 执行：先编写目标行为测试并运行聚焦命令；字段错误、安全通用提示、409刷新、requestId，无SQL/密码原文。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T334** [P] **[IMPL] GREEN：展示转换 errorformat**
  - 主要文件：`frontend/src/shared/presentation/problem.ts`；依赖：T172、T217、T333；依据：plan §8；D05/D07/D12。
  - 执行：仅在此文件实现展示转换 errorformat，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

## Phase 8：Vue 状态、组件与业务页面

执行编号：**T335～T380**；本阶段 46 项任务。按正文顺序推进，只有依赖已完成的 [P] 任务可并行；Q决策或环境条件不足时明确标阻塞。

- [x] **T335** [P] **[TEST] RED：identity Vue状态仓库**
  - 主要文件：`frontend/src/features/identity/store.test.ts`；依赖：T172、T320、T322；依据：plan §8；Q03/Q04/Q08。
  - 执行：先编写目标行为测试并运行聚焦命令；bootstrap/session、登录后CSRF刷新、退出清身份，绝不存密码/长期token；returnTo仅站内。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T336** [P] **[IMPL] GREEN：identity Vue状态仓库**
  - 主要文件：`frontend/src/features/identity/store.ts`；依赖：T172、T320、T322、T335；依据：plan §8；Q03/Q04/Q08。
  - 执行：仅在此文件实现identity Vue状态仓库，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T337** [P] **[TEST] RED：cart Vue状态仓库**
  - 主要文件：`frontend/src/features/cart/store.test.ts`；依赖：T172、T326；依据：plan §8；Q03/Q04/Q08。
  - 执行：先编写目标行为测试并运行聚焦命令；以服务端DTO为准、versions409提示刷新、明细行数与总数量区分、失败不丢内容，合并超限展示pendingMerges并支持调整后显式重试。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T338** [P] **[IMPL] GREEN：cart Vue状态仓库**
  - 主要文件：`frontend/src/features/cart/store.ts`；依赖：T172、T326、T337；依据：plan §8；Q03/Q04/Q08。
  - 执行：仅在此文件实现cart Vue状态仓库，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T339** [P] **[TEST] RED：order Vue状态仓库**
  - 主要文件：`frontend/src/features/order/store.test.ts`；依赖：T172、T328；依据：plan §8；Q03/Q04/Q08。
  - 执行：先编写目标行为测试并运行聚焦命令；保存同次checkout和幂等key引用，重复点击不换key，成功才结束尝试，离开清敏感输入。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T340** [P] **[IMPL] GREEN：order Vue状态仓库**
  - 主要文件：`frontend/src/features/order/store.ts`；依赖：T172、T328、T339；依据：plan §8；Q03/Q04/Q08。
  - 执行：仅在此文件实现order Vue状态仓库，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T341** [P] **[TEST] RED：Vue组件 field**
  - 主要文件：`frontend/src/shared/components/FieldErrors.test.ts`；依赖：T172、T334；依据：plan §8；PER；D07/D10。
  - 执行：先编写目标行为测试并运行聚焦命令；按字段关联错误和可访问提示，不显示敏感rejectedValue。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T342** [P] **[IMPL] GREEN：Vue组件 field**
  - 主要文件：`frontend/src/shared/components/FieldErrors.vue`；依赖：T172、T334、T341；依据：plan §8；PER；D07/D10。
  - 执行：仅在此文件实现Vue组件 field，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T343** [P] **[TEST] RED：Vue组件 address**
  - 主要文件：`frontend/src/features/order/components/AddressForm.test.ts`；依赖：T001、T015、T172、T217；依据：plan §8；PER；D07/D10。
  - 执行：先编写目标行为测试并运行聚焦命令；账单/收货独立模型，Q01/Q15必填，改账单不覆盖收货。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T344** [P] **[IMPL] GREEN：Vue组件 address**
  - 主要文件：`frontend/src/features/order/components/AddressForm.vue`；依赖：T001、T015、T172、T217、T343；依据：plan §8；PER；D07/D10。
  - 执行：仅在此文件实现Vue组件 address，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T345** [P] **[TEST] RED：Vue组件 cardform**
  - 主要文件：`frontend/src/features/order/components/FakePaymentForm.test.ts`；依赖：T005、T015、T172、T217；依据：plan §8；PER；D07/D10。
  - 执行：先编写目标行为测试并运行聚焦命令；三卡型/假卡提示/有效期规则，不持久化卡信息。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T346** [P] **[IMPL] GREEN：Vue组件 cardform**
  - 主要文件：`frontend/src/features/order/components/FakePaymentForm.vue`；依赖：T005、T015、T172、T217、T345；依据：plan §8；PER；D07/D10。
  - 执行：仅在此文件实现Vue组件 cardform，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T347** [P] **[TEST] RED：Vue组件 carttable**
  - 主要文件：`frontend/src/features/cart/components/CartTable.test.ts`；依赖：T172、T330、T338；依据：plan §8；PER；D07/D10。
  - 执行：先编写目标行为测试并运行聚焦命令；版本数量命令、零/负按决策移除、行数/总量/小计、空车与加载显示。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T348** [P] **[IMPL] GREEN：Vue组件 carttable**
  - 主要文件：`frontend/src/features/cart/components/CartTable.vue`；依赖：T172、T330、T338、T347；依据：plan §8；PER；D07/D10。
  - 执行：仅在此文件实现Vue组件 carttable，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T349** [P] **[TEST] RED：Vue组件 recommend**
  - 主要文件：`frontend/src/features/account/components/Recommendations.test.ts`；依赖：T172、T322；依据：plan §8；PER；D07/D10。
  - 执行：先编写目标行为测试并运行聚焦命令；偏好分类推荐非手动收藏，开关隐藏和缺内容正常，文本不执行脚本。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T350** [P] **[IMPL] GREEN：Vue组件 recommend**
  - 主要文件：`frontend/src/features/account/components/Recommendations.vue`；依赖：T172、T322、T349；依据：plan §8；PER；D07/D10。
  - 执行：仅在此文件实现Vue组件 recommend，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T351** [P] **[TEST] RED：页面 HomePage**
  - 主要文件：`frontend/src/features/catalog/pages/HomePage.test.ts`；依赖：T172、T324；依据：spec §4、§10；plan §8。
  - 执行：先编写目标行为测试并运行聚焦命令；分类五入口、加载/无结果/错误状态，标题正确。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T352** [P] **[IMPL] GREEN：页面 HomePage**
  - 主要文件：`frontend/src/features/catalog/pages/HomePage.vue`；依赖：T172、T324、T351；依据：spec §4、§10；plan §8。
  - 执行：仅在此文件实现页面 HomePage，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T353** [P] **[TEST] RED：页面 CategoryPage**
  - 主要文件：`frontend/src/features/catalog/pages/CategoryPage.test.ts`；依赖：T172、T324；依据：spec §4、§10；plan §8。
  - 执行：先编写目标行为测试并运行聚焦命令；分类产品列表与名称，分类404不显示伪页面。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T354** [P] **[IMPL] GREEN：页面 CategoryPage**
  - 主要文件：`frontend/src/features/catalog/pages/CategoryPage.vue`；依赖：T172、T324、T353；依据：spec §4、§10；plan §8。
  - 执行：仅在此文件实现页面 CategoryPage，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T355** [P] **[TEST] RED：页面 ProductPage**
  - 主要文件：`frontend/src/features/catalog/pages/ProductPage.test.ts`；依赖：T172、T324、T338；依据：spec §4、§10；plan §8。
  - 执行：先编写目标行为测试并运行聚焦命令；可售项列表、规格分行、加购入口，描述仅文本。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T356** [P] **[IMPL] GREEN：页面 ProductPage**
  - 主要文件：`frontend/src/features/catalog/pages/ProductPage.vue`；依赖：T172、T324、T338、T355；依据：spec §4、§10；plan §8。
  - 执行：仅在此文件实现页面 ProductPage，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T357** [P] **[TEST] RED：页面 ItemPage**
  - 主要文件：`frontend/src/features/catalog/pages/ItemPage.test.ts`；依赖：T172、T324、T330、T338；依据：spec §4、§10；plan §8。
  - 执行：先编写目标行为测试并运行聚焦命令；售价/库存/缺货提示与加购，提示不是库存预占。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T358** [P] **[IMPL] GREEN：页面 ItemPage**
  - 主要文件：`frontend/src/features/catalog/pages/ItemPage.vue`；依赖：T172、T324、T330、T338、T357；依据：spec §4、§10；plan §8。
  - 执行：仅在此文件实现页面 ItemPage，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T359** [P] **[TEST] RED：页面 SearchPage**
  - 主要文件：`frontend/src/features/catalog/pages/SearchPage.test.ts`；依赖：T014、T172、T324；依据：spec §4、§10；plan §8。
  - 执行：先编写目标行为测试并运行聚焦命令；名称搜索、大小写、空词/特殊输入按Q14，空结果。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T360** [P] **[IMPL] GREEN：页面 SearchPage**
  - 主要文件：`frontend/src/features/catalog/pages/SearchPage.vue`；依赖：T014、T172、T324、T359；依据：spec §4、§10；plan §8。
  - 执行：仅在此文件实现页面 SearchPage，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T361** [P] **[TEST] RED：页面 RegisterPage**
  - 主要文件：`frontend/src/features/account/pages/RegisterPage.test.ts`；依赖：T172、T322、T342、T344；依据：spec §4、§10；plan §8。
  - 执行：先编写目标行为测试并运行聚焦命令；注册全部字段、密码一致、冲突409、成功转登录不自动登录。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T362** [P] **[IMPL] GREEN：页面 RegisterPage**
  - 主要文件：`frontend/src/features/account/pages/RegisterPage.vue`；依赖：T172、T322、T342、T344、T361；依据：spec §4、§10；plan §8。
  - 执行：仅在此文件实现页面 RegisterPage，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T363** [P] **[TEST] RED：页面 LoginPage**
  - 主要文件：`frontend/src/features/identity/pages/LoginPage.test.ts`；依赖：T172、T336、T342；依据：spec §4、§10；plan §8。
  - 执行：先编写目标行为测试并运行聚焦命令；表单登录/通用错误/站内returnTo，刷新CSRF，不泄露用户是否存在。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T364** [P] **[IMPL] GREEN：页面 LoginPage**
  - 主要文件：`frontend/src/features/identity/pages/LoginPage.vue`；依赖：T172、T336、T342、T363；依据：spec §4、§10；plan §8。
  - 执行：仅在此文件实现页面 LoginPage，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T365** [P] **[TEST] RED：页面 AccountPage**
  - 主要文件：`frontend/src/features/account/pages/AccountPage.test.ts`；依赖：T172、T322、T336、T342、T344、T350；依据：spec §4、§10；plan §8。
  - 执行：先编写目标行为测试并运行聚焦命令；本人用户名只读、空密码保留、两次密码校验、资料/偏好开关刷新，语言按Q09。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T366** [P] **[IMPL] GREEN：页面 AccountPage**
  - 主要文件：`frontend/src/features/account/pages/AccountPage.vue`；依赖：T172、T322、T336、T342、T344、T350、T365；依据：spec §4、§10；plan §8。
  - 执行：仅在此文件实现页面 AccountPage，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T367** [P] **[TEST] RED：页面 CartPage**
  - 主要文件：`frontend/src/features/cart/pages/CartPage.test.ts`；依赖：T172、T338、T348、T350；依据：spec §4、§10；plan §8。
  - 执行：先编写目标行为测试并运行聚焦命令；游客购物车、推荐、空车无结算、数量更新、版本冲突提示。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T368** [P] **[IMPL] GREEN：页面 CartPage**
  - 主要文件：`frontend/src/features/cart/pages/CartPage.vue`；依赖：T172、T338、T348、T350、T367；依据：spec §4、§10；plan §8。
  - 执行：仅在此文件实现页面 CartPage，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T369** [P] **[TEST] RED：页面 CheckoutPage**
  - 主要文件：`frontend/src/features/order/pages/CheckoutPage.test.ts`；依赖：T172、T336、T340、T344、T346；依据：spec §4、§10；plan §8。
  - 执行：先编写目标行为测试并运行聚焦命令；登录前置、两地址/模拟支付→服务端确认快照，失效要求重新确认。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T370** [P] **[IMPL] GREEN：页面 CheckoutPage**
  - 主要文件：`frontend/src/features/order/pages/CheckoutPage.vue`；依赖：T172、T336、T340、T344、T346、T369；依据：spec §4、§10；plan §8。
  - 执行：仅在此文件实现页面 CheckoutPage，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T371** [P] **[TEST] RED：页面 ConfirmPage**
  - 主要文件：`frontend/src/features/order/pages/ConfirmPage.test.ts`；依赖：T172、T330、T340；依据：spec §4、§10；plan §8。
  - 执行：先编写目标行为测试并运行聚焦命令；显示服务端快照、重复点击复用key、变价409明确提示；未确认不建单。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T372** [P] **[IMPL] GREEN：页面 ConfirmPage**
  - 主要文件：`frontend/src/features/order/pages/ConfirmPage.vue`；依赖：T172、T330、T340、T371；依据：spec §4、§10；plan §8。
  - 执行：仅在此文件实现页面 ConfirmPage，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T373** [P] **[TEST] RED：页面 OrdersPage**
  - 主要文件：`frontend/src/features/order/pages/OrdersPage.test.ts`；依赖：T172、T328、T332；依据：spec §4、§10；plan §8。
  - 执行：先编写目标行为测试并运行聚焦命令；本人摘要、最新优先且首期不分页、空列表、401引导。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T374** [P] **[IMPL] GREEN：页面 OrdersPage**
  - 主要文件：`frontend/src/features/order/pages/OrdersPage.vue`；依赖：T172、T328、T332、T373；依据：spec §4、§10；plan §8。
  - 执行：仅在此文件实现页面 OrdersPage，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T375** [P] **[TEST] RED：页面 OrderDetailPage**
  - 主要文件：`frontend/src/features/order/pages/OrderDetailPage.test.ts`；依赖：T172、T328、T330、T332；依据：spec §4、§10；plan §8。
  - 执行：先编写目标行为测试并运行聚焦命令；成交金额/快照、商品删除占位、他人404、模拟卡展示范围按Q15。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T376** [P] **[IMPL] GREEN：页面 OrderDetailPage**
  - 主要文件：`frontend/src/features/order/pages/OrderDetailPage.vue`；依赖：T172、T328、T330、T332、T375；依据：spec §4、§10；plan §8。
  - 执行：仅在此文件实现页面 OrderDetailPage，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T377** **[TEST] RED：Vue路由及认证守卫**
  - 主要文件：`frontend/src/app/router.test.ts`；依赖：T336、T352、T354、T356、T358、T360、T362、T364、T366、T368、T370、T372、T374、T376；依据：plan §8。
  - 执行：先编写目标行为测试并运行聚焦命令；仅站内returnTo、路由参数编码、401引导和受保护页面；守卫不代替后端授权。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T378** **[IMPL] GREEN：Vue路由及认证守卫**
  - 主要文件：`frontend/src/app/router.ts`；依赖：T336、T352、T354、T356、T358、T360、T362、T364、T366、T368、T370、T372、T374、T376、T377；依据：plan §8。
  - 执行：仅在此文件实现Vue路由及认证守卫，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T379** **[TEST] RED：应用布局与导航**
  - 主要文件：`frontend/src/App.test.ts`；依赖：T336、T338、T378；依据：D07/D10；plan §8。
  - 执行：先编写目标行为测试并运行聚焦命令；登录/退出菜单、公共商品导航、欢迎/标题、错误反馈、无未知脚本执行。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T380** **[IMPL] GREEN：应用布局与导航**
  - 主要文件：`frontend/src/App.vue`；依赖：T336、T338、T378、T379；依据：D07/D10；plan §8。
  - 执行：仅在此文件实现应用布局与导航，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

## Phase 9：全栈开发验证、运行诊断与应用装配

执行编号：**T381～T406**；本阶段 26 项任务。按正文顺序推进，只有依赖已完成的 [P] 任务可并行；Q决策或环境条件不足时明确标阻塞。

- [x] **T381** [P] **[TEST] RED：请求关联过滤器**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/shared/observability/RequestIdFilterTest.java`；依赖：T176、T234；依据：plan §9.2；NFR-05。
  - 执行：先编写目标行为测试并运行聚焦命令；合法/过长/恶意header、生成ID、响应header、异常和正常finally清MDC；与幂等键独立。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T382** [P] **[IMPL] GREEN：请求关联过滤器**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/shared/observability/RequestIdFilter.java`；依赖：T176、T234、T381；依据：plan §9.2；NFR-05。
  - 执行：仅在此文件实现请求关联过滤器，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T383** [P] **[TEST] RED：低基数订单指标**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/order/infrastructure/OrderMetricsTest.java`；依赖：T294、T296；依据：plan §9.2；NFR-07。
  - 执行：先编写目标行为测试并运行聚焦命令；成功/失败/库存冲突/幂等重放/锁失败只计符合语义事件，无订单/用户/SKU标签；失败不计成交。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T384** [P] **[IMPL] GREEN：低基数订单指标**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/infrastructure/OrderMetrics.java`；依赖：T294、T296、T383；依据：plan §9.2；NFR-07。
  - 执行：仅在此文件实现低基数订单指标，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T385** [P] **[TEST] RED：下单协调器指标接入**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/order/application/OrderMetricsWiringTest.java`；依赖：T296、T384；依据：plan §9.2。
  - 执行：编写协调器级测试：首次成交成功、回滚、重放、库存冲突及锁失败各自指标次数；先运行记录尚未接入的失败。
  - 验收：断言真正通过协调器调用，不仅直接调用counter；无高基数标签。

- [x] **T386** [P] **[IMPL] 在下单协调器接入指标**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/application/IdempotentOrderService.java`；依赖：T296、T384、T385；依据：plan §9.2。
  - 执行：仅在此文件接入已测试指标，复用OrderMetricsWiringTest红灯；不在本任务修改测试或其他生产文件。
  - 验收：指标断言和原幂等测试通过，不改变事务/重放状态。

- [x] **T387** [P] **[TEST] RED：管理端点专用安全链**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/observability/ManagementSecurityConfigurationTest.java`；依赖：T232、T259、T382；依据：plan §9.1；NFR-02。
  - 执行：先编写目标行为测试并运行聚焦命令；实际过滤链验证必要探针可达、env/beans/mappings等非公开、与API链匹配次序明确；错误契约复用。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T388** [P] **[IMPL] GREEN：管理端点专用安全链**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/shared/configuration/ManagementSecurityConfiguration.java`；依赖：T232、T259、T382、T387；依据：plan §9.1；NFR-02。
  - 执行：仅在此文件实现管理端点专用安全链，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T389** [P] **[TEST] RED：健康探针与管理端点隔离**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/observability/ManagementSecurityIT.java`；依赖：T173、T178、T382、T388；依据：plan §9.1；NFR-02/07。
  - 执行：先编写目标行为测试并运行聚焦命令；用真实HTTP验证liveness不依赖DB、readiness包含DB、有限超时、管理端点非公开、Cookie和代理信任参数。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T390** [P] **[IMPL] GREEN：健康探针与管理端点隔离**
  - 主要文件：`backend/src/main/resources/application-prod.yml`；依赖：T173、T178、T382、T388、T389；依据：plan §9.1；NFR-02/07。
  - 执行：仅在此文件实现健康探针与管理端点隔离，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T391** [P] **[TEST] RED：JSON日志及脱敏输出**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/observability/StructuredLoggingIT.java`；依赖：T382、T390；依据：plan §9.2；D01/D07。
  - 执行：先编写目标行为测试并运行聚焦命令；捕获真实请求/失败日志，校验JSON字段requestId与无password/Cookie/CSRF/假卡/地址/SQL参数，不仅比配置文本。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T392** [P] **[IMPL] GREEN：JSON日志及脱敏输出**
  - 主要文件：`backend/src/main/resources/logback-spring.xml`；依赖：T382、T390、T391；依据：plan §9.2；D01/D07。
  - 执行：仅在此文件实现JSON日志及脱敏输出，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T393** [P] **[TEST] RED：同源 SPA 路由回退**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/shared/web/SpaRoutingConfigurationTest.java`；依赖：T176、T259、T378；依据：plan §3.1；NFR-02。
  - 执行：先编写目标行为测试并运行聚焦命令；已知Vue路由回index；/api未知404、Actuator/静态资源缺失不回HTML；版本和安全状态保留。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T394** [P] **[IMPL] GREEN：同源 SPA 路由回退**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/shared/web/SpaRoutingConfiguration.java`；依赖：T176、T259、T378、T393；依据：plan §3.1；NFR-02。
  - 执行：仅在此文件实现同源 SPA 路由回退，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T395** [P] **[TEST] RED：前端资源与JAR装配验收**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/delivery/PackagedApplicationIT.java`；依赖：T236、T261、T263、T265、T286、T300、T302、T306、T308、T310、T312、T314、T380、T394；依据：plan §9.3。
  - 执行：编写并先运行目标验收：真实JAR同时提供Vue入口/图片和API，缺dist打包失败，无旧dist泄漏，API错误不是HTML。
  - 验收：先有可复现失败证据；不能用mock上下文代替真实打包进程。

- [x] **T396** [P] **[CONFIG] 配置 Vite 开发与构建**
  - 主要文件：`frontend/vite.config.ts`；依赖：T070～T108、T168、T378、T395；依据：plan §3、§8、§9。
  - 执行：配置Vue、/api同源代理和build；publicDir仅引用legacy的META-INF/resources展示资产，不能暴露XML/SQL；仅此文件变化。
  - 验收：开发代理正确，dist包含所需安全展示资产；通过装配验收的前端部分。

- [x] **T397** [P] **[CONFIG] 创建 Vue HTML宿主**
  - 主要文件：`frontend/index.html`；依赖：T380、T395；依据：plan §8。
  - 执行：仅定义挂载点、页面meta和main入口，不添加内联凭据或业务实现。
  - 验收：Vite可构建宿主；真实页面可加载。

- [x] **T398** [P] **[TEST] RED：Vue依赖注入与启动入口**
  - 主要文件：`frontend/src/main.test.ts`；依赖：T336、T338、T340、T378、T380、T396、T397；依据：plan §8。
  - 执行：先编写目标行为测试并运行聚焦命令；装配Pinia/Router/HTTP、CSRF bootstrap、全局异常反馈；无第二业务服务。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [x] **T399** [P] **[IMPL] GREEN：Vue依赖注入与启动入口**
  - 主要文件：`frontend/src/main.ts`；依赖：T336、T338、T340、T378、T380、T396～T398；依据：plan §8。
  - 执行：仅在此文件实现Vue依赖注入与启动入口，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [x] **T400** [P] **[CONFIG] 应用基础样式**
  - 主要文件：`frontend/src/app/styles.css`；依赖：T399；依据：plan §8。
  - 执行：仅为已有组件提供响应式布局、焦点和错误可读性；不改变业务交互。
  - 验收：浏览器手机/桌面布局可用，无CSS隐藏错误。

- [x] **T401** [P] **[IMPL] 装配前端产物到后端JAR**
  - 主要文件：`backend/pom.xml`；依赖：T162、T395～T397、T399；依据：plan §9.3。
  - 执行：只修改此POM：显式release资源步骤复制dist、清旧产物、缺dist失败；Boot重打包/Java21 build-image配置，普通后端test不强制下载Node。
  - 验收：PackagedApplicationIT通过；./mvnw verify与发布profile语义分别明确。

- [x] **T402** [P] **[CONFIG] E2E与打包应用的 Compose 配置**
  - 主要文件：`infra/compose.yml`；依赖：T016、T178、T401；依据：plan §9。
  - 执行：固定版本MySQL与单后端打包镜像、健康检查及运行期secret；供真实E2E使用。与早期infra/compose.dev.yml用途分开，避免同时启动形成端口或数据卷冲突。
  - 验收：docker compose config通过，可供E2E启动同版本数据库与单后端。

- [x] **T403** [P] **[CONFIG] 真实浏览器测试运行配置**
  - 主要文件：`frontend/playwright.config.ts`；依赖：T168、T401、T402；依据：plan §10。
  - 执行：配置目标URL、独立browser contexts、失败截图/trace和启动就绪等待；不以sleep掩盖就绪。
  - 验收：可执行实际E2E，不mock整个后端或用H2。

- [x] **T404** [P] **[TEST] RED：本地全栈启动冒烟工具**
  - 主要文件：`scripts/tests/test_dev_smoke.py`；依赖：T023、T026、T396、T399；依据：plan §9、§11；开发与交付补充。
  - 执行：先写测试覆盖数据库/后端/Vite就绪、有界超时、Vue路由和代理API同源、失败诊断及仅清理脚本自己启动的进程；不依赖固定sleep。
  - 验收：工具缺失时先有红灯；测试不终止用户已有服务、不删数据库卷。

- [x] **T405** [P] **[IMPL] GREEN：本地全栈启动冒烟工具**
  - 主要文件：`scripts/dev-smoke.py`；依赖：T023、T026、T396、T399、T404；依据：plan §9、§11；开发与交付补充。
  - 执行：仅实现此脚本，按开发指南启动/连接MySQL、Boot和Vite，轮询就绪，检查Vue页面、目录API与CSRF bootstrap；采用锁定版本、限定超时，失败保留脱敏诊断并清理自有进程。
  - 验收：test_dev_smoke.py转绿；真实主机能够完成前端→Vite代理→后端→MySQL链路，退出不丢失数据库。

- [x] **T406** [P] **[VERIFY] 记录完整开发环境首次启动验收**
  - 主要文件：`docs/development/full-stack-verification.md`；依赖：T180、T229、T401、T405；依据：plan §9、§11；开发与交付补充。
  - 执行：从干净检出依开发指南执行首次安装、启动、Flyway、dev seed、Vue页面/图片、API代理、登录CSRF及一次模拟下单；验证热更新和停止重启行为按Q08；记录实际环境和结果。
  - 验收：开发环境搭建有完整可复现证据；Q未决、依赖缺失或业务未通过明确标阻塞，不能只验证静态compose语法。

## Phase 10：系统验收、数据迁移与持续交付

执行编号：**T407～T425**；本阶段 19 项任务。按正文顺序推进，只有依赖已完成的 [P] 任务可并行；Q决策或环境条件不足时明确标阻塞。

- [ ] **T407** [P] **[TEST] 端到端验收 account.spec.ts**
  - 主要文件：`frontend/tests/e2e/account.spec.ts`；依赖：T362、T364、T366、T403；依据：AT-01～04/14～17；AT-R09。
  - 执行：真实注册登录、用户名只读、空/新密码、偏好开关、退出和CSRF刷新；两用户会话。；先记录发现的目标失败，再逐文件补对应修正任务；禁止弱化或跳过场景。
  - 验收：真实服务/同版MySQL浏览器通过并保留失败/成功证据；不以组件mock替代。

- [ ] **T408** [P] **[TEST] 端到端验收 catalog-cart.spec.ts**
  - 主要文件：`frontend/tests/e2e/catalog-cart.spec.ts`；依赖：T360、T368、T403；依据：AT-05～08/18；AT-R07。
  - 执行：目录/搜索/图片/空状态、游客加购、改零重加、两标签页version、两个会话隔离。；先记录发现的目标失败，再逐文件补对应修正任务；禁止弱化或跳过场景。
  - 验收：真实服务/同版MySQL浏览器通过并保留失败/成功证据；不以组件mock替代。

- [ ] **T409** [P] **[TEST] 端到端验收 checkout-order.spec.ts**
  - 主要文件：`frontend/tests/e2e/checkout-order.spec.ts`；依赖：T304、T372、T376、T403；依据：AT-09～13；AT-R01～06/08。
  - 执行：同/独立地址、模拟支付确认、多SKU成功/失败、响应丢失同key重试、他人订单404、DB改价历史金额不变。；先记录发现的目标失败，再逐文件补对应修正任务；禁止弱化或跳过场景。
  - 验收：真实服务/同版MySQL浏览器通过并保留失败/成功证据；不以组件mock替代。

- [ ] **T410** [P] **[TEST] RED：受控历史数据导入工具**
  - 主要文件：`scripts/tests/test_import_legacy_data.py`；依赖：T012、T160、T219、T221、T223、T225、T227；依据：plan §7.4；Q12；NFR-06。
  - 执行：先编写目标行为测试并运行聚焦命令；临时MySQL/staging验证显式目标确认、dry-run、幂等ID映射、用户名冲突、hash转换/重置策略、不造旧时间、拒绝未知目标。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [ ] **T411** [P] **[IMPL] GREEN：受控历史数据导入工具**
  - 主要文件：`scripts/import-legacy-data.py`；依赖：T012、T160、T219、T221、T223、T225、T227、T410；依据：plan §7.4；Q12；NFR-06。
  - 执行：仅在此文件实现受控历史数据导入工具，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [ ] **T412** [P] **[TEST] RED：迁移后只读对账工具**
  - 主要文件：`scripts/tests/test_audit_migration.py`；依赖：T411；依据：plan §7.4；Q12。
  - 执行：先编写目标行为测试并运行聚焦命令；对比逐表行数/主键/账户关联/订单归属/金额/库存/最大ID和历史精度；不执行修复写入。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [ ] **T413** [P] **[IMPL] GREEN：迁移后只读对账工具**
  - 主要文件：`scripts/audit-migration.py`；依赖：T411、T412；依据：plan §7.4；Q12。
  - 执行：仅在此文件实现迁移后只读对账工具，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [ ] **T414** [P] **[TEST] 真实数据形态升级与恢复验收**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/migration/UpgradeRestoreIT.java`；依赖：T173、T219、T221、T223、T225、T227、T413；依据：plan §7；NFR-06。
  - 执行：使用脱敏或合成旧数据演练空库/升级/checksum失败、孤儿/大小写冲突、备份恢复、增量订单不丢失；仅临时库。
  - 验收：通过并记录无法自动恢复字段；MySQL DDL失败不假定整组回滚；不自动baseline。

- [ ] **T415** [P] **[TEST] RED：可重复非破坏性负载测量工具**
  - 主要文件：`scripts/tests/test_load_profile.py`；依赖：T012、T386、T390；依据：NFR-07；plan §9、§11。
  - 执行：先编写目标行为测试并运行聚焦命令；标准库统计与调度测试先行：固定样本、延迟百分位、错误率和有界并发；未知目标/无授权拒绝写负载，不预设SLO。
  - 验收：记录明确的预期失败；缺少目标类型可作为首轮编译红灯，后续必须形成行为断言；不得使用 Ignore/Disabled 掩盖失败。

- [ ] **T416** [P] **[IMPL] GREEN：可重复非破坏性负载测量工具**
  - 主要文件：`scripts/load-profile.py`；依赖：T012、T386、T390、T415；依据：NFR-07；plan §9、§11。
  - 执行：仅在此文件实现可重复非破坏性负载测量工具，复用已完成契约和协作者，不在此任务补写其他生产文件。
  - 验收：对应测试转绿；不删改正确断言；运行同模块必要回归，记录命令和结果。

- [ ] **T417** [P] **[VERIFY] 记录隔离环境容量和资源测量**
  - 主要文件：`docs/verification/performance.md`；依赖：T392、T407～T409、T416；依据：plan §10、§11。
  - 执行：在临时应用/MySQL上执行已决Q12负载，记录资源限制、样本、吞吐/延迟/错误率/连接池及瓶颈，不触碰真实生产；只在本报告记录环境、结果、证据及未验证项，发现实现缺口则新增单文件任务。
  - 验收：相关断言均通过或明确标记阻塞；不能将未执行、跳过或缺陷特征测试通过写成目标已完成。

- [ ] **T418** [P] **[TEST] RED：发布镜像运行验收**
  - 主要文件：`scripts/tests/test_release_image.py`；依赖：T390、T392、T401、T402；依据：plan §9.3。
  - 执行：先写真实镜像验收：Java21、非root、健康探针、同源静态/API、内存限制、优雅停机及无内置secret；Docker不可用明确阻塞。
  - 验收：先运行记录未有镜像/不满足时的失败；实现后同一测试可复跑。

- [ ] **T419** [P] **[IMPL] PR持续集成流水线**
  - 主要文件：`.github/workflows/ci.yml`；依赖：T169、T171、T392、T407～T409、T414、T418；依据：plan §9.3、§10。
  - 执行：仅创建此workflow：Java21/Node锁定、npm ci/lint/typecheck/test/build、mvnw verify、MySQL/Testcontainers、E2E、契约校验；失败上传报告；不在PR发布镜像。
  - 验收：在分支/PR事件验证，不在缺Docker时跳过集成当成功；脚本引用均存在。

- [ ] **T420** [P] **[IMPL] 受信任发布与镜像检查流水线**
  - 主要文件：`.github/workflows/release.yml`；依赖：T418、T419；依据：plan §9.3。
  - 执行：仅创建此workflow：受信任分支/标签、测试门禁、Java21 Buildpacks、锁builder/run digest、SBOM/扫描、不可变image digest；无未经授权自动生产部署。
  - 验收：临时环境镜像验收通过；凭据不进入镜像或日志；实际发布按用户授权。

- [ ] **T421** [P] **[CONFIG] 独立旧基线回归任务**
  - 主要文件：`.github/workflows/legacy-baseline.yml`；依赖：T160；依据：plan §3.3、§9.3。
  - 执行：JDK8独立执行legacy脚本并上传结果；历史依赖失败明确说明，不污染现代依赖树。
  - 验收：不把未跑旧WAR误记通过，也不以旧构建故障掩盖现代流水线失败。

- [ ] **T422** [P] **[DOC] 编写迁移、切换与回滚运行手册**
  - 主要文件：`docs/operations/runbook.md`；依赖：T414、T420；依据：plan §7.4、§9；Q12。
  - 执行：记录停旧写→最终导入→对账→迁移→readiness→冒烟→切流；新订单产生后的回迁/前向修复；日志/指标定位和报警阈值按实测。
  - 验收：每条命令有前提、预期、恢复步骤；不指示新旧系统同时写库存；生产操作需授权。

- [ ] **T423** [P] **[DOC] 更新项目研发入口**
  - 主要文件：`README.md`；依赖：T160、T180、T399、T401、T406、T422；依据：plan §14。
  - 执行：只更新README：现代启动/构建/test/verify/前端发布命令、legacy脚本路径、spec/plan/tasks链接和已知限制。 增加开发指南与两份环境验证报告链接。
  - 验收：所列路径和命令与实际产物一致，不声称未验收内容已通过。

- [ ] **T424** [P] **[DOC] 更新旧基线定位说明**
  - 主要文件：`docs/baseline/README.md`；依赖：T160；依据：旧基线；plan §3.3。
  - 执行：仅修正归档后legacy路径、启动工作目录及报告位置；保留历史验证日期和边界。
  - 验收：旧17条测试证据仍可追溯，不改写为新系统验证。

- [ ] **T425** **[VERIFY] 研发与系统技术验收（生产发布前）**
  - 主要文件：`docs/verification/modernization.md`；依赖：T164～T166、T179、T180、T229、T230、T304、T306、T308、T310、T312、T314、T386、T400、T406～T409、T414、T417、T420～T424；依据：plan §10、§11。
  - 执行：依次运行聚焦测试和./mvnw verify、前端全检、真实E2E、镜像/迁移验收；逐项核对spec的56业务规则、NFR-01～07、AT-01～18及AT-R01～09，附Q决策和未完成项；只在本报告记录环境、结果、证据及未验证项，发现实现缺口则新增单文件任务。 此报告是发布候选门槛，不代替Phase 11的实际服务器部署与发布后观察。
  - 验收：相关断言均通过或明确标记阻塞；不能将未执行、跳过或缺陷特征测试通过写成目标已完成。

## Phase 11：服务器部署、生产发布与运行交接

执行编号：**T426～T447**；本阶段 22 项任务。按正文顺序推进，只有依赖已完成的 [P] 任务可并行；Q决策或环境条件不足时明确标阻塞。

- [ ] **T426** **[DECISION] 确定部署目标和发布参数**
  - 主要文件：`docs/operations/deployment-target.md`；依赖：T012、T016；依据：plan §9、§11；开发与交付补充。
  - 执行：确认预发布/生产服务器、平台与CPU、镜像仓库、域名/TLS、DB部署方式、网络/runner可达性、secret来源、备份存储与RPO/RTO、观察窗口及发布权限。建议单主机Compose；其他平台需先追加等价单文件部署任务，不擅自引入Kubernetes。
  - 验收：真实目标和访问前提有用户确认，文档只记引用不记secret值；目标未给定则保持阻塞，仍可独立准备测试/模板。

- [ ] **T427** [P] **[CONFIG] 提供生产变量和secret引用模板**
  - 主要文件：`infra/.env.prod.example`；依赖：T390、T426；依据：plan §9、§11；开发与交付补充。
  - 执行：列出应用镜像@digest、MySQL目标、受限应用账号、Flyway迁移账号引用、域名、TLS文件引用、管理监听、资源限制及备份目标；只放占位符，不复用开发.env。
  - 验收：应用、迁移、备份凭据权限分离；所有敏感值从批准secret机制注入，不进入Git或前端bundle。

- [ ] **T428** [P] **[TEST] RED：服务器部署编排脚本**
  - 主要文件：`scripts/tests/test_deploy.py`；依赖：T418、T420、T426；依据：plan §9、§11；开发与交付补充。
  - 执行：先编写dry-run与隔离目标测试：拒绝浮动镜像、目标不匹配/未授权、缺secret、迁移失败、探针超时；必须先备份/迁移验证再启动切流，失败不假报成功；mock实际生产调用。
  - 验收：目标脚本缺失/顺序不符合时有红灯；测试不访问真实生产，不以旧库恢复掩盖新订单。

- [ ] **T429** [P] **[IMPL] GREEN：服务器部署编排脚本**
  - 主要文件：`scripts/deploy.py`；依赖：T426、T428；依据：plan §9、§11；开发与交付补充。
  - 执行：只实现部署脚本：校验发布清单/目标/digest、调用独立备份和迁移工具、按已批准切换策略停旧起新、等待readiness、执行独立冒烟；保留上次镜像引用，失败返回非零，默认dry-run。
  - 验收：test_deploy.py转绿；缺协作者只明确失败，不顺带创建其他文件；实际服务器运行另依赖T444/T445门槛。

- [ ] **T430** [P] **[CONFIG] 定义生产单体应用部署配置**
  - 主要文件：`infra/compose.prod.yml`；依赖：T401、T427、T428；依据：plan §9、§11；开发与交付补充。
  - 执行：只定义固定digest单后端和已确认MySQL/网络/持久卷、资源限制、非root运行、健康检查和优雅停止；同源代理按目标接入；若DB是外部实例不再启动第二数据库。
  - 验收：在隔离目标config/up检查通过，业务MySQL不暴露公网，不包含secret明文；稳态只有一个后端业务进程。

- [ ] **T431** [P] **[CONFIG] 配置同源 HTTPS 与反向代理**
  - 主要文件：`infra/nginx.conf`；依赖：T426、T428、T430；依据：plan §9、§11；开发与交付补充。
  - 执行：只配置已确认域名/TLS引用、前端/API同源转发、受信任forwarded headers、超时及管理端点隔离；未知API错误不改成SPA页面。若复用现有代理，此文件作为受控配置片段。
  - 验收：代理配置检查通过；HTTPS、Secure Cookie、API/静态资源及探针暴露范围在T444验证；证书私钥不进仓库。

- [ ] **T432** [P] **[TEST] RED：发布数据库迁移工具**
  - 主要文件：`scripts/tests/test_migrate_release.py`；依赖：T414、T426；依据：plan §9、§11；开发与交付补充。
  - 执行：先测试只允许精确release的迁移脚本与固定Flyway工具、只用迁移凭据、baseline默认关闭、checksum/目标版本不符失败、迁移失败禁止发布；在临时MySQL演练DDL中断。
  - 验收：无脚本或迁移策略不正确时有失败证据；不调用生产，不假定MySQL多DDL可整体回滚。

- [ ] **T433** [P] **[IMPL] GREEN：发布数据库迁移工具**
  - 主要文件：`scripts/migrate-release.py`；依赖：T426、T432；依据：plan §9、§11；开发与交付补充。
  - 执行：只实现固定版本Flyway工具的调用：校验与发布镜像/提交一致的迁移内容、目标库、凭据引用，执行validate/info/migrate并记录脱敏前后版本；密码不作为可见命令参数，未知结构拒绝自动baseline。
  - 验收：test_migrate_release.py转绿；版本及工具来源与T016/T426一致，失败返回非零，应用DML账号不获得DDL权限。

- [ ] **T434** [P] **[TEST] RED：备份与隔离恢复工具**
  - 主要文件：`scripts/tests/test_backup_restore.py`；依赖：T413、T414、T426；依据：plan §9、§11；开发与交付补充。
  - 执行：先测试一致性备份、完整性清单、受保护输出、错误检测、仅恢复到明确批准的隔离目标，以及恢复后行数/订单/库存对账；禁止误覆盖现役库。
  - 验收：目标脚本缺失时先有红灯；合成测试库含新增订单，恢复完整性可实测。

- [ ] **T435** [P] **[IMPL] GREEN：备份与隔离恢复工具**
  - 主要文件：`scripts/backup-restore.py`；依赖：T426、T434；依据：plan §9、§11；开发与交付补充。
  - 执行：只实现MySQL备份/恢复调用、时间与版本清单、校验值、受限文件权限和批准存储机制；恢复必须指定隔离目标。凭据通过受限配置注入，输出不打印数据或密码。
  - 验收：test_backup_restore.py转绿；失败不产生“有效备份”标记，不自动回灌生产旧快照。

- [ ] **T436** [P] **[VERIFY] 执行可恢复性演练**
  - 主要文件：`docs/verification/restore-rehearsal.md`；依赖：T413、T414、T435；依据：plan §9、§11；开发与交付补充。
  - 执行：对隔离MySQL进行备份→恢复到另一隔离实例→只读对账，记录耗时、校验、订单归属/金额/库存、时间精度和不完整数据；与已定恢复目标比较。
  - 验收：备份可恢复的证据齐备，不仅文件存在；报告只存位置/校验引用，不提交备份数据。

- [ ] **T437** [P] **[TEST] RED：部署后只读冒烟工具**
  - 主要文件：`scripts/tests/test_post_deploy_smoke.py`；依赖：T418、T425、T426；依据：plan §9、§11；开发与交付补充。
  - 执行：先测试HTTPS/静态资源/API、运行commit或digest关联、readiness、匿名身份与CSRF、未登录订单401、未知API404以及有界失败；默认只读，不向真实用户写测试订单。
  - 验收：工具缺失时有红灯；失败输出含requestId/检查项而非响应敏感内容。

- [ ] **T438** [P] **[IMPL] GREEN：部署后只读冒烟工具**
  - 主要文件：`scripts/post-deploy-smoke.py`；依赖：T426、T437；依据：plan §9、§11；开发与交付补充。
  - 执行：只实现冒烟脚本：检查目标/版本、TLS、前端资源及关键只读HTTP契约，输出脱敏结果和非零失败码；预发布完整E2E另行执行，生产写入测试必须另外授权。
  - 验收：test_post_deploy_smoke.py转绿；坏版本、错误代理和不可用DB均能正确阻断验收。

- [ ] **T439** [P] **[TEST] RED：应用回滚工具与数据保护**
  - 主要文件：`scripts/tests/test_rollback_release.py`；依赖：T414、T426、T436；依据：plan §9、§11；开发与交付补充。
  - 执行：先测试前一镜像digest/兼容迁移版本校验、停写与切换顺序、健康失败、存在新订单时禁止旧库覆盖；不可逆schema变化拒绝自动回退。
  - 验收：目标工具缺失或会丢新数据时有红灯；仅在隔离数据集验证，不做生产恢复。

- [ ] **T440** [P] **[IMPL] GREEN：受控应用回滚工具**
  - 主要文件：`scripts/rollback-release.py`；依赖：T426、T439；依据：plan §9、§11；开发与交付补充。
  - 执行：仅实现回滚脚本：核对允许回退的应用/schema组合，默认dry-run；授权后停写并切回前一兼容镜像，保持当前业务库，执行readiness/冒烟。不兼容时停止并引导前向修复/显式回迁。
  - 验收：test_rollback_release.py转绿；不自动执行破坏性DDL或旧快照覆盖，不把回滚镜像等同于回滚数据。

- [ ] **T441** [P] **[DOC] 定义每次发布清单与验收门槛**
  - 主要文件：`docs/operations/release-checklist.md`；依赖：T420、T425、T426、T436；依据：plan §9、§11；开发与交付补充。
  - 执行：定义发布记录必填项：commit、测试报告、镜像digest、迁移版本/checksum、secret引用、备份校验、应用/schema回退兼容性、操作者/授权、窗口与观察目标；把部署、冒烟、回滚脚本的参数对应到清单。
  - 验收：一次release信息可追溯；没有digest/备份恢复证据/授权时不执行生产发布，不含敏感值。

- [ ] **T442** [P] **[OPS] 准备预发布与生产服务器运行环境**
  - 主要文件：`docs/operations/server-preparation.md`；依赖：T426；依据：plan §9、§11；开发与交付补充。
  - 执行：在获授权目标上准备Docker/Compose或已批准平台、应用目录/非root权限、持久卷/磁盘、网络/runner连通、DNS/TLS及续期、secret注入和受限DB账号；逐项记录实际验证，不在文档中写凭据值。
  - 验收：目标主机可拉取受信任digest、访问所需DB且数据卷可保留；前提缺失标阻塞，不能无目标擅自创建云资源。

- [ ] **T443** [P] **[IMPL] 实现受控部署流水线**
  - 主要文件：`.github/workflows/deploy.yml`；依赖：T420、T428～T431、T433、T435、T436、T438、T440～T442；依据：plan §9、§11；开发与交付补充。
  - 执行：只创建workflow_dispatch部署工作流：指定环境和已通过CI的不可变digest、批准的runner/secret来源、并发部署锁、preflight/备份/迁移/启动/探针/冒烟与结果报告；使用GitHub环境保护，拒绝PR触发生产部署。
  - 验收：测试/预发布演练通过，引用脚本存在；生产执行须明确授权且目标已确认，失败不会继续切流或把旧DB当新DB。

- [ ] **T444** [P] **[VERIFY] 完成预发布部署与失败回滚演练**
  - 主要文件：`docs/verification/staging-release.md`；依赖：T425、T433、T435、T436、T438、T440～T443；依据：plan §9、§11；开发与交付补充。
  - 执行：在已确认的预发布主机按清单执行镜像拉取、secret注入、备份、迁移、停旧起新、HTTPS/探针/只读冒烟和完整E2E；故意用不健康版本验证中止与兼容镜像回退，确认新增订单不丢失。
  - 验收：真实服务器/代理/数据库演练通过，同一候选digest可晋级；不能只用docker compose config或mock测试替代。

- [ ] **T445** [P] **[OPS] 执行已授权生产发布并记录结果**
  - 主要文件：`docs/verification/production-release.md`；依赖：T438、T441、T442、T444；依据：plan §9、§11；开发与交付补充。
  - 执行：取得本次具体生产发布授权后，按已演练流程晋级同一digest，验证备份/迁移版本，执行部署与只读冒烟，记录切换时刻、版本、操作者、requestId和结果；未授权只保留待执行状态。
  - 验收：真实生产运行版本与候选一致，静态/API/认证/健康检查通过；失败按清单中止或批准的兼容回滚，不声明未执行上线成功。

- [ ] **T446** [P] **[CONFIG] 配置受控备份调度工作流**
  - 主要文件：`.github/workflows/backup.yml`；依赖：T426、T435、T442；依据：plan §9、§11；开发与交付补充。
  - 执行：仅配置批准的定时/手工备份任务，使用可达私有DB的受信任runner和独立备份凭据；调用已测工具，落实加密存储/保留期限/失败通知及并发锁，不做定时自动恢复。
  - 验收：在预发布验证调度/手工执行及失败通知；频率/保留符合Q12，不在公共runner日志或CI公开artifact暴露备份数据。

- [ ] **T447** **[VERIFY] 记录发布后观察与交接**
  - 主要文件：`docs/verification/release-observation.md`；依赖：T438、T445、T446；依据：plan §9、§11；开发与交付补充。
  - 执行：按已确认窗口观察错误率、延迟、连接池、资源、订单/库存冲突及幂等指标，核对备份任务和告警接收；记录异常处置、是否回滚及运维交接。
  - 验收：观察窗口与阈值有依据、告警可触达、备份计划可验证；发布状态明确，不以刚启动readiness正常代替持续观察。

## 补遗任务：归档后路径修正

执行编号：**T448～T452**；本阶段 5 项任务，均依赖 T160（Phase 2 已完成）。来源：[legacy-relocation-check.md](baseline/legacy-relocation-check.md) §5 及全仓排查；建议在 Phase 3（T161）前执行。各项互不依赖、分属不同文件，满足 [P] 并行条件。

- [x] **T448** [P] **[DOC] 修正 README 旧基线路径引用**
  - 主要文件：`README.md`；依赖：T160；依据：legacy-relocation-check §5；plan §3.3。
  - 执行：回归命令改为 `bash legacy/scripts/legacy-baseline.sh`，独立测试 POM 表述改为 `legacy/baseline/pom.xml`；仅修正路径，不改业务结论与文档结构。
  - 验收：README 不再存在指向已迁移旧路径的命令或引用；其余内容逐字不变。

- [x] **T449** [P] **[DOC] 修正旧系统基线 README 路径引用**
  - 主要文件：`docs/baseline/README.md`；依赖：T160；依据：legacy-relocation-check §5；plan §3.3。
  - 执行：交付索引表三行（测试 POM、脚本、测试目录）、§2.1 原始启动位置（仓库根目录改为 legacy/）与 §2.2 命令块统一改为 legacy/ 前缀；报告路径 `baseline/target/` 改为 `legacy/baseline/target/`；§1/§5 的阶段历史叙述保持事实不改写。
  - 验收：文件内所有可执行命令与文件路径在当前仓库真实存在；历史描述未被改写。

- [x] **T450** [P] **[DOC] 修正 spec 源码追溯索引路径**
  - 主要文件：`docs/spec.md`；依赖：T160；依据：legacy-relocation-check §5；plan §3.3。
  - 执行：§12.3 源码追溯索引的 `src/…` 路径统一改为 `legacy/src/…`（或改说明为相对 legacy/ 目录）；§12 其余引用一并核对；需求文字、编号与语义零变化。
  - 验收：索引列出的每个路径在仓库中实际存在；spec 需求语义逐字未变。

- [x] **T451** [P] **[DOC] 修正 data-model 依据路径**
  - 主要文件：`docs/baseline/data-model.md`；依赖：T160；依据：legacy-relocation-check §5。
  - 执行：依据行的 H2-schema.sql、H2-dataload.sql 路径改为 legacy/ 前缀；表结构与关系描述不变。
  - 验收：依据路径在仓库中存在；数据关系描述逐字不变。

- [x] **T452** [P] **[DOC] 为 verification 历史记录补归档注记**
  - 主要文件：`docs/baseline/verification.md`；依赖：T160；依据：legacy-relocation-check §5。
  - 执行：文件头部补一行注记：本页命令与结果记录于 2026-11-02 归档前，对应文件已在 T027～T160 迁移至 legacy/，复跑入口为 `legacy/scripts/legacy-baseline.sh`，迁移校验见 legacy-relocation-check.md；正文历史命令与实测结果不改写。
  - 验收：正文历史证据零改写；读者依注记可定位全部新路径。

## 补遗任务：Phase 6 实施暴露的契约与迁移缺口

执行编号：**T453～T465**；13 项。全部由 Phase 6 实施时的真实缺口触发，均为追加式修正：只补齐缺失的表达或约束，不改变已确认的业务语义（Q01～Q15、OpenAPI 契约不变）。

- [x] **T453** [CONTRACT] **cart 领域契约补正**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/cart/domain/CartModels.java`；依据：Q08、CRT-01/11、plan §5.4。
  - 执行：持有权增加“账号+当前会话绑定哈希”构造（登录合并需要定位同一会话的游客车）；购物车状态增加 PENDING_MERGE（合并超限时冻结保存）；快照增加 pendingMerges（否则契约里的 mergeConflict/pendingMerges 没有来源）。
  - 验收：追加式修正，原有语义不变；CartModels 测试与全量回归通过。

- [x] **T454** [CONTRACT] **CartAssembler 端口补正**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/cart/web/CartAssembler.java`；依据：plan §5.4。
  - 执行：删除只接受标识列表的 `toPendingMerges(List<String>)`——标识本身不足以组装带明细的 PendingCartMerge，待合并内容改由快照携带并由 `toCartResponse` 统一映射。

- [x] **T455** [CONTRACT] **确认快照明细补正**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/domain/CheckoutModels.java`；依据：ORD-19、Q15、D05。
  - 执行：`CheckoutLine` 增加商品名与规格快照——契约的确认响应要求 items 带 productName 与 attributes，且快照不得按当前商品重算。

- [x] **T456** [CONTRACT] **价格端口补正**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/catalog/api/PricingQuery.java`；依据：Q02、Q10。
  - 执行：新增 `isListed(itemId)`——`isPurchasable` 同时含库存条件，无法表达“下架拒绝、缺货可加购”的加购规则。

- [x] **T457** [IMPL] **目录服务实现 isListed**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/catalog/application/CatalogQueryService.java`；依据：Q02、Q10。
  - 执行：只判断商品状态 P，不含库存条件。

- [x] **T458** [MIGRATION] **V6：同一会话唯一活动购物车**
  - 主要文件：`backend/src/main/resources/db/migration/V6__unique_active_guest_cart.sql`；依据：Q08、CRT-01。
  - 执行：新增生成列与唯一索引 `uk_cart_active_session`——V3 只约束了账号活动车，游客并发加购可能产生两个活动车。

- [x] **T459** [TEST] **迁移链断言更新**
  - 主要文件：`backend/src/test/java/ik/am/jpetstore/migration/Schema5MigrationTest.java`；依据：plan §7.3。
  - 执行：迁移链断言随新增迁移更新到 V7，并继续校验不静默 baseline。

- [x] **T460** [IMPL] **安全链允许游客写购物车**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/identity/infrastructure/SecurityConfiguration.java`；依据：Q08、CRT-01。
  - 执行：PUT/DELETE 的 `/api/cart/**` 也列为公开——游客必须能读写自己的会话购物车，写操作仍受 CSRF 保护，归属由会话绑定保证。

- [x] **T461** [TEST] **V7 迁移链校验**
  - 主要文件：`backend/src/main/resources/db/migration/V7__checkout_item_product_snapshot.sql`；依据：ORD-19、Q15、D05。
  - 执行：`checkout_item` 增加商品名与规格列——订单必须在商品被删除或改名后仍能展示已确认内容。

- [x] **T462** [CONTRACT] **下单端口补正**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/api/OrderCommand.java`；依据：plan §5.7、契约 Idempotency-Replayed。
  - 执行：返回值改为 `Placement(snapshot, replayed)`——原签名丢失“是否重放”这一传输层需要的信息。

- [x] **T463** [CONTRACT] **订单转换端口补正**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/web/OrderAssembler.java`；依据：ORD-17、Q11。
  - 执行：`toOrderSummary` 参数由订单快照改为订单摘要——列表不应为转换摘要而加载每张订单的全部明细。

- [x] **T464** [BUILD] **测试期连接池收紧**
  - 主要文件：`backend/pom.xml`；依据：plan §10。
  - 执行：单元与集成测试的 Hikari 池收紧为 4/1——Spring 上下文缓存会为每个上下文保留连接池，共享容器连接数有限；生产配置不受影响。

- [x] **T465** [IMPL] **确认快照读取还原商品文本**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/order/application/CheckoutApplicationService.java`；依据：ORD-19、Q15。
  - 执行：读取本人快照时从新列还原商品名与规格，使订单可以完全脱离当前商品目录。

- [x] **T466** [BUILD] **前端 lint 允许下划线前缀的未用参数**
  - 主要文件：`frontend/eslint.config.js`；依据：plan §8、§10。
  - 执行：`@typescript-eslint/no-unused-vars` 增加 `argsIgnorePattern: '^_'`——测试里的传输桩只需满足签名，参数以 `_` 前缀声明为“故意不用”。

- [x] **T467** [BUILD] **前端 lint 豁免任务清单固定的组件名**
  - 主要文件：`frontend/eslint.config.js`；依据：plan §8、§10。
  - 执行：`vue/multi-word-component-names` 豁免 `Recommendations`——组件文件名由任务清单固定，不改名。

- [x] **T468** [IMPL] **安全链区分业务 API 与前端页面**
  - 主要文件：`backend/src/main/java/ik/am/jpetstore/identity/infrastructure/SecurityConfiguration.java`；依据：plan §3.1、§9.1、NFR-02。
  - 执行：授权规则改为“/api/** 需要登录，其余路径公开”——同源 SPA 的页面外壳必须公开可访问，否则未登录用户连登录页都打不开；真正的数据访问控制仍由服务端判定。

## 需求与验收追溯

范围缩写沿用spec；本表不是“已通过”声明，所有checkbox初始为空。

| 需求/验收 | 关键任务 |
| --- | --- |
| ACC-01～04；AT-01/02/15/16 | T250、T251、T264、T361、T407 |
| ACC-05～06；AT-09/17 | T258、T259、T260、T363、T407 |
| ACC-07～10；AT-03/04；Q01/Q13 | T252、T253、T365、T407 |
| CAT-01～08；AT-05/06；Q10/Q14 | T244、T246、T262、T359、T408 |
| CRT-01～11；AT-07/08/18；D04a | T283、T284、T281、T285、T408 |
| ORD-01～08/12/13；AT-09/10；D04b/D06/D11 | T287、T288、T299、T369、T409 |
| ORD-09～11；AT-11/12；D03 | T293、T294、T295、T296、T303、T304 |
| INV-01～03；D02；AT-R01 | T289、T290、T273、T303 |
| ORD-14～20；AT-13；D05/D12；AT-R04 | T181、T297、T298、T375、T409 |
| PER-01～04；AT-14；Q09 | T246、T252、T349、T365、T407 |
| AT-R02/03/05/06/07/08 | T295、T293、T283、T287、T303、T409 |
| D01/D07/D09/D10；AT-R09；NFR-02/04/05 | T248、T258、T233、T391、T379、T407 |
| NFR-01/03 | T293、T250、T303 |
| NFR-06/07；Q12 | T410、T412、T414、T389、T383、T417、T418、T425、T426、T436、T444、T445、T447 |

## 完成判定

只有决策记录、先行失败测试、正确实现、MySQL/HTTP/浏览器/镜像等适用验证和最终报告均有证据，才能勾选任务。文件已创建或编译成功不等于业务完成；若任务无法在一个文件内闭合，应扩展依赖清单，不能偷偷把任务扩大。

清单统计：452 项任务，444 项具备同阶段并行机会，133 项单文件旧代码归档；类型计数：CONFIG=26、CONTRACT=34、DECISION=16、DOC=13、FIXTURE=2、IMPL=107、MOVE=133、OPS=3、TEST=110、VERIFY=8。任务状态以复选框及执行记录为准。
