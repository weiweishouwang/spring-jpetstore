# 数据结构基线

依据：`legacy/src/main/resources/database/H2-schema.sql`、`H2-dataload.sql` 及 MyBatis Mapper。完整字段定义以原始 DDL 为准；本页记录业务关系和迁移时需要保留的约束。

## 1. 表结构与初始数据

| 表 | 主键 | 关键字段/约束 | 初始化行数 |
| --- | --- | --- | --- |
| `signon` | `username` | 用户名/密码均 varchar(25)，均非空 | 2 |
| `account` | `userid` | 编号 varchar(80)，邮箱/姓名/地址等；addr2 可空 | 2 |
| `profile` | `userid` | 语言、偏好分类、推荐/横幅标识 | 2 |
| `bannerdata` | `favcategory` | 横幅内容 varchar(255)，可包含 HTML | 5 |
| `category` | `catid` | 编号 varchar(10)、名称、描述 | 5 |
| `product` | `productid` | 编号 varchar(10)、category、名称、描述 | 16 |
| `item` | `itemid` | 编号 varchar(10)、productid、售价、成本、供应商、状态、attr1～5 | 28 |
| `inventory` | `itemid` | qty 整数非空，无非负检查约束 | 28，每项 10000 |
| `supplier` | `suppid` | 整数编号、名称、状态、地址、电话 | 2 |
| `orders` | `orderid` | 用户、日期、双地址、金额、卡片、承运商、地区 | 0 |
| `orderstatus` | `(orderid, linenum)` | DATE 时间、varchar(2) 状态 | 0 |
| `lineitem` | `(orderid, linenum)` | itemid、整数数量、decimal(10,2) 单价 | 0 |
| `sequence` | `name` | nextid 整数非空 | 1，`ordernum=1000` |

初始化统计是当前演示脚本的事实，不要求未来产品数、初始库存或订单起号固定为这些值。

## 2. 实际外键与逻辑关系

DDL 中明确存在的外键只有：

- `product.category → category.catid`。
- `item.productid → product.productid`。
- `item.supplier → supplier.suppid`。

其他主要关联依靠应用约定，DDL 没有对应外键：

| 逻辑关系 | 基数/用途 | 注意事项 |
| --- | --- | --- |
| account.userid = signon.username = profile.userid | 一份账户关联凭据与偏好 | 长度不统一；读账户依赖三表关联 |
| profile.favcategory = bannerdata.favcategory | 按偏好选择横幅 | 当前内连接意味着缺横幅可能导致账户查询不到 |
| inventory.itemid = item.itemid | 一可售项对应一份库存 | 商品查询使用连接；缺库存行会影响详情 |
| orders.userid = account.userid | 一个用户有多单 | 查询列表必须按当前用户限制 |
| lineitem.orderid = orders.orderid | 一单多明细 | 无外键，依靠事务保证整体写入 |
| lineitem.itemid = item.itemid | 历史明细关联当前商品 | 商品文本不是快照；缺失商品可能导致详情异常 |
| orderstatus.orderid = orders.orderid | 订单关联状态 | 查询不筛最新状态，新增多状态前需重新设计查询 |

非主键索引：`productCat(category)`、`productName(name)`、`itemProd(productid)`。名称搜索使用 `lower(name) LIKE '%...%'`，不能推导出该普通索引对包含搜索有效。

## 3. 金额、数量、地址与时间

- Item 售价/成本、订单总额、明细单价主要使用 `decimal(10,2)`；当前没有显式币种字段。
- 展示使用 `$`，不能仅凭符号确定 USD/CAD 或多币种需求。
- 数量与库存为整数，没有数据库非负约束或业务购买上限。
- 账单和收货地址分别存在订单头中；地址第一行等大部分字段非空，第二行可空。
- 订单头保存总额，明细保存数量及单价；当前明细小计计算错误地使用关联商品当前售价，见 D05。
- `orders.orderdate` 和 `orderstatus.timestamp` 为 DATE；服务端模型为 `java.util.Date`，页面显示时分秒。数据精度和时区不能由页面格式倒推出保证。
- 订单初始状态 `P`、承运商 `UPS`、地区 `CA` 为硬编码；没有完整状态迁移实现。

## 4. 写入与事务

| 操作 | 写入顺序 | 事务边界 |
| --- | --- | --- |
| 注册 | account → profile → signon | AccountService.insertAccount |
| 修改资料 | account → profile → 可选 signon | AccountService.updateAccount |
| 下单 | 读/更新序列 → 逐项减库存 → orders → orderstatus → lineitem | OrderService.insertOrder |

下单序列先读取旧 nextid，用旧值作为订单号，再写 nextid+1。没有显式行锁或比较更新；单事务回滚正确不代表并发分配一定正确。

状态插入时 `orderstatus.linenum` 使用订单号，并非独立的状态事件序号；当前只写一条初始状态，不能直接扩展为完整历史状态流。

## 5. 存储生命周期

- H2 是命名内存数据库，同一测试进程内连接池共享；重启应用不保留演示订单。
- 应用启动执行建表和装载脚本，`ignore-failures=ALL` 可能掩盖初始化错误。
- 购物车在 HTTP Session，不在这 13 张业务表中。
- 测试每次先清空内存库，再严格执行原始脚本，并清理 Mapper 缓存；不会给正式库做数据迁移。
- 后续真实数据迁移需要逐表核对行数、主键、逻辑孤儿记录、金额、库存及账号关联，不能只比较表名和字段名。
