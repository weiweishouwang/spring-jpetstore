package ik.am.jpetstore.catalog.application;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import ik.am.jpetstore.catalog.api.CatalogQuery;
import ik.am.jpetstore.catalog.api.PricingQuery;
import ik.am.jpetstore.catalog.domain.CatalogModels.Banner;
import ik.am.jpetstore.catalog.domain.CatalogModels.Category;
import ik.am.jpetstore.catalog.domain.CatalogModels.Item;
import ik.am.jpetstore.catalog.domain.CatalogModels.Product;
import ik.am.jpetstore.catalog.domain.CatalogModels.ProductDetail;
import ik.am.jpetstore.catalog.infrastructure.CatalogMapper;
import ik.am.jpetstore.shared.domain.Money;
import ik.am.jpetstore.shared.error.BusinessFailure;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T247: 商品和偏好展示查询服务。
 *
 * <p>只读服务：把持久层行映射为领域模型，不写库存、不改数据。价格永远来自服务端投影
 * （plan §5.3）。横幅缺失只隐藏该区域，不影响其他查询。</p>
 */
@Service
@Transactional(readOnly = true)
public class CatalogQueryService implements CatalogQuery, PricingQuery {

    private final CatalogMapper mapper;

    public CatalogQueryService(CatalogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<Category> categories() {
        return mapper.selectCategories().stream().map(this::toCategory).toList();
    }

    @Override
    public List<Product> productsByCategory(String categoryId) {
        return mapper.selectProductsByCategory(categoryId).stream().map(this::toProduct).toList();
    }

    @Override
    public Optional<ProductDetail> product(String productId) {
        CatalogMapper.ProductRow product = mapper.selectProduct(productId);
        if (product == null) {
            return Optional.empty();
        }
        List<Item> items = mapper.selectItemsByProduct(productId).stream().map(this::toItem).toList();
        return Optional.of(new ProductDetail(toProduct(product), items));
    }

    @Override
    public Optional<Item> item(String itemId) {
        CatalogMapper.ItemRow item = mapper.selectItem(itemId);
        return item == null ? Optional.empty() : Optional.of(toItem(item));
    }

    @Override
    public List<Product> searchByName(String keyword) {
        return mapper.selectProductsByName(keyword).stream().map(this::toProduct).toList();
    }

    @Override
    public List<Product> productsOfCategory(String categoryId) {
        return productsByCategory(categoryId);
    }

    @Override
    public Optional<Banner> banner(String categoryId) {
        CatalogMapper.BannerRow banner = mapper.selectBanner(categoryId);
        if (banner == null) {
            return Optional.empty();
        }
        return Optional.of(new Banner("/resources/images/banner_" + categoryId.toLowerCase(java.util.Locale.ROOT)
                + ".gif", banner.bannerName()));
    }

    @Override
    public Money priceOf(String itemId) {
        CatalogMapper.ItemRow item = mapper.selectItem(itemId);
        if (item == null) {
            throw new BusinessFailure(BusinessFailure.Code.RESOURCE_NOT_FOUND, "可售项不存在");
        }
        return Money.cny(new BigDecimal(item.listPrice()));
    }

    @Override
    public int availableQuantity(String itemId) {
        CatalogMapper.ItemRow item = mapper.selectItem(itemId);
        if (item == null) {
            throw new BusinessFailure(BusinessFailure.Code.RESOURCE_NOT_FOUND, "可售项不存在");
        }
        return item.availableQuantity() == null ? 0 : item.availableQuantity();
    }

    @Override
    public boolean isPurchasable(String itemId) {
        CatalogMapper.ItemRow item = mapper.selectItem(itemId);
        if (item == null) {
            throw new BusinessFailure(BusinessFailure.Code.RESOURCE_NOT_FOUND, "可售项不存在");
        }
        int available = item.availableQuantity() == null ? 0 : item.availableQuantity();
        return available > 0 && "P".equalsIgnoreCase(item.status());
    }

    private Category toCategory(CatalogMapper.CategoryRow row) {
        return new Category(row.categoryId(), row.name(), row.description());
    }

    private Product toProduct(CatalogMapper.ProductRow row) {
        return new Product(row.productId(), row.categoryId(), row.name(), row.description(), row.imagePath());
    }

    private Item toItem(CatalogMapper.ItemRow row) {
        int available = row.availableQuantity() == null ? 0 : row.availableQuantity();
        boolean purchasable = available > 0 && "P".equalsIgnoreCase(row.status());
        return new Item(row.itemId(), row.productId(), row.productName(),
                List.of(nonNull(row.attribute1()), nonNull(row.attribute2()), nonNull(row.attribute3()),
                        nonNull(row.attribute4()), nonNull(row.attribute5())).stream().filter(s -> !s.isBlank()).toList(),
                Money.cny(new BigDecimal(row.listPrice())), available, available > 0, purchasable);
    }

    private String nonNull(String value) {
        return value == null ? "" : value;
    }
}
