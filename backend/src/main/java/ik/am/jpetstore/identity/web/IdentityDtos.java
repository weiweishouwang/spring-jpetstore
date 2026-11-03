package ik.am.jpetstore.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * T205: identity HTTP 请求/响应契约及验证。
 *
 * <p>对齐 docs/contracts/openapi.yaml：CSRF token 与 header 名称、当前会话的最小身份信息。
 * 任何响应都不得携带凭据（plan §5.2、NFR-04）。</p>
 */
public final class IdentityDtos {

    private IdentityDtos() {
    }

    /** CSRF token；token 只保留在前端内存中。 */
    public record CsrfResponse(@NotBlank @Size(max = 2048) String token, @NotBlank String headerName) {
    }

    /**
     * 当前会话状态。
     *
     * @param username 未登录时为 null（Q13：长度 1～25，大小写敏感）
     */
    public record SessionResponse(boolean authenticated, @Size(max = 25) String username) {
    }
}
