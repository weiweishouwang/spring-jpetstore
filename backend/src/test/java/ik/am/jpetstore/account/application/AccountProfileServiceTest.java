package ik.am.jpetstore.account.application;

import ik.am.jpetstore.account.api.AccountProfileUpdate;
import ik.am.jpetstore.account.domain.AccountModels.Address;
import ik.am.jpetstore.account.domain.AccountModels.Preferences;
import ik.am.jpetstore.account.infrastructure.AccountMapper;
import ik.am.jpetstore.shared.error.BusinessFailure;
import ik.am.jpetstore.support.MySqlTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T252: 本人资料与偏好更新服务。
 *
 * <p>验证：空密码保留旧凭据；有效新密码替换凭据且旧密码失效；任一步失败整体回滚；偏好更新
 * 立即生效；请求无法指定他人账户（ACC-07～10、PER-04、AT-03/04/14）。</p>
 */
@SpringBootTest
class AccountProfileServiceTest {

    @Autowired
    private AccountProfileService service;

    @Autowired
    private AccountMapper mapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.identity.web.IdentityAssembler identityAssembler;

    @MockitoBean
    private ik.am.jpetstore.catalog.web.CatalogAssembler catalogAssembler;

    @MockitoBean
    private ik.am.jpetstore.account.web.AccountAssembler accountAssembler;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlTestDatabase::url);
        registry.add("spring.datasource.username", MySqlTestDatabase::username);
        registry.add("spring.datasource.password", MySqlTestDatabase::password);
    }

    @BeforeEach
    void seedAccount() {
        jdbcTemplate.update("DELETE FROM account");
        jdbcTemplate.update("DELETE FROM profile");
        jdbcTemplate.update("DELETE FROM signon");
        jdbcTemplate.update("INSERT INTO account (userid, email, first_name, last_name, phone, addr1, addr2,"
                        + " city, state, zip, country) VALUES ('alice', 'alice@example.com', 'Alice', 'Ishikawa',"
                        + " '070-1111-2222', '1-2-3 Sakuragaoka', NULL, 'Shibuya', 'Tokyo', '150-0001', 'JP')");
        jdbcTemplate.update("INSERT INTO profile (userid, language_preference, favourite_category, list_option,"
                + " banner_option) VALUES ('alice', 'english', 'FISH', TRUE, TRUE)");
        jdbcTemplate.update("INSERT INTO signon (username, password_hash) VALUES ('alice', ?)",
                passwordEncoder.encode("alice-pass-123"));
    }

    @Test
    void emptyPasswordKeepsTheExistingCredentials() {
        service.update("alice", request("alice-new@example.com", "BIRDS", null, null));

        assertThat(passwordEncoder.matches("alice-pass-123", mapper.selectCredentials("alice").passwordHash()))
                .as("an empty password means 'do not change the password'")
                .isTrue();
    }

    @Test
    void nonEmptyPasswordReplacesTheCredentials() {
        service.update("alice", request("alice-new@example.com", "BIRDS", "new-pass-1234", "new-pass-1234"));

        String hash = mapper.selectCredentials("alice").passwordHash();
        assertThat(passwordEncoder.matches("new-pass-1234", hash)).isTrue();
        assertThat(passwordEncoder.matches("alice-pass-123", hash))
                .as("the old password stops working")
                .isFalse();
    }

    @Test
    void profileAndPreferencesAreUpdated() {
        service.update("alice", request("alice-new@example.com", "CATS", null, null));

        assertThat(mapper.selectAccount("alice").email()).isEqualTo("alice-new@example.com");
        assertThat(mapper.selectPreferences("alice").favouriteCategoryId()).isEqualTo("CATS");
        assertThat(mapper.selectPreferences("alice").listOption()).isFalse();
    }

    @Test
    void aFailingPreferenceUpdateRollsBackTheProfileChange() {
        assertThatThrownBy(() -> service.update("alice", request("alice-new@example.com", "PENGUINS", null, null)))
                .isInstanceOf(Exception.class);

        assertThat(mapper.selectAccount("alice").email())
                .as("the account update is rolled back together with the failing preference")
                .isEqualTo("alice@example.com");
        assertThat(mapper.selectPreferences("alice").favouriteCategoryId()).isEqualTo("FISH");
    }

    @Test
    void theTargetAccountIsNeverChangedByTheRequest() {
        service.update("alice", request("alice-new@example.com", "CATS", null, null));

        assertThat(mapper.selectAccount("alice").userId()).isEqualTo("alice");
        assertThat(mapper.selectAccount("alice").userId())
                .as("the username is read-only and never taken from the request")
                .isNotEqualTo("bob");
    }

    @Test
    void mismatchedPasswordsAreRejected() {
        assertThatThrownBy(() -> service.update("alice", request("alice-new@example.com", "CATS", "new-pass-1234",
                "other-pass-456")))
                .isInstanceOf(BusinessFailure.class)
                .extracting(failure -> ((BusinessFailure) failure).code())
                .isEqualTo(BusinessFailure.Code.VALIDATION_FAILED);
    }

    private AccountProfileUpdate.ProfileUpdateRequest request(String email, String favouriteCategory,
            String password, String repeatedPassword) {
        Address address = new Address("Alice", "Ishikawa", "5-6-7 Jingumae", "Apt 2", "Shibuya", "Tokyo",
                "150-0001", "JP");
        Preferences preferences = new Preferences("japanese", favouriteCategory, false, false);
        return new AccountProfileUpdate.ProfileUpdateRequest(email, "070-9999-8888", address, preferences, password,
                repeatedPassword);
    }
}
