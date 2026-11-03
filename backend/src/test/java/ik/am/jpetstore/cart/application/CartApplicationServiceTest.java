package ik.am.jpetstore.cart.application;

import ik.am.jpetstore.cart.api.CartCommand;
import ik.am.jpetstore.cart.domain.CartModels.CartOwner;
import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;
import ik.am.jpetstore.cart.domain.CartModels.PendingMerge;
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

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T283: 会话购物车命令与查询服务。
 *
 * <p>覆盖同 SKU 加一、规格分行、改零/负值后删除再重加、批量非法整体拒绝、版本冲突、
 * 未加购不扣库存、游客会话隔离、账号跨设备一致且隔离他人，以及登录合并（Q08、CRT-01～11、
 * AT-07/08/18、D04a）。</p>
 */
@SpringBootTest
class CartApplicationServiceTest {

    @Autowired
    private CartApplicationService service;

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
                + " ('EST-2', 'FI-SW-01', 10.50, 6.00, 1, 'P', 'Small'),"
                + " ('EST-9', 'FI-SW-01', 5.50, 2.00, 1, 'D', 'Delisted')");
        jdbcTemplate.update("INSERT INTO inventory (itemid, qty, version) VALUES ('EST-1', 10000, 0),"
                + " ('EST-2', 0, 0), ('EST-9', 10000, 0)");
    }

    @Test
    void addingTheSameSkuTwiceIncreasesTheQuantityByOne() {
        CartOwner owner = CartOwner.anonymous("hash-a");

        CartSnapshot first = service.addItem(owner, "EST-1", 0);
        CartSnapshot second = service.addItem(owner, "EST-1", first.version());

        assertThat(second.lines()).hasSize(1);
        assertThat(second.lines().get(0).quantity()).isEqualTo(2);
        assertThat(second.totalQuantity()).isEqualTo(2);
        assertThat(second.version()).isEqualTo(first.version() + 1);
    }

    @Test
    void differentSkusOfTheSameProductAreSeparateLines() {
        CartOwner owner = CartOwner.anonymous("hash-b");

        CartSnapshot snapshot = service.addItem(owner, "EST-1", 0);
        snapshot = service.addItem(owner, "EST-2", snapshot.version());

        assertThat(snapshot.lines()).extracting(line -> line.itemId()).containsExactly("EST-1", "EST-2");
        assertThat(snapshot.lineCount()).isEqualTo(2);
    }

    @Test
    void settingZeroOrNegativeRemovesTheLineAndItCanBeAddedAgain() {
        CartOwner owner = CartOwner.anonymous("hash-c");
        CartSnapshot snapshot = service.addItem(owner, "EST-1", 0);
        snapshot = service.addItem(owner, "EST-1", snapshot.version());

        CartSnapshot removed = service.setQuantity(owner, "EST-1", 0, snapshot.version());
        assertThat(removed.lines()).isEmpty();
        assertThat(removed.subtotal().toPlainString()).isEqualTo("0.00");

        CartSnapshot readded = service.addItem(owner, "EST-1", removed.version());
        assertThat(readded.lines()).hasSize(1);
        assertThat(readded.lines().get(0).quantity()).isEqualTo(1);
    }

    @Test
    void aBatchWithAnInvalidEntryRejectsTheWholeBatch() {
        CartOwner owner = CartOwner.anonymous("hash-d");
        CartSnapshot snapshot = service.addItem(owner, "EST-1", 0);

        assertThatThrownBy(() -> service.applyQuantities(owner,
                List.of(new CartCommand.QuantityChange("EST-2", 2),
                        new CartCommand.QuantityChange("EST-1", 10000)), snapshot.version()))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(failure -> assertThat(((BusinessFailure) failure).code())
                        .isEqualTo(BusinessFailure.Code.VALIDATION_FAILED));

        CartSnapshot unchanged = service.currentCart(owner).orElseThrow();
        assertThat(unchanged.lines())
                .as("非法批量不做部分写入")
                .extracting(line -> line.itemId())
                .containsExactly("EST-1");
        assertThat(unchanged.lines().get(0).quantity()).isEqualTo(1);
    }

    @Test
    void aStaleExpectedVersionIsRejected() {
        CartOwner owner = CartOwner.anonymous("hash-e");
        service.addItem(owner, "EST-1", 0);

        assertThatThrownBy(() -> service.addItem(owner, "EST-1", 42))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(failure -> assertThat(((BusinessFailure) failure).code())
                        .isEqualTo(BusinessFailure.Code.CART_VERSION_CONFLICT));
    }

    @Test
    void addingToTheCartDoesNotTouchStock() {
        CartOwner owner = CartOwner.anonymous("hash-f");

        service.addItem(owner, "EST-1", 0);

        assertThat(jdbcTemplate.queryForObject("SELECT qty FROM inventory WHERE itemid = 'EST-1'", Integer.class))
                .as("加购不扣库存，库存只在成交时校验（CRT-05）")
                .isEqualTo(10000);
    }

    @Test
    void guestCartsAreIsolatedPerSession() {
        CartSnapshot first = service.addItem(CartOwner.anonymous("hash-g1"), "EST-1", 0);
        CartSnapshot second = service.addItem(CartOwner.anonymous("hash-g2"), "EST-2", 0);

        assertThat(first.lines()).extracting(line -> line.itemId()).containsExactly("EST-1");
        assertThat(second.lines()).extracting(line -> line.itemId()).containsExactly("EST-2");
    }

    @Test
    void anAccountCartIsTheSameOnEveryDeviceAndIsolatedFromOtherAccounts() {
        CartOwner alice = CartOwner.account("alice");
        CartSnapshot onFirstDevice = service.addItem(alice, "EST-1", 0);
        CartSnapshot onSecondDevice = service.addItem(CartOwner.account("alice"), "EST-2", onFirstDevice.version());

        assertThat(onSecondDevice.lines()).extracting(line -> line.itemId()).containsExactly("EST-1", "EST-2");

        CartSnapshot bob = service.addItem(CartOwner.account("bob"), "EST-2", 0);
        assertThat(bob.lines()).extracting(line -> line.itemId()).containsExactly("EST-2");
    }

    @Test
    void aDelistedItemCannotBeAddedButAnOutOfStockItemCan() {
        CartOwner owner = CartOwner.anonymous("hash-h");

        assertThatThrownBy(() -> service.addItem(owner, "EST-9", 0))
                .isInstanceOf(BusinessFailure.class)
                .satisfies(failure -> assertThat(((BusinessFailure) failure).code())
                        .isEqualTo(BusinessFailure.Code.ITEM_UNAVAILABLE));

        CartSnapshot snapshot = service.addItem(owner, "EST-2", 0);
        assertThat(snapshot.lines()).hasSize(1);
        assertThat(snapshot.lines().get(0).inStock())
                .as("缺货可以加购，有货标识只是参考（Q02、CRT-04）")
                .isFalse();
    }

    @Test
    void theSubtotalComesFromTheServerPrice() {
        CartSnapshot snapshot = service.addItem(CartOwner.anonymous("hash-i"), "EST-1", 0);

        assertThat(snapshot.subtotal().toPlainString()).isEqualTo("16.50");
    }

    @Test
    void readingTheCartDoesNotCreateBusinessRows() {
        Optional<CartSnapshot> snapshot = service.currentCart(CartOwner.anonymous("hash-j"));

        assertThat(snapshot).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM cart", Integer.class)).isZero();
    }

    @Test
    void anExpiredGuestCartIsTreatedAsEmpty() {
        jdbcTemplate.update("INSERT INTO cart (session_binding_hash, owner_userid, version, state, expires_at,"
                + " created_at) VALUES ('hash-expired', NULL, 3, 'ACTIVE', ?, ?)",
                java.sql.Timestamp.from(Instant.now().minusSeconds(60)),
                java.sql.Timestamp.from(Instant.now().minusSeconds(3600)));

        assertThat(service.currentCart(CartOwner.anonymous("hash-expired")))
                .as("游客购物车 30 分钟无活动失效（Q08）")
                .isEmpty();
    }

    @Test
    void loggingInMergesTheGuestCartIntoTheAccountCart() {
        service.addItem(CartOwner.anonymous("hash-k"), "EST-1", 0);
        service.addItem(CartOwner.anonymous("hash-k"), "EST-1", 1);

        CartSnapshot merged = service.currentCart(CartOwner.accountInSession("alice", "hash-k")).orElseThrow();

        assertThat(merged.lines()).extracting(line -> line.itemId()).containsExactly("EST-1");
        assertThat(merged.lines().get(0).quantity())
                .as("同 SKU 数量相加（Q08）")
                .isEqualTo(2);
    }

    @Test
    void aMergeBeyondTheLimitFreezesBothSidesInsteadOfLosingData() {
        jdbcTemplate.update("INSERT INTO cart (session_binding_hash, owner_userid, version, state, expires_at,"
                + " created_at) VALUES ('hash-l', NULL, 0, 'ACTIVE', ?, ?)",
                java.sql.Timestamp.from(Instant.now().plusSeconds(1800)),
                java.sql.Timestamp.from(Instant.now()));
        Long guestCartId = jdbcTemplate.queryForObject(
                "SELECT id FROM cart WHERE session_binding_hash = 'hash-l'", Long.class);
        jdbcTemplate.update("INSERT INTO cart_item (cart_id, itemid, quantity) VALUES (?, 'EST-1', 9999)",
                guestCartId);
        service.addItem(CartOwner.account("alice"), "EST-1", 0);

        CartSnapshot merged = service.currentCart(CartOwner.accountInSession("alice", "hash-l")).orElseThrow();

        assertThat(merged.pendingMerges())
                .as("合并超限时冻结保存，双方内容都不丢弃（Q08）")
                .hasSize(1);
        PendingMerge pending = merged.pendingMerges().get(0);
        assertThat(pending.items()).extracting(line -> line.itemId()).containsExactly("EST-1");
        assertThat(pending.items().get(0).quantity()).isEqualTo(9999);
        assertThat(merged.lines().get(0).quantity())
                .as("账号一侧的内容保持不变")
                .isEqualTo(1);
    }
}
