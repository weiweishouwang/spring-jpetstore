package ik.am.jpetstore.order.application;

import ik.am.jpetstore.cart.api.CartCommand;
import ik.am.jpetstore.cart.domain.CartModels.CartOwner;
import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;
import ik.am.jpetstore.order.api.CheckoutCommand.CreateCheckoutRequest;
import ik.am.jpetstore.order.api.CheckoutCommand.FakePayment;
import ik.am.jpetstore.order.domain.CheckoutModels.CheckoutSnapshot;
import ik.am.jpetstore.order.domain.OrderModels.Address;
import ik.am.jpetstore.order.domain.OrderRequestModels.IdempotencyKey;
import ik.am.jpetstore.shared.error.BusinessFailure;
import ik.am.jpetstore.support.MySqlTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T303: 独立连接并发验收。
 *
 * <p>用屏障让并发请求在同一时刻进入各自的事务与连接，不靠 sleep 排序（plan §10）。覆盖库存 1
 * 时两人抢购、同键同载荷、同键异载荷、同确认快照换键、多 SKU 部分失败、响应丢失重试与订单编号
 * 唯一（AT-R01～08）。</p>
 */
@SpringBootTest
class OrderConcurrencyIT {

    private static final Address BILLING = new Address("Alice", "Ishikawa", "1-2-3 Sakuragaoka", null, "Shibuya",
            "Tokyo", "150-0001", "JP");

    private static final Address SHIPPING = new Address("Bob", "Yamada", "4-5-6 Omotesando", "Apt 301", "Minato",
            "Tokyo", "107-0052", "JP");

    @Autowired
    private IdempotentOrderService orderService;

    @Autowired
    private ik.am.jpetstore.order.application.CheckoutApplicationService checkoutService;

