package ik.am.jpetstore.identity.web;

import ik.am.jpetstore.identity.application.CurrentUser;
import ik.am.jpetstore.identity.domain.SessionPrincipal;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * T261: 认证状态和 CSRF 端点。
 *
 * <p>GET /api/auth/session 报告当前会话身份（不含任何凭据）；GET /api/auth/csrf 显式物化
 * deferred CSRF token 与请求头名称并禁止缓存，token 只保留在前端内存中（plan §5.2、§6.2）。
 * 这两个端点由安全过滤器链处理，不参与 API 版本语义。</p>
 */
@RestController
public class IdentityController {

    private final IdentityAssembler assembler;

    private final CurrentUser currentUser;

    public IdentityController(IdentityAssembler assembler, CurrentUser currentUser) {
        this.assembler = assembler;
        this.currentUser = currentUser;
    }

    /** 当前会话状态：未登录时 authenticated=false 且 username 为空。 */
    @GetMapping("/api/auth/session")
    public IdentityDtos.SessionResponse session() {
        SessionPrincipal principal = currentUser.current();
        return assembler.toSessionResponse(principal.isAuthenticated(), principal.username());
    }

    /** 显式物化 CSRF token；响应不得被缓存。 */
    @GetMapping("/api/auth/csrf")
    public ResponseEntity<IdentityDtos.CsrfResponse> csrf(CsrfToken token) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(assembler.toCsrfResponse(token.getToken(), token.getHeaderName()));
    }
}
