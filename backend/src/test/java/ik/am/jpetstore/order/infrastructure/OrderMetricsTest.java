package ik.am.jpetstore.order.infrastructure;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T383: 低基数订单指标。
 *
 * <p>只按语义事件计数，不带订单号、用户或 SKU 标签，避免高基数把指标系统拖垮（plan §9.2、
 * NFR-07）。</p>
 */
class OrderMetricsTest {

    private SimpleMeterRegistry registry;

    private OrderMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new OrderMetrics(registry);
    }

    @Test
    void eachSemanticEventIsCountedOnce() {
        metrics.recordPlaced();
        metrics.recordFailed();
        metrics.recordStockConflict();
        metrics.recordReplay();
        metrics.recordLockFailure();

        assertThat(count("placed")).isEqualTo(1);
        assertThat(count("failed")).isEqualTo(1);
        assertThat(count("stock-conflict")).isEqualTo(1);
        assertThat(count("replay")).isEqualTo(1);
        assertThat(count("lock-failure")).isEqualTo(1);
    }

    @Test
    void aFailureIsNotCountedAsAPlacement() {
        metrics.recordFailed();

        assertThat(count("placed")).isZero();
        assertThat(count("failed")).isEqualTo(1);
    }

    @Test
    void noHighCardinalityTagsAreUsed() {
        metrics.recordPlaced();

        List<Meter> meters = registry.getMeters();
        assertThat(meters).isNotEmpty();
        for (Meter meter : meters) {
            assertThat(meter.getId().getTags())
                    .as("指标只允许低基数语义标签")
                    .allSatisfy(tag -> assertThat(tag.getKey()).isEqualTo("outcome"));
        }
    }

    private double count(String outcome) {
        io.micrometer.core.instrument.Counter counter = registry.find(OrderMetrics.METRIC)
                .tag("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }
}
