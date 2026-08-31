package site.yuqi.career.service;

import org.junit.jupiter.api.Test;
import site.yuqi.career.model.SiteCredential;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SiteCredentialServiceTest {
    @Test
    void normalizesHttpsOriginsWithoutPathsOrDefaultPorts() {
        assertThat(SiteCredentialService.normalizeOrigin("https://Jobs.Example.com:443/apply/42"))
                .isEqualTo("https://jobs.example.com");
        assertThat(SiteCredentialService.normalizeOrigin("https://jobs.example.com:8443/login"))
                .isEqualTo("https://jobs.example.com:8443");
    }

    @Test
    void rejectsNonHttpsOrigins() {
        assertThatThrownBy(() -> SiteCredentialService.normalizeOrigin("http://jobs.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generatesUniqueComplexPasswords() {
        String first = SiteCredentialService.generatePassword();
        String second = SiteCredentialService.generatePassword();

        assertThat(first).hasSize(24).isNotEqualTo(second);
        assertThat(first).containsPattern("[A-Z]")
                .containsPattern("[a-z]")
                .containsPattern("[0-9]")
                .containsPattern("[!@#$%*\\-_+]");
    }

    @Test
    void refusesToReuseAnOriginForADifferentUsername() {
        SiteCredential existing = new SiteCredential(
                "https://jobs.example.com", "owner@example.com", "secret", Instant.now());

        assertThatThrownBy(() -> SiteCredentialService.requireMatchingUsername(existing, "other@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("different username");
    }

    @Test
    void acceptsTheSameUsernameWithoutCaseSensitivity() {
        SiteCredential existing = new SiteCredential(
                "https://jobs.example.com", "owner@example.com", "secret", Instant.now());

        assertThat(SiteCredentialService.requireMatchingUsername(existing, "OWNER@example.com"))
                .isSameAs(existing);
    }
}
