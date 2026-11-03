package ik.am.jpetstore.order.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;

/**
 * T278: OrderRequest Mapper签名与类型约定。
 *
 * <p>幂等记录与订单创建处于同一事务：首次请求写入占位、建单、记录结果一起提交，失败时占位
 * 一起回滚（plan §5.7、NFR-03）。唯一键是 (user_id, operation, idempotency_key)，因此并发相同
 * 键的请求只有一个能成功插入，另一个必须完整回滚后由外层在新事务读取既有结果。</p>
 *
 * <p>保存的响应可重放且不含 Set-Cookie、CSRF、密码或完整模拟卡号（NFR-04）。</p>
 */
@Mapper
public interface OrderRequestMapper {

    /** 下单操作标识。 */
    String OPERATION_PLACE_ORDER = "PLACE_ORDER";

    /** order_request 表行。 */
    record OrderRequestRow(Long id, String userId, String operation, String idempotencyKey, String requestHash,
            Long orderId, Integer responseStatus, String responseLocation, String responseBody,
            Instant createdAt, Instant expiresAt) {
    }

    /**
     * 写入幂等占位。
     *
     * @return 影响行数；唯一键冲突时抛出异常，由外层在新事务读取既有结果
     */
    int insertOrderRequest(OrderRequestRow row);

    /** 按 (user, operation, key) 读取幂等记录；不同用户互相隔离。 */
    OrderRequestRow selectOrderRequest(@Param("userId") String userId, @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey);

    /**
     * 回填订单号与可重放响应。
     *
     * @return 影响行数；找不到对应记录时为 0
     */
    int completeOrderRequest(@Param("userId") String userId, @Param("operation") String operation,
            @Param("idempotencyKey") String idempotencyKey, @Param("orderId") Long orderId,
            @Param("responseStatus") int responseStatus, @Param("responseLocation") String responseLocation,
            @Param("responseBody") String responseBody);
}
