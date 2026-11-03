package ik.am.jpetstore.order.infrastructure;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

/**
 * T384: 低基数订单指标。
 *
 * <p>只按语义事件计数：成交成功、成交失败、库存冲突、幂等重放与锁等待失败。标签只有
 * {@code outcome}，绝不带订单号、用户或 SKU——高基数标签会让指标系统失去意义（plan §9.2、
 * NFR-07）。</p>
 */
@Component
public class OrderMetrics {

    /** 指标名。 */
    public static final String METRIC = "jpetstore.orders";

    /** 成交成功。 */
    public static final String OUTCOME_PLACED = "placed";

    /** 成交失败。 */
    public static final String OUTCOME_FAILED = "failed";

    /** 库存冲突。 */
    public static final String OUTCOME_STOCK_CONFLICT = "stock-conflict";

    /** 幂等重放。 */
    public static final String OUTCOME_REPLAY = "replay";

    /** 锁等待失败（可重试）。 */
    public static final String OUTCOME_LOCK_FAILURE = "lock-failure";

    private final MeterRegistry registry;

    public OrderMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** 成交成功。 */
    public void recordPlaced() {
        counter(OUTCOME_PLACED).increment();
    }

    /** 成交失败（不含库存冲突与锁失败，它们各有语义）。 */
    public void recordFailed() {
        counter(OUTCOME_FAILED).increment();
    }

    /** 库存不足导致的冲突。 */
    public void recordStockConflict() {
        counter(OUTCOME_STOCK_CONFLICT).increment();
    }

    /** 幂等重放：返回了原结果，没有新建订单。 */
    public void recordReplay() {
        counter(OUTCOME_REPLAY).increment();
    }

    /** 锁等待失败或死锁：可重试，客户端必须复用同一个幂等键。 */
    public void recordLockFailure() {
        counter(OUTCOME_LOCK_FAILURE).increment();
    }

    private io.micrometer.core.instrument.Counter counter(String outcome) {
        return io.micrometer.core.instrument.Counter.builder(METRIC)
                .tag("outcome", outcome)
                .description("订单语义事件计数")
                .register(registry);
    }
}
