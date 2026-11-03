package ik.am.jpetstore.catalog.api;

import ik.am.jpetstore.shared.domain.Money;

/**
 * T194: 服务端价格读取契约。
 *
 * <p>价格只能来自服务端，客户端不得提交权威价格（plan §5.5）。库存与可售状态同样由服务端
 * 判定；加购时的有货提示只是参考，成交时必须重新校验（CRT-04、INV-02、Q02）。</p>
 */
public interface PricingQuery {

    /** 当前售价（CNY）。 */
    Money priceOf(String itemId);

    /** 当前可用库存数量。 */
    int availableQuantity(String itemId);

    /** 是否允许成交：商品未下架（Q10）。 */
    boolean isPurchasable(String itemId);

    /**
     * 商品是否仍在售。
     *
     * <p>T456（补遗）：Phase 6 实施购物车与下单时发现“下架拒绝、缺货可加购”（Q02/Q10）需要
     * 区分“下架”和“缺货”，而 {@link #isPurchasable(String)} 同时包含库存条件，无法表达加购
     * 规则，因此追加只判断商品状态的读取方法。</p>
     */
    boolean isListed(String itemId);
}
