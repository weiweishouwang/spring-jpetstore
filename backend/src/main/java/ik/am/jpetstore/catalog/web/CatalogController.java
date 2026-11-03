package ik.am.jpetstore.catalog.web;

import java.util.List;

import ik.am.jpetstore.catalog.api.CatalogQuery;
import ik.am.jpetstore.catalog.domain.CatalogModels.Category;
import ik.am.jpetstore.catalog.domain.CatalogModels.Item;
import ik.am.jpetstore.catalog.domain.CatalogModels.ProductDetail;
import ik.am.jpetstore.shared.error.BusinessFailure;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * T263: Catalog REST适配。
 *
 * <p>控制器只做协议适配：读取路径与查询参数，调用应用端口，映射为 DTO。缺失资源统一 404，
 * 空关键词 400（CAT-04、Q14）。映射带版本 1.0，与已冻结的契约一致（plan §6.1）。</p>
 */
@RestController
public class CatalogController {

    private static final String VERSION = "1.0";

    private final CatalogQuery catalogQuery;

    private final CatalogAssembler assembler;

    public CatalogController(CatalogQuery catalogQuery, CatalogAssembler assembler) {
        this.catalogQuery = catalogQuery;
        this.assembler = assembler;
    }

    @GetMapping(path = "/api/categories", version = VERSION)
    public List<CatalogDtos.CategoryDto> categories() {
        return catalogQuery.categories().stream().map(assembler::toCategory).toList();
    }

    @GetMapping(path = "/api/categories/{id}/products", version = VERSION)
    public List<CatalogDtos.ProductDto> productsOfCategory(@PathVariable("id") String categoryId) {
        return catalogQuery.productsByCategory(categoryId).stream().map(assembler::toProduct).toList();
    }

    @GetMapping(path = "/api/products", version = VERSION)
    public List<CatalogDtos.ProductDto> search(@RequestParam(name = "keyword") String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            throw new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED, "搜索关键词不能为空");
        }
        return catalogQuery.searchByName(trimmed).stream().map(assembler::toProduct).toList();
    }

    @GetMapping(path = "/api/products/{id}", version = VERSION)
    public CatalogDtos.ProductDetailDto product(@PathVariable("id") String productId) {
        ProductDetail detail = catalogQuery.product(productId)
                .orElseThrow(() -> new BusinessFailure(BusinessFailure.Code.RESOURCE_NOT_FOUND, "产品不存在"));
        return assembler.toProductDetail(detail);
    }

    @GetMapping(path = "/api/items/{id}", version = VERSION)
    public CatalogDtos.ItemDto item(@PathVariable("id") String itemId) {
        Item item = catalogQuery.item(itemId)
                .orElseThrow(() -> new BusinessFailure(BusinessFailure.Code.RESOURCE_NOT_FOUND, "可售项不存在"));
        return assembler.toItem(item);
    }
}
