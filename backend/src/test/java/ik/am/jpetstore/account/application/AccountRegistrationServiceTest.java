package ik.am.jpetstore.account.application;

import ik.am.jpetstore.account.api.AccountRegistration;
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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T250: 账户原子注册服务。
 *
 * <p>在真实事务代理下验证：账户、偏好与凭据三表一起成功；最后一步凭据写入失败时整体回滚；
 * 重复用户名不覆盖既有账户；注册不建立登录身份（ACC-01～04、AT-01/02/15/16）。</p>
 */
@SpringBootTest
class AccountRegistrationServiceTest {

    @Autowired
    private AccountRegistrationService service;

    @Autowired
    private AccountMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.identity.web.IdentityAssembler identityAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.cart.web.CartAssembler cartAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.order.web.OrderAssembler orderAssembler;

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
    void cleanAccounts() {
        jdbcTemplate.update("DELETE FROM account");
        jdbcTemplate.update("DELETE FROM profile");
        jdbcTemplate.update("DELETE FROM signon");
    }

    @Test
    void registrationCreatesAllThreeTables() {
        service.register(request("newbie", "alice-pass-123", "alice-pass-123"));

        assertThat(mapper.selectAccount("newbie")).isNotNull();
        assertThat(mapper.selectPreferences("newbie")).isNotNull();
        assertThat(mapper.selectCredentials("newbie").passwordHash()).startsWith("$2a$");
        assertThat(mapper.selectAccount("newbie").email()).isEqualTo("newbie@example.com");
        assertThat(mapper.selectPreferences("newbie").favouriteCategoryId()).isEqualTo("BIRDS");
    }

    @Test
    void duplicateUsernameIsRejectedAndTheExistingAccountSurvives() {
        service.register(request("bob", "bob-pass-1234", "bob-pass-1234"));
        String originalHash = mapper.selectCredentials("bob").passwordHash();

        assertThatThrownBy(() -> service.register(request("bob", "other-pass-1234", "other-pass-1234")))
                .isInstanceOf(BusinessFailure.class)
                .extracting(failure -> ((BusinessFailure) failure).code())
                .isEqualTo(BusinessFailure.Code.USERNAME_CONFLICT);

        assertThat(mapper.selectCredentials("bob").passwordHash())
                .as("the existing credentials are never overwritten")
                .isEqualTo(originalHash);
        assertThat(mapper.selectAccount("bob").email()).isEqualTo("bob@example.com");
    }

    @Test
    void failureOnTheLastCredentialsWriteRollsBackTheWholeRegistration() {
        jdbcTemplate.update("INSERT INTO signon (username, password_hash) VALUES ('ghost', '$2a$10$existing')");

        assertThatThrownBy(() -> service.register(request("ghost", "ghost-pass-1234", "ghost-pass-1234")))
                .isInstanceOf(BusinessFailure.class)
                .extracting(failure -> ((BusinessFailure) failure).code())
                .isEqualTo(BusinessFailure.Code.USERNAME_CONFLICT);

        assertThat(mapper.selectAccount("ghost"))
                .as("the account row written before the failure is rolled back")
                .isNull();
        assertThat(mapper.selectPreferences("ghost")).isNull();
        assertThat(mapper.selectCredentials("ghost").passwordHash()).isEqualTo("$2a$10$existing");
    }

    @Test
    void mismatchedPasswordsAreRejectedBeforeAnythingIsWritten() {
        assertThatThrownBy(() -> service.register(request("mismatch", "alice-pass-123", "different-pass-456")))
                .isInstanceOf(BusinessFailure.class)
                .extracting(failure -> ((BusinessFailure) failure).code())
                .isEqualTo(BusinessFailure.Code.VALIDATION_FAILED);

        assertThat(mapper.selectAccount("mismatch")).isNull();
        assertThat(mapper.selectCredentials("mismatch")).isNull();
    }

    @Test
    void usernameIsTrimmedAndBlankNamesAreRejected() {
        service.register(request("  spaced  ", "alice-pass-123", "alice-pass-123"));

        assertThat(mapper.selectAccount("spaced")).isNotNull();

        assertThatThrownBy(() -> service.register(request("   ", "alice-pass-123", "alice-pass-123")))
                .isInstanceOf(BusinessFailure.class);
        assertThat(mapper.selectAccount("   ")).isNull();
    }

    @Test
    void passwordLongerThanTheByteLimitIsRejected() {
        assertThatThrownBy(() -> service.register(request("toolong", "x".repeat(73), "x".repeat(73))))
                .isInstanceOf(BusinessFailure.class)
                .extracting(failure -> ((BusinessFailure) failure).code())
                .isEqualTo(BusinessFailure.Code.VALIDATION_FAILED);

        assertThat(mapper.selectAccount("toolong")).isNull();
    }

    private AccountRegistration.RegistrationRequest request(String username, String password,
            String repeatedPassword) {
        Address address = new Address("Alice", "Ishikawa", "1-2-3 Sakuragaoka", null, "Shibuya", "Tokyo",
                "150-0001", "JP");
        Preferences preferences = new Preferences("english", "BIRDS", true, false);
        return new AccountRegistration.RegistrationRequest(username, password, repeatedPassword,
                username + "@example.com", "070-1111-2222", address, preferences);
    }
}
