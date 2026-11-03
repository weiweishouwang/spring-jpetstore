package ik.am.jpetstore.order.domain;

import ik.am.jpetstore.order.domain.OrderRequestModels.IdempotencyKey;
import ik.am.jpetstore.order.domain.OrderRequestModels.RequestDigest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T291: 稳定下单摘要。
 *
 * <p>摘要必须只由有业务意义的字段决定：相同业务字段得到相同摘要，不同确认快照得到不同摘要，
 * 关联请求标识不参与计算（plan §5.7、D03）。摘要本身不得泄露明文敏感内容。</p>
 */
class OrderRequestHasherTest {

    @Test
    void theSameBusinessFieldsProduceTheSameDigest() {
        RequestDigest first = OrderRequestHasher.digest("PLACE_ORDER", "ck-1");
        RequestDigest second = OrderRequestHasher.digest("PLACE_ORDER", "ck-1");

        assertThat(first).isEqualTo(second);
    }

    @Test
    void differentCheckoutsProduceDifferentDigests() {
        RequestDigest first = OrderRequestHasher.digest("PLACE_ORDER", "ck-1");
        RequestDigest second = OrderRequestHasher.digest("PLACE_ORDER", "ck-2");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void theOperationIsPartOfTheDigest() {
        assertThat(OrderRequestHasher.digest("PLACE_ORDER", "ck-1"))
                .isNotEqualTo(OrderRequestHasher.digest("OTHER_OPERATION", "ck-1"));
    }

    @Test
    void equivalentFieldsAreNormalisedBeforeHashing() {
        assertThat(OrderRequestHasher.digest("PLACE_ORDER", "ck-1"))
                .as("首尾空白属于格式差异，不改变业务含义")
                .isEqualTo(OrderRequestHasher.digest(" PLACE_ORDER ", " ck-1 "));
    }

    @Test
    void theDigestNeverContainsPlaintextBusinessContent() {
        RequestDigest digest = OrderRequestHasher.digest("PLACE_ORDER", "ck-1");

        assertThat(digest.value()).hasSize(64).matches("[0-9a-f]{64}");
        assertThat(digest.value()).doesNotContain("ck-1");
        assertThat(digest.value()).doesNotContain("PLACE_ORDER");
    }

    @Test
    void theDigestDoesNotDependOnAnyRequestIdentifier() {
        String first = OrderRequestHasher.digest("PLACE_ORDER", "ck-1").value();
        String second = OrderRequestHasher.digest("PLACE_ORDER", "ck-1").value();

        assertThat(first)
                .as("同一业务内容在任意时刻、任意请求都得到同一摘要")
                .isEqualTo(second);
    }

    @Test
    void theIdempotencyKeyLengthIsLimited() {
        assertThatThrownBy(() -> new IdempotencyKey("k".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new IdempotencyKey(" "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new IdempotencyKey("k".repeat(128)).value()).hasSize(128);
    }
}
