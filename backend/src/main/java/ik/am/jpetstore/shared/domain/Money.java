package ik.am.jpetstore.shared.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * T182: 十进制金额值及边界（Q06、ORD-14～16）。
 *
 * <p>CNY、两位小数、HALF_UP 舍入；商品售价必须大于零；订单总额不超过 99,999,999.99。
 * 金额是不可变值对象，所有算术都在 {@link BigDecimal} 上完成，不引入任何隐含税费、
 * 运费或折扣——总额只等于明细金额之和。</p>
 *
 * @param amount   两位小数的非负金额
 * @param currency 币种；当前业务只允许 CNY
 */
public record Money(BigDecimal amount, Currency currency) {

    /** 业务唯一币种（Q06）。 */
    public static final Currency CNY = Currency.getInstance("CNY");

    /** 金额标度（Q06）。 */
    public static final int SCALE = 2;

    /** 订单总额上限（Q06）。 */
    public static final BigDecimal MAX_ORDER_TOTAL = new BigDecimal("99999999.99");

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        amount = amount.setScale(SCALE, RoundingMode.HALF_UP);
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("金额不能为负数: " + amount.toPlainString());
        }
    }

    public static Money cny(BigDecimal amount) {
        return new Money(amount, CNY);
    }

    public static Money cny(String amount) {
        return cny(new BigDecimal(amount));
    }

    public static Money zero() {
        return cny(BigDecimal.ZERO);
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money subtract(Money other) {
        requireSameCurrency(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money multiply(int quantity) {
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    public boolean isZero() {
        return amount.signum() == 0;
    }

    public boolean isPositive() {
        return amount.signum() > 0;
    }

    /** 是否超过订单总额上限（Q06）。 */
    public boolean exceedsOrderTotalLimit() {
        return amount.compareTo(MAX_ORDER_TOTAL) > 0;
    }

    /** 校验并返回订单总额；超过上限时拒绝（Q06）。 */
    public static Money requireWithinOrderTotal(BigDecimal amount) {
        Money money = cny(amount);
        if (money.exceedsOrderTotalLimit()) {
            throw new IllegalArgumentException("订单总额超过上限: " + money.toPlainString());
        }
        return money;
    }

    /** 校验并返回商品售价；必须大于零（Q06、ORD-16）。 */
    public static Money requirePositivePrice(BigDecimal amount) {
        Money money = cny(amount);
        if (!money.isPositive()) {
            throw new IllegalArgumentException("商品售价必须大于零: " + money.toPlainString());
        }
        return money;
    }

    /** 定标十进制字符串（API 契约中的 Money 即为此形态）。 */
    public String toPlainString() {
        return amount.toPlainString();
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("币种不一致: " + currency.getCurrencyCode()
                    + " 与 " + other.currency.getCurrencyCode());
        }
    }
}
