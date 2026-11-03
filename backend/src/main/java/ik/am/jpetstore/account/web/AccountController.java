package ik.am.jpetstore.account.web;

import ik.am.jpetstore.account.api.AccountProfileUpdate;
import ik.am.jpetstore.account.api.AccountQuery;
import ik.am.jpetstore.account.api.AccountRegistration;
import ik.am.jpetstore.account.domain.AccountModels.AccountDetails;
import ik.am.jpetstore.catalog.api.CatalogQuery;
import ik.am.jpetstore.catalog.web.CatalogAssembler;
import ik.am.jpetstore.identity.application.CurrentUser;
import ik.am.jpetstore.shared.error.BusinessFailure;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * T265: Account REST适配。
 *
 * <p>注册成功返回 201 且不建立登录身份（ACC-04）；me 与 PATCH 只作用于当前认证身份解析出的
 * 用户，请求里没有用户名字段（ACC-07）；响应只含公开资料与偏好，不含凭据（NFR-04）。推荐与横幅
 * 取自本人偏好分类，缺失时以空值表达（PER-01～03）。</p>
 */
@RestController
public class AccountController {

    private static final String VERSION = "1.0";

    private final AccountRegistration registration;

    private final AccountProfileUpdate profileUpdate;

    private final AccountQuery accountQuery;

    private final AccountAssembler accountAssembler;

    private final CatalogQuery catalogQuery;

    private final CatalogAssembler catalogAssembler;

    private final CurrentUser currentUser;

    public AccountController(AccountRegistration registration, AccountProfileUpdate profileUpdate,
            AccountQuery accountQuery, AccountAssembler accountAssembler, CatalogQuery catalogQuery,
            CatalogAssembler catalogAssembler, CurrentUser currentUser) {
        this.registration = registration;
        this.profileUpdate = profileUpdate;
        this.accountQuery = accountQuery;
        this.accountAssembler = accountAssembler;
        this.catalogQuery = catalogQuery;
        this.catalogAssembler = catalogAssembler;
        this.currentUser = currentUser;
    }

    /** 注册：成功返回 201，不建立登录身份。 */
    @PostMapping(path = "/api/accounts", version = VERSION)
    public ResponseEntity<AccountDtos.AccountResponse> register(
            @Valid @RequestBody AccountDtos.RegisterRequest request) {
        registration.register(accountAssembler.toRegistration(request));
        AccountDetails details = new AccountDetails(
                new ik.am.jpetstore.account.domain.AccountModels.AccountProfile(request.username(), request.email(),
                        request.address().firstName(), request.address().lastName(), request.phone(),
                        new ik.am.jpetstore.account.domain.AccountModels.Address(request.address().firstName(),
                                request.address().lastName(), request.address().address1(), request.address().address2(),
                                request.address().city(), request.address().state(), request.address().zip(),
                                request.address().country())),
                new ik.am.jpetstore.account.domain.AccountModels.Preferences(
                        request.preferences().languagePreference(), request.preferences().favouriteCategoryId(),
                        request.preferences().listOption(), request.preferences().bannerOption()),
                null);
        return ResponseEntity.status(HttpStatus.CREATED).body(accountAssembler.toResponse(details));
    }

    /** 本人资料。 */
    @GetMapping(path = "/api/account/me", version = VERSION)
    public AccountDtos.AccountResponse me() {
        return accountAssembler.toResponse(currentAccount());
    }

    /** 更新本人资料、偏好与可选密码。 */
    @PatchMapping(path = "/api/account/me", version = VERSION)
    public AccountDtos.AccountResponse update(@Valid @RequestBody AccountDtos.UpdateRequest request) {
        String userId = currentUser.requireUsername();
        profileUpdate.update(userId, accountAssembler.toProfileUpdate(request));
        return accountAssembler.toResponse(currentAccount());
    }

    /** 偏好分类下的推荐产品（My List）。 */
    @GetMapping(path = "/api/account/me/recommendations", version = VERSION)
    public java.util.List<ik.am.jpetstore.catalog.web.CatalogDtos.ProductDto> recommendations() {
        String favouriteCategory = currentAccount().preferences().favouriteCategoryId();
        return catalogQuery.productsOfCategory(favouriteCategory).stream().map(catalogAssembler::toProduct).toList();
    }

    /** 偏好分类的横幅；缺失或开关关闭时返回空值。 */
    @GetMapping(path = "/api/account/me/banner", version = VERSION)
    public ResponseEntity<ik.am.jpetstore.catalog.web.CatalogDtos.BannerDto> banner() {
        AccountDetails details = currentAccount();
        if (!details.preferences().bannerOption()) {
            return ResponseEntity.ok().body(null);
        }
        return ResponseEntity.ok()
                .body(catalogQuery.banner(details.preferences().favouriteCategoryId())
                        .map(catalogAssembler::toBanner)
                        .orElse(null));
    }

    private AccountDetails currentAccount() {
        String userId = currentUser.requireUsername();
        return accountQuery.findByUserId(userId)
                .orElseThrow(() -> new BusinessFailure(BusinessFailure.Code.RESOURCE_NOT_FOUND, "账户不存在"));
    }
}
