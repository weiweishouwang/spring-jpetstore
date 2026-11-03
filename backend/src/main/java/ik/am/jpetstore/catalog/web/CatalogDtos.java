package ik.am.jpetstore.catalog.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * T208: catalog HTTP 请求/响应契约及验证。
 *
 * <p>对齐 docs/contracts/openapi.yaml：金额一律是十进制字符串（避免 JavaScript 精度丢失，
 * plan §6.1），商品文本为纯文本或受控静态资源引用，不含可执行 HTML（NFR-05）。</p>
 */
public final class CatalogDtos {

    private CatalogDtos() {
    }

    /** 分类。 */
    public record CategoryDto(
            @NotBlank @Size(max = 10) String categoryId,
            @NotBlank @Size(max = 80) String name,
            String description) {
    }

    /** 产品；imagePath 为受控站内静态资源路径，可为空。 */
    public record ProductDto(
            @NotBlank @Size(max = 10) String productId,
            @NotBlank @Size(max = 10) String categoryId,
            @NotBlank @Size(max = 80) String name,
            String description,
            String imagePath) {
    }

    /**
     * 可售项。
     *
     * @param unitPrice         十进制字符串，如 "16.50"；不是 JSON 数字
     * @param availableQuantity 当前可用库存，非负
     * @param purchasable       商品状态允许成交（Q10）
     */
    public record ItemDto(
            @NotBlank @Size(max = 10) String itemId,
            @NotBlank @Size(max = 10) String productId,
            @NotBlank @Size(max = 80) String productName,
            List<String> attributes,
            @NotBlank @Pattern(regexp = "^(0|[1-9][0-9]{0,7})\\.[0-9]{2}$") String unitPrice,
            boolean inStock,
            @PositiveOrZero int availableQuantity,
            boolean purchasable) {
    }

    /** 产品详情。 */
    public record ProductDetailDto(ProductDto product, List<ItemDto> items) {
    }

    /** 横幅：受控图片引用 + 纯文本说明。 */
    public record BannerDto(@NotBlank @Size(max = 512) String imagePath, String description) {
    }
}
