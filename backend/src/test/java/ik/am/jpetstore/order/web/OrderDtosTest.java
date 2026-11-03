package ik.am.jpetstore.order.web;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T213: order HTTP 请求/响应契约及验证。
 *
 * <p>对齐 docs/contracts/openapi.yaml：订单号是十进制字符串（避免 JavaScript 精度丢失），
 * 金额是十进制字符串，时间是 ISO 8601 UTC；模拟支付只返回卡型与尾号，原始假卡号绝不进入响应
 * （Q15、NFR-04）；courier/region 可空（Q07）。</p>
 */
class OrderDtosTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();

    private final Validator validator = validatorFactory.getValidator();

    @Test
    void orderSummaryKeepsIdsAndAmountsAsStrings() throws Exception {
        String json = objectMapper.writeValueAsString(new OrderDtos.OrderSummaryDto("1000",
                Instant.parse("2026-11-02T08:00:00Z"), "P", "已提交（模拟，未发生真实扣款）", "33.00", "CNY"));
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("orderId").isString()).as("orderId is a decimal string").isTrue();
        assertThat(node.get("orderId").asString()).isEqualTo("1000");
        assertThat(node.get("totalAmount").isString()).isTrue();
        assertThat(node.get("createdAt").isString()).isTrue();
        assertThat(node.propertyNames()).containsExactlyInAnyOrder("orderId", "createdAt", "statusCode",
                "statusLabel", "totalAmount", "currency");
    }

    @Test
    void paymentSummaryNeverExposesTheFakeCardNumber() throws Exception {
        String json = objectMapper.writeValueAsString(new OrderDtos.PaymentSummaryDto("Visa", "1234"));
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.propertyNames()).containsExactlyInAnyOrder("cardType", "lastFour");
        assertThat(json).doesNotContain("FAKE-").doesNotContain("cardNumber").doesNotContain("expiryDate");
    }

    @Test
    void fakePaymentRequestValidatesCardTypeNumberAndExpiry() {
        OrderDtos.FakePaymentRequest valid = new OrderDtos.FakePaymentRequest("Visa", "FAKE-4111-1111",
                "12/2030");
        assertThat(validator.validate(valid)).isEmpty();

        assertThat(validator.validate(new OrderDtos.FakePaymentRequest("Bitcoin", "FAKE-4111-1111", "12/2030")))
                .isNotEmpty();
        assertThat(validator.validate(new OrderDtos.FakePaymentRequest("Visa", "4111111111111111", "12/2030")))
                .isNotEmpty();
        assertThat(validator.validate(new OrderDtos.FakePaymentRequest("Visa", "FAKE-4111-1111", "2030-12")))
                .isNotEmpty();
    }

    @Test
    void orderLineCarriesSnapshotAmounts() throws Exception {
        String json = objectMapper.writeValueAsString(new OrderDtos.OrderLineDto("EST-1", "Angelfish",
                List.of("Large"), 2, "16.50", "33.00"));
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.propertyNames()).containsExactlyInAnyOrder("itemId", "productName", "attributes",
                "quantity", "unitPrice", "lineAmount");
        assertThat(node.get("lineAmount").asString()).isEqualTo("33.00");
    }

    @Test
    void orderDetailAllowsNullCourierAndRegion() throws Exception {
        String json = objectMapper.writeValueAsString(new OrderDtos.OrderDetailDto("1000",
                Instant.parse("2026-11-02T08:00:00Z"), "P", "已提交（模拟，未发生真实扣款）", "33.00", "CNY",
                List.of(), validAddress(), validAddress(), new OrderDtos.PaymentSummaryDto("Visa", "1234"), null,
                null));
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("courier").isNull()).isTrue();
        assertThat(node.get("region").isNull()).isTrue();
    }

    private OrderDtos.AddressDto validAddress() {
        return new OrderDtos.AddressDto("Bob", "Yamada", "4-5-6 Omotesando", null, "Minato", "Tokyo", "107-0052",
                "JP");
    }
}
