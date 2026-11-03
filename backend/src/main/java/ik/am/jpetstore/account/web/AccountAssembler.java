package ik.am.jpetstore.account.web;

import ik.am.jpetstore.account.api.AccountProfileUpdate;
import ik.am.jpetstore.account.api.AccountRegistration;
import ik.am.jpetstore.account.domain.AccountModels.AccountDetails;

/**
 * T203: account 显式 DTO/领域转换接口。
 *
 * <p>只声明签名：实现负责 DTO 与领域类型之间的逐字段转换，不使用通用 BeanUtils 批量绑定
 * （plan §5.1）。真实转换器在 Phase 7 落地；在此之前控制器测试可以替换本端口。</p>
 */
public interface AccountAssembler {

    /** 领域账户 → 对外账户响应。 */
    AccountDtos.AccountResponse toResponse(AccountDetails details);

    /** 注册请求 DTO → 注册命令。 */
    AccountRegistration.RegistrationRequest toRegistration(AccountDtos.RegisterRequest request);

    /** 资料更新请求 DTO → 资料更新命令。 */
    AccountProfileUpdate.ProfileUpdateRequest toProfileUpdate(AccountDtos.UpdateRequest request);
}
