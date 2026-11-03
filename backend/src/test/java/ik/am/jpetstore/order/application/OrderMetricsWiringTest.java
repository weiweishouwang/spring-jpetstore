package ik.am.jpetstore.order.application;

import io.micrometer.core.instrument.MeterRegistry;
import ik.am.jpetstore.cart.api.CartCommand;
import ik.am.jpetstore.cart.domain.CartModels.CartOwner;
import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;
import ik.am.jpetstore.order.api.CheckoutCommand.CreateCheckoutRequest;
import ik.am.jpetstore.order.api.CheckoutCommand.FakePayment;
import ik.am.jpetstore.order.domain.CheckoutModels.CheckoutSnapshot;
import ik.am.jpetstore.order.domain.OrderModels.Address;
import ik.am.jpetstore.order.domain.OrderRequestModels.IdempotencyKey;
import ik.am.jpetstore.order.infrastructure.OrderMetrics;
import ik.am.jpetstore.support.MySqlTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T385: 下单协调器指标接入。
 *
 * <p>断言真正经过协调器的调用会更新指标：成交成功、库存冲突与幂等重放各自计数；失败不计入成交
 * （plan §9.2、NFR-07）。</p>
 */
@SpringBootTest
class OrderMetricsWiringTest {

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

    @Autowired
    private MeterRegistry registry;

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
        // 指标注册表在上下文里是单例：每个用例从零开始计数。
        registry.clear();
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
    void aPlacedOrderIsCountedAsPlaced() {
        CheckoutSnapshot checkout = checkoutWith("alice");

        service.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("key-1"));

        assertThat(count(OrderMetrics.OUTCOME_PLACED)).isEqualTo(1.0);
        assertThat(count(OrderMetrics.OUTCOME_FAILED)).isZero();
    }

    @Test
    void aRepeatedSubmissionIsCountedAsReplay() {
        CheckoutSnapshot checkout = checkoutWith("alice");

        service.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("key-1"));
        service.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("key-1"));

        assertThat(count(OrderMetrics.OUTCOME_REPLAY)).isEqualTo(1.0);
        assertThat(count(OrderMetrics.OUTCOME_PLACED)).isEqualTo(1.0);
    }

    @Test
    void aStockConflictIsCountedAsAStockConflictAndNotAsAPlacement() {
        CheckoutSnapshot checkout = checkoutWith("alice");
        jdbcTemplate.update("UPDATE inventory SET qty = 0 WHERE itemid = 'EST-1'");

        assertThatThrownBy(() -> service.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("key-1")))
                .isInstanceOf(ik.am.jpetstore.shared.error.BusinessFailure.class);

        assertThat(count(OrderMetrics.OUTCOME_STOCK_CONFLICT)).isEqualTo(1.0);
        assertThat(count(OrderMetrics.OUTCOME_PLACED)).isZero();
    }

    private double count(String outcome) {
        io.micrometer.core.instrument.Counter counter = registry.find(OrderMetrics.METRIC)
                .tag("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    private CheckoutSnapshot checkoutWith(String userId) {
        CartSnapshot cart = cartCommand.addItem(CartOwner.account(userId), "EST-1", 0);
        return checkoutService.create(new CreateCheckoutRequest(userId, cart.version(), BILLING, SHIPPING,
                new FakePayment("Visa", "FAKE-4111-1111-1111-1111", "12/2026")));
    }
}
