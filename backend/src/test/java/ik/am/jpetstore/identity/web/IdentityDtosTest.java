package ik.am.jpetstore.identity.web;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T204: identity HTTP 请求/响应契约及验证。
 *
 * <p>响应只包含契约声明的字段，未登录时 username 为 null，且任何响应都不含凭据。</p>
 */
class IdentityDtosTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory();

    private final Validator validator = validatorFactory.getValidator();

    @Test
    void csrfResponseExposesTokenAndHeaderNameOnly() throws Exception {
        String json = objectMapper.writeValueAsString(new IdentityDtos.CsrfResponse("csrf-token-value",
                "X-CSRF-TOKEN"));
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.propertyNames()).containsExactlyInAnyOrder("token", "headerName");
        assertThat(node.get("headerName").asString()).isEqualTo("X-CSRF-TOKEN");
    }

    @Test
    void sessionResponseCarriesMinimalIdentity() throws Exception {
        String json = objectMapper.writeValueAsString(new IdentityDtos.SessionResponse(true, "alice"));
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.propertyNames()).containsExactlyInAnyOrder("authenticated", "username");
        assertThat(node.get("authenticated").asBoolean()).isTrue();
        assertThat(node.get("username").asString()).isEqualTo("alice");
        assertThat(json).doesNotContain("password");
    }

    @Test
    void anonymousSessionHasNullUsername() throws Exception {
        String json = objectMapper.writeValueAsString(new IdentityDtos.SessionResponse(false, null));
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.get("authenticated").asBoolean()).isFalse();
        assertThat(node.get("username").isNull()).isTrue();
    }

    @Test
    void rejectsEmptyCsrfTokenAndOverlongUsername() {
        assertThat(validator.validate(new IdentityDtos.CsrfResponse(" ", "X-CSRF-TOKEN"))).isNotEmpty();
        assertThat(validator.validate(new IdentityDtos.SessionResponse(true, "x".repeat(26)))).isNotEmpty();
        assertThat(validator.validate(new IdentityDtos.SessionResponse(false, null))).isEmpty();
    }
}
