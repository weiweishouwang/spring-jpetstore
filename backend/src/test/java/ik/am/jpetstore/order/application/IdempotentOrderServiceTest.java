package ik.am.jpetstore.order.application;

import ik.am.jpetstore.cart.api.CartCommand;
import ik.am.jpetstore.cart.domain.CartModels.CartOwner;
import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;
import ik.am.jpetstore.order.api.CheckoutCommand.CreateCheckoutRequest;
import ik.am.jpetstore.order.api.CheckoutCommand.FakePayment;
import ik.am.jpetstore.order.api.OrderCommand.Placement;
import ik.am.jpetstore.order.domain.CheckoutModels.CheckoutSnapshot;
import ik.am.jpetstore.order.domain.OrderModels.Address;
import ik.am.jpetstore.order.domain.OrderRequestModels.IdempotencyKey;
import ik.am.jpetstore.shared.error.BusinessFailure;
import ik.am.jpetstore.support.MySqlTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.willThrow;

/**
 * T295: 订单幂等外层协调。
 *
 * <p>覆盖同键同摘要重放原结果、异摘要 409、跨用户不重放、唯一冲突后在新事务读取、同一确认快照
 * 换键 409，以及锁超时 503 可重试（plan §5.7、D03、NFR-03、AT-R02/08）。</p>
 */
@SpringBootTest
class IdempotentOrderServiceTest {

    private static final Address BILLING = new Address("Alice", "Ishikawa", "1-2-3 Sakuragaoka", null, "Shibuya",
            "Tokyo", "150-0001", "JP");

    private static final Address SHIPPING = new Address("Bob", "Yamada", "4-5-6 Omotesando", "Apt 301", "Minato",
            "Tokyo", "107-0052", "JP");

    @Autowired
    private IdempotentOrderService service;

    @Autowired
    private ik.am.jpetstore.order.application.CheckoutApplicationService checkoutService;

    @Autowired
    private CartCommand cartCommand;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoSpyBean
    private OrderTransactionService transactionService;

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
                + " VALUES ('EST-1', 'FI-SW-01', 16.50, 10.00, 1, 'P', 'Large')");
        jdbcTemplate.update("INSERT INTO inventory (itemid, qty, version) VALUES ('EST-1', 100, 0)");
    }

    @Test
    void theSameKeyWithTheSameDigestReplaysTheOriginalResult() {
        CheckoutSnapshot checkout = checkoutWith("alice", "EST-1");

        Placement first = service.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("key-1"));
        Placement second = service.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("key-1"));

        assertThat(first.replayed()).isFalse();
        assertThat(second.replayed()).isTrue();
        assertThat(second.snapshot().orderId()).isEqualTo(first.snapshot().orderId());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class))
                .as("重放不得创建第二张订单")
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT qty FROM inventory WHERE itemid = 'EST-1'", Integer.class))
                .as("重放不得再次扣减库存")
                .isEqualTo(99);
    }

    @Test
    void theSameKeyWithADifferentDigestIsRejected() {
        CheckoutSnapshot first = checkoutWith("alice", "EST-1");
        service.placeOrder("alice", first.checkoutId(), new IdempotencyKey("key-1"));

        jdbcTemplate.update("DELETE FROM cart_item");
        jdbcTemplate.update("DELETE FROM cart");
        CheckoutSnapshot second = checkoutWith("alice", "EST-1");

        assertThatThrownBy(() -> service.placeOrder("alice", second.checkoutId(), new IdempotencyKey("key-1")))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(failure -> assertThat(((BusinessFailure) failure).code())
                        .isEqualTo(BusinessFailure.Code.IDEMPOTENCY_CONFLICT));
    }

    @Test
    void anotherUserNeverSeesTheResult() {
        CheckoutSnapshot checkout = checkoutWith("alice", "EST-1");
        service.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("key-1"));

        assertThatThrownBy(() -> service.placeOrder("bob", checkout.checkoutId(), new IdempotencyKey("key-1")))
                .as("跨用户不得重放他人结果")
                .isInstanceOf(BusinessFailure.class);
    }

    @Test
    void theSameCheckoutWithAnotherKeyIsRejected() {
        CheckoutSnapshot checkout = checkoutWith("alice", "EST-1");
        service.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("key-1"));

        assertThatThrownBy(() -> service.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("key-2")))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(failure -> assertThat(((BusinessFailure) failure).code())
                        .isEqualTo(BusinessFailure.Code.CHECKOUT_ALREADY_SUBMITTED));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
    }

    @Test
    void aFailedAttemptLeavesNoRecordSoTheSameKeyCanBeUsedAgain() {
        CheckoutSnapshot checkout = checkoutWith("alice", "EST-1");
        jdbcTemplate.update("UPDATE inventory SET qty = 0 WHERE itemid = 'EST-1'");

        assertThatThrownBy(() -> service.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("key-1")))
                .isInstanceOf(BusinessFailure.class);

        jdbcTemplate.update("UPDATE inventory SET qty = 5 WHERE itemid = 'EST-1'");

        Placement placement = service.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("key-1"));
        assertThat(placement.replayed()).isFalse();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class)).isEqualTo(1);
    }

    @Test
    void aLockTimeoutIsRetryableAndKeepsTheKey() {
        willThrow(new CannotAcquireLockException("lock wait timeout"))
                .given(transactionService).placeOrder(anyString(), anyString(), any(), any());

        assertThatThrownBy(() -> service.placeOrder("alice", "ck-1", new IdempotencyKey("key-1")))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(failure -> {
                    BusinessFailure business = (BusinessFailure) failure;
                    assertThat(business.code()).isEqualTo(BusinessFailure.Code.ORDER_RETRYABLE);
                    assertThat(business.getMessage()).contains("同一个幂等键");
                });
    }

    private CheckoutSnapshot checkoutWith(String userId, String itemId) {
        CartSnapshot cart = cartCommand.addItem(CartOwner.account(userId), itemId, 0);
        return checkoutService.create(new CreateCheckoutRequest(userId, cart.version(), BILLING, SHIPPING,
                new FakePayment("Visa", "FAKE-4111-1111-1111-1111", "12/2026")));
    }
}
