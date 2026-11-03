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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T220: MySQL 迁移 V2__create_orders_and_inventory.sql。
 *
 * <p>验证订单头/状态/明细/库存结构、金额精度与成交快照字段，并确认明细不依赖实时商品
 * （商品删除后历史订单仍可展示，plan §7.2）。</p>
 */
class Schema2MigrationTest {

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
    void createsOrderInventoryAndSequenceTables() throws Exception {
        migrate();

        assertThat(tableNames()).contains("inventory", "orders", "orderstatus", "lineitem", "sequence");
        assertThat(primaryKey("lineitem")).containsExactly("orderid", "linenum");
        assertThat(primaryKey("orderstatus")).containsExactly("orderid", "linenum");
    }

    @Test
    void orderIdUsesAutoIncrementBigint() throws Exception {
        migrate();

        Map<String, Object> column = column("orders", "orderid");
        assertThat(column.get("column_type")).isEqualTo("bigint");
        assertThat(String.valueOf(column.get("extra"))).contains("auto_increment");
    }

    @Test
    void lineItemStoresTradeSnapshotAndMoneyPrecision() throws Exception {
        migrate();

        assertThat(column("lineitem", "unit_price").get("column_type")).isEqualTo("decimal(10,2)");
        assertThat(column("lineitem", "quantity").get("column_type")).isEqualTo("int");
        assertThat(column("lineitem", "product_name")).isNotNull();
        assertThat(column("lineitem", "item_attribute")).isNotNull();
        assertThat(column("orders", "total_amount").get("column_type")).isEqualTo("decimal(10,2)");
        assertThat(column("orders", "checkout_id")).isNotNull();
    }

    @Test
    void inventoryCarriesOptimisticVersion() throws Exception {
        migrate();

        assertThat(column("inventory", "qty").get("column_type")).isEqualTo("int");
        assertThat(column("inventory", "version").get("column_type")).isEqualTo("bigint");
    }

    @Test
    void orderDetailsBelongToTheirOrderButNotToLiveItems() throws Exception {
        migrate();

        assertThat(foreignKeyTargets("orderstatus")).containsExactly("orders");
        assertThat(foreignKeyTargets("lineitem")).containsExactly("orders");
        assertThat(foreignKeyTargets("lineitem"))
                .as("no strong foreign key to item: deleted products must not break historical orders")
                .doesNotContain("item");
    }

    @Test
    void ownOrdersAreQueryableByUserAndDate() throws Exception {
        migrate();

        assertThat(indexColumns("orders", "idx_orders_user_date")).containsExactly("userid", "orderdate", "orderid");
    }

    private void migrate() {
        Flyway.configure()
                .dataSource(MySqlTestDatabase.url(), MySqlTestDatabase.username(), MySqlTestDatabase.password())
                .locations("classpath:db/migration")
                .target("2")
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
                    row.put(rs.getMetaData().getColumnLabel(i).toLowerCase(java.util.Locale.ROOT), rs.getObject(i));
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
}
