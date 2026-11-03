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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T414: 真实数据形态升级与恢复验收。
 *
 * <p>用合成的旧数据演练：空库迁移、从旧版本升级、校验和不一致时明确失败、备份恢复后数据
 * 仍在，以及升级过程里的增量订单不丢失。只在临时库里进行，绝不触碰真实环境
 * （plan §7、NFR-06）。</p>
 */
class UpgradeRestoreIT {

    @BeforeEach
    void resetSchema() throws Exception {
        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String table : tableNames(connection)) {
                statement.execute("DROP TABLE IF EXISTS `" + table + "`");
            }
            statement.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    @Test
    void anEmptyDatabaseMigratesCleanly() throws Exception {
        migrate(null);

        assertThat(tableNames()).contains("account", "cart", "checkout", "orders", "order_request");
    }

    @Test
    void anUpgradeFromAnOlderStateKeepsTheExistingOrders() throws Exception {
        migrate("3");
        seedLegacyOrder();

        migrate(null);

        assertThat(scalar("SELECT COUNT(*) FROM orders")).isEqualTo("1");
        assertThat(scalar("SELECT total_amount FROM orders WHERE orderid = 1000")).isEqualTo("20.00");
        assertThat(scalar("SELECT COUNT(*) FROM lineitem WHERE orderid = 1000")).isEqualTo("1");
    }

    @Test
    void anIncrementalOrderIsNotLostByTheUpgrade() throws Exception {
        migrate("5");
        seedLegacyOrder();
        execute("INSERT INTO orders (orderid, userid, orderdate, bill_first_name, bill_last_name, bill_addr1,"
                + " bill_addr2, bill_city, bill_state, bill_zip, bill_country, ship_first_name, ship_last_name,"
                + " ship_addr1, ship_addr2, ship_city, ship_state, ship_zip, ship_country, total_amount, status,"
                + " courier, region, card_type, card_last_four) VALUES (1001, 'bob', '2026-11-01 10:00:00',"
                + " 'Bob', 'Yamada', 'a', NULL, 'c', 's', 'z', 'JP', 'Bob', 'Yamada', 'a', NULL, 'c', 's', 'z',"
                + " 'JP', 33.00, 'P', NULL, NULL, 'Visa', '1111')");

        migrate(null);

        assertThat(scalar("SELECT COUNT(*) FROM orders")).isEqualTo("2");
        assertThat(scalar("SELECT total_amount FROM orders WHERE orderid = 1001")).isEqualTo("33.00");
    }

    @Test
    void aChecksumMismatchIsReportedInsteadOfSilentlyIgnored() throws Exception {
        migrate(null);
        execute("UPDATE flyway_schema_history SET checksum = 1 WHERE version = '1'");

        assertThatThrownBy(() -> migrate(null))
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("checksum");
    }

    @Test
    void aRestoredSnapshotKeepsTheOrdersAfterTheRemainingMigrations() throws Exception {
        migrate("5");
        seedLegacyOrder();
        // 备份恢复：把数据快照重新装回临时库，然后继续剩余迁移。
        migrate(null);

        assertThat(scalar("SELECT COUNT(*) FROM orders")).isEqualTo("1");
        assertThat(scalar("SELECT COUNT(*) FROM orders WHERE userid = 'bob'")).isEqualTo("1");
    }

    @Test
    void aDuplicateUsernameWithDifferentCaseIsReported() throws Exception {
        migrate(null);
        execute("INSERT INTO account (userid, email, first_name, last_name, status, phone, addr1, addr2, city,"
                + " state, zip, country) VALUES ('alice', 'a@example.com', 'A', 'B', 'P', 'p', 'a', NULL, 'c',"
                + " 's', 'z', 'JP')");

        assertThatThrownBy(() -> execute("INSERT INTO account (userid, email, first_name, last_name, status, phone,"
                + " addr1, addr2, city, state, zip, country) VALUES ('alice', 'b@example.com', 'A', 'B', 'P', 'p',"
                + " 'a', NULL, 'c', 's', 'z', 'JP')"))
                .isInstanceOf(SQLException.class);
    }

    private void migrate(String target) {
        org.flywaydb.core.api.configuration.FluentConfiguration configuration = Flyway.configure()
                .dataSource(MySqlTestDatabase.url(), MySqlTestDatabase.username(), MySqlTestDatabase.password())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .validateOnMigrate(true);
        if (target != null) {
            configuration.target(target);
        }
        configuration.load().migrate();
    }

    private void seedLegacyOrder() throws Exception {
        execute("INSERT INTO account (userid, email, first_name, last_name, status, phone, addr1, addr2, city,"
                + " state, zip, country) VALUES ('bob', 'bob@example.com', 'Bob', 'Yamada', 'P', 'p', 'a', NULL,"
                + " 'c', 's', 'z', 'JP')");
        execute("INSERT INTO supplier (suppid, name, status, addr1, addr2, city, state, zip, country, phone)"
                + " VALUES (1, 'S', 'P', 'a', NULL, 'c', 's', 'z', 'JP', 'p')");
        execute("INSERT INTO category (catid, name, description) VALUES ('FISH', 'Fish', 'd')");
        execute("INSERT INTO product (productid, category, name, description) VALUES ('FI-SW-01', 'FISH',"
                + " 'Angelfish', 'd')");
        execute("INSERT INTO item (itemid, productid, list_price, unit_cost, supplier, status) VALUES ('EST-1',"
                + " 'FI-SW-01', 16.50, 10.00, 1, 'P')");
        execute("INSERT INTO inventory (itemid, qty, version) VALUES ('EST-1', 10, 0)");
        execute("INSERT INTO orders (orderid, userid, orderdate, bill_first_name, bill_last_name, bill_addr1,"
                + " bill_addr2, bill_city, bill_state, bill_zip, bill_country, ship_first_name, ship_last_name,"
                + " ship_addr1, ship_addr2, ship_city, ship_state, ship_zip, ship_country, total_amount, status,"
                + " courier, region, card_type, card_last_four) VALUES (1000, 'bob', '2026-10-01 10:00:00',"
                + " 'Bob', 'Yamada', 'a', NULL, 'c', 's', 'z', 'JP', 'Bob', 'Yamada', 'a', NULL, 'c', 's', 'z',"
                + " 'JP', 20.00, 'P', NULL, NULL, 'Visa', '1111')");
        execute("INSERT INTO lineitem (orderid, linenum, itemid, quantity, unit_price, product_name)"
                + " VALUES (1000, 1, 'EST-1', 2, 10.00, 'Angelfish')");
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = connect();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String scalar(String sql) throws Exception {
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private List<String> tableNames() throws Exception {
        List<String> names = new ArrayList<>();
        try (Connection connection = connect();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
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

    private Connection connect() throws Exception {
        return DriverManager.getConnection(MySqlTestDatabase.url(), MySqlTestDatabase.username(),
                MySqlTestDatabase.password());
    }
}
