package ik.am.jpetstore.cart.infrastructure;

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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T267: Cart参数化 SQL。
 *
 * <p>覆盖游客会话持有权、账号活动车唯一与跨设备恢复、锁定读、version CAS、同 SKU 唯一、
 * 合并超限不丢数据以及 CONSUMED 不可重复消费（Q08、CRT-01～11、plan §5.4）。</p>
 */
class CartMapperTest {

    private static final String NAMESPACE = "ik.am.jpetstore.cart.infrastructure.CartMapper";

    private static SqlSessionFactory sqlSessionFactory;

    private SqlSession session;

    private CartMapper mapper;

    @BeforeAll
    static void createSessionFactory() {
        DataSourcelessSessionFactory factory = new DataSourcelessSessionFactory(
                "mapper/cart/CartMapper.xml", CartMapper.class);
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
        mapper = session.getMapper(CartMapper.class);
    }

    @AfterEach
    void closeSession() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void aGuestCartIsFoundByItsSessionBindingHash() {
        Long cartId = insertCart("hash-guest-1", null);

        CartMapper.CartRow row = mapper.selectActiveCartBySession("hash-guest-1");

        assertThat(row).isNotNull();
        assertThat(row.cartId()).isEqualTo(cartId);
        assertThat(row.ownerUserId()).as("a guest cart has no account owner").isNull();
        assertThat(row.version()).isZero();
        assertThat(row.state()).isEqualTo(CartMapper.STATE_ACTIVE);
    }

    @Test
    void theRawSessionIdIsNeverStoredInTheCartTable() throws Exception {
        insertCart("hash-guest-2", null);

        assertThat(cartColumnValues("session_binding_hash"))
                .as("only the binding hash is persisted")
                .containsExactly("hash-guest-2");
    }

    @Test
    void anAccountCartIsUniqueAndRecoverableAcrossDevices() {
        Long cartId = insertCart(null, "alice");

        assertThat(mapper.selectActiveCartByOwner("alice")).isNotNull();
        assertThat(mapper.selectActiveCartByOwner("alice").cartId()).isEqualTo(cartId);
        assertThat(mapper.selectActiveCartByOwner("bob"))
                .as("another account never sees the first cart")
                .isNull();

        assertThatThrownBy(() -> insertCart(null, "alice"))
                .as("only one ACTIVE cart may exist per account")
                .hasRootCauseInstanceOf(SQLException.class);
    }

    @Test
    void lockingTheCartUsesAForUpdateRead() {
        insertCart(null, "alice");

        mapper.selectActiveCartByOwnerForUpdate("alice");
        mapper.selectActiveCartBySessionForUpdate("hash-guest-1");

        assertThat(sqlOf("selectActiveCartByOwnerForUpdate")).contains("FOR UPDATE");
        assertThat(sqlOf("selectActiveCartBySessionForUpdate")).contains("FOR UPDATE");
    }

    @Test
    void versionCompareAndSetOnlySucceedsForTheExpectedVersion() {
        Long cartId = insertCart(null, "alice");

        assertThat(mapper.bumpVersion(cartId, 0, Instant.now())).isEqualTo(1);
        assertThat(mapper.bumpVersion(cartId, 0, Instant.now()))
                .as("a stale expected version must not change anything")
                .isZero();
        assertThat(mapper.selectActiveCartByOwner("alice").version()).isEqualTo(1);
    }

    @Test
    void theSameSkuCanOnlyExistOnceInACart() {
        Long cartId = insertCart(null, "alice");
        mapper.insertCartItem(new CartMapper.CartItemRow(cartId, "EST-1", 2));

        assertThatThrownBy(() -> mapper.insertCartItem(new CartMapper.CartItemRow(cartId, "EST-1", 3)))
                .as("(cart_id, itemid) is unique")
                .hasRootCauseInstanceOf(SQLException.class);

        assertThat(mapper.updateCartItemQuantity(cartId, "EST-1", 5)).isEqualTo(1);
        assertThat(mapper.selectCartItems(cartId)).hasSize(1);
        assertThat(mapper.selectCartItems(cartId).get(0).quantity()).isEqualTo(5);
    }

    @Test
    void aFrozenMergeKeepsBothSidesContent() {
        Long guestCart = insertCart("hash-guest-3", null);
        mapper.insertCartItem(new CartMapper.CartItemRow(guestCart, "EST-1", 4));
        Long accountCart = insertCart(null, "alice");
        mapper.insertCartItem(new CartMapper.CartItemRow(accountCart, "EST-1", 1));

        assertThat(mapper.freezeAsPendingMerge(guestCart, "alice")).isEqualTo(1);

        List<CartMapper.PendingMergeRow> pending = mapper.selectPendingMerges("alice");
        assertThat(pending).as("the frozen guest content is kept, not dropped").hasSize(1);
        assertThat(pending.get(0).itemId()).isEqualTo("EST-1");
        assertThat(pending.get(0).quantity()).isEqualTo(4);
        assertThat(pending.get(0).mergeId()).isEqualTo(guestCart);
        assertThat(mapper.selectActiveCartBySession("hash-guest-3"))
                .as("the original guest session can no longer modify the frozen cart")
                .isNull();
        assertThat(mapper.selectActiveCartByOwner("alice").cartId()).isEqualTo(accountCart);
        assertThat(mapper.selectCartItems(accountCart)).as("the account side is untouched").hasSize(1);
    }

    @Test
    void aConsumedCartCannotBeConsumedTwice() {
        Long cartId = insertCart(null, "alice");
        mapper.insertCartItem(new CartMapper.CartItemRow(cartId, "EST-1", 1));

        assertThat(mapper.updateState(cartId, CartMapper.STATE_CONSUMED)).isEqualTo(1);

        assertThat(mapper.selectActiveCartByOwner("alice"))
                .as("a consumed cart is no longer the active cart")
                .isNull();
        assertThat(mapper.updateState(cartId, CartMapper.STATE_CONSUMED))
                .as("consuming twice changes nothing")
                .isZero();
    }

    @Test
    void transferringAGuestCartToAnAccountKeepsItsLines() {
        Long guestCart = insertCart("hash-guest-4", null);
        mapper.insertCartItem(new CartMapper.CartItemRow(guestCart, "EST-2", 3));

        assertThat(mapper.transferToAccount(guestCart, "alice")).isEqualTo(1);

        assertThat(mapper.selectActiveCartByOwner("alice").cartId()).isEqualTo(guestCart);
        assertThat(mapper.selectCartItems(guestCart)).hasSize(1);
        assertThat(mapper.selectActiveCartBySession("hash-guest-4"))
                .as("the guest binding no longer owns the cart")
                .isNull();
    }

    private Long insertCart(String sessionBindingHash, String ownerUserId) {
        CartMapper.NewCart cart = new CartMapper.NewCart(sessionBindingHash, ownerUserId, 0,
                CartMapper.STATE_ACTIVE, Instant.now(), Instant.now().plusSeconds(1800));
        mapper.insertCart(cart);
        return cart.getCartId();
    }

    private String sqlOf(String statementId) {
        org.apache.ibatis.mapping.MappedStatement statement =
                sqlSessionFactory.getConfiguration().getMappedStatement(NAMESPACE + "." + statementId);
        return statement.getBoundSql(null).getSql().toUpperCase(java.util.Locale.ROOT);
    }

    private List<String> cartColumnValues(String column) throws Exception {
        List<String> values = new ArrayList<>();
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT " + column + " FROM cart")) {
            while (rs.next()) {
                values.add(rs.getString(1));
            }
        }
        return values;
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
