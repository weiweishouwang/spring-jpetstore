package ik.am.jpetstore.account.api;

import ik.am.jpetstore.account.domain.AccountModels.Address;
import ik.am.jpetstore.account.domain.AccountModels.Preferences;

/**
 * T191: 账户注册命令端口。
 *
 * <p>注册在同一事务内创建账户、偏好与凭据，任一步失败都不得留下部分数据（ACC-03、NFR-01）。
 * 用户名唯一由数据库约束最终保证，冲突映射为 {@code USERNAME_CONFLICT}，不依赖"先查再插"。
 * 注册成功不建立登录身份（ACC-04）。</p>
 */
public interface AccountRegistration {

    /**
     * 注册一个新账户。
     *
     * @param request 注册命令；密码仅用于计算哈希，不得记录日志或回显
     */
    void register(RegistrationRequest request);

    /**
     * 注册命令。
     *
     * @param username         trim 后 1～25 字符、大小写敏感（Q13）
     * @param password         8～64 字符且 UTF-8 不超过 72 字节，不 trim、不得截断（Q13）
     * @param repeatedPassword 必须与 password 一致，仅用于输入确认（NFR-04）
     */
    record RegistrationRequest(String username, String password, String repeatedPassword, String email,
            String phone, Address address, Preferences preferences) {
    }
}
