package ik.am.jpetstore.shared.observability;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T381: 请求关联过滤器。
 *
 * <p>验证合法请求标识被沿用、过长或非法标识被丢弃并重新生成、响应带同一个标识、正常与异常路径
 * 都清理 MDC，且请求标识与幂等键彼此独立（plan §9.2、NFR-05）。</p>
 */
class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @AfterEach
    void clearMdc() {
        org.slf4j.MDC.clear();
    }

    @Test
    void aLegalRequestIdIsKept() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        request.addHeader(RequestIdFilter.HEADER, "req-1234");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new org.springframework.mock.web.MockFilterChain());

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("req-1234");
    }

    @Test
    void aMissingRequestIdIsGenerated() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new org.springframework.mock.web.MockFilterChain());

        String generated = response.getHeader(RequestIdFilter.HEADER);
        assertThat(generated).isNotBlank();
        assertThat(generated).hasSize(36);
    }

    @Test
    void anOverlongOrHostileRequestIdIsReplaced() throws Exception {
        for (String candidate : new String[] { "x".repeat(200), "bad id with spaces", "<script>alert(1)</script>" }) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
            request.addHeader(RequestIdFilter.HEADER, candidate);
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, new org.springframework.mock.web.MockFilterChain());

            assertThat(response.getHeader(RequestIdFilter.HEADER))
                    .as("非法标识不会被回显")
                    .isNotEqualTo(candidate)
                    .hasSize(36);
        }
    }

    @Test
    void theMdcIsClearedAfterTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new org.springframework.mock.web.MockFilterChain());

        assertThat(org.slf4j.MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void theMdcIsClearedAfterAFailure() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/cart");
        MockHttpServletResponse response = new MockHttpServletResponse();

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> filter.doFilter(request, response, (req, res) -> {
                    throw new IllegalStateException("boom");
                }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(org.slf4j.MDC.get(RequestIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void theRequestIdIsIndependentOfTheIdempotencyKey() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/orders");
        request.addHeader("Idempotency-Key", "key-1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new org.springframework.mock.web.MockFilterChain());

        assertThat(response.getHeader(RequestIdFilter.HEADER)).isNotEqualTo("key-1");
    }
}
