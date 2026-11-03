package ik.am.jpetstore.order.infrastructure;

import ik.am.jpetstore.support.MySqlTestDatabase;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T276: Order参数化 SQL。
 *
 * <p>覆盖自增订单号唯一、成交快照、只读本人订单、最新优先且首期不分页（ORD-09、ORD-17～19、
 * Q11、Q15、D05）。</p>
 */
class OrderMapperTest {

    private static SqlSessionFactory sqlSessionFactory;

    private SqlSession session;

    private OrderMapper mapper;

    @BeforeAll
    static void createSessionFactory() {
        org.apache.ibatis.mapping.Environment environment = new org.apache.ibatis.mapping.Environment("test",
                new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),
                new org.springframework.jdbc.datasource.DriverManagerDataSource(MySqlTestDatabase.url(),
                        MySqlTestDatabase.username(), MySqlTestDatabase.password()));
        org.apache.ibatis.session.Configuration configuration =
                new org.apache.ibatis.session.Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        try (java.io.InputStream in = new ClassPathResource("mapper/order/OrderMapper.xml").getInputStream()) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(in, configuration,
                    "mapper/order/OrderMapper.xml", configuration.getSqlFragments()).parse();
        } catch (Exception e) {
            throw new IllegalStateException("无法加载 Mapper XML", e);
        }
        sqlSessionFactory = new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String table : tableNames(connection)) {
                statement.execute("DROP TABLE IF EXISTS `" + table + "`");
            }
            statement.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
        Flyway.configure()
                .dataSource(MySqlTestDatabase.url(), MySqlTestDatabase.username(), MySqlTestDatabase.password())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load()
                .migrate();
        try (Connection connection = openConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("fixtures/shop.sql"));
        }
        session = sqlSessionFactory.openSession(true);
        mapper = session.getMapper(OrderMapper.class);
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void generatedOrderNumbersAreUnique() {
        Long first = insertOrder("alice", Instant.parse("2026-11-01T10:00:00Z"), "16.50", null);
        Long second = insertOrder("alice", Instant.parse("2026-11-02T10:00:00Z"), "16.50", null);

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void theTradedPriceIsStoredAsASnapshot() {
        Long orderId = insertOrder("alice", Instant.parse("2026-11-01T10:00:00Z"), "33.00", "ck-1");

        mapper.insertOrderLine(new OrderMapper.OrderLineRow(orderId, 1, "EST-1", 2, "16.50", "Angelfish",
                "Large / Adult male"));
        mapper.insertOrderStatus(new OrderMapper.OrderStatusRow(orderId, 1, OrderMapper.STATUS_SUBMITTED,
                Instant.parse("2026-11-01T10:00:00Z")));

        List<OrderMapper.OrderLineRow> lines = mapper.selectOrderLines(orderId);
        assertThat(lines).hasSize(1);
        assertThat(lines.get(0).unitPrice()).isEqualTo("16.50");
        assertThat(lines.get(0).productName()).isEqualTo("Angelfish");
        assertThat(lines.get(0).itemAttribute()).isEqualTo("Large / Adult male");

        OrderMapper.OrderRow order = mapper.selectOrder("alice", orderId);
        assertThat(order.totalAmount()).isEqualTo("33.00");
        assertThat(order.status()).isEqualTo("P");
        assertThat(order.checkoutId()).isEqualTo("ck-1");
        assertThat(order.cardLastFour()).isEqualTo("1111");
        assertThat(order.cardType()).isEqualTo("Visa");
    }

    @Test
    void anotherAccountNeverSeesTheOrder() {
        Long orderId = insertOrder("alice", Instant.parse("2026-11-01T10:00:00Z"), "16.50", null);

        assertThat(mapper.selectOrder("bob", orderId))
                .as("他人订单与不存在订单在 SQL 层就不可见")
                .isNull();
        assertThat(mapper.selectOrder("alice", orderId)).isNotNull();
        assertThat(mapper.selectOrderSummaries("alice"))
                .as("列表只包含本人订单")
                .extracting(OrderMapper.OrderSummaryRow::orderId)
                .doesNotContain(1000L);
        assertThat(mapper.selectOrderSummaries("bob"))
                .extracting(OrderMapper.OrderSummaryRow::orderId)
                .doesNotContain(orderId);
    }

    @Test
    void theListIsNewestFirstAndNotPaginated() {
        insertOrder("alice", Instant.parse("2026-10-01T10:00:00Z"), "16.50", null);
        insertOrder("alice", Instant.parse("2026-11-03T10:00:00Z"), "10.50", null);
        insertOrder("alice", Instant.parse("2026-11-02T10:00:00Z"), "5.50", null);

        List<OrderMapper.OrderSummaryRow> summaries = mapper.selectOrderSummaries("alice");

        assertThat(summaries).hasSize(3);
        assertThat(summaries.get(0).orderDate()).isEqualTo(Instant.parse("2026-11-03T10:00:00Z"));
        assertThat(summaries.get(1).orderDate()).isEqualTo(Instant.parse("2026-11-02T10:00:00Z"));
        assertThat(summaries.get(2).orderDate()).isEqualTo(Instant.parse("2026-10-01T10:00:00Z"));
        assertThat(summaries.get(0).totalAmount()).isEqualTo("10.50");
    }

    @Test
    void aCheckoutCanOnlyProduceOneOrder() {
        insertOrder("alice", Instant.parse("2026-11-01T10:00:00Z"), "16.50", "ck-9");

        assertThatThrownBy(() -> insertOrder("alice", Instant.parse("2026-11-02T10:00:00Z"), "16.50", "ck-9"))
                .as("同一确认快照只能关联一张订单")
                .hasRootCauseInstanceOf(SQLException.class);
    }

    private Long insertOrder(String userId, Instant orderDate, String totalAmount, String checkoutId) {
        OrderMapper.NewOrder order = new OrderMapper.NewOrder();
        order.setUserId(userId);
        order.setOrderDate(orderDate);
        order.setBillFirstName("Alice");
        order.setBillLastName("Ishikawa");
        order.setBillAddr1("1-2-3 Sakuragaoka");
        order.setBillAddr2(null);
        order.setBillCity("Shibuya");
        order.setBillState("Tokyo");
        order.setBillZip("150-0001");
        order.setBillCountry("JP");
        order.setShipFirstName("Alice");
        order.setShipLastName("Ishikawa");
        order.setShipAddr1("4-5-6 Omotesando");
        order.setShipAddr2("Apt 301");
        order.setShipCity("Minato");
        order.setShipState("Tokyo");
        order.setShipZip("107-0052");
        order.setShipCountry("JP");
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderMapper.STATUS_SUBMITTED);
        order.setCourier(null);
        order.setRegion(null);
        order.setCardType("Visa");
        order.setCardLastFour("1111");
        order.setCheckoutId(checkoutId);
        mapper.insertOrder(order);
        return order.getOrderId();
    }

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection(MySqlTestDatabase.url(), MySqlTestDatabase.username(),
                MySqlTestDatabase.password());
    }

    private List<String> tableNames(Connection connection) throws Exception {
        List<String> names = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }
}
