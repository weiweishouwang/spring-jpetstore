package ik.am.jpetstore.cart.domain;

import java.util.List;

import ik.am.jpetstore.shared.domain.Money;

/**
 * T189: 会话持有权、版本和明细数据类型（Q08）。
 *
 * <p>游客购物车按会话绑定哈希定位，登录购物车按账号持久化并支持跨设备恢复、退出保留。
 * 购物车明细唯一键为 (cart_id, itemid)，只有一份明细集合，不存在旧实现的列表/索引双状态
 * （D04a）。</p>
 *
 * <p>T453（补遗）：Phase 6 实施时发现冻结契约缺少“待合并”表达，补齐三处——持有权可同时携带
 * 会话绑定哈希（登录合并需要定位同一个会话里的游客车）、购物车状态新增 PENDING_MERGE、快照携带
 * 待合并内容（否则 OpenAPI 的 mergeConflict/pendingMerges 无来源）。均为追加式修正，不改变既有
 * 语义。</p>
 */
public final class CartModels {

    private CartModels() {
    }

    /**
     * 购物车持有权：游客会话或登录账号，二者其一。
     *
     * <p>登录后的账号持有权可以同时携带当前会话的绑定哈希：账号车仍然只按 {@code userId} 定位，
     * 携带哈希只是为了在登录合并时找到同一个会话里的游客车（Q08）。原始 Session ID 既不落业务
     * 表也不落日志，业务表里只有不可逆的绑定哈希。</p>
     */
    public record CartOwner(String sessionBindingHash, String userId) {

        public boolean isAnonymous() {
            return userId == null;
        }

        /** 游客持有权：只保存会话绑定哈希，原始 Session ID 不落业务表和日志。 */
        public static CartOwner anonymous(String sessionBindingHash) {
            return new CartOwner(sessionBindingHash, null);
        }

        /** 账号持有权：owner_userid 是权威归属。 */
        public static CartOwner account(String userId) {
            return new CartOwner(null, userId);
        }

        /** 账号持有权 + 当前会话绑定哈希：用于登录时合并同一个会话里的游客车。 */
        public static CartOwner accountInSession(String userId, String sessionBindingHash) {
            return new CartOwner(sessionBindingHash, userId);
        }
    }

    /** 购物车状态。 */
    public enum CartState {
        /** 活动购物车。 */
        ACTIVE,
        /** 已随成交订单消费。 */
        CONSUMED,
        /** 已过期回收。 */
        EXPIRED,
        /** 登录合并超限时冻结保存，等待账号调整购物车后显式重试（Q08）。 */
        PENDING_MERGE
    }

    /**
     * 购物车明细。
     *
     * @param quantity 正整数；数量小于 1 表示移除（沿用旧交互）
     * @param inStock  加购时的参考状态，不替代下单时的库存校验（CRT-04）
     */
    public record CartLine(String itemId, int quantity, boolean inStock) {
    }

    /**
     * 购物车快照。
     *
     * @param version 乐观锁版本；并发修改基于该版本检测冲突
     * @param pendingMerges 当前账号的待合并内容；登录合并超限时非空，双方内容都不丢弃（Q08）
     */
    public record CartSnapshot(String cartId, CartOwner owner, long version, CartState state,
            List<CartLine> lines, Money subtotal, List<PendingMerge> pendingMerges) {

        /** 行数（不等于商品数量总和，CRT-09）。 */
        public int lineCount() {
            return lines.size();
        }

        /** 商品数量总和。 */
        public int totalQuantity() {
            return lines.stream().mapToInt(CartLine::quantity).sum();
        }
    }

    /** 待合并内容：登录合并游客车超限时冻结保存，不丢弃（Q08）。 */
    public record PendingMerge(String mergeId, List<CartLine> items) {
    }
}
