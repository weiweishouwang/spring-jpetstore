package ik.am.jpetstore.identity.infrastructure;

import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * T249: 密码编码与输入字节保护。
 *
 * <p>密码只以带算法标识的哈希存储（NFR-04、D01）。BCrypt 只处理前 72 字节，因此超过该长度的
 * 输入必须明确拒绝，而不是被静默截断（Q13）。字符长度 8～64 由请求 DTO 校验，字节上限由本
 * 处的编码器兜底。</p>
 */
@Configuration
public class PasswordConfiguration {

    /** BCrypt 处理的最大 UTF-8 字节数（Q13）。 */
    public static final int MAX_PASSWORD_BYTES = 72;

    /** 密码编码器：BCrypt，输出自带算法与成本标识。 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new ByteBoundedPasswordEncoder(new BCryptPasswordEncoder());
    }

    /** 在编码前检查字节上限的包装器。 */
    static final class ByteBoundedPasswordEncoder implements PasswordEncoder {

        private final PasswordEncoder delegate;

        ByteBoundedPasswordEncoder(PasswordEncoder delegate) {
            this.delegate = delegate;
        }

        @Override
        public String encode(CharSequence rawPassword) {
            requireWithinByteLimit(rawPassword);
            return delegate.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            requireWithinByteLimit(rawPassword);
            return delegate.matches(rawPassword, encodedPassword);
        }

        @Override
        public boolean upgradeEncoding(String encodedPassword) {
            return delegate.upgradeEncoding(encodedPassword);
        }

        private void requireWithinByteLimit(CharSequence rawPassword) {
            if (rawPassword == null) {
                throw new IllegalArgumentException("密码不能为空");
            }
            int bytes = rawPassword.toString().getBytes(StandardCharsets.UTF_8).length;
            if (bytes > MAX_PASSWORD_BYTES) {
                throw new IllegalArgumentException(
                        "密码的 UTF-8 长度不能超过 " + MAX_PASSWORD_BYTES + " 字节，实际 " + bytes + " 字节");
            }
        }
    }
}
