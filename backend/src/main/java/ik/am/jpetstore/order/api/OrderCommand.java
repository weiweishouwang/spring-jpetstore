package ik.am.jpetstore.order.api;

import ik.am.jpetstore.order.domain.OrderModels.OrderSnapshot;
import ik.am.jpetstore.order.domain.OrderRequestModels.IdempotencyKey;

/**
 * T199: 带幂等键的订单提交端口（NFR-03、ORD-09、ORD-10）。
 *
 * <p>客户端只提交 checkoutId 与 Idempotency-Key，不得传归属、价格、状态或明细覆盖服务端
 * 快照。同一 checkout 只能关联一张订单；相同幂等键与相同摘要重放原结果，不同摘要返回冲突。
 * 订单、状态、明细与库存变更必须在同一事务内整体提交。</p>
 */
public interface OrderCommand {

    /**
     * 提交订单。
     *
     * @param userId 当前认证身份解析出的用户
     * @param checkoutId 服务端确认快照标识
     * @param idempotencyKey 客户端生成的幂等键；重试必须复用同一个键
     */
    OrderSnapshot placeOrder(String userId, String checkoutId, IdempotencyKey idempotencyKey);
}
