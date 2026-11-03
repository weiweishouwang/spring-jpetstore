package ik.am.jpetstore.order.application;

import ik.am.jpetstore.order.domain.OrderModels.OrderSnapshot;
import ik.am.jpetstore.order.domain.OrderModels.OrderSummary;
import ik.am.jpetstore.shared.domain.Money;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T297: 本人历史订单快照查询。
 *
 * <p>覆盖按身份过滤列表与详情、他人订单与不存在订单统一不可见、最新优先且首期不分页、
 * 成交金额不随当前商品价格变化、无 N+1（一次列表查询即可）、只含初始状态（ORD-14～20、
 * AT-13、AT-R04、Q11、D05）。</p>
 */
@SpringBootTest
class OrderQueryServiceTest {

    @Autowired
    private OrderQueryService service;

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
        jdbcTemplate.update("DELETE FROM orderstatus");
        jdbcTemplate.update("DELETE FROM lineitem");
        jdbcTemplate.update("DELETE FROM orders");
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
        jdbcTemplate.update("INSERT INTO item (itemid, productid, list_price, unit_cost, supplier, status)"
                + " VALUES ('EST-1', 'FI-SW-01', 16.50, 10.00, 1, 'P')");
        jdbcTemplate.update("INSERT INTO inventory (itemid, qty, version) VALUES ('EST-1', 100, 0)");
        insertOrder("alice", "2026-10-01 10:00:00", "10.00", 1);
        insertOrder("alice", "2026-11-02 10:00:00", "20.00", 2);
        insertOrder("bob", "2026-11-03 10:00:00", "30.00", 3);
    }

    @Test
    void theListOnlyContainsTheOwnOrdersNewestFirst() {
        List<OrderSummary> summaries = service.listOwnOrders("alice");

        assertThat(summaries).hasSize(2);
        assertThat(summaries.get(0).createdAt()).isAfter(summaries.get(1).createdAt());
        assertThat(summaries.get(0).totalAmount().toPlainString()).isEqualTo("20.00");
        assertThat(summaries.get(1).totalAmount().toPlainString()).isEqualTo("10.00");
    }

    @Test
    void anotherAccountsOrderIsInvisible() {
        assertThat(service.findOwnOrder("alice", "3")).isEmpty();
        assertThat(service.listOwnOrders("carol")).isEmpty();
    }

    @Test
    void anUnknownOrderIsNotFound() {
        assertThat(service.findOwnOrder("alice", "999")).isEmpty();
        assertThat(service.findOwnOrder("alice", "not-a-number")).isEmpty();
    }

    @Test
    void theTradedAmountNeverFollowsTheCurrentPrice() {
        jdbcTemplate.update("UPDATE item SET list_price = 99.00 WHERE itemid = 'EST-1'");

        Optional<OrderSnapshot> snapshot = service.findOwnOrder("alice", "1");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().totalAmount().toPlainString()).isEqualTo("10.00");
        assertThat(snapshot.get().lines().get(0).unitPrice().toPlainString()).isEqualTo("10.00");
        assertThat(snapshot.get().lines().get(0).lineAmount().toPlainString()).isEqualTo("20.00");
    }

    @Test
    void theDetailKeepsTheAddressesAndThePaymentSummary() {
        Optional<OrderSnapshot> snapshot = service.findOwnOrder("alice", "1");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().billingAddress().firstName()).isEqualTo("Alice");
        assertThat(snapshot.get().shippingAddress().firstName()).isEqualTo("Bob");
        assertThat(snapshot.get().payment().cardType()).isEqualTo("Visa");
        assertThat(snapshot.get().payment().lastFour()).isEqualTo("1111");
        assertThat(snapshot.get().statusCode()).isEqualTo("P");
        assertThat(snapshot.get().courier()).isNull();
        assertThat(snapshot.get().region()).isNull();
    }

    @Test
    void theListDoesNotLoadTheLines() {
        service.listOwnOrders("alice");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM lineitem WHERE orderid = 1", Integer.class))
                .as("列表不读取明细，避免 N+1")
                .isEqualTo(1);
    }

    private void insertOrder(String userId, String orderDate, String totalAmount, int orderId) {
        jdbcTemplate.update("INSERT INTO orders (orderid, userid, orderdate, bill_first_name, bill_last_name,"
                + " bill_addr1, bill_addr2, bill_city, bill_state, bill_zip, bill_country, ship_first_name,"
                + " ship_last_name, ship_addr1, ship_addr2, ship_city, ship_state, ship_zip, ship_country,"
                + " total_amount, status, courier, region, card_type, card_last_four) VALUES (?, ?, ?, 'Alice',"
                + " 'Ishikawa', '1-2-3 Sakuragaoka', NULL, 'Shibuya', 'Tokyo', '150-0001', 'JP', 'Bob', 'Yamada',"
                + " '4-5-6 Omotesando', 'Apt 301', 'Minato', 'Tokyo', '107-0052', 'JP', ?, 'P', NULL, NULL,"
                + " 'Visa', '1111')",
                orderId, userId, java.sql.Timestamp.valueOf(orderDate), new java.math.BigDecimal(totalAmount));
        jdbcTemplate.update("INSERT INTO lineitem (orderid, linenum, itemid, quantity, unit_price, product_name,"
                + " item_attribute) VALUES (?, 1, 'EST-1', 2, ?, 'Angelfish', 'Large / Adult male')",
                orderId, new java.math.BigDecimal(totalAmount));
        jdbcTemplate.update("INSERT INTO orderstatus (orderid, linenum, status, timestamp) VALUES (?, 1, 'P', ?)",
                orderId, java.sql.Timestamp.valueOf(orderDate));
    }
}
