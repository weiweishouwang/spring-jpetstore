package ik.am.jpetstore.shared.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * T388: 管理端点专用安全链。
 *
 * <p>管理端点与业务 API 用不同的过滤链：健康探针公开可读，其余管理端点一律要求认证，运营
 * 侧再通过网络层隔离（plan §9.1、NFR-02）。这条链排在业务链之前，业务链的语义保持不变。</p>
 *
 * <p>管理端点不返回任何凭据或配置原文：敏感属性由应用配置遮蔽（见 application-prod.yml）。</p>
 */
@Configuration
public class ManagementSecurityConfiguration {

    /** 允许匿名访问的健康路径。 */
    private static final String[] PUBLIC_HEALTH_PATHS = {
            "/actuator/health", "/actuator/health/**"
    };

    @Bean
    @Order(1)
    public SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/actuator/**")
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(PUBLIC_HEALTH_PATHS).permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .httpBasic(Customizer.withDefaults())
                .formLogin(form -> form.disable());
        return http.build();
    }
}
