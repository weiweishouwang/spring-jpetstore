package ik.am.jpetstore.account.web;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T201: account HTTP 请求/响应契约及验证。
 *
 * <p>对齐 docs/contracts/openapi.yaml 的 account 部分：字段名与必填性、金额与标识的字符串
 * 语义、合法枚举，以及响应中绝不出现密码或哈希（NFR-04）。</p>
 */
class AccountDtosTest {

    private final ObjectMapper objectMapper = JsonMapperHolder.strictMapper();

    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();

    private final Validator validator = validatorFactory.getValidator();

    @Test
    void accountResponseExposesOnlyPublicFields() throws Exception {
        AccountDtos.AccountResponse response = new AccountDtos.AccountResponse(
                "alice",
                "alice@example.com",
                "070-1234-5678",
                new AccountDtos.Address("Alice", "Ishikawa", "1-2-3 Sakuragaoka", null, "Shibuya", "Tokyo",
                        "150-0001", "JP"),
                new AccountDtos.Preferences("english", "BIRDS", true, false));

        String json = objectMapper.writeValueAsString(response);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.propertyNames())
                .containsExactlyInAnyOrder("username", "email", "phone", "address", "preferences");
        assertThat(node.get("address").get("address2").isNull())
                .as("address2 is optional (Q01) and serialized as null")
                .isTrue();
        assertThat(json).doesNotContain("password").doesNotContain("passwordHash");
    }

    @Test
    void registrationRequestRejectsInvalidUsernamePasswordAndEmail() {
        AccountDtos.RegisterRequest request = new AccountDtos.RegisterRequest(
                "  ", "short", "short", "not-an-email", "070-1234-5678",
                validAddress(), validPreferences());

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("username", "password", "repeatedPassword", "email");
    }

    @Test
    void registrationRequestRejectsMismatchedPasswords() {
        AccountDtos.RegisterRequest request = new AccountDtos.RegisterRequest(
                "alice", "alice-pass-123", "different-pass-456", "alice@example.com", "070-1234-5678",
                validAddress(), validPreferences());

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("passwordConfirmed");
    }

    @Test
    void registrationRequestRejectsUnknownLanguageAndCategory() {
        AccountDtos.RegisterRequest request = new AccountDtos.RegisterRequest(
                "alice", "alice-pass-123", "alice-pass-123", "alice@example.com", "070-1234-5678",
                validAddress(), new AccountDtos.Preferences("french", "PENGUINS", true, false));

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("preferences.languagePreference", "preferences.favouriteCategoryId");
    }

    @Test
    void addressSecondLineIsOptionalButBounded() {
        AccountDtos.Address withoutSecondLine = new AccountDtos.Address("Alice", "Ishikawa", "1-2-3 Sakuragaoka",
                null, "Shibuya", "Tokyo", "150-0001", "JP");
        assertThat(validator.validate(withoutSecondLine)).isEmpty();

        AccountDtos.Address tooLongSecondLine = new AccountDtos.Address("Alice", "Ishikawa", "1-2-3 Sakuragaoka",
                "x".repeat(41), "Shibuya", "Tokyo", "150-0001", "JP");
        assertThat(validator.validate(tooLongSecondLine)).isNotEmpty();
    }

    @Test
    void requestRejectsUnknownFieldsSoCredentialsCannotBeInjected() {
        assertThatThrownBy(() -> objectMapper.readValue(
                "{\"username\":\"alice\",\"passwordHash\":\"$2a$10$leak\"}", AccountDtos.RegisterRequest.class))
                .as("unknown fields are rejected, so no hash field can be injected")
                .isInstanceOf(Exception.class);
    }

    private AccountDtos.Address validAddress() {
        return new AccountDtos.Address("Alice", "Ishikawa", "1-2-3 Sakuragaoka", null, "Shibuya", "Tokyo",
                "150-0001", "JP");
    }

    private AccountDtos.Preferences validPreferences() {
        return new AccountDtos.Preferences("english", "BIRDS", true, false);
    }

    /** Jackson 3 mapper with the strictness the application uses for request bodies. */
    private static final class JsonMapperHolder {

        private static ObjectMapper strictMapper() {
            return JsonMapper.builder()
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .build();
        }
    }
}
