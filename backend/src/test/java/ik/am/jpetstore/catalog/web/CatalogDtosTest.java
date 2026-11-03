package ik.am.jpetstore.catalog.web;

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
 * T207: catalog HTTP 请求/响应契约及验证。
 *
 * <p>金额必须是十进制字符串而不是 JSON 数字（契约 Money 形态），字段名与契约一致，
 * 可空字段以 null 表达。</p>
 */
class CatalogDtosTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();

    private final Validator validator = validatorFactory.getValidator();

    @Test
    void itemPriceIsSerializedAsDecimalString() throws Exception {
        String json = objectMapper.writeValueAsString(new CatalogDtos.ItemDto("EST-1", "FI-SW-01", "Angelfish",
                List.of("Large", "Adult male"), "16.50", true, 10000, true));
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("unitPrice").isString())
                .as("money is a decimal string, never a JSON number")
                .isTrue();
        assertThat(node.get("unitPrice").asString()).isEqualTo("16.50");
        assertThat(node.propertyNames()).containsExactlyInAnyOrder("itemId", "productId", "productName",
                "attributes", "unitPrice", "inStock", "availableQuantity", "purchasable");
    }

    @Test
    void productKeepsContractFieldNamesAndNullableImage() throws Exception {
        String json = objectMapper.writeValueAsString(new CatalogDtos.ProductDto("FI-SW-01", "FISH", "Angelfish",
                "Saltwater fish from Australia", null));
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.propertyNames()).containsExactlyInAnyOrder("productId", "categoryId", "name",
                "description", "imagePath");
        assertThat(node.get("imagePath").isNull()).isTrue();
    }

    @Test
    void productDetailNestsProductAndItems() throws Exception {
        CatalogDtos.ItemDto item = new CatalogDtos.ItemDto("EST-1", "FI-SW-01", "Angelfish", List.of(), "16.50",
                true, 10000, true);
        String json = objectMapper.writeValueAsString(new CatalogDtos.ProductDetailDto(
                new CatalogDtos.ProductDto("FI-SW-01", "FISH", "Angelfish", "d", null), List.of(item)));
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.propertyNames()).containsExactlyInAnyOrder("product", "items");
        assertThat(node.get("items")).hasSize(1);
    }

    @Test
    void rejectsMalformedPriceAndNegativeStock() {
        CatalogDtos.ItemDto malformedPrice = new CatalogDtos.ItemDto("EST-1", "FI-SW-01", "Angelfish", List.of(),
                "16.5", true, 1, true);
        CatalogDtos.ItemDto negativeStock = new CatalogDtos.ItemDto("EST-1", "FI-SW-01", "Angelfish", List.of(),
                "16.50", false, -1, true);

        assertThat(validator.validate(malformedPrice)).isNotEmpty();
        assertThat(validator.validate(negativeStock)).isNotEmpty();
    }
}
