package ik.am.jpetstore.account.infrastructure;

import org.apache.ibatis.annotations.Mapper;

/**
 * T240: Account Mapper签名与类型约定。
 *
 * <p>行类型是账户三表（account/profile/signon）的扁平快照；领域模型与行的转换由应用服务负责，
 * 持久层不暴露给外部契约。SQL 定义在 mapper/account/AccountMapper.xml（T242）。</p>
 */
@Mapper
public interface AccountMapper {

    /** account 表行。 */
    record AccountRow(String userId, String email, String firstName, String lastName, String phone,
            String addr1, String addr2, String city, String state, String zip, String country, String status) {
    }

    /** profile 表行；开关使用包装类型以便 MyBatis 构造器映射。 */
    record PreferencesRow(String userId, String languagePreference, String favouriteCategoryId,
            Boolean listOption, Boolean bannerOption) {
    }

    /** signon 表行；哈希永不出现在对外响应中。 */
    record CredentialsRow(String username, String passwordHash) {
    }

    AccountRow selectAccount(String userId);

    PreferencesRow selectPreferences(String userId);

    CredentialsRow selectCredentials(String username);

    int insertAccount(AccountRow row);

    int insertProfile(PreferencesRow row);

    int insertSignon(CredentialsRow row);

    int updateAccount(AccountRow row);

    int updateProfile(PreferencesRow row);

    int updateCredentials(CredentialsRow row);
}
