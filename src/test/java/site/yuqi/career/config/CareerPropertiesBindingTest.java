package site.yuqi.career.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CareerPropertiesBindingTest {
    @Test
    void bindsCanonicalRecordConstructorWithResumeStorage() {
        Binder binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "career.internal-token", "internal",
                "career.owner-key", "owner",
                "career.profile-ttl", "6h",
                "career.mcp-gateway.base-url", "https://gateway.example",
                "career.mcp-gateway.token", "gateway-token",
                "career.resume-storage.base-url", "https://storage.example",
                "career.resume-storage.service-role-key", "service-role"
        )));

        CareerProperties properties = binder.bind("career", Bindable.of(CareerProperties.class))
                .orElseThrow(() -> new AssertionError("Career properties did not bind"));

        assertThat(properties.profileTtl()).isEqualTo(Duration.ofHours(6));
        assertThat(properties.mcpGateway().baseUrl()).isEqualTo("https://gateway.example");
        assertThat(properties.resumeStorage().bucket()).isEqualTo("career-resumes");
    }
}
