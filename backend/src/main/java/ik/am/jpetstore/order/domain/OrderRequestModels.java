package ik.am.jpetstore.order.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * T188: 幂等键、摘要和可重放结果类型（NFR-03）。
 *
 * <p>幂等记录与订单创建处于同一事务：相同键与相同摘要重放原响应，不同摘要返回冲突。
 * 可重放结果不得包含 Set-Cookie、CSRF、密码或完整模拟卡号（plan §5.7）。</p>
 */
public final class OrderRequestModels {

    private OrderRequestModels() {
    }

    /** 幂等键：随机 UUID 字符串，最长 128 字符。 */
    public record IdempotencyKey(String value) {

        public IdempotencyKey {
            Objects.requireNonNull(value, "value");
            if (value.isBlank() || value.length() > 128) {
                throw new IllegalArgumentException("幂等键长度必须为 1～128 字符");
            }
        }
    }

    /** 请求摘要：基于 checkoutId 与有业务意义的字段规范化计算，不含关联日志标识。 */
    public record RequestDigest(String value) {

        public RequestDigest {
            Objects.requireNonNull(value, "value");
            if (value.isBlank()) {
                throw new IllegalArgumentException("请求摘要不能为空");
            }
        }
    }

    /** 可重放的响应：状态码、Location 与响应体。 */
    public record ReplayableResponse(int status, String location, String body) {
    }

    /**
     * 幂等请求记录。
     *
     * @param userId  发起人；重放必须包含用户身份，不允许跨用户返回结果
     * @param expiresAt 保留到期时间；删除后不得宣称无限期 exactly-once
     */
    public record OrderRequestRecord(String userId, String operation, IdempotencyKey idempotencyKey,
            RequestDigest digest, String orderId, ReplayableResponse response, Instant createdAt,
            Instant expiresAt) {
    }
}
