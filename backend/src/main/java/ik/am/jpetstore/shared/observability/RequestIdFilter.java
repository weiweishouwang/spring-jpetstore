package ik.am.jpetstore.shared.observability;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * T382: 请求关联过滤器。
 *
 * <p>每个请求都有一个关联标识：客户端提供且合法时沿用，否则由服务端生成。标识只允许字母、
 * 数字、连字符与下划线且长度有上限，避免把任意内容带进日志（plan §9.2、NFR-05）。</p>
 *
 * <p>标识写入 MDC 供日志模板使用，并在 finally 中清理；请求结束后仍可通过响应头取回，与幂等键
 * 彼此独立。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestIdFilter.class);

    /** 请求与响应上的关联标识头。 */
    public static final String HEADER = "X-Request-Id";

    /** MDC 中的键名。 */
    public static final String MDC_KEY = "requestId";

    /** 允许的最大长度。 */
    private static final int MAX_LENGTH = 64;

    /** 只允许字母、数字、连字符与下划线。 */
    private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9_-]{1," + MAX_LENGTH + "}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            // 调试级别：生产默认 INFO 级别不会输出，请求路径不含查询参数与正文。
            logger.debug("request completed: {} {}", request.getMethod(), request.getRequestURI());
            MDC.remove(MDC_KEY);
        }
    }

    /** 合法标识沿用，否则生成新的 UUID。 */
    private String resolve(String candidate) {
        if (candidate != null && SAFE.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
