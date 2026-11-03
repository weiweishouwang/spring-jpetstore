package ik.am.jpetstore.cart.infrastructure;

import ik.am.jpetstore.cart.domain.CartModels.CartOwner;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * T282: 会话购物车持有权绑定。
 *
 * <p>游客购物车不按 Session ID 定位，而是按会话内的随机绑定标识定位：业务表里只保存该标识的
 * SHA-256 摘要，原始 Session ID 与绑定标识都不出内存、不落日志、不进数据库（NFR-04）。用会话
 * 内的绑定标识而不是 Session ID 本身，是因为登录会轮换 Session ID（会话固定攻击防护），而绑定
 * 标识保存在会话属性里，轮换后仍然存在——因此登录后仍能找到同一个会话里的游客车（Q08）。</p>
 *
 * <p>接口不接受客户端指定的任意 cartId：持有权只能由当前请求解析，购物车不是可转发的凭据
 * （NFR-02）。本类型只解析持有权，不创建任何业务行。</p>
 */
@Component
public class SessionCartBinding {

    /** 会话内保存绑定标识的属性名。 */
    static final String BINDING_ATTRIBUTE = "jpetstore.cart.binding";

    /**
     * 当前会话的绑定哈希。
     *
     * <p>会话还没有绑定标识时生成一个随机标识并放入会话；哈希是对该标识的 SHA-256 摘要，不可
     * 逆推标识本身。</p>
     */
    public String bindingHash(HttpServletRequest request) {
        HttpSession session = request.getSession(true);
        Object token = session.getAttribute(BINDING_ATTRIBUTE);
        String existing = token instanceof String value ? value : null;
        if (existing == null || existing.isBlank()) {
            existing = UUID.randomUUID().toString();
            session.setAttribute(BINDING_ATTRIBUTE, existing);
        }
        return sha256Hex(existing);
    }

    /**
     * 解析当前请求的购物车持有权。
     *
     * @param username 当前认证身份解析出的用户；匿名时为 null
     * @return 登录用户是账号持有权（携带当前会话绑定哈希以便登录合并），匿名是游客持有权
     */
    public CartOwner currentOwner(HttpServletRequest request, String username) {
        String hash = bindingHash(request);
        if (username == null || username.isBlank()) {
            return CartOwner.anonymous(hash);
        }
        return CartOwner.accountInSession(username, hash);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
