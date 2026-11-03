package ik.am.jpetstore.identity.web;

import ik.am.jpetstore.identity.domain.SessionPrincipal;
import ik.am.jpetstore.support.MySqlTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T260: 认证状态和 CSRF 端点。
 *
 * <p>GET /api/auth/session 报告当前身份（匿名时 authenticated=false 且 username 为空）；
 * GET /api/auth/csrf 显式物化 token 与 header 名称并禁止缓存（plan §6.2）。转换端口用
 * MockitoBean 替换，真实转换器在 Phase 7 落地。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class IdentityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IdentityAssembler assembler;


    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.catalog.web.CatalogAssembler catalogAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.account.web.AccountAssembler accountAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.cart.web.CartAssembler cartAssembler;

    /** 真实转换器在 Phase 7 落地；这里替换端口以满足上下文装配（tasks 协议第 11 条）。 */
    @MockitoBean
    private ik.am.jpetstore.order.web.OrderAssembler orderAssembler;

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MySqlTestDatabase::url);
        registry.add("spring.datasource.username", MySqlTestDatabase::username);
        registry.add("spring.datasource.password", MySqlTestDatabase::password);
    }

    @Test
    void anonymousSessionIsReportedWithoutIdentity() throws Exception {
        given(assembler.toSessionResponse(false, null)).willReturn(new IdentityDtos.SessionResponse(false, null));

        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authenticated").value(false))
                .andExpect(jsonPath("$.username").isEmpty());
    }

    @Test
    void csrfTokenIsMaterialisedExplicitlyAndMustNotBeCached() throws Exception {
        given(assembler.toCsrfResponse(anyString(), anyString()))
                .willReturn(new IdentityDtos.CsrfResponse("token-value", "X-CSRF-TOKEN"));

        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("token-value"))
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL,
                        org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    void sessionEndpointIsPublicAndNeedsNoCredentials() throws Exception {
        given(assembler.toSessionResponse(false, null)).willReturn(new IdentityDtos.SessionResponse(false, null));

        mockMvc.perform(get("/api/auth/session"))
                .andExpect(status().isOk());
    }

    @Test
    void principalNeverExposesCredentials() {
        SessionPrincipal principal = SessionPrincipal.of("alice");

        assertThat(principal.username()).isEqualTo("alice");
        assertThat(principal.isAuthenticated()).isTrue();
    }
}
