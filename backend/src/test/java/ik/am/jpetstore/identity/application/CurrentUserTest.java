package ik.am.jpetstore.identity.application;

import ik.am.jpetstore.identity.domain.SessionPrincipal;
import ik.am.jpetstore.shared.error.BusinessFailure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T238: 当前身份读取与未认证处理。
 *
 * <p>身份只来自安全上下文：匿名（含 Spring 匿名令牌）返回匿名主体；认证主体解析出用户名；
 * 无法识别的主体类型明确报错，而不是静默降级。任何请求字段都替换不了当前身份（ORD-01、
 * NFR-02）——本类型不提供接受请求用户名的方法。</p>
 */
class CurrentUserTest {

    private final CurrentUser currentUser = new CurrentUser();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void emptySecurityContextIsAnonymous() {
        assertThat(currentUser.current()).isEqualTo(SessionPrincipal.anonymous());
        assertThat(currentUser.current().isAuthenticated()).isFalse();
    }

    @Test
    void springAnonymousTokenIsAnonymous() {
        AnonymousAuthenticationToken token = new AnonymousAuthenticationToken("key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(token);

        assertThat(currentUser.current().isAuthenticated()).isFalse();
    }

    @Test
    void sessionPrincipalTokenResolvesTheUsername() {
        authenticate(new UsernamePasswordAuthenticationToken(SessionPrincipal.of("alice"), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        assertThat(currentUser.current()).isEqualTo(SessionPrincipal.of("alice"));
    }

    @Test
    void userDetailsPrincipalResolvesTheUsername() {
        UserDetails userDetails = User.withUsername("bob").password("hash").authorities("ROLE_USER").build();
        authenticate(new UsernamePasswordAuthenticationToken(userDetails, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        assertThat(currentUser.current()).isEqualTo(SessionPrincipal.of("bob"));
    }

    @Test
    void unrecognisedPrincipalTypeFailsExplicitly() {
        authenticate(new UsernamePasswordAuthenticationToken(42, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        assertThatThrownBy(() -> currentUser.current())
                .isInstanceOf(BusinessFailure.class)
                .extracting(failure -> ((BusinessFailure) failure).code())
                .isEqualTo(BusinessFailure.Code.INTERNAL_ERROR);
    }

    @Test
    void requireUsernameFailsWith401WhenAnonymous() {
        assertThatThrownBy(currentUser::requireUsername)
                .isInstanceOf(BusinessFailure.class)
                .extracting(failure -> ((BusinessFailure) failure).status())
                .isEqualTo(401);
    }

    @Test
    void requireUsernameReturnsTheAuthenticatedUsername() {
        authenticate(new UsernamePasswordAuthenticationToken(SessionPrincipal.of("alice"), null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        assertThat(currentUser.requireUsername()).isEqualTo("alice");
    }

    private void authenticate(Authentication authentication) {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
