package ik.am.jpetstore.identity.domain;

import java.util.Objects;

/**
 * T237: 不含密码的会话身份。
 *
 * <p>会话主体只携带用户名，绝不携带密码、哈希或整份账户资料（plan §5.1）。匿名会话以
 * {@code null} 用户名表达。登录后的身份由安全上下文解析，不接受请求字段替换。</p>
 *
 * @param username 用户名；未登录时为 null（Q13：大小写敏感）
 */
public record SessionPrincipal(String username) {

    public SessionPrincipal {
        if (username != null) {
            Objects.requireNonNull(username, "username");
            if (username.isBlank()) {
                throw new IllegalArgumentException("用户名不能为空白");
            }
        }
    }

    public static SessionPrincipal anonymous() {
        return new SessionPrincipal(null);
    }

    public static SessionPrincipal of(String username) {
        return new SessionPrincipal(Objects.requireNonNull(username, "username"));
    }

    public boolean isAuthenticated() {
        return username != null;
    }
}
