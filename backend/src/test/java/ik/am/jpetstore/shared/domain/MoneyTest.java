package ik.am.jpetstore.shared.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T181: 十进制金额值及边界（Q06）。
 *
 * <p>CNY、两位小数、HALF_UP 舍入；商品售价必须大于零；订单总额不超过 99,999,999.99；
 * 金额计算不得引入任何隐含税费、运费或折扣。</p>
 */
class MoneyTest {

    @Test
    void keepsTwoDecimalsForCny() {
        Money money = Money.cny(new BigDecimal("16.5"));

        assertThat(money.currency()).isEqualTo(Currency.getInstance("CNY"));
        assertThat(money.amount().scale()).isEqualTo(2);
        assertThat(money.toPlainString()).isEqualTo("16.50");
    }

    @Test
    void roundsHalfUpToTwoDecimals() {
        assertThat(Money.cny(new BigDecimal("0.125")).toPlainString()).isEqualTo("0.13");
        assertThat(Money.cny(new BigDecimal("16.505")).toPlainString()).isEqualTo("16.51");
    }

    @Test
    void multipliesWithoutFloatingPointError() {
        Money money = Money.cny(new BigDecimal("16.50")).multiply(2);

        assertThat(money.toPlainString()).isEqualTo("33.00");
    }

    @Test
    void addsExactly() {
        Money total = Money.cny(new BigDecimal("16.50")).add(Money.cny(new BigDecimal("3.25")));

        assertThat(total.toPlainString()).isEqualTo("19.75");
    }

    @Test
    void addsNoImplicitTaxShippingOrDiscount() {
        Money twoItems = Money.cny(new BigDecimal("16.50")).multiply(2);
        Money extraItem = Money.cny(new BigDecimal("3.25"));

        assertThat(twoItems.add(extraItem).toPlainString()).isEqualTo("36.25");
    }

    @Test
    void rejectsForeignCurrency() {
        Money usd = new Money(new BigDecimal("1.00"), Currency.getInstance("USD"));

        assertThatThrownBy(() -> Money.cny(new BigDecimal("1.00")).add(usd))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNegativeAmount() {
        assertThatThrownBy(() -> Money.cny(new BigDecimal("-1.00")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> Money.cny((BigDecimal) null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void acceptsTotalUpToTheOrderLimitAndRejectsBeyond() {
        assertThat(Money.cny(new BigDecimal("99999999.99")).exceedsOrderTotalLimit()).isFalse();
        assertThat(Money.cny(new BigDecimal("100000000.00")).exceedsOrderTotalLimit()).isTrue();
    }

    @Test
    void rejectsTotalAboveTheOrderLimit() {
        assertThatThrownBy(() -> Money.requireWithinOrderTotal(new BigDecimal("100000000.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Money.requireWithinOrderTotal(new BigDecimal("99999999.99")).toPlainString())
                .isEqualTo("99999999.99");
    }

    @Test
    void rejectsNonPositivePrice() {
        assertThatThrownBy(() -> Money.requirePositivePrice(new BigDecimal("0.00")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Money.requirePositivePrice(new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Money.requirePositivePrice(new BigDecimal("0.01")).toPlainString()).isEqualTo("0.01");
    }
}
