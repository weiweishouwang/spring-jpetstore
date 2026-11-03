package ik.am.jpetstore.configuration;

import ik.am.jpetstore.support.MySqlTestDatabase;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.autoconfigure.MybatisAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T177: application environment configuration behaviour.
 *
 * <p>Verifies behaviour rather than YAML text: the context fails fast when required datasource
 * properties are missing, connects to the real pinned MySQL engine, disables MyBatis caching,
 * and keeps Flyway strict (no baseline, no out-of-order, migration location only).</p>
 */
class ApplicationConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(
                    DataSourceAutoConfiguration.class,
                    MybatisAutoConfiguration.class,
                    FlywayAutoConfiguration.class));

    @Test
    void contextFailsFastWithoutRequiredDataSourceProperties() {
        runner.run(context -> {
            assertThat(context).as("missing datasource configuration must fail the context").hasFailed();
        });
    }

    @Test
    void connectsToThePinnedRealMysqlEngine() {
        runner.withPropertyValues(
                        "spring.datasource.url=" + MySqlTestDatabase.url(),
                        "spring.datasource.username=" + MySqlTestDatabase.username(),
                        "spring.datasource.password=" + MySqlTestDatabase.password())
                .run(context -> {
                    assertThat(context).as("real MySQL context starts").hasNotFailed();
                    DataSource dataSource = context.getBean(DataSource.class);
                    try (Connection connection = dataSource.getConnection();
                         Statement statement = connection.createStatement()) {
                        assertThat(statement.execute("SELECT 1")).isTrue();
                        try (ResultSet rs = statement.getResultSet()) {
                            assertThat(rs.next()).isTrue();
                            assertThat(rs.getInt(1)).isEqualTo(1);
                        }
                    }
                });
    }

    @Test
    void mybatisSecondLevelAndLocalCachingAreDisabled() {
        runner.withPropertyValues(
                        "spring.datasource.url=" + MySqlTestDatabase.url(),
                        "spring.datasource.username=" + MySqlTestDatabase.username(),
                        "spring.datasource.password=" + MySqlTestDatabase.password())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    SqlSessionFactory sqlSessionFactory = context.getBean(SqlSessionFactory.class);
                    assertThat(sqlSessionFactory.getConfiguration().isCacheEnabled())
                            .as("MyBatis second-level cache must be disabled")
                            .isFalse();
                    assertThat(sqlSessionFactory.getConfiguration().getLocalCacheScope())
                            .as("local (first-level) cache must be statement-scoped")
                            .isEqualTo(LocalCacheScope.STATEMENT);
                });
    }

    @Test
    void flywayIsStrictAndHasNoSeedLocations() {
        runner.withPropertyValues(
                        "spring.datasource.url=" + MySqlTestDatabase.url(),
                        "spring.datasource.username=" + MySqlTestDatabase.username(),
                        "spring.datasource.password=" + MySqlTestDatabase.password())
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FlywayProperties flyway = context.getBean(FlywayProperties.class);
                    assertThat(flyway.isValidateOnMigrate()).as("validate-on-migrate").isTrue();
                    assertThat(flyway.isBaselineOnMigrate()).as("baseline-on-migrate").isFalse();
                    assertThat(flyway.isOutOfOrder()).as("out-of-order").isFalse();
                    assertThat(flyway.getLocations())
                            .as("only schema migrations; dev/test seed data is never a migration location")
                            .containsExactly("classpath:db/migration");
                });
    }
}
