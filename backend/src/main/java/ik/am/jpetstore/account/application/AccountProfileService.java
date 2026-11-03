package ik.am.jpetstore.account.application;

import ik.am.jpetstore.account.api.AccountProfileUpdate;
import ik.am.jpetstore.account.api.AccountQuery;
import ik.am.jpetstore.account.domain.AccountModels.AccountDetails;
import ik.am.jpetstore.account.domain.AccountModels.AccountProfile;
import ik.am.jpetstore.account.domain.AccountModels.Address;
import ik.am.jpetstore.account.domain.AccountModels.Credentials;
import ik.am.jpetstore.account.domain.AccountModels.Preferences;
import ik.am.jpetstore.account.infrastructure.AccountMapper;
import ik.am.jpetstore.shared.error.BusinessFailure;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * T253: 本人资料与偏好更新服务。
 *
 * <p>只能修改本人资料：userId 由当前身份解析，请求里没有用户名字段（ACC-07）。密码留空表示
 * 不改密；非空时必须一致，成功后旧密码失效（ACC-08、Q13）。资料、偏好与密码在同一事务内
 * 更新，任一步失败整体回滚（NFR-01）。</p>
 */
@Service
public class AccountProfileService implements AccountProfileUpdate, AccountQuery {

    private final AccountMapper mapper;

    private final PasswordEncoder passwordEncoder;

    public AccountProfileService(AccountMapper mapper, PasswordEncoder passwordEncoder) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountDetails> findByUserId(String userId) {
        AccountMapper.AccountRow account = mapper.selectAccount(userId);
        if (account == null) {
            return Optional.empty();
        }
        AccountMapper.PreferencesRow preferences = mapper.selectPreferences(userId);
        AccountMapper.CredentialsRow credentials = mapper.selectCredentials(userId);
        return Optional.of(toDetails(account, preferences, credentials));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AccountDetails> findByUsername(String username) {
        return findByUserId(username);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean usernameExists(String username) {
        return mapper.selectCredentials(username) != null;
    }

    @Override
    @Transactional
    public void update(String userId, ProfileUpdateRequest request) {
        AccountMapper.AccountRow account = new AccountMapper.AccountRow(userId, request.email(),
                request.address().firstName(), request.address().lastName(), request.phone(),
                request.address().address1(), request.address().address2(), request.address().city(),
                request.address().state(), request.address().zip(), request.address().country(), null);
        AccountMapper.PreferencesRow preferences = new AccountMapper.PreferencesRow(userId,
                request.preferences().languagePreference(), request.preferences().favouriteCategoryId(),
                request.preferences().listOption(), request.preferences().bannerOption());

        mapper.updateAccount(account);
        mapper.updateProfile(preferences);

        if (hasNewPassword(request.password())) {
            if (!request.password().equals(request.repeatedPassword())) {
                throw new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED, "两次密码必须一致");
            }
            String hash;
            try {
                hash = passwordEncoder.encode(request.password());
            } catch (IllegalArgumentException e) {
                throw new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED, e.getMessage());
            }
            mapper.updateCredentials(new AccountMapper.CredentialsRow(userId, hash));
        }
    }

    private boolean hasNewPassword(String password) {
        return password != null && !password.isEmpty();
    }

    /** 三行扁平快照 → 领域视图；横幅数据不参与账户读取（PER-03）。 */
    private AccountDetails toDetails(AccountMapper.AccountRow account, AccountMapper.PreferencesRow preferences,
            AccountMapper.CredentialsRow credentials) {
        Address address = new Address(account.firstName(), account.lastName(), account.addr1(), account.addr2(),
                account.city(), account.state(), account.zip(), account.country());
        AccountProfile profile = new AccountProfile(account.userId(), account.email(), account.firstName(),
                account.lastName(), account.phone(), address);
        Preferences preference = preferences == null
                ? new Preferences("english", "FISH", false, false)
                : new Preferences(preferences.languagePreference(), preferences.favouriteCategoryId(),
                        Boolean.TRUE.equals(preferences.listOption()), Boolean.TRUE.equals(preferences.bannerOption()));
        Credentials credential = credentials == null
                ? new Credentials(account.userId(), null)
                : new Credentials(credentials.username(), credentials.passwordHash());
        return new AccountDetails(profile, preference, credential);
    }
}
