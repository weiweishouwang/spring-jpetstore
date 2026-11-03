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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T218: MySQL 迁移 V1__create_account_and_catalog.sql。
 *
 * <p>从空 schema 迁移后验证：账户凭据/偏好、商品关系、用户名比较规则、密码哈希长度与外键。</p>
 */
class Schema1MigrationTest {

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = DriverManager.getConnection(MySqlTestDatabase.url(), MySqlTestDatabase.username(),
                MySqlTestDatabase.password());
             Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String table : tablesInSchema(connection)) {
                statement.execute("DROP TABLE IF EXISTS `" + table + "`");
            }
            statement.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    @Test
    void createsAccountCredentialAndPreferenceTables() throws Exception {
        migrate();

        assertThat(tablesInSchema()).contains("signon", "account", "profile", "bannerdata");
        assertThat(columnLength("signon", "password_hash")).isEqualTo(255);
        assertThat(columnLength("signon", "username")).isEqualTo(25);
        assertThat(primaryKeyColumns("signon")).containsExactly("username");
        assertThat(primaryKeyColumns("profile")).containsExactly("userid");
    }

    @Test
    void usernameComparisonIsCaseSensitive() throws Exception {
        migrate();

        assertThat(collation("signon", "username")).endsWith("_bin");
        assertThat(collation("account", "userid")).endsWith("_bin");
        assertThat(collation("profile", "userid")).endsWith("_bin");
    }

    @Test
    void createsCatalogHierarchyWithForeignKeys() throws Exception {
        migrate();

        assertThat(tablesInSchema()).contains("category", "product", "item", "supplier");
        assertThat(foreignKeys("product")).contains("category");
        assertThat(foreignKeys("item")).contains("product");
    }

    @Test
    void itemPriceKeepsTwoDecimalPrecision() throws Exception {
        migrate();

        assertThat(columnType("item", "list_price")).isEqualTo("decimal(10,2)");
        assertThat(columnType("item", "unit_cost")).isEqualTo("decimal(10,2)");
    }

    private void migrate() {
        Flyway.configure()
                .dataSource(MySqlTestDatabase.url(), MySqlTestDatabase.username(), MySqlTestDatabase.password())
                .locations("classpath:db/migration")
                .target("1")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load()
                .migrate();
    }

    private List<String> tablesInSchema() throws Exception {
        try (Connection connection = DriverManager.getConnection(MySqlTestDatabase.url(), MySqlTestDatabase.username(),
                MySqlTestDatabase.password());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()")) {
            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
            return tables;
        }
    }

    private List<String> tablesInSchema(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()")) {
            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
            return tables;
        }
    }

    private int columnLength(String table, String column) throws Exception {
        return ((Number) columnValue("CHARACTER_MAXIMUM_LENGTH", table, column)).intValue();
    }

    private String columnType(String table, String column) throws Exception {
        Object value = columnValue("COLUMN_TYPE", table, column);
        return value == null ? null : value.toString();
    }

    private String collation(String table, String column) throws Exception {
        Object value = columnValue("COLLATION_NAME", table, column);
        return value == null ? null : value.toString();
    }

    private Object columnValue(String informationSchemaColumn, String table, String column) throws Exception {
        try (Connection connection = DriverManager.getConnection(MySqlTestDatabase.url(), MySqlTestDatabase.username(),
                MySqlTestDatabase.password());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT " + informationSchemaColumn
                     + " FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = '"
                     + table + "' AND column_name = '" + column + "'")) {
            return rs.next() ? rs.getObject(1) : null;
        }
    }

    private List<String> primaryKeyColumns(String table) throws Exception {
        try (Connection connection = DriverManager.getConnection(MySqlTestDatabase.url(), MySqlTestDatabase.username(),
                MySqlTestDatabase.password());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT column_name FROM information_schema.key_column_usage WHERE table_schema = DATABASE() "
                             + "AND table_name = '" + table + "' AND constraint_name = 'PRIMARY' ORDER BY ordinal_position")) {
            List<String> columns = new ArrayList<>();
            while (rs.next()) {
                columns.add(rs.getString(1));
            }
            return columns;
        }
    }

    private List<String> foreignKeys(String table) throws Exception {
        try (Connection connection = DriverManager.getConnection(MySqlTestDatabase.url(), MySqlTestDatabase.username(),
                MySqlTestDatabase.password());
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT DISTINCT referenced_table_name FROM information_schema.key_column_usage "
                             + "WHERE table_schema = DATABASE() AND table_name = '" + table
                             + "' AND referenced_table_name IS NOT NULL")) {
            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
            return tables;
        }
    }
}
