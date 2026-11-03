package ik.am.jpetstore.configuration;

import ik.am.jpetstore.shared.configuration.DevDataSeeder;
import ik.am.jpetstore.support.MySqlTestDatabase;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T228: 隔离的开发演示数据导入。
 *
 * <p>演示数据只在 dev/test 环境存在：生产环境既没有演示账户，也没有默认明文密码；导入过程
 * 幂等，重复执行不产生重复数据（plan §7.3）。</p>
 */
class DevDataSeederTest {

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareSchema() {
        Flyway.configure()
                .dataSource(MySqlTestDatabase.url(), MySqlTestDatabase.username(), MySqlTestDatabase.password())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .validateOnMigrate(true)
                .load()
                .migrate();

        DriverManagerDataSource dataSource = new DriverManagerDataSource(MySqlTestDatabase.url(),
                MySqlTestDatabase.username(), MySqlTestDatabase.password());
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void isOnlyActiveInDevAndTestProfiles() {
        Profile profile = DevDataSeeder.class.getAnnotation(Profile.class);

        assertThat(profile).as("the seeder must be profile scoped").isNotNull();
        assertThat(profile.value()).contains("dev", "test").doesNotContain("prod");
    }

    @Test
    void seedsDemoDataIdempotently() {
        DevDataSeeder seeder = new DevDataSeeder(jdbcTemplate, passwordEncoder);

        seeder.seed();
        int accountsAfterFirstRun = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account", Integer.class);
        int itemsAfterFirstRun = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM item", Integer.class);

        seeder.seed();
        int accountsAfterSecondRun = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account", Integer.class);
        int itemsAfterSecondRun = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM item", Integer.class);

        assertThat(accountsAfterFirstRun).isPositive();
        assertThat(accountsAfterSecondRun).isEqualTo(accountsAfterFirstRun);
        assertThat(itemsAfterSecondRun).isEqualTo(itemsAfterFirstRun);
    }

    @Test
    void storesHashedPasswordsOnly() {
        DevDataSeeder seeder = new DevDataSeeder(jdbcTemplate, passwordEncoder);
        seeder.seed();

        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM signon WHERE username = ?", String.class, DevDataSeeder.DEMO_USERNAME);

        assertThat(hash).isNotNull();
        assertThat(hash).startsWith("$2a$").doesNotContain(DevDataSeeder.DEMO_PASSWORD);
        assertThat(passwordEncoder.matches(DevDataSeeder.DEMO_PASSWORD, hash)).isTrue();
    }

    @Test
    void demoAccountHasUsablePreferencesAndCatalog() {
        DevDataSeeder seeder = new DevDataSeeder(jdbcTemplate, passwordEncoder);
        seeder.seed();

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM category", Integer.class)).isEqualTo(5);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM profile WHERE userid = ?", Integer.class, DevDataSeeder.DEMO_USERNAME))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM inventory WHERE qty > 0", Integer.class)).isPositive();
    }
}
