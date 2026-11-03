package ik.am.jpetstore.identity.infrastructure;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T248: 密码编码与输入字节保护。
 *
 * <p>密码只以带算法标识的哈希存储，绝不等于明文（D01、NFR-04）；算法标识可升级（旧的哈希
 * 在下次成功登录时重新计算）；UTF-8 超过 72 字节的输入明确拒绝，绝不静默截断（Q13）。</p>
 */
class PasswordConfigurationTest {

    private final PasswordEncoder encoder = new PasswordConfiguration().passwordEncoder();

    @Test
    void encodedValueIsNeitherPlaintextNorReversible() {
        String encoded = encoder.encode("alice-pass-123");

        assertThat(encoded).isNotEqualTo("alice-pass-123");
        assertThat(encoded).startsWith("$2a$");
        assertThat(encoded).doesNotContain("alice-pass-123");
    }

    @Test
    void matchingPasswordIsAcceptedAndWrongPasswordFails() {
        String encoded = encoder.encode("alice-pass-123");

        assertThat(encoder.matches("alice-pass-123", encoded)).isTrue();
        assertThat(encoder.matches("wrong-pass-456", encoded)).isFalse();
        assertThat(encoder.matches("", encoded)).isFalse();
    }

    @Test
    void hashCarriesTheAlgorithmIdentifierSoItCanBeUpgraded() {
        String encoded = encoder.encode("alice-pass-123");

        assertThat(encoded).startsWith("$2a$");
        assertThat(encoder.upgradeEncoding(encoded))
                .as("a hash written with the current cost needs no upgrade")
                .isFalse();

        String legacyHash = new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(4)
                .encode("alice-pass-123");
        assertThat(encoder.upgradeEncoding(legacyHash))
                .as("a hash written with an older cost is detected and can be upgraded")
                .isTrue();
    }

    @Test
    void inputLongerThan72BytesIsRejectedInsteadOfTruncated() {
        String tooLong = "x".repeat(73);

        assertThatThrownBy(() -> encoder.encode(tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72");
    }

    @Test
    void byteLimitCountsUtf8BytesNotCharacters() {
        String twentyFiveCjkCharacters = "密".repeat(25);

        assertThat(twentyFiveCjkCharacters).hasSize(25);
        assertThatThrownBy(() -> encoder.encode(twentyFiveCjkCharacters))
                .as("75 UTF-8 bytes exceed the limit even though the character count is small")
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void inputExactlyAtTheByteLimitIsAccepted() {
        String atLimit = "x".repeat(72);

        assertThat(encoder.encode(atLimit)).startsWith("$2a$");
    }
}
