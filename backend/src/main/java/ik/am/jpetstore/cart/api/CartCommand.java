package ik.am.jpetstore.cart.api;

import java.util.List;

import ik.am.jpetstore.cart.domain.CartModels.CartOwner;
import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;

/**
 * T196: 单项及整批数量命令契约。
 *
 * <p>所有变更在应用服务事务内锁定购物车，校验持有权与 expectedVersion 后再写入明细并递增
 * 版本（plan §5.4）。数量为整数：0 与负数表示移除（沿用旧交互），订单明细数量必须为正。
 * 批量更新先完整校验再写入，非法输入不做半批更新。</p>
 */
public interface CartCommand {

    /** 加购：同一可售项再次添加时数量加 1（CRT-02）。 */
    CartSnapshot addItem(CartOwner owner, String itemId, long expectedVersion);

    /** 修改数量：小于 1 表示移除（CRT-06、CRT-08）。 */
    CartSnapshot setQuantity(CartOwner owner, String itemId, int quantity, long expectedVersion);

    /** 显式移除某个可售项；移除不存在的项不改变其他内容。 */
    CartSnapshot removeItem(CartOwner owner, String itemId, long expectedVersion);

    /** 整批数量更新：先校验整批，非法时不部分写入（CRT-07）。 */
    CartSnapshot applyQuantities(CartOwner owner, List<QuantityChange> changes, long expectedVersion);

    /** 登录时把游客车合并进账号车：同 SKU 相加，超限不丢双方内容（Q08）。 */
    CartSnapshot mergeGuestCart(CartOwner owner, long expectedVersion);

    /**
     * 数量变更项。
     *
     * @param quantity 小于 1 表示移除，正数 1～9999（Q15）
     */
    record QuantityChange(String itemId, int quantity) {
    }
}
