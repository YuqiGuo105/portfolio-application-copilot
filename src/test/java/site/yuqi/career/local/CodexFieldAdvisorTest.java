package site.yuqi.career.local;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class CodexFieldAdvisorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void sanitizesProfileAndDropsPrivateApplicationPreferences() throws Exception {
        var request = mapper.readTree("""
                {
                  "requestId":"request-1",
                  "page":{"origin":"https://apply.workable.com","title":"Software Engineer","pageType":"APPLICATION"},
                  "profile":{"summary":"Backend engineer","skills":["Java","Kafka"],
                    "experience":[{"title":"Software Engineer","company":"Example","private":"drop"}],
                    "applicationPreferences":{"password":"never-send","i140":"no"}},
                  "fields":[{"id":"question-1","label":"Why this role?","type":"textarea","required":true,"options":[]}]
                }
                """);
        var advisor = new CodexFieldAdvisor(mapper, Duration.ofSeconds(1), "/not-used", "low");

        var sanitized = advisor.sanitize(request);

        assertThat(sanitized.path("profile").has("applicationPreferences")).isFalse();
        assertThat(sanitized.toString()).doesNotContain("never-send", "i140");
        assertThat(sanitized.path("profile").path("skills").get(0).asText()).isEqualTo("Java");
    }
}
