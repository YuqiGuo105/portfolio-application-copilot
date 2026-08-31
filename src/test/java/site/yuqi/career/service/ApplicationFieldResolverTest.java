package site.yuqi.career.service;
import org.junit.jupiter.api.Test;
import site.yuqi.career.model.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
class ApplicationFieldResolverTest {
    @Test void resolvesPublicSkillsButNeverGuessesVisaAnswers() {
        CandidateProfile profile = new CandidateProfile("v1", Instant.now(), "Backend engineer", List.of("Java", "Spring Boot"),
                List.of(), List.of(), Map.of(), new CandidateProfile.Source("mcp", List.of("admin.search_content"), "fresh"));
        CandidateProfileProvider profiles = () -> profile;
        List<FieldResolution> result = new ApplicationFieldResolver(profiles).resolve(new ResolveFieldsRequest("app-1", List.of(
                new ResolveFieldsRequest.Field("skills", "Technical skills", "text", List.of()),
                new ResolveFieldsRequest.Field("visa", "Will you require visa sponsorship?", "select", List.of("Yes", "No")))));
        assertThat(result.get(0).status()).isEqualTo(FieldResolution.ResolutionStatus.RESOLVED);
        assertThat(result.get(0).value()).isEqualTo("Java, Spring Boot");
        assertThat(result.get(1).status()).isEqualTo(FieldResolution.ResolutionStatus.NEEDS_CONFIRMATION);
        assertThat(result.get(1).value()).isNull();
    }
}
