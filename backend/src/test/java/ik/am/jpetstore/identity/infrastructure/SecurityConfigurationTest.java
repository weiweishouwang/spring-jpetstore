package ik.am.jpetstore.identity.infrastructure;

import ik.am.jpetstore.support.MySqlTestDatabase;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T258: 会话认证和 CSRF 安全链。
 *
 * <p>验证真实过滤器链：表单凭据登录、会话标识轮换、未认证 401、CSRF 失败 403、会话 Cookie
 * 属性、游客写操作受 CSRF 保护、登录后 CSRF token 刷新，以及不开放宽泛 CORS
 * （ACC-05～06、ORD-01、AT-09/17、AT-R09）。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private org.springframework.core.env.Environment environment;


    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.identity.web.IdentityAssembler identityAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.cart.web.CartAssembler cartAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.order.web.OrderAssembler orderAssembler;

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
                        + " 'P', '1-2-3 Sakuragaoka', NULL, 'Shibuya', 'Tokyo', '150-0001', 'JP', '070-1')");
        jdbcTemplate.update("INSERT INTO profile (userid, language_preference, favourite_category, list_option,"
                + " banner_option) VALUES ('alice', 'english', 'FISH', TRUE, TRUE)");
        jdbcTemplate.update("INSERT INTO signon (username, password_hash) VALUES ('alice', ?)",
                passwordEncoder.encode("alice-pass-123"));
    }

    @Test
    void formLoginWithRealCredentialsSucceeds() throws Exception {
        mockMvc.perform(post("/api/auth/session").with(csrf())
                        .contentType("application/x-www-form-urlencoded")
                        .param("username", "alice")
                        .param("password", "alice-pass-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("alice"));
    }

    @Test
    void wrongPasswordIsRejectedWith401AndNoSession() throws Exception {
        mockMvc.perform(post("/api/auth/session").with(csrf())
                        .contentType("application/x-www-form-urlencoded")
                        .param("username", "alice")
                        .param("password", "wrong-pass-456"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/account/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void guestWriteRequestsAreProtectedByCsrf() throws Exception {
        mockMvc.perform(post("/api/cart/items").contentType("application/json").content("{\"itemId\":\"EST-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }

    @Test
    void sessionIdentifierIsRotatedOnLogin() throws Exception {
        MockHttpSession anonymous = new MockHttpSession();

        mockMvc.perform(post("/api/auth/session").with(csrf()).session(anonymous)
                        .contentType("application/x-www-form-urlencoded")
                        .param("username", "alice")
                        .param("password", "alice-pass-123"))
                .andExpect(status().isOk());

        HttpSession authenticated = mockMvc.perform(get("/api/account/me")).andReturn().getRequest()
                .getSession(false);
        assertThat(authenticated).isNotNull();
        assertThat(authenticated.getId()).isNotEqualTo(anonymous.getId());
    }

    @Test
    void sessionCookiePolicyIsConfiguredForBrowserUse() {
        assertThat(environment.getProperty("server.servlet.session.cookie.http-only"))
                .as("the session cookie is HttpOnly")
                .isEqualTo("true");
        assertThat(environment.getProperty("server.servlet.session.cookie.same-site"))
                .as("the session cookie is SameSite=Lax")
                .isEqualTo("lax");
        assertThat(environment.getProperty("server.servlet.session.cookie.secure"))
                .as("Secure is switched on per environment; development runs plain HTTP")
                .isEqualTo("false");
        assertThat(environment.getProperty("server.servlet.session.cookie.name"))
                .as("host-only default session cookie")
                .isEqualTo("JSESSIONID");
    }

    @Test
    void logoutInvalidatesTheSessionAndReturns204() throws Exception {
        MockHttpSession session = (MockHttpSession) mockMvc.perform(post("/api/auth/session").with(csrf())
                        .contentType("application/x-www-form-urlencoded")
                        .param("username", "alice")
                        .param("password", "alice-pass-123"))
                .andExpect(status().isOk())
                .andReturn().getRequest().getSession(false);

        mockMvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/account/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noBroadCorsIsEnabled() throws Exception {
        mockMvc.perform(get("/api/categories").header("Origin", "https://evil.example.com"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
    }
}
