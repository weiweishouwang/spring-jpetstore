package ik.am.jpetstore.identity.application;

import ik.am.jpetstore.identity.domain.SessionPrincipal;
import ik.am.jpetstore.shared.error.BusinessFailure;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * T239: 当前身份读取与未认证处理。
 *
 * <p>身份只从 {@link SecurityContextHolder} 解析，本类型不提供任何接受请求用户名的方法——
 * 归属永远来自当前认证身份（ORD-01、NFR-02）。匿名会话（含 Spring 匿名令牌）得到匿名主体；
 * 需要登录的操作通过 {@link #requireUsername()} 显式失败（401）。</p>
 */
@Component
public class CurrentUser {

    /** 当前会话身份；匿名时 username 为 null。 */
    public SessionPrincipal current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return SessionPrincipal.anonymous();
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof SessionPrincipal sessionPrincipal) {
            return sessionPrincipal;
        }
        if (principal instanceof UserDetails userDetails) {
            return SessionPrincipal.of(userDetails.getUsername());
        }
        if (principal instanceof String username && !"anonymousUser".equals(username)) {
            return SessionPrincipal.of(username);
        }
        throw new BusinessFailure(BusinessFailure.Code.INTERNAL_ERROR, "无法识别的认证主体类型");
    }

    /** 需要登录的操作使用；未登录抛出 AUTHENTICATION_REQUIRED（401）。 */
    public String requireUsername() {
        SessionPrincipal principal = current();
        if (!principal.isAuthenticated()) {
            throw new BusinessFailure(BusinessFailure.Code.AUTHENTICATION_REQUIRED, "需要登录");
        }
        return principal.username();
    }
}
