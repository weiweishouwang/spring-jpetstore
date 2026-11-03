package ik.am.jpetstore.order.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

/**
 * T214: order HTTP 请求/响应契约及验证。
 *
 * <p>对齐 docs/contracts/openapi.yaml：订单号为十进制字符串、金额为十进制字符串、时间是
 * ISO 8601 UTC（Q11）；模拟支付只返回卡型与尾号（Q15、NFR-04）；状态 P 表示已提交（模拟，
 * 未发生真实扣款），courier/region 允许为空（Q07）。</p>
 */
public final class OrderDtos {

    private OrderDtos() {
    }

    /** 订单地址（Q01：第二行选填）。 */
    public record AddressDto(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Size(max = 80) String address1,
            @Size(max = 40) String address2,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 80) String state,
            @NotBlank @Size(max = 20) String zip,
            @NotBlank @Size(max = 20) String country) {
    }

    /**
     * 模拟支付输入（Q05、Q15）。
     *
     * @param cardNumber 假卡号，必须以 FAKE- 开头；只用于模拟，禁止记录日志
     * @param expiryDate MM/YYYY 格式；不做未来日期校验
     */
    public record FakePaymentRequest(
            @NotBlank @Pattern(regexp = "Visa|MasterCard|American Express") String cardType,
            @NotBlank @Size(min = 6, max = 80) @Pattern(regexp = "^FAKE-.+$") String cardNumber,
            @NotBlank @Size(min = 7, max = 7) @Pattern(regexp = "^(0[1-9]|1[0-2])/[0-9]{4}$") String expiryDate) {
    }

    /** 模拟支付摘要：仅卡型与尾号。 */
    public record PaymentSummaryDto(
            @NotBlank @Pattern(regexp = "Visa|MasterCard|American Express") String cardType,
            @NotBlank @Size(max = 4) String lastFour) {
    }

    /** 成交明细快照（Q15、ORD-19）。 */
    public record OrderLineDto(
            @NotBlank @Size(max = 10) String itemId,
            @NotBlank @Size(max = 80) String productName,
            List<String> attributes,
            @Min(1) @Max(9999) int quantity,
            @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,7})\\.[0-9]{2}$") String unitPrice,
            @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,7})\\.[0-9]{2}$") String lineAmount) {
    }

    /** 创建确认快照请求。 */
    public record CreateCheckoutRequest(
            @PositiveOrZero long cartVersion,
            @NotNull @Valid AddressDto billingAddress,
            @NotNull @Valid AddressDto shippingAddress,
            @NotNull @Valid FakePaymentRequest payment) {
    }

    /** 确认快照响应（Q03：15 分钟有效，不锁价、不预占库存）。 */
    public record CheckoutResponseDto(
            @NotBlank @Size(max = 128) String checkoutId,
            @PositiveOrZero long cartVersion,
            Instant createdAt,
            Instant expiresAt,
            @NotNull @Valid AddressDto billingAddress,
            @NotNull @Valid AddressDto shippingAddress,
            @NotNull @Valid PaymentSummaryDto payment,
            @NotNull List<@Valid OrderLineDto> items,
            @NotBlank String totalAmount,
            @Pattern(regexp = "CNY") String currency) {
    }

    /** 提交订单请求：只允许携带 checkoutId。 */
    public record CreateOrderRequest(@NotBlank @Size(max = 128) String checkoutId) {
    }

    /** 订单摘要（ORD-17）。 */
    public record OrderSummaryDto(
            @NotBlank @Pattern(regexp = "^[1-9][0-9]*$") String orderId,
            Instant createdAt,
            @NotBlank @Pattern(regexp = "P") String statusCode,
            @NotBlank String statusLabel,
            @NotBlank String totalAmount,
            @Pattern(regexp = "CNY") String currency) {
    }

    /** 订单创建结果。 */
    public record OrderCreatedResponseDto(
            @NotBlank @Pattern(regexp = "^[1-9][0-9]*$") String orderId,
            Instant createdAt,
            @NotBlank @Pattern(regexp = "P") String statusCode,
            @NotBlank String statusLabel,
            @NotBlank String totalAmount,
            @Pattern(regexp = "CNY") String currency) {
    }

    /** 订单详情（ORD-19）。 */
    public record OrderDetailDto(
            @NotBlank @Pattern(regexp = "^[1-9][0-9]*$") String orderId,
            Instant createdAt,
            @NotBlank @Pattern(regexp = "P") String statusCode,
            @NotBlank String statusLabel,
            @NotBlank String totalAmount,
            @Pattern(regexp = "CNY") String currency,
            @NotNull List<@Valid OrderLineDto> items,
            @NotNull @Valid AddressDto billingAddress,
            @NotNull @Valid AddressDto shippingAddress,
            @NotNull @Valid PaymentSummaryDto payment,
            String courier,
            String region) {
    }
}
