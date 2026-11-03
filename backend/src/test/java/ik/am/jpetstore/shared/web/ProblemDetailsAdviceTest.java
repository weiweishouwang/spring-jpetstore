package ik.am.jpetstore.shared.web;

import ik.am.jpetstore.shared.error.BusinessFailure;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T233: 统一 Problem Details 映射。
 *
 * <p>业务 400/404/409、Malformed JSON 400、405/415 保持框架状态与必要响应头、500 脱敏；
 * 不允许把一切错误都吞成 500。</p>
 */
class ProblemDetailsAdviceTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new ProblemDetailsAdvice(new ProblemDetailsWriter(objectMapper)))
                .build();
    }

    @Test
    void businessFailuresKeepTheirConfiguredStatusAndCode() throws Exception {
        mockMvc.perform(get("/probe/business-failure"))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("OUT_OF_STOCK"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void validationFailuresMapTo400WithViolations() throws Exception {
        mockMvc.perform(post("/probe/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.violations[0].field").value("text"));
    }

    @Test
    void malformedJsonMapsTo400WithoutEchoingThePayload() throws Exception {
        mockMvc.perform(post("/probe/validated")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\": broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_JSON"));
    }

    @Test
    void methodNotAllowedKeepsStatusAndAllowHeader() throws Exception {
        mockMvc.perform(post("/probe/get-only"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"));
    }

    @Test
    void unsupportedMediaTypeStays415() throws Exception {
        mockMvc.perform(post("/probe/validated")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("text"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void internalErrorsAreSanitizedTo500() throws Exception {
        String body = mockMvc.perform(get("/probe/internal"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("SELECT")
                .doesNotContain("secret")
                .doesNotContain("SQLException");
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/business-failure")
        String businessFailure() {
            throw new BusinessFailure(BusinessFailure.Code.OUT_OF_STOCK, "库存不足");
        }

        @PostMapping("/probe/validated")
        String validated(@Valid @RequestBody ProbeRequest request) {
            return request.text();
        }

        @GetMapping("/probe/get-only")
        String getOnly() {
            return "ok";
        }

        @GetMapping("/probe/internal")
        String internal() {
            throw new IllegalStateException("SELECT * FROM secret_table (SQLException in log)");
        }
    }

    record ProbeRequest(@NotBlank String text) {
    }
}
