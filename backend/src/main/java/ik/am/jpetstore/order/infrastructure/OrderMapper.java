package ik.am.jpetstore.order.infrastructure;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * T275: Order Mapper签名与类型约定。
 *
 * <p>订单号由 MySQL 自增产生并用 generated keys 回读，因此并发创建不会分配到同一个编号
 * （ORD-09）；自增允许跳号，编号连续性不是需求。明细保存成交单价与商品文本快照，商品改价或
 * 删除都不改变历史订单显示金额（ORD-19、Q15、D05）。查询一律带 userid 过滤：他人订单与不存在
 * 订单在 SQL 层就不可见（NFR-02）。</p>
 */
@Mapper
public interface OrderMapper {

    /** 订单初始状态：已提交（模拟，未发生真实扣款）（Q07）。 */
    String STATUS_SUBMITTED = "P";

    /** orders 表行。 */
    record OrderRow(Long orderId, String userId, Instant orderDate, String status, String totalAmount,
            String billFirstName, String billLastName, String billAddr1, String billAddr2, String billCity,
            String billState, String billZip, String billCountry,
            String shipFirstName, String shipLastName, String shipAddr1, String shipAddr2, String shipCity,
            String shipState, String shipZip, String shipCountry,
            String cardType, String cardLastFour, String checkoutId, String courier, String region) {
    }

    /** 订单列表摘要行。 */
    record OrderSummaryRow(Long orderId, Instant orderDate, String status, String totalAmount) {
    }

    /** lineitem 表行：成交单价与商品文本快照。 */
    record OrderLineRow(Long orderId, Integer lineNumber, String itemId, Integer quantity, String unitPrice,
            String productName, String itemAttribute) {
    }

    /** orderstatus 表行。 */
    record OrderStatusRow(Long orderId, Integer lineNumber, String status, Instant timestamp) {
    }

    /**
     * 新建订单的插入载体。
     *
     * <p>自增主键需要 generated keys 回填，记录类没有可写属性，因此使用带 setter 的可变载体。</p>
     */
    final class NewOrder {

        private Long orderId;

        private String userId;

        private Instant orderDate;

        private String billFirstName;

        private String billLastName;

        private String billAddr1;

        private String billAddr2;

        private String billCity;

        private String billState;

        private String billZip;

        private String billCountry;

        private String shipFirstName;

        private String shipLastName;

        private String shipAddr1;

        private String shipAddr2;

        private String shipCity;

        private String shipState;

        private String shipZip;

        private String shipCountry;

        private String totalAmount;

        private String status;

        private String courier;

        private String region;

        private String cardType;

        private String cardLastFour;

        private String checkoutId;

        public NewOrder() {
        }

        public Long getOrderId() {
            return orderId;
        }

        public void setOrderId(Long orderId) {
            this.orderId = orderId;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public Instant getOrderDate() {
            return orderDate;
        }

        public void setOrderDate(Instant orderDate) {
            this.orderDate = orderDate;
        }

        public String getBillFirstName() {
            return billFirstName;
        }

        public void setBillFirstName(String billFirstName) {
            this.billFirstName = billFirstName;
        }

        public String getBillLastName() {
            return billLastName;
        }

        public void setBillLastName(String billLastName) {
            this.billLastName = billLastName;
        }

        public String getBillAddr1() {
            return billAddr1;
        }

        public void setBillAddr1(String billAddr1) {
            this.billAddr1 = billAddr1;
        }

        public String getBillAddr2() {
            return billAddr2;
        }

        public void setBillAddr2(String billAddr2) {
            this.billAddr2 = billAddr2;
        }

        public String getBillCity() {
            return billCity;
        }

        public void setBillCity(String billCity) {
            this.billCity = billCity;
        }

        public String getBillState() {
            return billState;
        }

        public void setBillState(String billState) {
            this.billState = billState;
        }

        public String getBillZip() {
            return billZip;
        }

        public void setBillZip(String billZip) {
            this.billZip = billZip;
        }

        public String getBillCountry() {
            return billCountry;
        }

        public void setBillCountry(String billCountry) {
            this.billCountry = billCountry;
        }

        public String getShipFirstName() {
            return shipFirstName;
        }

        public void setShipFirstName(String shipFirstName) {
            this.shipFirstName = shipFirstName;
        }

        public String getShipLastName() {
            return shipLastName;
        }

        public void setShipLastName(String shipLastName) {
            this.shipLastName = shipLastName;
        }

        public String getShipAddr1() {
            return shipAddr1;
        }

        public void setShipAddr1(String shipAddr1) {
            this.shipAddr1 = shipAddr1;
        }

        public String getShipAddr2() {
            return shipAddr2;
        }

        public void setShipAddr2(String shipAddr2) {
            this.shipAddr2 = shipAddr2;
        }

        public String getShipCity() {
            return shipCity;
        }

        public void setShipCity(String shipCity) {
            this.shipCity = shipCity;
        }

        public String getShipState() {
            return shipState;
        }

        public void setShipState(String shipState) {
            this.shipState = shipState;
        }

        public String getShipZip() {
            return shipZip;
        }

        public void setShipZip(String shipZip) {
            this.shipZip = shipZip;
        }

        public String getShipCountry() {
            return shipCountry;
        }

        public void setShipCountry(String shipCountry) {
            this.shipCountry = shipCountry;
        }

        public String getTotalAmount() {
            return totalAmount;
        }

        public void setTotalAmount(String totalAmount) {
            this.totalAmount = totalAmount;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getCourier() {
            return courier;
        }

        public void setCourier(String courier) {
            this.courier = courier;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getCardType() {
            return cardType;
        }

        public void setCardType(String cardType) {
            this.cardType = cardType;
        }

        public String getCardLastFour() {
            return cardLastFour;
        }

        public void setCardLastFour(String cardLastFour) {
            this.cardLastFour = cardLastFour;
        }

        public String getCheckoutId() {
            return checkoutId;
        }

        public void setCheckoutId(String checkoutId) {
            this.checkoutId = checkoutId;
        }
    }

    /** 写入订单头；返回自增订单号。 */
    Long insertOrder(NewOrder order);

    /** 写入成交明细快照。 */
    int insertOrderLine(OrderLineRow row);

    /** 写入初始订单状态。 */
    int insertOrderStatus(OrderStatusRow row);

    /** 本人订单列表摘要，按日期倒序、订单号稳定排序（Q11）。 */
    List<OrderSummaryRow> selectOrderSummaries(@Param("userId") String userId);

    /** 本人订单详情；userid 过滤在 SQL 层完成，他人订单读不到。 */
    OrderRow selectOrder(@Param("userId") String userId, @Param("orderId") Long orderId);

    /** 订单成交明细快照，按行号升序。 */
    List<OrderLineRow> selectOrderLines(@Param("orderId") Long orderId);
}
