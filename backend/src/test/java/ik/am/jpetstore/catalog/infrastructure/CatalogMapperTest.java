package ik.am.jpetstore.catalog.infrastructure;

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
 * T244: Catalog参数化 SQL。
 *
 * <p>覆盖分类/产品/SKU 关联、名称包含搜索、字面通配符按 Q14 处理、缺横幅正常返回空、实时库存
 * 投影，以及 SQL 参数化（用户输入不拼接进语句）。</p>
 */
class CatalogMapperTest {

    private static SqlSessionFactory sqlSessionFactory;

    private SqlSession session;

    private CatalogMapper mapper;

    @BeforeAll
    static void createSessionFactory() {
        org.apache.ibatis.mapping.Environment environment = new org.apache.ibatis.mapping.Environment("test",
                new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),
                new org.springframework.jdbc.datasource.DriverManagerDataSource(MySqlTestDatabase.url(),
                        MySqlTestDatabase.username(), MySqlTestDatabase.password()));
        org.apache.ibatis.session.Configuration configuration =
                new org.apache.ibatis.session.Configuration(environment);
        configuration.setMapUnderscoreToCamelCase(true);
        try (java.io.InputStream in = new ClassPathResource("mapper/catalog/CatalogMapper.xml").getInputStream()) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(in, configuration,
                    "mapper/catalog/CatalogMapper.xml", configuration.getSqlFragments()).parse();
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
        mapper = session.getMapper(CatalogMapper.class);
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void selectsTheFiveCategories() {
        List<CatalogMapper.CategoryRow> categories = mapper.selectCategories();

        assertThat(categories).hasSize(5);
        assertThat(categories).extracting(CatalogMapper.CategoryRow::categoryId)
                .containsExactlyInAnyOrder("FISH", "DOGS", "REPTILES", "CATS", "BIRDS");
    }

    @Test
    void selectsProductsWithTheirCategory() {
        List<CatalogMapper.ProductRow> products = mapper.selectProductsByCategory("FISH");

        assertThat(products).isNotEmpty();
        assertThat(products).allSatisfy(product -> assertThat(product.categoryId()).isEqualTo("FISH"));
    }

    @Test
    void selectsItemsWithLiveStockProjection() {
        List<CatalogMapper.ItemRow> lowStockItems = mapper.selectItemsByProduct("RP-SN-01");

        assertThat(lowStockItems).hasSize(1);
        CatalogMapper.ItemRow lowStock = lowStockItems.get(0);
        assertThat(lowStock.itemId()).isEqualTo("EST-20");
        assertThat(lowStock.availableQuantity()).isEqualTo(1);

        List<CatalogMapper.ItemRow> outOfStockItems = mapper.selectItemsByProduct("RP-LI-01");
        assertThat(outOfStockItems).hasSize(1);
        assertThat(outOfStockItems.get(0).itemId()).isEqualTo("EST-21");
        assertThat(outOfStockItems.get(0).availableQuantity()).isZero();
    }

    @Test
    void nameSearchMatchesProductNameCaseInsensitively() {
        List<CatalogMapper.ProductRow> matches = mapper.selectProductsByName("angel");

        assertThat(matches).extracting(CatalogMapper.ProductRow::productId).contains("FI-SW-01");
        assertThat(mapper.selectProductsByName("ANGEL")).hasSameSizeAs(matches);
    }

    @Test
    void nameSearchDoesNotMatchDescriptions() {
        assertThat(mapper.selectProductsByName("australia"))
                .as("descriptions do not participate in the name search (CAT-05)")
                .isEmpty();
    }

    @Test
    void wildcardCharactersAreMatchedLiterally() {
        assertThat(mapper.selectProductsByName("%"))
                .as("a percent sign is a literal, not a wildcard (Q14)")
                .isEmpty();
        assertThat(mapper.selectProductsByName("_")).isEmpty();
    }

    @Test
    void missingBannerReturnsNoRow() throws Exception {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM bannerdata");
        }

        assertThat(mapper.selectBanner("FISH")).isNull();
        assertThat(mapper.selectBanner("BIRDS")).isNull();
    }

    @Test
    void missingProductAndItemReturnNoRow() {
        assertThat(mapper.selectProduct("NOPE")).isNull();
        assertThat(mapper.selectItem("NOPE")).isNull();
        assertThat(mapper.selectItemsByProduct("NOPE")).isEmpty();
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
