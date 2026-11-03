package ik.am.jpetstore.cart.web;

import ik.am.jpetstore.cart.domain.CartModels.CartSnapshot;

/**
 * T212: cart 显式 DTO/领域转换接口。
 *
 * <p>只声明签名；真实转换器在 Phase 7 落地。金额字符串化与行数/数量统计由转换器负责，
 * 权威金额始终来自服务端（plan §5.4）。</p>
 *
 * <p>T454（补遗）：Phase 6 实施时确认“待合并内容”应由快照携带（见 CartModels.CartSnapshot），
 * 因此删除只接受标识列表的 toPendingMerges：标识本身不足以组装出带明细的 PendingCartMerge，
 * 而快照已经提供了 mergeId 与明细。转换器在 toCartResponse 内部完成该映射。</p>
 */
public interface CartAssembler {

    /** 购物车快照 → 对外购物车响应。 */
    CartDtos.CartResponseDto toCartResponse(CartSnapshot snapshot);

    /** 购物车快照 → 可售项视图。 */
    CartDtos.ItemView toItemView(String itemId);
}
