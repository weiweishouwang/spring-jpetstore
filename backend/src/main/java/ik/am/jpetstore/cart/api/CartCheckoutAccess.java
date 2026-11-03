package ik.am.jpetstore.cart.api;

import ik.am.jpetstore.cart.domain.CartModels.CartOwner;
import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;

/**
 * T197: 同事务锁定及消费购物车端口。
 *
 * <p>只有订单模块在创建订单的事务中调用本端口消费购物车；cart 模块之外不得直接写购物车表
 * （plan §3.2、§5.6）。消费与订单创建处于同一事务：失败时内容保留（Q04、ORD-12）。</p>
 */
public interface CartCheckoutAccess {

    /** 在当前事务内锁定并返回活动购物车；版本不符或购物车不可用时由实现抛出业务失败。 */
    CartSnapshot lockActiveCart(CartOwner owner, long expectedVersion);

    /** 在同一事务内消费该购物车；已消费购物车视为空，后续写入新建购物车。 */
    void consume(CartOwner owner, String cartId, long version);
}
