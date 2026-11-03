package ik.am.jpetstore.shared.error;

import java.util.List;
import java.util.Objects;

/**
 * T216: 带稳定错误码的业务失败类型。
 *
 * <p>业务失败使用运行时异常，以便在订单事务中触发回滚（plan §5.6）；HTTP 状态与错误码由
 * 这里集中定义，映射为 RFC 9457 Problem Details（plan §6.4）。错误消息不得包含 SQL、异常栈、
 * 密码、假卡号或地址原文（NFR-04、NFR-05）。</p>
 */
public final class BusinessFailure extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 稳定错误码及其 HTTP 状态。 */
    public enum Code {

        VALIDATION_FAILED(400),
        MALFORMED_JSON(400),
        API_VERSION_UNSUPPORTED(400),
        AUTHENTICATION_REQUIRED(401),
        INVALID_CREDENTIALS(401),
        FORBIDDEN(403),
        CSRF_INVALID(403),
        ACCOUNT_DISABLED(403),
        RESOURCE_NOT_FOUND(404),
        ORDER_NOT_FOUND(404),
        USERNAME_CONFLICT(409),
        CART_VERSION_CONFLICT(409),
        CHECKOUT_STALE(409),
        CHECKOUT_ALREADY_SUBMITTED(409),
        IDEMPOTENCY_CONFLICT(409),
        OUT_OF_STOCK(409),
        ITEM_UNAVAILABLE(409),
        INTERNAL_ERROR(500),
        DATABASE_UNAVAILABLE(503),
        ORDER_RETRYABLE(503);

        private final int status;

        Code(int status) {
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    /**
     * 字段级校验信息。
     *
     * @param field   出错的字段名
     * @param message 说明；不得包含被拒绝的原值（NFR-04）
     */
    public record Violation(String field, String message) {
    }

    private final Code code;

    private final List<Violation> violations;

    public BusinessFailure(Code code, String message) {
        this(code, message, List.of());
    }

    public BusinessFailure(Code code, String message, List<Violation> violations) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.violations = List.copyOf(violations);
    }

    public Code code() {
        return code;
    }

    public int status() {
        return code.status();
    }

    public List<Violation> violations() {
        return violations;
    }
}
