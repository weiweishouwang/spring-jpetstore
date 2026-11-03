package ik.am.jpetstore.account.application;

import ik.am.jpetstore.account.api.AccountRegistration;
import ik.am.jpetstore.account.infrastructure.AccountMapper;
import ik.am.jpetstore.shared.error.BusinessFailure;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T251: 账户原子注册服务。
 *
 * <p>在同一事务内写入账户、偏好与凭据：任一步失败都不留下部分数据（ACC-03、NFR-01）。
 * 用户名唯一由数据库约束最终保证，冲突映射为 {@code USERNAME_CONFLICT}，不采用"先查再插"
 * （ACC-02）。注册成功不建立登录身份（ACC-04）。</p>
 */
@Service
public class AccountRegistrationService implements AccountRegistration {

    private static final int MAX_USERNAME_LENGTH = 25;

    private final AccountMapper mapper;

    private final PasswordEncoder passwordEncoder;

    public AccountRegistrationService(AccountMapper mapper, PasswordEncoder passwordEncoder) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void register(RegistrationRequest request) {
        String username = normalizeUsername(request.username());
        validatePasswords(request.password(), request.repeatedPassword());

        String passwordHash;
        try {
            passwordHash = passwordEncoder.encode(request.password());
        } catch (IllegalArgumentException e) {
            throw new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED, e.getMessage());
        }

        AccountMapper.AccountRow account = new AccountMapper.AccountRow(username, request.email(),
                request.address().firstName(), request.address().lastName(), request.phone(),
                request.address().address1(), request.address().address2(), request.address().city(),
                request.address().state(), request.address().zip(), request.address().country(), "P");
        AccountMapper.PreferencesRow preferences = new AccountMapper.PreferencesRow(username,
                request.preferences().languagePreference(), request.preferences().favouriteCategoryId(),
                request.preferences().listOption(), request.preferences().bannerOption());

        try {
            mapper.insertAccount(account);
            mapper.insertProfile(preferences);
            mapper.insertSignon(new AccountMapper.CredentialsRow(username, passwordHash));
        } catch (DuplicateKeyException e) {
            // 用户名唯一由数据库约束保证；冲突时整体回滚，既有账户不受影响。
            throw new BusinessFailure(BusinessFailure.Code.USERNAME_CONFLICT, "用户名已存在");
        }
    }

    /** 用户名 trim 后 1～25 字符，大小写敏感（Q13）。 */
    private String normalizeUsername(String username) {
        String trimmed = username == null ? "" : username.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_USERNAME_LENGTH) {
            throw new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED, "用户名长度必须为 1～25 字符");
        }
        return trimmed;
    }

    /** 两次密码必须一致（ACC-03）；密码不 trim（Q13）。 */
    private void validatePasswords(String password, String repeatedPassword) {
        if (password == null || password.isEmpty()) {
            throw new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED, "密码不能为空");
        }
        if (!password.equals(repeatedPassword)) {
            throw new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED, "两次密码必须一致");
        }
    }
}
