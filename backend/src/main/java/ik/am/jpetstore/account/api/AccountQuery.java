package ik.am.jpetstore.account.api;

import java.util.Optional;

import ik.am.jpetstore.account.domain.AccountModels.AccountDetails;

/**
 * T190: 仅本人可用的账户查询端口。
 *
 * <p>调用方只允许传入当前认证身份解析出的 userId；不得用请求字段替换归属（NFR-02、ORD-01）。
 * 实现必须保证他人数据不可见。</p>
 */
public interface AccountQuery {

    /** 按 userId 读取账户；不存在时返回空。 */
    Optional<AccountDetails> findByUserId(String userId);

    /** 按用户名读取账户；用户名大小写敏感（Q13）。 */
    Optional<AccountDetails> findByUsername(String username);

    /** 用户名是否已被占用；用于注册唯一性判定（ACC-02）。 */
    boolean usernameExists(String username);
}
