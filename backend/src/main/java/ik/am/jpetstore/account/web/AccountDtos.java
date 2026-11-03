package ik.am.jpetstore.account.web;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * T202: account HTTP 请求/响应契约及验证。
 *
 * <p>对齐 docs/contracts/openapi.yaml 的 account 部分。用户名 trim 后 1～25 字符、大小写敏感
 * （Q13）；密码 8～64 字符且 UTF-8 不超过 72 字节（字节长度由应用服务校验，不在此截断）；
 * 两次密码必须一致，仅用于输入确认（NFR-04）。响应类型不含任何密码或哈希字段。</p>
 */
public final class AccountDtos {

    private AccountDtos() {
    }

    /** 地址（Q01：第二行选填，非空时最多 40 字符）。 */
    public record Address(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @NotBlank @Size(max = 80) String address1,
            @Size(max = 40) String address2,
            @NotBlank @Size(max = 80) String city,
            @NotBlank @Size(max = 80) String state,
            @NotBlank @Size(max = 20) String zip,
            @NotBlank @Size(max = 20) String country) {
    }

    /** 用户偏好（Q09：只保存 english/japanese；Q10：偏好分类取自五个固定分类）。 */
    public record Preferences(
            @NotBlank @Pattern(regexp = "english|japanese") String languagePreference,
            @NotBlank @Pattern(regexp = "FISH|DOGS|REPTILES|CATS|BIRDS") String favouriteCategoryId,
            boolean listOption,
            boolean bannerOption) {
    }

    /** 注册请求。username 不是可修改字段，更新请求中不存在该字段（ACC-07）。 */
    public record RegisterRequest(
            @NotBlank @Size(min = 1, max = 25) String username,
            @NotBlank @Size(min = 8, max = 64) String password,
            @NotBlank @Size(min = 8, max = 64) String repeatedPassword,
            @NotBlank @Size(max = 80) @Email String email,
            @NotBlank @Size(max = 80) String phone,
            @NotNull @Valid Address address,
            @NotNull @Valid Preferences preferences) {

        /** 两次密码一致性；仅用于校验，不进入任何响应。 */
        @AssertTrue
        @JsonIgnore
        public boolean isPasswordConfirmed() {
            return password == null || repeatedPassword == null || password.equals(repeatedPassword);
        }
    }

    /**
     * 资料更新请求。
     *
     * @param password         留空表示不改密
     * @param repeatedPassword 留空表示不改密；非空时必须与 password 一致
     */
    public record UpdateRequest(
            @NotBlank @Size(max = 80) @Email String email,
            @NotBlank @Size(max = 80) String phone,
            @NotNull @Valid Address address,
            @NotNull @Valid Preferences preferences,
            @Size(max = 64) String password,
            @Size(max = 64) String repeatedPassword) {

        /** 两次密码一致性；两者都留空时视为不改密。 */
        @AssertTrue
        @JsonIgnore
        public boolean isPasswordConfirmed() {
            if (password == null || password.isEmpty()) {
                return repeatedPassword == null || repeatedPassword.isEmpty();
            }
            return password.equals(repeatedPassword);
        }
    }

    /** 账户响应：只含公开资料，绝不含凭据。 */
    public record AccountResponse(String username, String email, String phone, Address address,
            Preferences preferences) {
    }
}
