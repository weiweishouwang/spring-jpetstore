package ik.am.jpetstore.order.domain;

import ik.am.jpetstore.order.domain.OrderRequestModels.RequestDigest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * T292: 稳定下单摘要。
 *
 * <p>幂等摘要只由有业务意义的字段决定：操作标识与确认快照标识。关联请求标识（requestId）、
 * 时间戳等与业务无关的量不参与计算，因此同一业务内容在任何时刻都得到同一摘要——这正是重放
 * 判定的前提（plan §5.7、D03、NFR-03）。</p>
 *
 * <p>规范化规则固定：字段去首尾空白，用不可见的分隔符拼接后再取 SHA-256，避免拼接歧义。
 * 摘要本身是不可逆的十六进制串，不包含任何明文业务内容（NFR-04）。</p>
 */
public final class OrderRequestHasher {

    /** 字段分隔符：不出现在任何业务标识里，保证拼接无歧义。 */
    private static final char SEPARATOR = '\u0000';

    private OrderRequestHasher() {
    }

    /**
     * 计算下单请求摘要。
     *
     * @param operation 操作标识，例如 PLACE_ORDER
     * @param checkoutId 服务端确认快照标识
     * @return 稳定的十六进制摘要
     */
    public static RequestDigest digest(String operation, String checkoutId) {
        String canonical = canonical(operation) + SEPARATOR + canonical(checkoutId);
        return new RequestDigest(sha256Hex(canonical));
    }

    private static String canonical(String value) {
        return value == null ? "" : value.trim();
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }
}
