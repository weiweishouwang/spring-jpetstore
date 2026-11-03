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
 * T222: MySQL 迁移 V3__create_cart_and_checkout.sql。
 *
 * <p>验证会话绑定、版本号、明细联合唯一、账户活动购物车唯一性与确认快照的有效期与消费状态
 * （Q08、Q03、Q04）。</p>
 */
class Schema3MigrationTest {

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
    void createsCartAndCheckoutTables() throws Exception {
        migrate();

        assertThat(tableNames()).contains("cart", "cart_item", "checkout", "checkout_item");
        assertThat(column("cart", "version")).isNotNull();
        assertThat(column("cart", "state")).isNotNull();
        assertThat(column("cart", "expires_at")).isNotNull();
        assertThat(column("cart", "session_binding_hash")).isNotNull();
        assertThat(column("cart", "owner_userid")).isNotNull();
    }

    @Test
    void cartItemsAreUniqueByCartAndItem() throws Exception {
        migrate();

        assertThat(primaryKey("cart_item")).containsExactly("cart_id", "itemid");
        assertThat(foreignKeyTargets("cart_item")).containsExactly("cart");
    }

    @Test
    void onlyOneActiveCartPerAccount() throws Exception {
        migrate();

        assertThat(indexColumns("cart", "uk_cart_active_owner")).containsExactly("active_owner");
        assertThat(generatedColumns("cart")).contains("active_owner");
    }

    @Test
    void checkoutKeepsExpiryVersionAndConsumptionState() throws Exception {
        migrate();

        assertThat(column("checkout", "expires_at")).isNotNull();
        assertThat(column("checkout", "cart_version")).isNotNull();
        assertThat(column("checkout", "state")).isNotNull();
        assertThat(column("checkout", "total_amount").get("column_type")).isEqualTo("decimal(10,2)");
        assertThat(primaryKey("checkout_item")).containsExactly("checkout_id", "linenum");
        assertThat(foreignKeyTargets("checkout_item")).containsExactly("checkout");
    }

    private void migrate() {
        Flyway.configure()
                .dataSource(MySqlTestDatabase.url(), MySqlTestDatabase.username(), MySqlTestDatabase.password())
                .locations("classpath:db/migration")
                .target("3")
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

    private Map<String, Object> column(String table, String column) throws Exception {
        try (Connection connection = connect()) {
            List<Map<String, Object>> rows = query(connection,
                    "SELECT COLUMN_TYPE, IS_NULLABLE, EXTRA, COLLATION_NAME FROM information_schema.columns "
                            + "WHERE table_schema = DATABASE() AND table_name = '" + table + "' AND column_name = '"
                            + column + "'");
            return rows.isEmpty() ? null : rows.get(0);
        }
    }

    private List<String> primaryKey(String table) throws Exception {
        try (Connection connection = connect()) {
            List<String> columns = new ArrayList<>();
            for (Map<String, Object> row : query(connection,
                    "SELECT column_name FROM information_schema.key_column_usage WHERE table_schema = DATABASE() "
                            + "AND table_name = '" + table + "' AND constraint_name = 'PRIMARY' "
                            + "ORDER BY ordinal_position")) {
                columns.add(row.get("column_name").toString());
            }
            return columns;
        }
    }

    private List<String> foreignKeyTargets(String table) throws Exception {
        try (Connection connection = connect()) {
            List<String> targets = new ArrayList<>();
            for (Map<String, Object> row : query(connection,
                    "SELECT DISTINCT referenced_table_name FROM information_schema.key_column_usage "
                            + "WHERE table_schema = DATABASE() AND table_name = '" + table
                            + "' AND referenced_table_name IS NOT NULL")) {
                targets.add(row.get("referenced_table_name").toString());
            }
            return targets;
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

    private List<String> generatedColumns(String table) throws Exception {
        try (Connection connection = connect()) {
            List<String> columns = new ArrayList<>();
            for (Map<String, Object> row : query(connection,
                    "SELECT column_name FROM information_schema.columns WHERE table_schema = DATABASE() "
                            + "AND table_name = '" + table + "' AND extra LIKE '%GENERATED%'")) {
                columns.add(row.get("column_name").toString());
            }
            return columns;
        }
    }
}
