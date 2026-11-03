package ik.am.jpetstore.migration;

import ik.am.jpetstore.support.MySqlTestDatabase;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T226: MySQL 迁移 V5__add_validated_constraints_and_indexes.sql。
 *
 * <p>验证在已清理数据上建立的约束与查询索引：库存不得为负（Q02）、成交数量必须为正（Q15）、
 * 售价必须大于零（Q06）、偏好分类必须合法；并且对未知 schema 不静默 baseline。</p>
 */
class Schema5MigrationTest {

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
    void addsValidatedCheckConstraints() throws Exception {
        migrate();

        assertThat(checkConstraints()).contains("chk_inventory_qty", "chk_lineitem_quantity",
                "chk_cart_item_quantity", "chk_checkout_item_quantity", "chk_item_list_price",
                "chk_profile_favourite_category");
    }

    @Test
    void addsQueryIndexes() throws Exception {
        migrate();

        assertThat(indexColumns("product", "idx_product_category")).containsExactly("category");
        assertThat(indexColumns("product", "idx_product_name")).containsExactly("name");
        assertThat(indexColumns("item", "idx_item_product")).containsExactly("productid");
        assertThat(indexColumns("lineitem", "idx_lineitem_item")).containsExactly("itemid");
    }

    @Test
    void rejectsNegativeStockAtDatabaseLevel() throws Exception {
        migrate();
        seedMinimalCatalog();

        assertThatThrownBy(() -> execute("INSERT INTO inventory (itemid, qty, version) VALUES ('EST-1', -1, 0)"))
                .as("Q02 forbids negative stock")
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> execute("INSERT INTO item (itemid, productid, list_price, unit_cost, supplier, status)"
                + " VALUES ('EST-2', 'FI-1', 0.00, 1.00, 1, 'P')"))
                .as("Q06 requires prices greater than zero")
                .isInstanceOf(SQLException.class);
    }

    @Test
    void refusesToBaselineAnUnknownSchemaSilently() throws Exception {
        execute("CREATE TABLE unknown_table (id INT NOT NULL)");

        assertThatThrownBy(() -> Flyway.configure()
                .dataSource(MySqlTestDatabase.url(), MySqlTestDatabase.username(), MySqlTestDatabase.password())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load()
                .migrate())
                .as("a non-empty schema without history must fail instead of being baselined")
                .isInstanceOf(FlywayException.class);
    }

    @Test
    void wholeMigrationChainAppliesCleanly() throws Exception {
        migrate();

        assertThat(tableNames()).contains("signon", "account", "profile", "bannerdata", "category", "product",
                "item", "supplier", "inventory", "orders", "orderstatus", "lineitem", "sequence", "cart",
                "cart_item", "checkout", "checkout_item", "order_request");
        assertThat(schemaHistoryVersions()).containsExactly("1", "2", "3", "4", "5");
    }

    private void migrate() {
        Flyway.configure()
                .dataSource(MySqlTestDatabase.url(), MySqlTestDatabase.username(), MySqlTestDatabase.password())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    private void seedMinimalCatalog() throws Exception {
        execute("INSERT INTO supplier (suppid, name, status, addr1, addr2, city, state, zip, country, phone)"
                + " VALUES (1, 'Test Supplier', 'P', '1 Main Street', NULL, 'Tokyo', 'Tokyo', '100-0001', 'JP', '000')");
        execute("INSERT INTO category (catid, name, description) VALUES ('FISH', 'Fish', 'Fish')");
        execute("INSERT INTO product (productid, category, name, description) VALUES ('FI-1', 'FISH', 'Angelfish', 'd')");
        execute("INSERT INTO item (itemid, productid, list_price, unit_cost, supplier, status)"
                + " VALUES ('EST-1', 'FI-1', 16.50, 10.00, 1, 'P')");
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
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

    private List<String> schemaHistoryVersions() throws Exception {
        try (Connection connection = connect()) {
            List<String> versions = new ArrayList<>();
            for (Map<String, Object> row : query(connection,
                    "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank")) {
                versions.add(row.get("version").toString());
            }
            return versions;
        }
    }

    private List<String> checkConstraints() throws Exception {
        try (Connection connection = connect()) {
            List<String> names = new ArrayList<>();
            for (Map<String, Object> row : query(connection,
                    "SELECT constraint_name FROM information_schema.check_constraints "
                            + "WHERE constraint_schema = DATABASE()")) {
                names.add(row.get("constraint_name").toString());
            }
            return names;
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
