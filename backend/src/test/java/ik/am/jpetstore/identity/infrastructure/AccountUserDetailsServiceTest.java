package ik.am.jpetstore.identity.infrastructure;

import ik.am.jpetstore.account.infrastructure.AccountMapper;
import ik.am.jpetstore.support.MySqlTestDatabase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T254: 真实凭据认证适配。
 *
 * <p>用户存在时返回带哈希的凭据；用户不存在时明确失败；停用账户不能登录且失败方式与未知用户
 * 一致（不泄露账户是否存在）；缺少横幅不影响认证；principal 不缓存密码以外的账户资料
 * （ACC-05、D01、Q10）。</p>
 */
@SpringBootTest
class AccountUserDetailsServiceTest {

    @Autowired
    private AccountUserDetailsService service;

    @Autowired
    private AccountMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.identity.web.IdentityAssembler identityAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.catalog.web.CatalogAssembler catalogAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.account.web.AccountAssembler accountAssembler;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlTestDatabase::url);
        registry.add("spring.datasource.username", MySqlTestDatabase::username);
        registry.add("spring.datasource.password", MySqlTestDatabase::password);
    }

    @BeforeEach
    void seedAccounts() {
        jdbcTemplate.update("DELETE FROM account");
        jdbcTemplate.update("DELETE FROM profile");
        jdbcTemplate.update("DELETE FROM signon");
        jdbcTemplate.update("INSERT INTO account (userid, email, first_name, last_name, status, addr1, addr2, city,"
                        + " state, zip, country, phone) VALUES ('alice', 'alice@example.com', 'Alice', 'Ishikawa',"
                        + " 'P', '1-2-3 Sakuragaoka', NULL, 'Shibuya', 'Tokyo', '150-0001', 'JP', '070-1'),"
                        + " ('mallory', 'mallory@example.com', 'Mallory', 'Doe', 'D', '9-9-9 Locked', NULL, 'Nowhere',"
                        + " 'NA', '00000', 'US', '000-0')");
        jdbcTemplate.update("INSERT INTO signon (username, password_hash) VALUES ('alice', '$2a$10$alice'),"
                + " ('mallory', '$2a$10$mallory')");
    }

    @Test
    void loadsTheStoredCredentialsForAnExistingUser() {
        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getUsername()).isEqualTo("alice");
        assertThat(details.getPassword()).isNotBlank();
        assertThat(details.getAuthorities()).extracting(GrantedAuthority::getAuthority).contains("ROLE_USER");
    }

    @Test
    void unknownUserIsRejected() {
        assertThatThrownBy(() -> service.loadUserByUsername("nobody"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void disabledAccountCannotAuthenticateAndIsIndistinguishableFromAnUnknownUser() {
        UsernameNotFoundException disabled = null;
        try {
            service.loadUserByUsername("mallory");
        } catch (UsernameNotFoundException e) {
            disabled = e;
        }
        UsernameNotFoundException unknown = null;
        try {
            service.loadUserByUsername("nobody");
        } catch (UsernameNotFoundException e) {
            unknown = e;
        }

        assertThat(disabled).as("a disabled account must not authenticate (Q10)").isNotNull();
        assertThat(disabled.getMessage())
                .as("the failure must not reveal that the account exists")
                .isEqualTo(unknown.getMessage());
    }

    @Test
    void authenticationDoesNotDependOnBannerData() {
        jdbcTemplate.update("DELETE FROM bannerdata");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bannerdata", Integer.class)).isZero();

        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details.getUsername()).isEqualTo("alice");
    }

    @Test
    void principalCarriesNoAccountProfile() {
        UserDetails details = service.loadUserByUsername("alice");

        assertThat(details)
                .as("the principal holds the identifier and the hash only")
                .isNotInstanceOf(AccountMapper.AccountRow.class);
        assertThat(details.getAuthorities()).hasSize(1);
    }

    @Test
    void theServiceIsUsableAsASpringUserDetailsService() {
        assertThat(service).isInstanceOf(UserDetailsService.class);
        assertThat(mapper.selectAccount("alice")).isNotNull();
    }
}
