package ik.am.jpetstore.cart.web;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T210: cart HTTP 请求/响应契约及验证。
 *
 * <p>对齐 docs/contracts/openapi.yaml：金额是十进制字符串，数量小于 1 表示移除，正数上限 9999
 * （Q15），version 为并发控制字段。</p>
 */
class CartDtosTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();

    private final Validator validator = validatorFactory.getValidator();

    @Test
    void cartResponseUsesContractFieldNamesAndStringAmounts() throws Exception {
        CartDtos.CartItemDto item = new CartDtos.CartItemDto(
                new CartDtos.ItemView("EST-1", "FI-SW-01", "Angelfish", List.of("Large"), "16.50", true, 10000, true),
                2, "33.00");

        String json = objectMapper.writeValueAsString(new CartDtos.CartResponseDto(7, 1, 2, List.of(item),
                "33.00", "CNY", false, List.of()));
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.propertyNames()).containsExactlyInAnyOrder("version", "lineCount", "totalQuantity", "items",
                "subtotal", "currency", "mergeConflict", "pendingMerges");
        assertThat(node.get("subtotal").isString()).as("subtotal is a decimal string").isTrue();
        assertThat(node.get("currency").asString()).isEqualTo("CNY");
        assertThat(node.get("items").get(0).get("lineAmount").asString()).isEqualTo("33.00");
    }

    @Test
    void cartLineExposesNestedItemAndQuantity() throws Exception {
        CartDtos.CartItemDto item = new CartDtos.CartItemDto(
                new CartDtos.ItemView("EST-1", "FI-SW-01", "Angelfish", List.of(), "16.50", true, 10000, true),
                1, "16.50");
        String json = objectMapper.writeValueAsString(item);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.propertyNames()).containsExactlyInAnyOrder("item", "quantity", "lineAmount");
        assertThat(node.get("item").get("unitPrice").isString()).isTrue();
    }

    @Test
    void quantityBelowOneIsAllowedButAboveLimitIsRejected() {
        assertThat(validator.validate(new CartDtos.CartQuantityRequest(0, 1))).isEmpty();
        assertThat(validator.validate(new CartDtos.CartQuantityRequest(-1, 1))).isEmpty();
        assertThat(validator.validate(new CartDtos.CartQuantityRequest(10_000, 1))).isNotEmpty();
    }

    @Test
    void requestsRequireItemIdAndExpectedVersion() {
        assertThat(validator.validate(new CartDtos.AddCartItemRequest(" ", 0))).isNotEmpty();
        assertThat(validator.validate(new CartDtos.AddCartItemRequest("EST-1", 0))).isEmpty();
        assertThat(validator.validate(new CartDtos.CartBatchRequest(1, List.of()))).isNotEmpty();
    }
}
