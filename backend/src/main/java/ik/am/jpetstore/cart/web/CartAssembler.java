package ik.am.jpetstore.cart.web;

import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;

import java.util.List;

/**
 * T212: cart 显式 DTO/领域转换接口。
 *
 * <p>只声明签名；真实转换器在 Phase 7 落地。金额字符串化与行数/数量统计由转换器负责，
 * 权威金额始终来自服务端（plan §5.4）。</p>
 */
public interface CartAssembler {

    /** 购物车快照 → 对外购物车响应。 */
    CartDtos.CartResponseDto toCartResponse(CartSnapshot snapshot);

    /** 购物车快照 → 可售项视图。 */
    CartDtos.ItemView toItemView(String itemId);

    /** 待合并内容 → DTO。 */
    List<CartDtos.PendingMergeDto> toPendingMerges(List<String> mergeIds);
}
