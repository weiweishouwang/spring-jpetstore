package ik.am.jpetstore.support;

import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * T173: shared MySQL test database for migration, mapper and concurrency tests.
 *
 * <p>Starts the exact image digest locked in {@code docs/decisions/versions.md} ("OCI镜像不可变引用"),
 * i.e. the same MySQL 8.4.13 engine as the dev Compose file and the deployment target. The
 * container starts once per JVM and is shared by all tests in the process; each test suite is
 * responsible for isolating its schema. H2 is never used as a substitute, and a missing or
 * unreachable Docker daemon fails loudly here instead of being swallowed.</p>
 */
public final class MySqlTestDatabase {

    /** library/mysql@sha256:a0225d... — the immutable reference from docs/decisions/versions.md. */
    private static final DockerImageName MYSQL_8_4_13 = DockerImageName
            .parse("mysql@sha256:a0225d1ea6631c8bc2385dc4c2faba17bbf1834ecf369e1659a9a382bd8d5dd7");

    private static final MySQLContainer MYSQL = new MySQLContainer(MYSQL_8_4_13)
            .withDatabaseName("jpetstore_test")
            .withUsername("jpetstore_test")
            .withPassword("jpetstore_test")
            .withUrlParam("connectionTimeZone", "UTC");

    static {
        MYSQL.start();
    }

    private MySqlTestDatabase() {
    }

    /** JDBC URL of the shared test container (database {@code jpetstore_test}). */
    public static String url() {
        return MYSQL.getJdbcUrl();
    }

    public static String username() {
        return MYSQL.getUsername();
    }

    public static String password() {
        return MYSQL.getPassword();
    }

    /** The shared container, for tests that need direct access (e.g. executing setup SQL). */
    public static MySQLContainer container() {
        return MYSQL;
    }
}
