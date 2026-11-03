package ik.am.jpetstore.account.domain;

/**
 * T184: 账户资料、偏好与凭据内部数据类型。
 *
 * <p>只描述业务数据，不含持久化注解、不含校验逻辑、不暴露给外部契约。用户名按 Q13 trim
 * 后 1～25 字符且大小写敏感；地址第二行选填（Q01）；凭据只保存哈希且永不出现在响应中
 * （NFR-04）。</p>
 */
public final class AccountModels {

    private AccountModels() {
    }

    /** 地址（Q01：第二行选填）。 */
    public record Address(String firstName, String lastName, String address1, String address2,
            String city, String state, String zip, String country) {
    }

    /** 账户资料（ACC-01、ACC-07）。 */
    public record AccountProfile(String userId, String email, String firstName, String lastName,
            String phone, Address address) {
    }

    /** 用户偏好（PER-01～04、Q09、Q10）。 */
    public record Preferences(String languagePreference, String favouriteCategoryId,
            boolean listOption, boolean bannerOption) {
    }

    /** 凭据：仅保存带算法标识的哈希（NFR-04）。 */
    public record Credentials(String userId, String passwordHash) {
    }

    /** 账户视图：资料 + 偏好 + 凭据。 */
    public record AccountDetails(AccountProfile profile, Preferences preferences, Credentials credentials) {
    }
}
