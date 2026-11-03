package ik.am.jpetstore.identity.web;

import org.springframework.stereotype.Component;

/**
 * T308: identity 显式 DTO/领域转换。
 *
 * <p>只搬运两个最小信息：会话是否已认证与当前用户名，以及 CSRF token 与请求头名称。任何凭据
 * 都不进入响应（plan §5.2、NFR-04）。未登录时用户名为空，而不是空字符串或 "anonymousUser"。</p>
 */
@Component
public class DefaultIdentityAssembler implements IdentityAssembler {

    @Override
    public IdentityDtos.CsrfResponse toCsrfResponse(String token, String headerName) {
        return new IdentityDtos.CsrfResponse(token, headerName);
    }

    @Override
    public IdentityDtos.SessionResponse toSessionResponse(boolean authenticated, String username) {
        return new IdentityDtos.SessionResponse(authenticated, authenticated ? username : null);
    }
}
