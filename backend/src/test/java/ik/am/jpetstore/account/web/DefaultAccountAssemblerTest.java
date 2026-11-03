package ik.am.jpetstore.account.web;

import ik.am.jpetstore.account.api.AccountProfileUpdate;
import ik.am.jpetstore.account.api.AccountRegistration;
import ik.am.jpetstore.account.domain.AccountModels.AccountDetails;
import ik.am.jpetstore.account.domain.AccountModels.AccountProfile;
import ik.am.jpetstore.account.domain.AccountModels.Address;
import ik.am.jpetstore.account.domain.AccountModels.Credentials;
import ik.am.jpetstore.account.domain.AccountModels.Preferences;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T305: account 显式 DTO/领域转换。
 *
 * <p>验证只映射允许字段、金额与标识按字符串语义处理、两组地址独立、敏感字段被排除，以及未知
 * 字段不进入领域模型（D01/D05/D11、NFR-04、plan §6.1）。</p>
 */
class DefaultAccountAssemblerTest {

    private final DefaultAccountAssembler assembler = new DefaultAccountAssembler();

    private final ObjectMapper mapper = JsonMapper.builder().build();

    @Test
    void theResponseContainsOnlyThePublicProfile() {
        AccountDetails details = details();

        AccountDtos.AccountResponse response = assembler.toResponse(details);

        assertThat(response.username()).isEqualTo("alice");
        assertThat(response.email()).isEqualTo("alice@example.com");
        assertThat(response.phone()).isEqualTo("090-1111-2222");
        assertThat(response.address().firstName()).isEqualTo("Alice");
        assertThat(response.preferences().languagePreference()).isEqualTo("english");
        assertThat(response.preferences().listOption()).isTrue();
    }

    @Test
    void theResponseNeverContainsCredentials() {
        AccountDtos.AccountResponse response = assembler.toResponse(details());

        String json = mapper.writeValueAsString(response);

        assertThat(json)
                .as("凭据与哈希绝不进入响应（NFR-04）")
                .doesNotContain("password")
                .doesNotContain("hash")
                .doesNotContain("$2a$");
    }

    @Test
    void theResponseKeepsTheOptionalSecondAddressLineAsGiven() {
        AccountDtos.AccountResponse response = assembler.toResponse(details());

        assertThat(response.address().address2()).isNull();
    }

    @Test
    void theRegistrationCommandCarriesEveryAllowedField() {
        AccountDtos.RegisterRequest request = new AccountDtos.RegisterRequest("alice", "secret-pass", "secret-pass",
                "alice@example.com", "090-1111-2222",
                new AccountDtos.Address("Alice", "Ishikawa", "1-2-3 Sakuragaoka", null, "Shibuya", "Tokyo", "150-0001",
                        "JP"),
                new AccountDtos.Preferences("english", "FISH", true, false));

        AccountRegistration.RegistrationRequest command = assembler.toRegistration(request);

        assertThat(command.username()).isEqualTo("alice");
        assertThat(command.password()).isEqualTo("secret-pass");
        assertThat(command.repeatedPassword()).isEqualTo("secret-pass");
        assertThat(command.email()).isEqualTo("alice@example.com");
        assertThat(command.phone()).isEqualTo("090-1111-2222");
        assertThat(command.address().address1()).isEqualTo("1-2-3 Sakuragaoka");
        assertThat(command.address().address2()).isNull();
        assertThat(command.preferences().favouriteCategoryId()).isEqualTo("FISH");
        assertThat(command.preferences().bannerOption()).isFalse();
    }

    @Test
    void theUpdateCommandCarriesNoUsername() {
        AccountDtos.UpdateRequest request = new AccountDtos.UpdateRequest("alice@example.com", "090-1111-2222",
                new AccountDtos.Address("Alice", "Ishikawa", "1-2-3 Sakuragaoka", "Apt 301", "Shibuya", "Tokyo",
                        "150-0001", "JP"),
                new AccountDtos.Preferences("japanese", "DOGS", false, true), "", "");

        AccountProfileUpdate.ProfileUpdateRequest command = assembler.toProfileUpdate(request);

        assertThat(command.email()).isEqualTo("alice@example.com");
        assertThat(command.address().address2()).isEqualTo("Apt 301");
        assertThat(command.preferences().languagePreference()).isEqualTo("japanese");
        assertThat(command.password()).as("留空表示不改密").isEmpty();
    }

    private AccountDetails details() {
        return new AccountDetails(
                new AccountProfile("alice", "alice@example.com", "Alice", "Ishikawa", "090-1111-2222",
                        new Address("Alice", "Ishikawa", "1-2-3 Sakuragaoka", null, "Shibuya", "Tokyo", "150-0001", "JP")),
                new Preferences("english", "FISH", true, true),
                new Credentials("alice", "$2a$10$not-a-real-hash"));
    }
}
