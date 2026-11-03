package ik.am.jpetstore.account.infrastructure;

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

import javax.sql.DataSource;
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
 * T241: Account参数化 SQL。
 *
 * <p>对齐 shop.sql fixture 与真实 MySQL：三表查询不依赖横幅；用户名唯一冲突映射为明确失败；
 * 更新只作用于目标账户；密码哈希非空由数据库约束兜底。</p>
 */
class AccountMapperTest {

    private static SqlSessionFactory sqlSessionFactory;

    private SqlSession session;

    private AccountMapper mapper;

    @BeforeAll
    static void createSessionFactory() throws Exception {
        DataSourcelessSessionFactory factory = new DataSourcelessSessionFactory(
                "mapper/account/AccountMapper.xml", AccountMapper.class);
        sqlSessionFactory = factory.build();
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
        mapper = session.getMapper(AccountMapper.class);
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void selectsAccountPreferencesAndCredentialsOfASeededUser() {
        AccountMapper.AccountRow account = mapper.selectAccount("alice");

        assertThat(account).isNotNull();
        assertThat(account.email()).isEqualTo("alice@example.com");
        assertThat(account.addr2()).isNull();

        AccountMapper.PreferencesRow preferences = mapper.selectPreferences("alice");
        assertThat(preferences.favouriteCategoryId()).isEqualTo("BIRDS");

        AccountMapper.CredentialsRow credentials = mapper.selectCredentials("alice");
        assertThat(credentials.passwordHash()).startsWith("$2a$");
    }

    @Test
    void missingUserReturnsNullRows() {
        assertThat(mapper.selectAccount("nobody")).isNull();
        assertThat(mapper.selectPreferences("nobody")).isNull();
        assertThat(mapper.selectCredentials("nobody")).isNull();
    }

    @Test
    void accountQueriesNeverDependOnBannerData() throws Exception {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("DELETE FROM bannerdata");
        }

        assertThat(mapper.selectAccount("bob")).isNotNull();
        assertThat(mapper.selectCredentials("bob")).isNotNull();
        assertThat(mapper.selectPreferences("bob")).isNotNull();
    }

    @Test
    void insertsCreateAllThreeTablesAtomicallyAtTheCallerLevel() {
        AccountMapper.AccountRow account = new AccountMapper.AccountRow("carol", "carol@example.com", "Carol",
                "Sato", "070-0000-0000", "5-6-7 Jingumae", null, "Shibuya", "Tokyo", "150-0001", "JP", "P");
        AccountMapper.PreferencesRow preferences = new AccountMapper.PreferencesRow("carol", "japanese", "DOGS",
                true, false);
        AccountMapper.CredentialsRow credentials = new AccountMapper.CredentialsRow("carol", "$2a$10$hash");

        assertThat(mapper.insertAccount(account)).isEqualTo(1);
        assertThat(mapper.insertProfile(preferences)).isEqualTo(1);
        assertThat(mapper.insertSignon(credentials)).isEqualTo(1);

        assertThat(mapper.selectCredentials("carol").passwordHash()).isEqualTo("$2a$10$hash");
        assertThat(mapper.selectPreferences("carol").languagePreference()).isEqualTo("japanese");
    }

    @Test
    void duplicateUsernameIsRejectedByTheUniqueConstraint() {
        AccountMapper.CredentialsRow duplicate = new AccountMapper.CredentialsRow("alice", "$2a$10$other");

        assertThatThrownBy(() -> mapper.insertSignon(duplicate))
                .hasRootCauseInstanceOf(SQLException.class);
        assertThat(mapper.selectCredentials("alice").passwordHash())
                .as("the existing account is never overwritten")
                .isNotEqualTo("$2a$10$other");
    }

    @Test
    void updatesOnlyTouchTheirTargetAccount() {
        AccountMapper.AccountRow bob = mapper.selectAccount("bob");
        AccountMapper.AccountRow changed = new AccountMapper.AccountRow(bob.userId(), "bob-new@example.com",
                bob.firstName(), bob.lastName(), bob.phone(), bob.addr1(), bob.addr2(), bob.city(), bob.state(),
                bob.zip(), bob.country(), bob.status());

        assertThat(mapper.updateAccount(changed)).isEqualTo(1);

        assertThat(mapper.selectAccount("bob").email()).isEqualTo("bob-new@example.com");
        assertThat(mapper.selectAccount("alice").email()).isEqualTo("alice@example.com");
    }

    @Test
    void passwordHashIsEnforcedNonNullByTheDatabase() {
        assertThatThrownBy(() -> mapper.insertSignon(new AccountMapper.CredentialsRow("dave", null)))
                .hasRootCauseInstanceOf(SQLException.class);
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

    /** 从指定 XML 构建 SqlSessionFactory；仅供 Mapper 测试复用。 */
    static final class DataSourcelessSessionFactory {

        private final String mapperLocation;

        private final Class<?> mapperInterface;

        DataSourcelessSessionFactory(String mapperLocation, Class<?> mapperInterface) {
            this.mapperLocation = mapperLocation;
            this.mapperInterface = mapperInterface;
        }

        SqlSessionFactory build() {
            org.apache.ibatis.mapping.Environment environment = new org.apache.ibatis.mapping.Environment("test",
                    new org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory(),
                    new org.springframework.jdbc.datasource.DriverManagerDataSource(MySqlTestDatabase.url(),
                            MySqlTestDatabase.username(), MySqlTestDatabase.password()));
            org.apache.ibatis.session.Configuration configuration =
                    new org.apache.ibatis.session.Configuration(environment);
            configuration.setMapUnderscoreToCamelCase(true);
            try (java.io.InputStream in = new ClassPathResource(mapperLocation).getInputStream()) {
                new org.apache.ibatis.builder.xml.XMLMapperBuilder(in, configuration, mapperLocation,
                        configuration.getSqlFragments()).parse();
            } catch (Exception e) {
                throw new IllegalStateException("无法加载 Mapper XML: " + mapperLocation, e);
            }
            return new org.apache.ibatis.session.SqlSessionFactoryBuilder().build(configuration);
        }
    }
}
