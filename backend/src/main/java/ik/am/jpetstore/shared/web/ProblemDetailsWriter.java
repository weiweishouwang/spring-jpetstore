package ik.am.jpetstore.shared.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import ik.am.jpetstore.shared.error.BusinessFailure;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * T232: 统一安全与MVC错误序列化。
 *
 * <p>同一个序列化器服务两处：MVC 的 ControllerAdvice（{@link ProblemDetailsAdvice}）与安全
 * 过滤链的 JSON 处理器。输出 RFC 9457 字段与扩展字段（code/requestId/violations）；
 * type 使用 about:blank，直到项目拥有真实的错误文档 URI（plan §6.4，不伪造文档链接）。
 * 任何内部异常细节、SQL、凭据或被拒绝的输入原值都不会出现在输出中（NFR-04、NFR-05）。</p>
 */
@Component
public class ProblemDetailsWriter {

    /** RFC 9457 media type。 */
    public static final String MEDIA_TYPE = "application/problem+json";

    private final ObjectMapper objectMapper;

    public ProblemDetailsWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 业务失败 → Problem JSON 字符串。 */
    public String body(BusinessFailure failure, String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", failure.getMessage());
        body.put("status", failure.status());
        body.put("detail", failure.getMessage());
        body.put("code", failure.code().name());
        body.put("requestId", requestId);
        if (!failure.violations().isEmpty()) {
            body.put("violations", failure.violations().stream()
                    .map(violation -> Map.of("field", violation.field(), "message", violation.message()))
                    .toList());
        }
        return objectMapper.writeValueAsString(body);
    }

    /** 内部错误 → 脱敏 Problem JSON；原始异常信息绝不输出。 */
    public String internalErrorBody(String requestId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", "服务器内部错误");
        body.put("status", 500);
        body.put("code", BusinessFailure.Code.INTERNAL_ERROR.name());
        body.put("requestId", requestId);
        return objectMapper.writeValueAsString(body);
    }

    /** 写入 servlet 响应；供安全过滤链使用（ControllerAdvice 走 ResponseEntity 路径）。 */
    public void write(HttpServletResponse response, BusinessFailure failure, String requestId)
            throws IOException {
        response.setStatus(failure.status());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(body(failure, requestId));
    }
}
