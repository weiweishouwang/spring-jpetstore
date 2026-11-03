package ik.am.jpetstore.account.api;

import ik.am.jpetstore.account.domain.AccountModels.Address;
import ik.am.jpetstore.account.domain.AccountModels.Preferences;

/**
 * T192: 资料和可选密码更新端口。
 *
 * <p>只能修改本人资料：userId 由当前身份解析，不接受请求字段指定（ACC-07）。用户名只读。
 * 密码与重复密码都留空表示不改密；非空时必须一致，成功后旧密码失效且全部会话失效
 * （ACC-08、ACC-10、Q13）。</p>
 */
public interface AccountProfileUpdate {

    /**
     * 更新本人资料、偏好与可选密码。
     *
     * @param userId  当前认证身份解析出的账户
     */
    void update(String userId, ProfileUpdateRequest request);

    /**
     * 资料更新命令。
     *
     * @param password         新密码，留空表示不改密
     * @param repeatedPassword 新密码确认，留空表示不改密
     */
    record ProfileUpdateRequest(String email, String phone, Address address, Preferences preferences,
            String password, String repeatedPassword) {
    }
}
