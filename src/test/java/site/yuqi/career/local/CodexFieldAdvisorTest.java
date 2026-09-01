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

    @Test
    void classificationInputContainsQuestionsButNeverCandidateMemory() throws Exception {
        var request = mapper.readTree("""
                {
                  "requestId":"request-2",
                  "page":{"origin":"https://ats.example","title":"Application","pageType":"APPLICATION"},
                  "profile":{"applicationPreferences":{"gender":"Male","race":"Asian"}},
                  "fields":[
                    {"id":"field-1","label":"Please identify your race","type":"combobox","required":false,"options":["Asian","White"]}
                  ]
                }
                """);
        var advisor = new CodexFieldAdvisor(mapper, Duration.ofSeconds(1), "/not-used", "low");

        var sanitized = advisor.sanitizeClassification(request);

        assertThat(sanitized.has("profile")).isFalse();
        assertThat(sanitized.toString()).doesNotContain("Male");
        assertThat(sanitized.path("fields").get(0).path("label").asText()).isEqualTo("Please identify your race");
        assertThat(sanitized.path("fields").get(0).path("options").get(0).asText()).isEqualTo("Asian");
    }
}
