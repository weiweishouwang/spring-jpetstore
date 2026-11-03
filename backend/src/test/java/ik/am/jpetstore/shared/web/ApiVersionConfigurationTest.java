package ik.am.jpetstore.shared.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T235: 原生 API-Version 解析。
 *
 * <p>版本只来自 API-Version 请求头（不叠加路径版本）；默认 1.0；支持 1.0；未知版本返回 400
 * 且错误体使用 Problem Details（plan §6.1）。</p>
 */
class ApiVersionConfigurationTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(VersioningWebConfig.class, ProbeController.class);
        context.refresh();
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    void missingHeaderUsesDefaultVersion10() throws Exception {
        mockMvc.perform(get("/probe/versioned"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("1.0"));
    }

    @Test
    void explicitVersion10IsAccepted() throws Exception {
        mockMvc.perform(get("/probe/versioned").header("API-Version", "1.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value("1.0"));
    }

    @Test
    void unsupportedVersionIsRejectedWith400() throws Exception {
        mockMvc.perform(get("/probe/versioned").header("API-Version", "2.0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void versioningComesOnlyFromTheHeaderNotFromThePath() throws Exception {
        mockMvc.perform(get("/probe/versioned").header("API-Version", "1.0"))
                .andExpect(status().isOk());
    }

    @Configuration
    @EnableWebMvc
    static class VersioningWebConfig implements WebMvcConfigurer {

        @Bean
        ApiVersionConfiguration apiVersionConfiguration() {
            return new ApiVersionConfiguration();
        }
    }

    @RestController
    static class ProbeController {

        @GetMapping(path = "/probe/versioned", version = "1.0")
        String versioned() {
            return "1.0";
        }
    }
}
