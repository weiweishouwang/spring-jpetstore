package ik.am.jpetstore.cart.api;

import java.util.Optional;

import ik.am.jpetstore.cart.domain.CartModels.CartOwner;
import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;

/**
 * T195: 会话购物车读取契约。
 *
 * <p>读取不创建业务行：无购物车或已消费时返回空购物车（plan §5.4）。cartId 不是可绕过身份或
 * 会话校验的 bearer 凭据（NFR-02）。</p>
 */
public interface CartQuery {

    /** 读取当前持有权的购物车；不存在时返回空快照。 */
    Optional<CartSnapshot> currentCart(CartOwner owner);

    /** 按 cartId 读取；只能读取属于该持有权的购物车。 */
    Optional<CartSnapshot> findOwnedCart(CartOwner owner, String cartId);
}
