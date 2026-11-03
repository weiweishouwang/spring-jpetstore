package ik.am.jpetstore.shared.configuration;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * T229: 隔离的开发演示数据导入。
 *
 * <p>只在 dev/test profile 下存在（plan §7.3）：生产环境既没有演示账户，也没有默认明文密码。
 * 导入是幂等的，重复执行不产生重复数据；密码在导入时用 {@link PasswordEncoder} 计算哈希，
 * 数据库里只保存带算法标识的哈希值（NFR-04）。</p>
 */
@Component
@Profile({"dev", "test"})
@Transactional
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    /** 演示账户（仅 dev/test）。 */
    public static final String DEMO_USERNAME = "demo";

    /** 演示账户口令（仅 dev/test，导入时立即编码，不落库）。 */
    public static final String DEMO_PASSWORD = "demo-pass-123";

    private static final List<String[]> CATEGORIES = List.of(
            new String[]{"FISH", "Fish", "Saltwater and freshwater friends"},
            new String[]{"DOGS", "Dogs", "Various sizes of man's best friend"},
            new String[]{"REPTILES", "Reptiles", "Lizards, turtles and snakes"},
            new String[]{"CATS", "Cats", "Felines of all persuasions"},
            new String[]{"BIRDS", "Birds", "Feathered companions"});

    private static final List<String[]> PRODUCTS = List.of(
            new String[]{"FI-SW-01", "FISH", "Angelfish", "Saltwater fish from Australia"},
            new String[]{"FI-SW-02", "FISH", "Tiger Shark", "Saltwater fish from Australia"},
            new String[]{"K9-BD-01", "DOGS", "Bulldog", "Friendly dog from England"},
            new String[]{"RP-SN-01", "REPTILES", "Rattlesnake", "Dangerous snake from the Americas"},
            new String[]{"AV-CB-01", "BIRDS", "Amazon Parrot", "Long-living parrot from the Amazon"});

    private static final List<String[]> ITEMS = List.of(
            new String[]{"EST-1", "FI-SW-01", "16.50", "10.00", "Large", "Adult male"},
            new String[]{"EST-2", "FI-SW-01", "10.50", "6.00", "Small", "Adult female"},
            new String[]{"EST-3", "FI-SW-02", "18.50", "12.00", "Toothless", "Juvenile"},
            new String[]{"EST-4", "K9-BD-01", "25.50", "15.00", "Spotless", "Adult male"},
            new String[]{"EST-5", "RP-SN-01", "15.50", "9.00", "Venomless", "Adult"},
            new String[]{"EST-6", "AV-CB-01", "120.00", "85.00", "Talking", "Adult"});

    private final JdbcTemplate jdbcTemplate;

    private final PasswordEncoder passwordEncoder;

    public DevDataSeeder(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /** 幂等导入演示数据。 */
    public void seed() {
        seedCredentials();
        seedAccount();
        seedCatalog();
        log.info("Dev demo data seeded for user '{}'", DEMO_USERNAME);
    }

    private void seedCredentials() {
        jdbcTemplate.update("INSERT IGNORE INTO signon (username, password_hash) VALUES (?, ?)",
                DEMO_USERNAME, passwordEncoder.encode(DEMO_PASSWORD));
    }

    private void seedAccount() {
        jdbcTemplate.update("INSERT INTO account (userid, email, first_name, last_name, phone, addr1, addr2, city,"
                        + " state, zip, country) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE email = VALUES(email), first_name = VALUES(first_name),"
                        + " last_name = VALUES(last_name), phone = VALUES(phone), addr1 = VALUES(addr1),"
                        + " addr2 = VALUES(addr2), city = VALUES(city), state = VALUES(state), zip = VALUES(zip),"
                        + " country = VALUES(country)",
                DEMO_USERNAME, "demo@example.com", "Demo", "User", "000-0000-0000", "1-2-3 Sakuragaoka", null,
                "Shibuya", "Tokyo", "150-0001", "JP");

        jdbcTemplate.update("INSERT INTO profile (userid, language_preference, favourite_category, list_option,"
                        + " banner_option) VALUES (?, ?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE language_preference = VALUES(language_preference),"
                        + " favourite_category = VALUES(favourite_category), list_option = VALUES(list_option),"
                        + " banner_option = VALUES(banner_option)",
                DEMO_USERNAME, "english", "FISH", true, true);

        for (String[] category : CATEGORIES) {
            jdbcTemplate.update("INSERT INTO bannerdata (favcategory, banner_name) VALUES (?, ?) "
                    + "ON DUPLICATE KEY UPDATE banner_name = VALUES(banner_name)",
                    category[0], category[1].toLowerCase(java.util.Locale.ROOT) + "_banner");
        }
    }

    private void seedCatalog() {
        jdbcTemplate.update("INSERT INTO supplier (suppid, name, status, addr1, addr2, city, state, zip, country,"
                        + " phone) VALUES (1, 'Demo Supplier', 'P', '1 Main Street', NULL, 'Tokyo', 'Tokyo',"
                        + " '100-0001', 'JP', '000-0000-0000') "
                        + "ON DUPLICATE KEY UPDATE name = VALUES(name), status = VALUES(status)");

        for (String[] category : CATEGORIES) {
            jdbcTemplate.update("INSERT INTO category (catid, name, description) VALUES (?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description)",
                    category[0], category[1], category[2]);
        }

        for (String[] product : PRODUCTS) {
            jdbcTemplate.update("INSERT INTO product (productid, category, name, description) VALUES (?, ?, ?, ?) "
                    + "ON DUPLICATE KEY UPDATE category = VALUES(category), name = VALUES(name),"
                    + " description = VALUES(description)",
                    product[0], product[1], product[2], product[3]);
        }

        for (String[] item : ITEMS) {
            jdbcTemplate.update("INSERT INTO item (itemid, productid, list_price, unit_cost, supplier, status,"
                            + " attr1, attr2) VALUES (?, ?, ?, ?, ?, ?, ?, ?) "
                            + "ON DUPLICATE KEY UPDATE productid = VALUES(productid), list_price = VALUES(list_price),"
                            + " unit_cost = VALUES(unit_cost), attr1 = VALUES(attr1), attr2 = VALUES(attr2)",
                    item[0], item[1], item[2], item[3], 1, "P", item[4], item[5]);

            jdbcTemplate.update("INSERT INTO inventory (itemid, qty, version) VALUES (?, 10000, 0) "
                    + "ON DUPLICATE KEY UPDATE qty = VALUES(qty)", item[0]);
        }
    }
}
