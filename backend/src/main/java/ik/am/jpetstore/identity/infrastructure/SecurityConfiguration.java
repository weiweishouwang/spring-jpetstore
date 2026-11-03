package ik.am.jpetstore.identity.infrastructure;

import ik.am.jpetstore.identity.web.SecurityJsonHandlers;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.SessionManagementConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * T259: 会话认证和 CSRF 安全链。
 *
 * <p>浏览器同源使用 HttpSession + Cookie 认证（ADR-02）：登录处理 URL 为
 * {@code POST /api/auth/session}（form-urlencoded），登录成功轮换会话标识，退出失效会话并清理
 * Cookie。CSRF 保持开启，缺失或无效 token 一律 403。不开放宽泛 CORS：无跨域部署需求时不开
 * CORS（plan §5.2）。</p>
 *
 * <p>T460（补遗）：Phase 6 引入购物车写接口后，游客会话必须能读写自己的购物车（Q08、CRT-01），
 * 因此把 PUT/DELETE 的 /api/cart/** 也列为公开——写操作仍受 CSRF 保护，归属由会话绑定保证。</p>
 *
 * <p>T468（补遗）：Phase 9 引入同源 SPA 回退后，前端页面外壳必须公开可访问（否则未登录用户连
 * 登录页都打不开），因此授权规则改为“业务 API 需要登录，其余路径公开”。真正的数据访问控制
 * 始终由服务端的身份校验决定，守卫不代替后端授权（NFR-02）。</p>
 */
@Configuration
public class SecurityConfiguration {

    /** 登录处理地址。 */
    public static final String LOGIN_URL = "/api/auth/session";

    /** 退出地址。 */
    public static final String LOGOUT_URL = "/api/auth/logout";

    /** 公开的只读目录与注册入口。 */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/auth/csrf", LOGIN_URL, "/api/accounts", "/api/categories", "/api/categories/**",
            "/api/products", "/api/products/**", "/api/items/**", "/api/cart", "/api/cart/**"};

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityJsonHandlers handlers)
            throws Exception {
        http
                .csrf(Customizer.withDefaults())
                .cors(cors -> cors.disable())
                .sessionManagement(session -> session
                        .sessionFixation(SessionManagementConfigurer.SessionFixationConfigurer::changeSessionId))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/accounts", "/api/cart/**").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/cart/**").permitAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/cart/**").permitAll()
                        .requestMatchers(HttpMethod.POST, LOGIN_URL).permitAll()
                        .requestMatchers(HttpMethod.POST, LOGOUT_URL).permitAll()
                        // 业务 API 需要登录；其余路径是前端页面的外壳，由前端路由处理，
                        // 未登录也能打开（T468：SPA 回退要求前端路由公开可访问）。
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll())
                .formLogin(form -> form
                        .loginProcessingUrl(LOGIN_URL)
                        .usernameParameter("username")
                        .passwordParameter("password")
                        .successHandler(handlers.authenticationSuccessHandler())
                        .failureHandler(handlers.authenticationFailureHandler())
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl(LOGOUT_URL)
                        .logoutSuccessHandler(handlers.logoutSuccessHandler())
                        .deleteCookies("JSESSIONID")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(handlers.authenticationEntryPoint())
                        .accessDeniedHandler(handlers.accessDeniedHandler()));
        return http.build();
    }
}
