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
}
