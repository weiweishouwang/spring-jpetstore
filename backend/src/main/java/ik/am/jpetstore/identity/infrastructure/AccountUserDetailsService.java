package ik.am.jpetstore.identity.infrastructure;

import ik.am.jpetstore.account.infrastructure.AccountMapper;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * T255: 真实凭据认证适配。
 *
 * <p>凭据来自 signon 表的哈希，认证查询不依赖横幅数据（PER-03）。停用账户不能登录，且失败方式
 * 与未知用户完全一致，不泄露账户是否存在（Q10、plan §5.1）。返回的主体只含用户名、哈希与
 * 权限，不缓存整份账户资料（NFR-04）。</p>
 */
@Service
public class AccountUserDetailsService implements UserDetailsService {

    /** 未登录与停用账户使用同一提示，避免暴露账户是否存在。 */
    private static final String GENERIC_FAILURE = "用户名或密码不正确";

    private final AccountMapper mapper;

    public AccountUserDetailsService(AccountMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        AccountMapper.CredentialsRow credentials = mapper.selectCredentials(username);
        if (credentials == null) {
            throw new UsernameNotFoundException(GENERIC_FAILURE);
        }
        AccountMapper.AccountRow account = mapper.selectAccount(username);
        if (account != null && "D".equalsIgnoreCase(account.status())) {
            throw new UsernameNotFoundException(GENERIC_FAILURE);
        }
        return User.withUsername(credentials.username())
                .password(credentials.passwordHash())
                .authorities("ROLE_USER")
                .build();
    }
}
