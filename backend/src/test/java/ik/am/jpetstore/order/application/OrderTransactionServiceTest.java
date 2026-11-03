package ik.am.jpetstore.order.application;

import ik.am.jpetstore.cart.api.CartCommand;
import ik.am.jpetstore.cart.domain.CartModels.CartOwner;
import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;
import ik.am.jpetstore.order.api.CheckoutCommand.CreateCheckoutRequest;
import ik.am.jpetstore.order.api.CheckoutCommand.FakePayment;
import ik.am.jpetstore.order.domain.CheckoutModels.CheckoutSnapshot;
import ik.am.jpetstore.order.domain.OrderModels.Address;
import ik.am.jpetstore.order.domain.OrderRequestModels.IdempotencyKey;
import ik.am.jpetstore.order.domain.OrderRequestModels.RequestDigest;
import ik.am.jpetstore.order.domain.OrderRequestHasher;
import ik.am.jpetstore.order.infrastructure.OrderRequestMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T293: 下单本地事务。
 *
 * <p>验证幂等键占用、确认快照锁、购物车锁、升序库存扣减、订单与明细与状态写入、确认快照与购物车
 * 消费、可重放响应保存都在同一个事务里；任一步失败整体回滚；自增订单号允许跳号但不重复
 * （ORD-09～13、NFR-01/03、AT-11/12、AT-R03/08、plan §5.6）。</p>
 */
@SpringBootTest
class OrderTransactionServiceTest {

    private static final Address BILLING = new Address("Alice", "Ishikawa", "1-2-3 Sakuragaoka", null, "Shibuya",
            "Tokyo", "150-0001", "JP");

    private static final Address SHIPPING = new Address("Bob", "Yamada", "4-5-6 Omotesando", "Apt 301", "Minato",
            "Tokyo", "107-0052", "JP");