    @Autowired
    private CartCommand cartCommand;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.identity.web.IdentityAssembler identityAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.catalog.web.CatalogAssembler catalogAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.account.web.AccountAssembler accountAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.cart.web.CartAssembler cartAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.order.web.OrderAssembler orderAssembler;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlTestDatabase::url);
        registry.add("spring.datasource.username", MySqlTestDatabase::username);
        registry.add("spring.datasource.password", MySqlTestDatabase::password);
    }

    @BeforeEach
    void resetData() {
        jdbcTemplate.update("DELETE FROM order_request");
        jdbcTemplate.update("DELETE FROM orderstatus");
        jdbcTemplate.update("DELETE FROM lineitem");
        jdbcTemplate.update("DELETE FROM orders");
        jdbcTemplate.update("DELETE FROM checkout_item");
        jdbcTemplate.update("DELETE FROM checkout");
        jdbcTemplate.update("DELETE FROM cart_item");
        jdbcTemplate.update("DELETE FROM cart");
        jdbcTemplate.update("DELETE FROM inventory");
        jdbcTemplate.update("DELETE FROM item");
        jdbcTemplate.update("DELETE FROM product");
        jdbcTemplate.update("DELETE FROM supplier");
        jdbcTemplate.update("DELETE FROM category");
        jdbcTemplate.update("INSERT INTO category (catid, name, description) VALUES ('FISH', 'Fish', 'd')");
        jdbcTemplate.update("INSERT INTO supplier (suppid, name, status, addr1, addr2, city, state, zip, country,"
                + " phone) VALUES (1, 'S', 'P', 'a', NULL, 'c', 's', 'z', 'C', 'p')");
        jdbcTemplate.update("INSERT INTO product (productid, category, name, description)"
                + " VALUES ('FI-SW-01', 'FISH', 'Angelfish', 'd')");
        jdbcTemplate.update("INSERT INTO item (itemid, productid, list_price, unit_cost, supplier, status, attr1)"
                + " VALUES ('EST-1', 'FI-SW-01', 16.50, 10.00, 1, 'P', 'Large'),"
                + " ('EST-2', 'FI-SW-01', 10.50, 6.00, 1, 'P', 'Small')");
        jdbcTemplate.update("INSERT INTO inventory (itemid, qty, version) VALUES ('EST-1', 1, 0),"
                + " ('EST-2', 10, 0)");
    }

    @Test
    void onlyOneOfTwoBuyersWinsWhenTheStockIsOne() throws Exception {
        CheckoutSnapshot alice = checkoutWith("alice", "EST-1");
        CheckoutSnapshot bob = checkoutWith("bob", "EST-1");

        List<Result> results = runConcurrently(
                () -> orderService.placeOrder("alice", alice.checkoutId(), new IdempotencyKey("alice-key")),
                () -> orderService.placeOrder("bob", bob.checkoutId(), new IdempotencyKey("bob-key")));

        assertThat(results).as("库存只有 1，最多一单成功").filteredOn(Result::succeeded).hasSize(1);
        assertThat(jdbcTemplate.queryForObject("SELECT qty FROM inventory WHERE itemid = 'EST-1'", Integer.class))
                .as("库存不得为负（Q02）")
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lineitem", Integer.class)).isEqualTo(1);
    }

    @Test
    void theSameKeyWithTheSamePayloadCreatesOnlyOneOrder() throws Exception {
        CheckoutSnapshot checkout = checkoutWith("alice", "EST-1");

        List<Result> results = runConcurrently(
                () -> orderService.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("same-key")),
                () -> orderService.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("same-key")));

        assertThat(results).filteredOn(Result::succeeded).hasSize(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class))
                .as("同键同载荷只创建一张订单")
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT qty FROM inventory WHERE itemid = 'EST-1'", Integer.class))
                .isZero();
    }

    @Test
    void theSameKeyWithADifferentPayloadIsRejected() throws Exception {
        CartSnapshot cart = cartCommand.addItem(CartOwner.account("alice"), "EST-1", 0);
        // 两张快照来自同一个购物车版本：只有标识不同，摘要因此不同（幂等键冲突的前提）。
        CheckoutSnapshot first = checkoutService.create(new CreateCheckoutRequest("alice", cart.version(), BILLING,
                SHIPPING, new FakePayment("Visa", "FAKE-4111-1111-1111-1111", "12/2026")));
        CheckoutSnapshot second = checkoutService.create(new CreateCheckoutRequest("alice", cart.version(), BILLING,
                SHIPPING, new FakePayment("Visa", "FAKE-4111-1111-1111-1111", "12/2026")));

        List<Result> results = runConcurrently(
                () -> orderService.placeOrder("alice", first.checkoutId(), new IdempotencyKey("conflict-key")),
                () -> orderService.placeOrder("alice", second.checkoutId(), new IdempotencyKey("conflict-key")));

        assertThat(results).filteredOn(Result::succeeded).hasSize(1);
        assertThat(results).filteredOn(result -> !result.succeeded())
                .extracting(result -> result.failure().code())
                .contains(BusinessFailure.Code.IDEMPOTENCY_CONFLICT);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
    }

    @Test
    void theSameCheckoutWithAnotherKeyIsRejected() throws Exception {
        CheckoutSnapshot checkout = checkoutWith("alice", "EST-1");

        List<Result> results = runConcurrently(
                () -> orderService.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("key-a")),
                () -> orderService.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("key-b")));

        assertThat(results).filteredOn(Result::succeeded).hasSize(1);
        assertThat(results).filteredOn(result -> !result.succeeded())
                .extracting(result -> result.failure().code())
                .contains(BusinessFailure.Code.CHECKOUT_ALREADY_SUBMITTED);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
    }

    @Test
    void aPartialFailureAcrossMultipleSkusRollsBackEverything() throws Exception {
        jdbcTemplate.update("UPDATE inventory SET qty = 0 WHERE itemid = 'EST-2'");
        CheckoutSnapshot checkout = checkoutWithTwoItems("alice");

        Result result = attempt(new CyclicBarrier(1),
                () -> orderService.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("multi-key")));

        assertThat(result.succeeded()).isFalse();
        assertThat(result.failure().code()).isEqualTo(BusinessFailure.Code.OUT_OF_STOCK);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class))
                .as("任一 SKU 不足时整单回滚")
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT qty FROM inventory WHERE itemid = 'EST-1'", Integer.class))
                .as("已扣减的 SKU 必须回滚")
                .isEqualTo(1);
    }

    @Test
    void aLostResponseCanBeRetriedWithTheSameKey() throws Exception {
        CheckoutSnapshot checkout = checkoutWith("alice", "EST-1");

        var first = orderService.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("retry-key"));
        var retry = orderService.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("retry-key"));

        assertThat(retry.replayed()).isTrue();
        assertThat(retry.snapshot().orderId()).isEqualTo(first.snapshot().orderId());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
    }

    @Test
    void concurrentOrdersGetDistinctOrderNumbers() throws Exception {
        jdbcTemplate.update("UPDATE inventory SET qty = 100 WHERE itemid = 'EST-1'");
        CheckoutSnapshot alice = checkoutWith("alice", "EST-1");
        CheckoutSnapshot bob = checkoutWith("bob", "EST-1");

        List<Result> results = runConcurrently(
                () -> orderService.placeOrder("alice", alice.checkoutId(), new IdempotencyKey("alice-key")),
                () -> orderService.placeOrder("bob", bob.checkoutId(), new IdempotencyKey("bob-key")));

        assertThat(results).filteredOn(Result::succeeded).hasSize(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT orderid) FROM orders", Integer.class))
                .as("并发创建的订单号必须唯一（ORD-09）")
                .isEqualTo(2);
    }

    // ------------------------------------------------------------------ 辅助

    /** 一次并发尝试的结果。 */
    private record Result(boolean succeeded, String orderId, BusinessFailure failure) {

        static Result ok(String orderId) {
            return new Result(true, orderId, null);
        }

        static Result failed(BusinessFailure failure) {
            return new Result(false, null, failure);
        }
    }

    private List<Result> runConcurrently(Callable<ik.am.jpetstore.order.api.OrderCommand.Placement> first,
            Callable<ik.am.jpetstore.order.api.OrderCommand.Placement> second) throws Exception {
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Result> one = executor.submit(() -> attempt(barrier, first));
            Future<Result> two = executor.submit(() -> attempt(barrier, second));
            return List.of(one.get(30, TimeUnit.SECONDS), two.get(30, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private List<Result> runConcurrently(Callable<ik.am.jpetstore.order.api.OrderCommand.Placement> only)
            throws Exception {
        return runConcurrently(only, only);
    }

    private Result attempt(CyclicBarrier barrier,
            Callable<ik.am.jpetstore.order.api.OrderCommand.Placement> action) {
        try {
            barrier.await(30, TimeUnit.SECONDS);
            return Result.ok(action.call().snapshot().orderId());
        } catch (BusinessFailure e) {
            return Result.failed(e);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private CheckoutSnapshot checkoutWith(String userId, String itemId) {
        CartSnapshot cart = cartCommand.addItem(CartOwner.account(userId), itemId, 0);
        return checkoutService.create(new CreateCheckoutRequest(userId, cart.version(), BILLING, SHIPPING,
                new FakePayment("Visa", "FAKE-4111-1111-1111-1111", "12/2026")));
    }

    private CheckoutSnapshot checkoutWithTwoItems(String userId) {
        CartSnapshot cart = cartCommand.addItem(CartOwner.account(userId), "EST-1", 0);
        cart = cartCommand.addItem(CartOwner.account(userId), "EST-2", cart.version());
        return checkoutService.create(new CreateCheckoutRequest(userId, cart.version(), BILLING, SHIPPING,
                new FakePayment("Visa", "FAKE-4111-1111-1111-1111", "12/2026")));
    }
}
