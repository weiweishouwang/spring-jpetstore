package ik.am.jpetstore.shared.configuration;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * T183: 统一 UTC Clock 与展示时区（Q11）。
 *
 * <p>时间一律以 UTC 存储与传输；界面按 Asia/Shanghai 展示。业务代码只注入 {@link Clock}，
 * 测试可替换为固定时钟。不要在业务代码中直接调用 {@code Instant.now()} 或 {@code LocalDateTime.now()}。</p>
 */
@Configuration
public class ClockConfiguration {

    /** 存储与传输使用的 UTC 时钟。 */
    @Bean
    public Clock utcClock() {
        return Clock.systemUTC();
    }

    /** 界面展示时区（Q11）。 */
    @Bean
    public ZoneId displayZone() {
        return ZoneId.of("Asia/Shanghai");
    }
}
