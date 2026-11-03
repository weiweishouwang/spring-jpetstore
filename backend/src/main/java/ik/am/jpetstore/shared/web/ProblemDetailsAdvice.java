package ik.am.jpetstore.shared.web;

import java.util.UUID;

import ik.am.jpetstore.shared.error.BusinessFailure;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * T234: 统一 Problem Details 映射。
 *
 * <p>继承 {@link ResponseEntityExceptionHandler}，框架异常（404/405/415 等）保持原生状态与
 * 响应头，不被 catch-all 吞成 500；业务失败与 Malformed JSON 走 {@link ProblemDetailsWriter}
 * 的同一序列化；未知异常统一脱敏为 INTERNAL_ERROR（plan §6.4）。requestId 在观测基础设施
 * （Phase 9 的请求关联过滤器）落地前由本类生成。</p>
 */
@RestControllerAdvice
public class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    private final ProblemDetailsWriter writer;

    public ProblemDetailsAdvice(ProblemDetailsWriter writer) {
        this.writer = writer;
    }

    /** 业务失败：状态与错误码来自 BusinessFailure。 */
    @ExceptionHandler(BusinessFailure.class)
    public ResponseEntity<String> businessFailure(BusinessFailure failure) {
        return problem(failure.status(), writer.body(failure, requestId()));
    }

    /** Malformed JSON：400 + MALFORMED_JSON，不回显请求体。 */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, org.springframework.http.HttpStatusCode status, WebRequest request) {
        BusinessFailure failure = new BusinessFailure(BusinessFailure.Code.MALFORMED_JSON, "请求体不是合法的JSON");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.parseMediaType(ProblemDetailsWriter.MEDIA_TYPE))
                .body(writer.body(failure, requestId()));
    }

    /** 字段校验失败：400 + VALIDATION_FAILED + 字段级 violations，不带被拒绝的原值。 */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            org.springframework.web.bind.MethodArgumentNotValidException ex, HttpHeaders headers,
            org.springframework.http.HttpStatusCode status, WebRequest request) {
        java.util.List<BusinessFailure.Violation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new BusinessFailure.Violation(error.getField(), error.getDefaultMessage()))
                .toList();
        BusinessFailure failure = new BusinessFailure(BusinessFailure.Code.VALIDATION_FAILED, "输入不合法",
                violations);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.parseMediaType(ProblemDetailsWriter.MEDIA_TYPE))
                .body(writer.body(failure, requestId()));
    }

    /** 未知异常：500 且完全脱敏，不暴露 SQL、栈或输入。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> unexpected(Exception failure) {
        logger.warn("Unhandled exception mapped to INTERNAL_ERROR", failure);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(MediaType.parseMediaType(ProblemDetailsWriter.MEDIA_TYPE))
                .body(writer.internalErrorBody(requestId()));
    }

    private ResponseEntity<String> problem(int status, String body) {
        return ResponseEntity.status(status)
                .contentType(MediaType.parseMediaType(ProblemDetailsWriter.MEDIA_TYPE))
                .body(body);
    }

    private String requestId() {
        return UUID.randomUUID().toString();
    }
}
