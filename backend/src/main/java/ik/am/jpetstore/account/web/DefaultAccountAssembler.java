package ik.am.jpetstore.account.web;

import ik.am.jpetstore.account.api.AccountProfileUpdate;
import ik.am.jpetstore.account.api.AccountRegistration;
import ik.am.jpetstore.account.domain.AccountModels.AccountDetails;
import ik.am.jpetstore.account.domain.AccountModels.Address;
import ik.am.jpetstore.account.domain.AccountModels.Preferences;

import org.springframework.stereotype.Component;

/**
 * T306: account 显式 DTO/领域转换。
 *
 * <p>逐字段映射，不使用通用 BeanUtils 批量绑定：只把允许的字段从一个方向搬到另一个方向
 * （plan §6.1）。凭据只进入注册命令，绝不进入任何响应（NFR-04）。</p>
 *
 * <p>金额与标识都不是本模块的字段：用户名的 trim 与长度规则由应用服务负责（Q13），地址第二行
 * 允许为空（Q01），这里只做原样搬运。</p>
 */
@Component
public class DefaultAccountAssembler implements AccountAssembler {

    @Override
    public AccountDtos.AccountResponse toResponse(AccountDetails details) {
        return new AccountDtos.AccountResponse(details.profile().userId(), details.profile().email(),
                details.profile().phone(), toDto(details.profile().address()),
                toDto(details.preferences()));
    }

    @Override
    public AccountRegistration.RegistrationRequest toRegistration(AccountDtos.RegisterRequest request) {
        return new AccountRegistration.RegistrationRequest(request.username(), request.password(),
                request.repeatedPassword(), request.email(), request.phone(), toDomain(request.address()),
                toDomain(request.preferences()));
    }

    @Override
    public AccountProfileUpdate.ProfileUpdateRequest toProfileUpdate(AccountDtos.UpdateRequest request) {
        return new AccountProfileUpdate.ProfileUpdateRequest(request.email(), request.phone(),
                toDomain(request.address()), toDomain(request.preferences()), request.password(),
                request.repeatedPassword());
    }

    private AccountDtos.Address toDto(Address address) {
        return new AccountDtos.Address(address.firstName(), address.lastName(), address.address1(),
                address.address2(), address.city(), address.state(), address.zip(), address.country());
    }

    private AccountDtos.Preferences toDto(Preferences preferences) {
        return new AccountDtos.Preferences(preferences.languagePreference(), preferences.favouriteCategoryId(),
                preferences.listOption(), preferences.bannerOption());
    }

    private Address toDomain(AccountDtos.Address address) {
        return new Address(address.firstName(), address.lastName(), address.address1(), address.address2(),
                address.city(), address.state(), address.zip(), address.country());
    }

    private Preferences toDomain(AccountDtos.Preferences preferences) {
        return new Preferences(preferences.languagePreference(), preferences.favouriteCategoryId(),
                preferences.listOption(), preferences.bannerOption());
    }
}
