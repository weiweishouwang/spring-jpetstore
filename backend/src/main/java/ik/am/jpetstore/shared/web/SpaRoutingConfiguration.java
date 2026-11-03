package ik.am.jpetstore.shared.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * T394: 同源 SPA 路由回退。
 *
 * <p>浏览器端路由的路径（/cart、/orders/1001 等）必须回前端入口，由前端路由接管；但 API、
 * 管理端点与静态资源不能被回退成 HTML：未知 API 仍然是错误响应（plan §3.1、NFR-02）。</p>
 *
 * <p>回退只对明确要求 HTML 的 GET 生效，并且只在构建产物存在时启用：没有前端产物时后端可以
 * 独立运行，未知路径照旧 404。</p>
 */
@Configuration
public class SpaRoutingConfiguration {

    /** 不参与回退的路径前缀。 */
    private static final String[] NOT_FALLBACK = {
            "/api", "/actuator", "/assets", "/resources", "/webjars", "/error"
    };

    @Bean
    public FilterRegistrationBean<SpaFallbackFilter> spaFallbackFilter() {
        FilterRegistrationBean<SpaFallbackFilter> registration =
                new FilterRegistrationBean<>(new SpaFallbackFilter());
        registration.setOrder(Ordered.LOWEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /** SPA 回退过滤器。 */
    static final class SpaFallbackFilter extends OncePerRequestFilter {

        private final byte[] index = readIndex();

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            if (index == null || !"GET".equals(request.getMethod()) || isExcluded(request) || !acceptsHtml(request)) {
                chain.doFilter(request, response);
                return;
            }
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.TEXT_HTML_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.getOutputStream().write(index);
        }

        private boolean isExcluded(HttpServletRequest request) {
            String path = request.getRequestURI();
            for (String prefix : NOT_FALLBACK) {
                if (path.startsWith(prefix)) {
                    return true;
                }
            }
            return false;
        }

        private boolean acceptsHtml(HttpServletRequest request) {
            String accept = request.getHeader("Accept");
            return accept == null || accept.contains(MediaType.TEXT_HTML_VALUE);
        }

        private static byte[] readIndex() {
            // 开发时入口在类路径根目录，打包后位于静态资源目录。
            for (String candidate : new String[] { "index.html", "static/index.html" }) {
                try {
                    ClassPathResource resource = new ClassPathResource(candidate);
                    if (!resource.exists()) {
                        continue;
                    }
                    try (var input = resource.getInputStream()) {
                        return input.readAllBytes();
                    }
                } catch (IOException e) {
                    // 继续尝试下一个候选位置。
                }
            }
            return null;
        }
    }
}
