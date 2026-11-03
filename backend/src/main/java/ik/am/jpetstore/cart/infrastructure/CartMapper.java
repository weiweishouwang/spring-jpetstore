package ik.am.jpetstore.cart.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * T266: Cart Mapper签名与类型约定。
 *
 * <p>购物车数据落在数据库而不是 HTTP 会话里：游客车按会话绑定哈希定位，账号车按
 * {@code owner_userid} 定位并跨设备、跨重启保留（Q08）。原始 Session ID 既不落业务表也不落
 * 日志；业务表里只有不可逆的绑定哈希。明细唯一键是 (cart_id, itemid)，因此只有一份明细集合，
 * 旧的 List/Map 双状态不可能重现（D04a）。</p>
 *
 * <p>行类型是持久层扁平快照，领域模型的转换由应用服务负责；SQL 定义在
 * mapper/cart/CartMapper.xml（T268）。</p>
 */
@Mapper
public interface CartMapper {

    /** 活动购物车状态。 */
    String STATE_ACTIVE = "ACTIVE";

    /** 已随成交订单消费。 */
    String STATE_CONSUMED = "CONSUMED";

    /** 已过期回收。 */
    String STATE_EXPIRED = "EXPIRED";

    /** 登录合并超限时冻结保存的待合并车（Q08：双方内容都不丢弃）。 */
    String STATE_PENDING_MERGE = "PENDING_MERGE";

    /** cart 表行。 */
    record CartRow(Long cartId, String sessionBindingHash, String ownerUserId, Long version, String state,
            Instant createdAt, Instant expiresAt) {
    }

    /** cart_item 表行。 */
    record CartItemRow(Long cartId, String itemId, Integer quantity) {
    }

    /** 待合并购物车行：冻结的游客车内容，归属当前账号。 */
    record PendingMergeRow(Long mergeId, String itemId, Integer quantity) {
    }

    /**
     * 新建购物车的插入载体。
     *
     * <p>自增主键需要 generated keys 回填，记录类没有可写属性，因此这里使用带 setter 的可变
     * 载体；调用方只能读取 {@code cartId}。</p>
     */
    final class NewCart {

        private Long cartId;

        private String sessionBindingHash;

        private String ownerUserId;

        private long version;

        private String state;

        private Instant createdAt;

        private Instant expiresAt;

        public NewCart() {
        }

        public NewCart(String sessionBindingHash, String ownerUserId, long version, String state,
                Instant createdAt, Instant expiresAt) {
            this.sessionBindingHash = sessionBindingHash;
            this.ownerUserId = ownerUserId;
            this.version = version;
            this.state = state;
            this.createdAt = createdAt;
            this.expiresAt = expiresAt;
        }

        public Long getCartId() {
            return cartId;
        }

        public void setCartId(Long cartId) {
            this.cartId = cartId;
        }

        public String getSessionBindingHash() {
            return sessionBindingHash;
        }

        public void setSessionBindingHash(String sessionBindingHash) {
            this.sessionBindingHash = sessionBindingHash;
        }

        public String getOwnerUserId() {
            return ownerUserId;
        }

        public void setOwnerUserId(String ownerUserId) {
            this.ownerUserId = ownerUserId;
        }

        public long getVersion() {
            return version;
        }

        public void setVersion(long version) {
            this.version = version;
        }

        public String getState() {
            return state;
        }

        public void setState(String state) {
            this.state = state;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(Instant createdAt) {
            this.createdAt = createdAt;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(Instant expiresAt) {
            this.expiresAt = expiresAt;
        }
    }

    /** 新建购物车；返回自增主键。 */
    Long insertCart(NewCart cart);

    /** 按会话绑定哈希读取活动购物车（不加锁）。 */
    CartRow selectActiveCartBySession(@Param("sessionBindingHash") String sessionBindingHash);

    /** 按账号读取活动购物车（不加锁）。 */
    CartRow selectActiveCartByOwner(@Param("ownerUserId") String ownerUserId);

    /** 锁定会话的活动购物车；必须在有效事务内调用（plan §5.4）。 */
    CartRow selectActiveCartBySessionForUpdate(@Param("sessionBindingHash") String sessionBindingHash);

    /** 锁定的账号活动购物车；必须在有效事务内调用（plan §5.4）。 */
    CartRow selectActiveCartByOwnerForUpdate(@Param("ownerUserId") String ownerUserId);

    /** 购物车明细，按 itemid 升序。 */
    List<CartItemRow> selectCartItems(@Param("cartId") Long cartId);

    /** 写入一条明细；同一 (cart_id, itemid) 只能有一条。 */
    int insertCartItem(CartItemRow row);

    /** 修改明细数量。 */
    int updateCartItemQuantity(@Param("cartId") Long cartId, @Param("itemId") String itemId,
            @Param("quantity") int quantity);

    /** 删除明细。 */
    int deleteCartItem(@Param("cartId") Long cartId, @Param("itemId") String itemId);

    /**
     * 递增购物车版本（乐观锁 CAS）。
     *
     * @param expectedVersion 期望的当前版本；不匹配时影响行数为 0
     * @return 影响行数；必须是 1，否则说明并发修改（CRT-10）
     */
    int bumpVersion(@Param("cartId") Long cartId, @Param("expectedVersion") long expectedVersion,
            @Param("expiresAt") Instant expiresAt);

    /** 修改购物车状态（消费、过期回收）。 */
    int updateState(@Param("cartId") Long cartId, @Param("state") String state);

    /** 把游客车整体转为账号车（保留明细，登录时没有账号车时使用）。 */
    int transferToAccount(@Param("cartId") Long cartId, @Param("ownerUserId") String ownerUserId);

    /** 冻结为待合并车：归属转为账号，原游客会话不再能修改（Q08）。 */
    int freezeAsPendingMerge(@Param("cartId") Long cartId, @Param("ownerUserId") String ownerUserId);

    /** 当前账号拥有的待合并内容。 */
    List<PendingMergeRow> selectPendingMerges(@Param("ownerUserId") String ownerUserId);

    /** 删除购物车及其明细（合并成功后清理冻结的游客车）。 */
    int deleteCart(@Param("cartId") Long cartId);
}
