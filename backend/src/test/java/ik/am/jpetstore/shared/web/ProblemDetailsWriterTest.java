package ik.am.jpetstore.shared.web;

import ik.am.jpetstore.shared.error.BusinessFailure;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T231: 统一安全与MVC错误序列化。
 *
 * <p>同一个 {@link ProblemDetailsWriter} 同时服务 MVC ControllerAdvice 与安全过滤链：
 * RFC 9457 字段（type/title/status/detail/instance）加扩展 code/requestId/violations；
 * violations 不携带被拒绝的原值；输出 Content-Type 为 application/problem+json。</p>
 */
class ProblemDetailsWriterTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private final ProblemDetailsWriter writer = new ProblemDetailsWriter(objectMapper);

    @Test
    void writesRfc9457FieldsAndStableCode() throws Exception {
        String body = writer.body(new BusinessFailure(BusinessFailure.Code.OUT_OF_STOCK, "库存不足"),
                "req-1");

        JsonNode node = objectMapper.readTree(body);
        assertThat(node.get("type").asString()).isEqualTo("about:blank");
        assertThat(node.get("title").asString()).isEqualTo("库存不足");
        assertThat(node.get("status").asInt()).isEqualTo(409);
        assertThat(node.get("detail").asString()).isEqualTo("库存不足");
        assertThat(node.get("code").asString()).isEqualTo("OUT_OF_STOCK");
        assertThat(node.get("requestId").asString()).isEqualTo("req-1");
    }

    @Test
    void statusStaysConsistentWithTheFailureCode() throws Exception {
        String body = writer.body(new BusinessFailure(BusinessFailure.Code.ORDER_NOT_FOUND, "订单不存在"),
                "req-2");

        assertThat(objectMapper.readTree(body).get("status").asInt()).isEqualTo(404);
    }

    @Test
    void violationsCarryFieldAndMessageButNeverTheRejectedValue() throws Exception {
        BusinessFailure failure = new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED, "输入不合法",
                List.of(new BusinessFailure.Violation("password", "长度必须为8～64字符")));

        String body = writer.body(failure, "req-3");
        JsonNode node = objectMapper.readTree(body);

        assertThat(node.get("violations")).hasSize(1);
        assertThat(node.get("violations").get(0).get("field").asString()).isEqualTo("password");
        assertThat(node.get("violations").get(0).get("message").asString()).isEqualTo("长度必须为8～64字符");
        assertThat(node.get("violations").get(0).propertyNames()).containsExactlyInAnyOrder("field", "message");
        assertThat(body).doesNotContain("rejectedValue");
    }

    @Test
    void omitsAbsentOptionalFieldsInsteadOfWritingNulls() throws Exception {
        String body = writer.body(new BusinessFailure(BusinessFailure.Code.INVALID_CREDENTIALS, "认证失败"),
                "req-4");

        JsonNode node = objectMapper.readTree(body);
        assertThat(node.has("violations")).isFalse();
        assertThat(node.has("instance")).isFalse();
    }

    @Test
    void writesProblemJsonToServletResponseForTheFilterChain() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, new BusinessFailure(BusinessFailure.Code.CSRF_INVALID, "CSRF 校验失败"), "req-5");

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(objectMapper.readTree(response.getContentAsString()).get("code").asString())
                .isEqualTo("CSRF_INVALID");
    }

    @Test
    void internalErrorsNeverLeakTheirUnderlyingMessage() throws Exception {
        String body = writer.internalErrorBody("req-6");

        JsonNode node = objectMapper.readTree(body);
        assertThat(node.get("status").asInt()).isEqualTo(500);
        assertThat(node.get("code").asString()).isEqualTo("INTERNAL_ERROR");
        assertThat(body).doesNotContain("SQLException").doesNotContain("secret");
    }
}
