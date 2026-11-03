package ik.am.jpetstore.identity.web;

/**
 * T206: identity 显式 DTO/领域转换接口。
 *
 * <p>只声明签名；真实转换器在 Phase 7 落地。</p>
 */
public interface IdentityAssembler {

    /** CSRF token 与请求头名称。 */
    IdentityDtos.CsrfResponse toCsrfResponse(String token, String headerName);

    /**
     * 当前会话摘要。
     *
     * @param username 未登录时为 null
     */
    IdentityDtos.SessionResponse toSessionResponse(boolean authenticated, String username);
}
