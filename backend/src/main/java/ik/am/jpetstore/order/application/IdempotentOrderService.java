package ik.am.jpetstore.order.application;

import ik.am.jpetstore.order.api.OrderCommand;
import ik.am.jpetstore.order.domain.OrderModels.OrderSnapshot;
import ik.am.jpetstore.order.domain.OrderRequestModels.IdempotencyKey;
import ik.am.jpetstore.order.domain.OrderRequestModels.RequestDigest;
import ik.am.jpetstore.order.domain.OrderRequestHasher;
import ik.am.jpetstore.order.infrastructure.OrderMetrics;
import ik.am.jpetstore.order.infrastructure.OrderRequestMapper;
import ik.am.jpetstore.shared.error.BusinessFailure;

import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * T296: 订单幂等外层协调。
 *
 * <p>本层不做业务写入，只负责幂等语义（plan §5.7、NFR-03、D03）：</p>
 * <ul>
 *   <li>首次请求在事务内占用幂等键并建单；失败时占位一起回滚，客户端可以用同一个键重试。</li>
 *   <li>并发相同键的请求只有一个能成功插入，另一个完整回滚后由本层在<strong>新事务</strong>里
 *       读取既有结果；相同摘要重放原结果，不同摘要返回 409。</li>
 *   <li>重放读取必须包含用户身份：不允许跨用户返回结果。</li>
 *   <li>锁等待超时或死锁是可重试故障：返回 503 并要求客户端保留同一个键，不得重新生成键盲目下单。</li>
 * </ul>
 */
@Service
public class IdempotentOrderService implements OrderCommand {

    private final OrderTransactionService transactionService;

    private final OrderRequestMapper orderRequestMapper;

    private final OrderQueryService orderQueryService;

    private final OrderMetrics metrics;

    public IdempotentOrderService(OrderTransactionService transactionService,
            OrderRequestMapper orderRequestMapper, OrderQueryService orderQueryService, OrderMetrics metrics) {
        this.transactionService = transactionService;
        this.orderRequestMapper = orderRequestMapper;
        this.orderQueryService = orderQueryService;
        this.metrics = metrics;
    }

    /**
     * 提交订单（幂等）。
     *
     * <p>本方法刻意不在事务里：首次执行由 {@link OrderTransactionService} 开启事务，冲突后的
     * 重放读取必须在失败事务结束之后的新事务中进行。</p>
     */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Placement placeOrder(String userId, String checkoutId, IdempotencyKey idempotencyKey) {
        RequestDigest digest = OrderRequestHasher.digest(OrderRequestMapper.OPERATION_PLACE_ORDER, checkoutId);
        try {
            OrderTransactionService.OrderPlacement placement = transactionService.placeOrder(userId, checkoutId,
                    idempotencyKey, digest);
            metrics.recordPlaced();
            return new Placement(placement.snapshot(), false);
        } catch (DuplicateKeyException e) {
            Placement placement = replay(userId, idempotencyKey, digest);
            metrics.recordReplay();
            return placement;
        } catch (CannotAcquireLockException | DeadlockLoserDataAccessException e) {
            metrics.recordLockFailure();
            throw new BusinessFailure(BusinessFailure.Code.ORDER_RETRYABLE,
                    "订单正在处理中，请使用同一个幂等键重试");
        } catch (BusinessFailure e) {
            if (e.code() == BusinessFailure.Code.OUT_OF_STOCK) {
                metrics.recordStockConflict();
            } else {
                metrics.recordFailed();
            }
            throw e;
        }
    }

    /** 幂等键已被占用：读取既有结果并按摘要决定重放还是冲突。 */
    private Placement replay(String userId, IdempotencyKey idempotencyKey, RequestDigest digest) {
        OrderRequestMapper.OrderRequestRow record = orderRequestMapper.selectOrderRequest(userId,
                OrderRequestMapper.OPERATION_PLACE_ORDER, idempotencyKey.value());
        if (record == null || record.orderId() == null) {
            throw new BusinessFailure(BusinessFailure.Code.ORDER_RETRYABLE,
                    "上一次下单没有完成，请使用同一个幂等键重试");
        }
        if (!record.requestHash().equals(digest.value())) {
            throw new BusinessFailure(BusinessFailure.Code.IDEMPOTENCY_CONFLICT,
                    "同一个幂等键不能用于不同的请求");
        }
        OrderSnapshot snapshot = orderQueryService.findOwnOrder(userId, String.valueOf(record.orderId()))
                .orElseThrow(() -> new BusinessFailure(BusinessFailure.Code.ORDER_NOT_FOUND, "订单不存在"));
        return new Placement(snapshot, true);
    }
}
