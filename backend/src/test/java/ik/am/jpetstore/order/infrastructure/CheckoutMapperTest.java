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
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T270: Checkout参数化 SQL。
 *
 * <p>覆盖快照归属与版本、有效期、明细价格快照、锁定读以及单次消费（Q03、Q04、ORD-02～07）。
 * 原始模拟卡号不落库，只保存卡型与尾号（NFR-04）。</p>
 */
class CheckoutMapperTest {

    private static final String NAMESPACE = "ik.am.jpetstore.order.infrastructure.CheckoutMapper";

    private static SqlSessionFactory sqlSessionFactory;

    private SqlSession session;

    private CheckoutMapper mapper;

    @BeforeAll
    static void createSessionFactory() {
        org.apache.ibatis.mapping.Environment environment = new org.apache.ibatis.mapping.Environment("test",
                new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),
                new org.springframework.jdbc.datasource.DriverManagerDataSource(MySqlTestDatabase.url(),
                        MySqlTestDatabase.username(), MySqlTestDatabase.password()));
        org.apache.ibatis.session.Configuration configuration =
                new org.apache.ibatis.session.Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        try (java.io.InputStream in = new ClassPathResource("mapper/order/CheckoutMapper.xml").getInputStream()) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(in, configuration,
                    "mapper/order/CheckoutMapper.xml", configuration.getSqlFragments()).parse();
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
        mapper = session.getMapper(CheckoutMapper.class);
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void storesTheSnapshotWithBothAddressesAndThePaymentSummary() {
        mapper.insertCheckout(checkout("ck-1", "alice", 42L, 7L, "33.00"));

        CheckoutMapper.CheckoutRow row = mapper.selectCheckout("ck-1");

        assertThat(row).isNotNull();
        assertThat(row.userId()).isEqualTo("alice");
        assertThat(row.cartId()).isEqualTo(42L);
        assertThat(row.cartVersion()).isEqualTo(7L);
        assertThat(row.state()).isEqualTo(CheckoutMapper.STATE_OPEN);
        assertThat(row.totalAmount()).isEqualTo("33.00");
        assertThat(row.billFirstName()).isEqualTo("Alice");
        assertThat(row.billAddr2()).isNull();
        assertThat(row.shipCity()).isEqualTo("Minato");
        assertThat(row.cardType()).isEqualTo("Visa");
        assertThat(row.cardLastFour()).isEqualTo("1111");
    }

    @Test
    void theRawCardNumberIsNeverPersisted() throws Exception {
        mapper.insertCheckout(checkout("ck-2", "alice", 42L, 7L, "16.50"));

        List<String> columns = new ArrayList<>();
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT column_name FROM information_schema.columns WHERE table_name = 'checkout'")) {
            while (rs.next()) {
                columns.add(rs.getString(1));
            }
        }

        assertThat(columns)
                .as("there is no column that could hold the raw card number")
                .noneMatch(name -> name.contains("card_number") || name.contains("expiry"));
    }

    @Test
    void theLineItemsKeepTheConfirmedPrice() {
        mapper.insertCheckout(checkout("ck-3", "alice", 42L, 7L, "33.00"));
        mapper.insertCheckoutItem(new CheckoutMapper.CheckoutItemRow("ck-3", 1, "EST-1", 2, "16.50", "Angelfish",
                "Large / Adult male"));

        List<CheckoutMapper.CheckoutItemRow> items = mapper.selectCheckoutItems("ck-3");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).itemId()).isEqualTo("EST-1");
        assertThat(items.get(0).quantity()).isEqualTo(2);
        assertThat(items.get(0).unitPrice()).isEqualTo("16.50");
        assertThat(items.get(0).productName()).isEqualTo("Angelfish");
        assertThat(items.get(0).itemAttribute()).isEqualTo("Large / Adult male");
    }

    @Test
    void lockingTheSnapshotUsesAForUpdateRead() {
        mapper.insertCheckout(checkout("ck-4", "alice", 42L, 7L, "33.00"));

        mapper.selectCheckoutForUpdate("ck-4");

        String sql = sqlSessionFactory.getConfiguration()
                .getMappedStatement(NAMESPACE + ".selectCheckoutForUpdate")
                .getBoundSql(null).getSql().toUpperCase(java.util.Locale.ROOT);
        assertThat(sql).contains("FOR UPDATE");
    }

    @Test
    void aSnapshotCanOnlyBeConsumedOnce() {
        mapper.insertCheckout(checkout("ck-5", "alice", 42L, 7L, "33.00"));

        assertThat(mapper.updateCheckoutState("ck-5", CheckoutMapper.STATE_CONSUMED)).isEqualTo(1);
        assertThat(mapper.updateCheckoutState("ck-5", CheckoutMapper.STATE_CONSUMED))
                .as("the same snapshot must not be consumed twice")
                .isZero();
        assertThat(mapper.selectCheckout("ck-5").state()).isEqualTo(CheckoutMapper.STATE_CONSUMED);
    }

    @Test
    void anUnknownSnapshotIsNotFound() {
        assertThat(mapper.selectCheckout("nope")).isNull();
        assertThat(mapper.selectCheckoutItems("nope")).isEmpty();
    }

    private CheckoutMapper.CheckoutRow checkout(String checkoutId, String userId, long cartId, long cartVersion,
            String total) {
        Instant createdAt = Instant.now();
        return new CheckoutMapper.CheckoutRow(checkoutId, userId, cartId, cartVersion, CheckoutMapper.STATE_OPEN,
                createdAt, createdAt.plusSeconds(900), total,
                "Alice", "Ishikawa", "1-2-3 Sakuragaoka", null, "Shibuya", "Tokyo", "150-0001", "JP",
                "Alice", "Ishikawa", "4-5-6 Omotesando", "Apt 301", "Minato", "Tokyo", "107-0052", "JP",
                "Visa", "1111");
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
