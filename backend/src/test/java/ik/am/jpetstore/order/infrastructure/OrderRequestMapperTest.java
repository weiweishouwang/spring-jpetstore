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
 * T279: OrderRequest参数化 SQL。
 *
 * <p>覆盖 (用户, 操作, 键) 联合唯一、响应与摘要持久化、不同用户隔离，以及过期与重放内容不含
 * Cookie/CSRF（plan §5.7、NFR-03、NFR-04）。</p>
 */
class OrderRequestMapperTest {

    private static SqlSessionFactory sqlSessionFactory;

    private SqlSession session;

    private OrderRequestMapper mapper;

    @BeforeAll
    static void createSessionFactory() {
        org.apache.ibatis.mapping.Environment environment = new org.apache.ibatis.mapping.Environment("test",
                new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),
                new org.springframework.jdbc.datasource.DriverManagerDataSource(MySqlTestDatabase.url(),
                        MySqlTestDatabase.username(), MySqlTestDatabase.password()));
        org.apache.ibatis.session.Configuration configuration =
                new org.apache.ibatis.session.Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        try (java.io.InputStream in = new ClassPathResource("mapper/order/OrderRequestMapper.xml")
                .getInputStream()) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(in, configuration,
                    "mapper/order/OrderRequestMapper.xml", configuration.getSqlFragments()).parse();
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
        mapper = session.getMapper(OrderRequestMapper.class);
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void theUserOperationAndKeyCombinationIsUnique() {
        mapper.insertOrderRequest(request("alice", "PLACE_ORDER", "key-1", "hash-1"));

        assertThatThrownBy(() -> mapper.insertOrderRequest(request("alice", "PLACE_ORDER", "key-1", "hash-2")))
                .as("同一用户、同一操作、同一键只能占位一次")
                .hasRootCauseInstanceOf(SQLException.class);

        assertThat(mapper.insertOrderRequest(request("alice", "PLACE_ORDER", "key-2", "hash-2")))
                .as("另一个键可以占位")
                .isEqualTo(1);
    }

    @Test
    void theResponseAndDigestArePersisted() {
        mapper.insertOrderRequest(request("alice", "PLACE_ORDER", "key-1", "hash-1"));

        assertThat(mapper.completeOrderRequest("alice", "PLACE_ORDER", "key-1", 1001L, 201, "/api/orders/1001",
                "{\"orderId\":\"1001\"}"))
                .isEqualTo(1);

        OrderRequestMapper.OrderRequestRow row =
                mapper.selectOrderRequest("alice", "PLACE_ORDER", "key-1");
        assertThat(row.requestHash()).isEqualTo("hash-1");
        assertThat(row.orderId()).isEqualTo(1001L);
        assertThat(row.responseStatus()).isEqualTo(201);
        assertThat(row.responseLocation()).isEqualTo("/api/orders/1001");
        assertThat(row.responseBody()).contains("1001");
    }

    @Test
    void anotherUserNeverSeesTheRecord() {
        mapper.insertOrderRequest(request("alice", "PLACE_ORDER", "key-1", "hash-1"));

        assertThat(mapper.selectOrderRequest("bob", "PLACE_ORDER", "key-1"))
                .as("重放读取必须包含用户身份")
                .isNull();
        assertThat(mapper.selectOrderRequest("alice", "PLACE_ORDER", "key-1")).isNotNull();
    }

    @Test
    void theExpiryIsStoredSoRetentionIsExplicit() {
        Instant expiresAt = Instant.parse("2026-11-04T10:00:00Z");
        mapper.insertOrderRequest(request("alice", "PLACE_ORDER", "key-1", "hash-1", expiresAt));

        OrderRequestMapper.OrderRequestRow row =
                mapper.selectOrderRequest("alice", "PLACE_ORDER", "key-1");
        assertThat(row.expiresAt()).isEqualTo(expiresAt);
        assertThat(row.createdAt()).isNotNull();
    }

    @Test
    void theStoredResponseCarriesNoCookieOrCsrfMaterial() {
        mapper.insertOrderRequest(request("alice", "PLACE_ORDER", "key-1", "hash-1"));
        mapper.completeOrderRequest("alice", "PLACE_ORDER", "key-1", 1001L, 201, "/api/orders/1001",
                "{\"orderId\":\"1001\"}");

        OrderRequestMapper.OrderRequestRow row =
                mapper.selectOrderRequest("alice", "PLACE_ORDER", "key-1");

        assertThat(row.responseBody())
                .as("可重放响应不含 Set-Cookie、CSRF、密码或完整卡号")
                .doesNotContain("Set-Cookie")
                .doesNotContain("JSESSIONID")
                .doesNotContain("csrf")
                .doesNotContain("FAKE-");
        assertThat(row.responseLocation()).doesNotContain("token");
    }

    private OrderRequestMapper.OrderRequestRow request(String userId, String operation, String key, String hash) {
        return request(userId, operation, key, hash, Instant.parse("2026-11-03T10:00:00Z").plusSeconds(86400));
    }

    private OrderRequestMapper.OrderRequestRow request(String userId, String operation, String key, String hash,
            Instant expiresAt) {
        return new OrderRequestMapper.OrderRequestRow(null, userId, operation, key, hash, null, null, null, null,
                Instant.parse("2026-11-03T10:00:00Z"), expiresAt);
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