    @Autowired
    private OrderTransactionService service;

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
        jdbcTemplate.update("INSERT INTO inventory (itemid, qty, version) VALUES ('EST-1', 10, 0),"
                + " ('EST-2', 1, 0)");
    }

    @Test
    void anOrderIsCreatedWithItsLinesStatusAndConsumedCart() {
        CheckoutSnapshot checkout = checkoutWith("EST-1");

        OrderTransactionService.OrderPlacement placement = service.placeOrder("alice", checkout.checkoutId(),
                new IdempotencyKey("key-1"), digest(checkout.checkoutId()));

        assertThat(placement.snapshot().orderId()).isNotBlank();
        assertThat(placement.snapshot().totalAmount().toPlainString()).isEqualTo("16.50");
        assertThat(placement.response().status()).isEqualTo(201);
        assertThat(placement.response().location()).isEqualTo("/api/orders/" + placement.snapshot().orderId());

        Long orderId = Long.valueOf(placement.snapshot().orderId());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders WHERE orderid = ?", Integer.class, orderId))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lineitem WHERE orderid = ?", Integer.class,
                orderId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orderstatus WHERE orderid = ?", Integer.class,
                orderId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT qty FROM inventory WHERE itemid = 'EST-1'", Integer.class))
                .isEqualTo(9);
        assertThat(jdbcTemplate.queryForObject("SELECT state FROM cart WHERE owner_userid = 'alice'", String.class))
                .as("成交后购物车被消费")
                .isEqualTo("CONSUMED");
        assertThat(jdbcTemplate.queryForObject("SELECT state FROM checkout WHERE checkout_id = ?", String.class,
                checkout.checkoutId())).isEqualTo("CONSUMED");
    }

    @Test
    void theIdempotencyRecordKeepsTheReplayableResponse() {
        CheckoutSnapshot checkout = checkoutWith("EST-1");

        service.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("key-1"),
                digest(checkout.checkoutId()));

        OrderRequestMapper.OrderRequestRow record = jdbcTemplate.queryForObject(
                "SELECT user_id, operation, idempotency_key, request_hash, order_id, response_status,"
                        + " response_location, response_body FROM order_request WHERE user_id = 'alice'",
                (rs, rowNum) -> new OrderRequestMapper.OrderRequestRow(null, rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getString(4), rs.getLong(5), rs.getInt(6), rs.getString(7),
                        rs.getString(8), null, null));
        assertThat(record.operation()).isEqualTo(OrderRequestMapper.OPERATION_PLACE_ORDER);
        assertThat(record.idempotencyKey()).isEqualTo("key-1");
        assertThat(record.responseStatus()).isEqualTo(201);
        assertThat(record.responseBody())
                .as("可重放响应不含 Cookie、CSRF、密码或完整卡号")
                .doesNotContain("Set-Cookie")
                .doesNotContain("FAKE-");
    }

    @Test
    void insufficientStockRollsBackTheWholeTransaction() {
        CheckoutSnapshot checkout = checkoutWith("EST-1");
        jdbcTemplate.update("UPDATE inventory SET qty = 0 WHERE itemid = 'EST-1'");

        assertThatThrownBy(() -> service.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("key-1"),
                digest(checkout.checkoutId())))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(failure -> assertThat(((BusinessFailure) failure).code())
                        .isEqualTo(BusinessFailure.Code.OUT_OF_STOCK));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM orders", Integer.class))
                .as("失败时不留下订单")
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM order_request", Integer.class))
                .as("失败时幂等占位一起回滚")
                .isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT state FROM cart WHERE owner_userid = 'alice'", String.class))
                .as("失败时购物车内容保留")
                .isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("SELECT state FROM checkout WHERE checkout_id = ?", String.class,
                checkout.checkoutId())).isEqualTo("OPEN");
    }

    @Test
    void aChangedCartIsRejectedAndAsksForAReconfirmation() {
        CheckoutSnapshot checkout = checkoutWith("EST-1");
        cartCommand.addItem(CartOwner.account("alice"), "EST-2", checkout.cartVersion());

        assertThatThrownBy(() -> service.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("key-1"),
                digest(checkout.checkoutId())))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(failure -> assertThat(((BusinessFailure) failure).code())
                        .isEqualTo(BusinessFailure.Code.CHECKOUT_STALE));
    }

    @Test
    void aPriceChangeIsRejectedAndAsksForAReconfirmation() {
        CheckoutSnapshot checkout = checkoutWith("EST-1");
        jdbcTemplate.update("UPDATE item SET list_price = 20.00 WHERE itemid = 'EST-1'");

        assertThatThrownBy(() -> service.placeOrder("alice", checkout.checkoutId(), new IdempotencyKey("key-1"),
                digest(checkout.checkoutId())))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(failure -> assertThat(((BusinessFailure) failure).code())
                        .isEqualTo(BusinessFailure.Code.CHECKOUT_STALE));
    }

    @Test
    void theConfirmedProductTextIsKeptEvenAfterTheProductChanges() {
        CheckoutSnapshot checkout = checkoutWith("EST-1");
        jdbcTemplate.update("UPDATE item SET status = 'D' WHERE itemid = 'EST-1'");
        jdbcTemplate.update("UPDATE product SET name = 'Renamed' WHERE productid = 'FI-SW-01'");

        OrderTransactionService.OrderPlacement placement = service.placeOrder("alice", checkout.checkoutId(),
                new IdempotencyKey("key-1"), digest(checkout.checkoutId()));

        Long orderId = Long.valueOf(placement.snapshot().orderId());
        assertThat(jdbcTemplate.queryForObject("SELECT product_name FROM lineitem WHERE orderid = ?", String.class,
                orderId))
                .as("成交明细使用确认时的商品文本快照（ORD-19、D05）")
                .isEqualTo("Angelfish");
    }

    @Test
    void theOrderNumberIsUniqueAndMaySkip() {
        CheckoutSnapshot first = checkoutWith("EST-1");
        service.placeOrder("alice", first.checkoutId(), new IdempotencyKey("key-1"), digest(first.checkoutId()));

        jdbcTemplate.update("DELETE FROM cart_item");
        jdbcTemplate.update("DELETE FROM cart");
        CheckoutSnapshot second = checkoutWith("EST-2");
        OrderTransactionService.OrderPlacement placement = service.placeOrder("alice", second.checkoutId(),
                new IdempotencyKey("key-2"), digest(second.checkoutId()));

        assertThat(placement.snapshot().orderId()).isNotEqualTo(
                jdbcTemplate.queryForObject("SELECT MIN(orderid) FROM orders", String.class));
    }

    private CheckoutSnapshot checkoutWith(String itemId) {
        CartSnapshot cart = cartCommand.addItem(CartOwner.account("alice"), itemId, 0);
        return checkoutService.create(new CreateCheckoutRequest("alice", cart.version(), BILLING, SHIPPING,
                new FakePayment("Visa", "FAKE-4111-1111-1111-1111", "12/2026")));
    }

    private RequestDigest digest(String checkoutId) {
        return OrderRequestHasher.digest(OrderRequestMapper.OPERATION_PLACE_ORDER, checkoutId);
    }
}
