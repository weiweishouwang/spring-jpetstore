package ik.am.jpetstore.order.application;

import ik.am.jpetstore.cart.api.CartCommand;
import ik.am.jpetstore.cart.domain.CartModels.CartOwner;
import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;
import ik.am.jpetstore.order.api.CheckoutCommand.CreateCheckoutRequest;
import ik.am.jpetstore.order.api.CheckoutCommand.FakePayment;
import ik.am.jpetstore.order.domain.CheckoutModels.CheckoutSnapshot;
import ik.am.jpetstore.order.domain.OrderModels.Address;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T287: 服务端确认快照应用服务。
 *
 * <p>覆盖锁购物车与版本校验、非空校验、两组地址独立、价格来自服务端、不预占库存、有效期与归属
 * （Q03、Q04、ORD-02～07/13、D11、AT-10、AT-R05/06）。快照不能由客户端伪造：请求里没有价格、
 * 归属和明细字段。</p>
 */
@SpringBootTest
class CheckoutApplicationServiceTest {

    private static final Address BILLING = new Address("Alice", "Ishikawa", "1-2-3 Sakuragaoka", null, "Shibuya",
            "Tokyo", "150-0001", "JP");

    private static final Address SHIPPING = new Address("Bob", "Yamada", "4-5-6 Omotesando", "Apt 301", "Minato",
            "Tokyo", "107-0052", "JP");

    @Autowired
    private CheckoutApplicationService service;

