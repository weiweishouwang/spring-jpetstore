package ik.am.jpetstore.identity.web;

import java.io.IOException;

import ik.am.jpetstore.identity.domain.SessionPrincipal;
import ik.am.jpetstore.shared.error.BusinessFailure;
import ik.am.jpetstore.shared.web.ProblemDetailsWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/**
 * T257: 安全过滤器 JSON 处理器。
 *
 * <p>过滤链不返回 HTML：认证失败 401、CSRF 与权限拒绝 403、登录成功 200、退出 204，全部使用与
 * MVC 相同的 Problem Details 序列化（plan §5.2）。失败响应不区分用户不存在与密码错误，也不
 * 回显凭据或异常细节（NFR-04、NFR-05）。</p>
 */
@Component
public class SecurityJsonHandlers {

    private final ProblemDetailsWriter writer;

    public SecurityJsonHandlers(ProblemDetailsWriter writer) {
        this.writer = writer;
    }

    /** 登录成功：返回当前身份摘要。 */
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            SessionPrincipal principal = SessionPrincipal.of(authentication.getName());
            response.getWriter().write("{\"authenticated\":true,\"username\":\"" + principal.username() + "\"}");
        };
    }

    /** 退出成功：204，无响应体。 */
    public LogoutSuccessHandler logoutSuccessHandler() {
        return (request, response, authentication) -> {
            SecurityContextHolder.clearContext();
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
        };
    }

    /** 登录失败：401，提示保持通用。 */
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return (request, response, exception) -> authenticationEntryPoint().commence(request, response, exception);
    }

    /** 未认证访问受保护资源：401。 */
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, exception) -> {
            BusinessFailure failure = new BusinessFailure(BusinessFailure.Code.AUTHENTICATION_REQUIRED,
                    "需要登录或凭据不正确");
            write(response, failure);
        };
    }

    /** 权限或 CSRF 拒绝：403。 */
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, exception) -> {
            BusinessFailure.Code code = exception instanceof CsrfException
                    ? BusinessFailure.Code.CSRF_INVALID
                    : BusinessFailure.Code.FORBIDDEN;
            BusinessFailure failure = new BusinessFailure(code,
                    code == BusinessFailure.Code.CSRF_INVALID ? "CSRF 校验失败" : "没有操作权限");
            write(response, failure);
        };
    }

    private void write(HttpServletResponse response, BusinessFailure failure) {
        try {
            writer.write(response, failure, "security-chain");
        } catch (IOException e) {
            response.setStatus(failure.status());
        }
    }
}
