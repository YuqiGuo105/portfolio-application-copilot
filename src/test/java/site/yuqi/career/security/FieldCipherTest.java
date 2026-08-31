package site.yuqi.career.security;

import org.junit.jupiter.api.Test;
import site.yuqi.career.config.CareerProperties;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldCipherTest {
    @Test
    void encryptsWithRandomIvAndAuthenticatesCiphertext() {
        FieldCipher cipher = new FieldCipher(properties("owner-secret"));

        String first = cipher.encrypt("sensitive answer");
        String second = cipher.encrypt("sensitive answer");

        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("sensitive answer");
        assertThatThrownBy(() -> cipher.decrypt(first.substring(0, first.length() - 2) + "AA"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesToStartWithoutAnOwnerKey() {
        assertThatThrownBy(() -> new FieldCipher(properties("")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CAREER_OWNER_KEY");
    }

    private CareerProperties properties(String key) {
        return new CareerProperties("internal", key, Duration.ofHours(6),
                new CareerProperties.McpGateway("http://localhost:8091", "gateway", Duration.ofSeconds(12)));
    }
}