    @Autowired
    private CartCommand cartCommand;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.identity.web.IdentityAssembler identityAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.cart.web.CartAssembler cartAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.order.web.OrderAssembler orderAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.catalog.web.CatalogAssembler catalogAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.account.web.AccountAssembler accountAssembler;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlTestDatabase::url);
        registry.add("spring.datasource.username", MySqlTestDatabase::username);
        registry.add("spring.datasource.password", MySqlTestDatabase::password);
    }

    @BeforeEach
    void resetData() {
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
        jdbcTemplate.update("INSERT INTO inventory (itemid, qty, version) VALUES ('EST-1', 10000, 0)");
    }

    @Test
    void theSnapshotUsesTheServerPriceAndTheCartVersion() {
        CartSnapshot cart = cartCommand.addItem(CartOwner.account("alice"), "EST-1", 0);

        CheckoutSnapshot snapshot = service.create(request("alice", cart.version()));

        assertThat(snapshot.totalAmount().toPlainString()).isEqualTo("16.50");
        assertThat(snapshot.cartVersion()).isEqualTo(cart.version());
        assertThat(snapshot.lines()).hasSize(1);
        assertThat(snapshot.lines().get(0).unitPrice().toPlainString()).isEqualTo("16.50");
        assertThat(snapshot.lines().get(0).productName()).isEqualTo("Angelfish");
    }

    @Test
    void bothAddressesAreStoredIndependently() {
        CartSnapshot cart = cartCommand.addItem(CartOwner.account("alice"), "EST-1", 0);

        CheckoutSnapshot snapshot = service.create(request("alice", cart.version()));

        assertThat(snapshot.billingAddress().firstName()).isEqualTo("Alice");
        assertThat(snapshot.billingAddress().address2()).isNull();
        assertThat(snapshot.shippingAddress().firstName()).isEqualTo("Bob");
        assertThat(snapshot.shippingAddress().address2()).isEqualTo("Apt 301");
    }

    @Test
    void anEmptyCartIsRejected() {
        assertThatThrownBy(() -> service.create(request("alice", 0)))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(failure -> assertThat(((BusinessFailure) failure).code())
                        .isEqualTo(BusinessFailure.Code.VALIDATION_FAILED));
    }

    @Test
    void aStaleCartVersionIsRejected() {
        cartCommand.addItem(CartOwner.account("alice"), "EST-1", 0);

        assertThatThrownBy(() -> service.create(request("alice", 99)))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(failure -> assertThat(((BusinessFailure) failure).code())
                        .isEqualTo(BusinessFailure.Code.CART_VERSION_CONFLICT));
    }

    @Test
    void theSnapshotIsValidForFifteenMinutes() {
        CartSnapshot cart = cartCommand.addItem(CartOwner.account("alice"), "EST-1", 0);

        CheckoutSnapshot snapshot = service.create(request("alice", cart.version()));

        assertThat(snapshot.expiresAt().getEpochSecond() - snapshot.createdAt().getEpochSecond())
                .isEqualTo(15 * 60);
        assertThat(snapshot.isExpired(snapshot.expiresAt().plusSeconds(1))).isTrue();
        assertThat(snapshot.isExpired(snapshot.expiresAt().minusSeconds(1))).isFalse();
    }

    @Test
    void theSnapshotIsBoundToItsOwner() {
        CartSnapshot cart = cartCommand.addItem(CartOwner.account("alice"), "EST-1", 0);
        CheckoutSnapshot snapshot = service.create(request("alice", cart.version()));

        assertThat(service.findOwnCheckout("alice", snapshot.checkoutId())).isPresent();
        assertThat(service.findOwnCheckout("bob", snapshot.checkoutId()))
                .as("持有 checkoutId 不替代身份授权（ORD-01）")
                .isEmpty();
    }

    @Test
    void theRawCardNumberIsNeverPersisted() {
        CartSnapshot cart = cartCommand.addItem(CartOwner.account("alice"), "EST-1", 0);

        CheckoutSnapshot snapshot = service.create(request("alice", cart.version()));

        assertThat(snapshot.payment().cardType()).isEqualTo("Visa");
        assertThat(snapshot.payment().lastFour()).isEqualTo("1111");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM checkout WHERE card_last_four = 'FAKE-'",
                Integer.class)).isZero();
    }

    @Test
    void anInvalidCardInputIsRejected() {
        CartSnapshot cart = cartCommand.addItem(CartOwner.account("alice"), "EST-1", 0);
        CreateCheckoutRequest request = new CreateCheckoutRequest("alice", cart.version(), BILLING, SHIPPING,
                new FakePayment("Visa", "4111111111111111", "12/2026"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(failure -> assertThat(((BusinessFailure) failure).code())
                        .isEqualTo(BusinessFailure.Code.VALIDATION_FAILED));
    }

    @Test
    void aDelistedItemIsRejected() {
        CartSnapshot cart = cartCommand.addItem(CartOwner.account("alice"), "EST-1", 0);
        jdbcTemplate.update("UPDATE item SET status = 'D' WHERE itemid = 'EST-1'");

        assertThatThrownBy(() -> service.create(request("alice", cart.version())))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(failure -> assertThat(((BusinessFailure) failure).code())
                        .isEqualTo(BusinessFailure.Code.ITEM_UNAVAILABLE));
    }

    @Test
    void creatingASnapshotDoesNotReserveStock() {
        CartSnapshot cart = cartCommand.addItem(CartOwner.account("alice"), "EST-1", 0);

        service.create(request("alice", cart.version()));

        assertThat(jdbcTemplate.queryForObject("SELECT qty FROM inventory WHERE itemid = 'EST-1'", Integer.class))
                .as("快照不预占库存（Q03）")
                .isEqualTo(10000);
    }

    @Test
    void anUnknownSnapshotIsNotFound() {
        assertThat(service.findOwnCheckout("alice", "nope")).isEmpty();
        assertThat((Optional<CheckoutSnapshot>) service.findOwnCheckout("alice", "nope")).isEmpty();
    }

    private CreateCheckoutRequest request(String userId, long cartVersion) {
        return new CreateCheckoutRequest(userId, cartVersion, BILLING, SHIPPING,
                new FakePayment("Visa", "FAKE-4111-1111-1111-1111", "12/2026"));
    }
}
