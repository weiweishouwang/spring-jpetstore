package ik.am.jpetstore.identity.web;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T307: identity 显式 DTO/领域转换。
 *
 * <p>验证会话摘要只报告身份状态、不泄露任何凭据，未登录时用户名为空，CSRF 响应只含 token 与
 * header 名称（NFR-04、plan §5.2）。</p>
 */
class DefaultIdentityAssemblerTest {

    private final DefaultIdentityAssembler assembler = new DefaultIdentityAssembler();

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void theSessionResponseReportsTheAuthenticationState() {
        IdentityDtos.SessionResponse response = assembler.toSessionResponse(true, "alice");

        assertThat(response.authenticated()).isTrue();
        assertThat(response.username()).isEqualTo("alice");
    }

    @Test
    void anAnonymousSessionHasNoUsername() {
        IdentityDtos.SessionResponse response = assembler.toSessionResponse(false, null);

        assertThat(response.authenticated()).isFalse();
        assertThat(response.username()).isNull();
    }

    @Test
    void theSessionResponseNeverCarriesCredentials() {
        String json = mapper.writeValueAsString(assembler.toSessionResponse(true, "alice"));

        assertThat(json).doesNotContain("password").doesNotContain("hash");
    }

    @Test
    void theCsrfResponseCarriesTheTokenAndTheHeaderName() {
        IdentityDtos.CsrfResponse response = assembler.toCsrfResponse("token-value", "X-CSRF-TOKEN");

        assertThat(response.token()).isEqualTo("token-value");
        assertThat(response.headerName()).isEqualTo("X-CSRF-TOKEN");
    }
}
