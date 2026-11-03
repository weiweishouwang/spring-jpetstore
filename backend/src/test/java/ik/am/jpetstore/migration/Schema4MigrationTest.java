package ik.am.jpetstore.migration;

import ik.am.jpetstore.support.MySqlTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T224: MySQL 迁移 V4__create_order_idempotency.sql。
 *
 * <p>验证 (user, operation, key) 唯一约束、请求摘要与可重放结果的存储、订单与确认快照的一对一
 * 关联以及过期清理索引（NFR-03、plan §5.7、§7.2）。</p>
 */
class Schema4MigrationTest {

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (Map<String, Object> row : query(connection,
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()")) {
                statement.execute("DROP TABLE IF EXISTS `" + row.get("table_name") + "`");
            }
            statement.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    @Test
    void createsOrderRequestTable() throws Exception {
        migrate();

        assertThat(tableNames()).contains("order_request");
        assertThat(column("order_request", "user_id")).isNotNull();
        assertThat(column("order_request", "operation")).isNotNull();
        assertThat(column("order_request", "idempotency_key")).isNotNull();
        assertThat(column("order_request", "request_hash")).isNotNull();
        assertThat(column("order_request", "order_id")).isNotNull();
        assertThat(column("order_request", "expires_at")).isNotNull();
    }

    @Test
    void storesReplayableResultWithoutSensitiveFields() throws Exception {
        migrate();

        assertThat(column("order_request", "response_status")).isNotNull();
        assertThat(column("order_request", "response_location")).isNotNull();
        assertThat(column("order_request", "response_body")).isNotNull();
        assertThat(columnNames("order_request"))
                .as("no cookie, csrf or password column is stored")
                .doesNotContain("set_cookie", "csrf", "password");
    }

    @Test
    void requestKeyIsUniquePerUserAndOperation() throws Exception {
        migrate();

        assertThat(indexColumns("order_request", "uk_order_request"))
                .containsExactly("user_id", "operation", "idempotency_key");
        assertThat(indexColumns("order_request", "idx_order_request_expiry")).containsExactly("expires_at");
    }

    @Test
    void oneCheckoutCanOnlyProduceOneOrder() throws Exception {
        migrate();

        assertThat(indexColumns("orders", "uk_orders_checkout")).containsExactly("checkout_id");
    }

    private void migrate() {
        Flyway.configure()
                .dataSource(MySqlTestDatabase.url(), MySqlTestDatabase.username(), MySqlTestDatabase.password())
                .locations("classpath:db/migration")
                .target("4")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection(MySqlTestDatabase.url(), MySqlTestDatabase.username(),
                MySqlTestDatabase.password());
    }

    private List<Map<String, Object>> query(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            List<Map<String, Object>> rows = new ArrayList<>();
            int columns = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= columns; i++) {
                    row.put(rs.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT), rs.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        }
    }

    private List<String> tableNames() throws Exception {
        try (Connection connection = connect()) {
            List<String> names = new ArrayList<>();
            for (Map<String, Object> row : query(connection,
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()")) {
                names.add(row.get("table_name").toString());
            }
            return names;
        }
    }

    private List<String> columnNames(String table) throws Exception {
        try (Connection connection = connect()) {
            List<String> names = new ArrayList<>();
            for (Map<String, Object> row : query(connection,
                    "SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() "
                            + "AND table_name = '" + table + "'")) {
                names.add(row.get("column_name").toString());
            }
            return names;
        }
    }

    private Map<String, Object> column(String table, String column) throws Exception {
        try (Connection connection = connect()) {
            List<Map<String, Object>> rows = query(connection,
                    "SELECT COLUMN_TYPE, IS_NULLABLE, EXTRA FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = '" + table + "' AND column_name = '"
                            + column + "'");
            return rows.isEmpty() ? null : rows.get(0);
        }
    }

    private List<String> indexColumns(String table, String index) throws Exception {
        try (Connection connection = connect()) {
            List<String> columns = new ArrayList<>();
            for (Map<String, Object> row : query(connection,
                    "SELECT column_name FROM information_schema.statistics WHERE table_schema = DATABASE() "
                            + "AND table_name = '" + table + "' AND index_name = '" + index
                            + "' ORDER BY seq_in_index")) {
                columns.add(row.get("column_name").toString());
            }
            return columns;
        }
    }
}
