package ik.am.jpetstore.shared.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * T236: 原生 API-Version 解析。
 *
 * <p>使用 Framework 7 原生版本解析：版本只来自 API-Version 请求头，默认 1.0，唯一解析来源
 * 固定在此处；不叠加路径版本（plan §6.1）。新增版本时只修改 addSupportedVersions 与对应
 * Controller 映射，不允许引入第二套版本机制。</p>
 */
@Configuration
public class ApiVersionConfiguration implements WebMvcConfigurer {

    /** 版本请求头；安全过滤器链的基础设施端点不参与版本语义。 */
    public static final String VERSION_HEADER = "API-Version";

    /** 当前默认与唯一支持的版本。 */
    public static final String SUPPORTED_VERSION = "1.0";

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.useRequestHeader(VERSION_HEADER)
                .setDefaultVersion(SUPPORTED_VERSION)
                .addSupportedVersions(SUPPORTED_VERSION);
    }
}
