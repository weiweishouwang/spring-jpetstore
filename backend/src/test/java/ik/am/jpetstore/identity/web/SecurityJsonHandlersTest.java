package ik.am.jpetstore.identity.web;

import ik.am.jpetstore.shared.web.ProblemDetailsWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.MissingCsrfTokenException;
import org.springframework.security.web.access.AccessDeniedHandler;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T256: 安全过滤器 JSON 处理器。
 *
 * <p>登录成功 200、退出 204、认证失败 401、CSRF/权限拒绝 403，全部输出统一的 Problem Details；
 * 任何响应都不回显密码、异常细节或凭据（plan §5.2、NFR-04～05）。</p>
 */
class SecurityJsonHandlersTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private SecurityJsonHandlers handlers;

    @BeforeEach
    void setUp() {
        handlers = new SecurityJsonHandlers(new ProblemDetailsWriter(objectMapper));
    }

    @Test
    void successfulLoginWrites200WithTheCurrentIdentity() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Authentication authentication = authenticationOf("alice");

        handlers.authenticationSuccessHandler().onAuthenticationSuccess(request, response, authentication);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(objectMapper.readTree(response.getContentAsString()).get("username").asString()).isEqualTo("alice");
    }

    @Test
    void logoutWrites204WithoutABody() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.logoutSuccessHandler().onLogoutSuccess(request, response, authenticationOf("alice"));

        assertThat(response.getStatus()).isEqualTo(204);
        assertThat(response.getContentAsString()).isEmpty();
    }

    @Test
    void authenticationFailureWrites401ProblemDetails() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.authenticationEntryPoint().commence(request, response,
                new org.springframework.security.authentication.BadCredentialsException("密码不正确"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(objectMapper.readTree(response.getContentAsString()).get("code").asString())
                .isEqualTo("AUTHENTICATION_REQUIRED");
    }

    @Test
    void authenticationFailureNeverEchoesTheCredentialOrTheCause() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("j_password", "super-secret-password");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.authenticationEntryPoint().commence(request, response,
                new org.springframework.security.authentication.BadCredentialsException("密码不正确"));

        assertThat(response.getContentAsString())
                .doesNotContain("super-secret-password")
                .doesNotContain("密码不正确")
                .doesNotContain("BadCredentialsException");
    }

    @Test
    void accessDeniedWrites403ProblemDetails() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handlers.accessDeniedHandler().handle(request, response, new MissingCsrfTokenException("missing"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(objectMapper.readTree(response.getContentAsString()).get("code").asString())
                .isEqualTo("CSRF_INVALID");
    }

    @Test
    void handlersAreWiredToTheExpectedSpringContracts() {
        assertThat(handlers.authenticationSuccessHandler()).isInstanceOf(AuthenticationSuccessHandler.class);
        assertThat(handlers.logoutSuccessHandler()).isInstanceOf(LogoutSuccessHandler.class);
        assertThat(handlers.authenticationFailureHandler()).isInstanceOf(AuthenticationFailureHandler.class);
        assertThat(handlers.accessDeniedHandler()).isInstanceOf(AccessDeniedHandler.class);
    }

    private Authentication authenticationOf(String username) {
        UserDetails userDetails = User.withUsername(username).password("hash").authorities("ROLE_USER").build();
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(userDetails, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
