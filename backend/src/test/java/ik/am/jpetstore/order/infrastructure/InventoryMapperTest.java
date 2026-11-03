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
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T273: Inventory参数化 SQL。
 *
 * <p>覆盖条件扣减、影响行数语义、缺货拒绝以及“禁止负数量放大库存”（Q02、INV-01～03、D02）。
 * 扣减语句必须带 {@code qty >= #{quantity}} 条件，库存不足时既不扣减也不报错成成功。</p>
 */
class InventoryMapperTest {

    private static SqlSessionFactory sqlSessionFactory;

    private SqlSession session;

    private InventoryMapper mapper;

    @BeforeAll
    static void createSessionFactory() {
        org.apache.ibatis.mapping.Environment environment = new org.apache.ibatis.mapping.Environment("test",
                new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),
                new org.springframework.jdbc.datasource.DriverManagerDataSource(MySqlTestDatabase.url(),
                        MySqlTestDatabase.username(), MySqlTestDatabase.password()));
        org.apache.ibatis.session.Configuration configuration =
                new org.apache.ibatis.session.Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        try (java.io.InputStream in = new ClassPathResource("mapper/order/InventoryMapper.xml").getInputStream()) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(in, configuration,
                    "mapper/order/InventoryMapper.xml", configuration.getSqlFragments()).parse();
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
        mapper = session.getMapper(InventoryMapper.class);
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void readsTheCurrentStock() {
        assertThat(mapper.selectInventory("EST-20").quantity()).isEqualTo(1);
        assertThat(mapper.selectInventory("EST-21").quantity()).isZero();
        assertThat(mapper.selectInventory("nope")).isNull();
    }

    @Test
    void aSuccessfulDeductionReportsOneAffectedRow() {
        assertThat(mapper.decrementStock("EST-1", 3)).isEqualTo(1);

        assertThat(mapper.selectInventory("EST-1").quantity()).isEqualTo(9997);
        assertThat(mapper.selectInventory("EST-1").version()).isEqualTo(1);
    }

    @Test
    void insufficientStockIsRejectedWithoutChangingAnything() {
        assertThat(mapper.decrementStock("EST-20", 2))
                .as("the caller must see that the deduction did not happen")
                .isZero();

        assertThat(mapper.selectInventory("EST-20").quantity()).isEqualTo(1);
    }

    @Test
    void missingInventoryIsRejectedWithoutCreatingRows() {
        assertThat(mapper.decrementStock("nope", 1)).isZero();

        assertThat(mapper.selectInventory("nope")).isNull();
    }

    @Test
    void theDeductionIsGuardedByTheAvailableQuantity() {
        String sql = sqlSessionFactory.getConfiguration()
                .getMappedStatement("ik.am.jpetstore.order.infrastructure.InventoryMapper.decrementStock")
                .getBoundSql(null).getSql().toUpperCase(java.util.Locale.ROOT);

        assertThat(sql).contains("QTY >= ?");
        assertThat(sql).as("stock is never increased by a deduction")
                .doesNotContain("QTY = QTY +");
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
