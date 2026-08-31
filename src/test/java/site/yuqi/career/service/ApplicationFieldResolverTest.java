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

    @Test void resolvesStoredContactDataButKeepsStoredVisaAnswersReviewable() {
        CandidateProfile profile = new CandidateProfile("v1", Instant.now(), "Backend engineer", List.of(),
                List.of(), List.of(), Map.of("email address", "owner@example.com", "visa sponsorship", "No"),
                new CandidateProfile.Source("mcp", List.of("admin.search_content"), "fresh"));
        List<FieldResolution> result = new ApplicationFieldResolver(() -> profile).resolve(
                new ResolveFieldsRequest("app-2", List.of(
                        new ResolveFieldsRequest.Field("email", "Email", "email", List.of()),
                        new ResolveFieldsRequest.Field("sponsor", "Visa sponsorship", "select", List.of("Yes", "No")))));

        assertThat(result.get(0).status()).isEqualTo(FieldResolution.ResolutionStatus.RESOLVED);
        assertThat(result.get(0).value()).isEqualTo("owner@example.com");
        assertThat(result.get(1).status()).isEqualTo(FieldResolution.ResolutionStatus.NEEDS_CONFIRMATION);
        assertThat(result.get(1).value()).isEqualTo("No");
    }

    @Test void keepsStoredI140StatusReviewable() {
        CandidateProfile profile = new CandidateProfile("v1", Instant.now(), "Backend engineer", List.of(),
                List.of(), List.of(), Map.of("do you have an approved i 140", "No"),
                new CandidateProfile.Source("mcp", List.of("admin.search_content"), "fresh"));

        FieldResolution result = new ApplicationFieldResolver(() -> profile).resolve(
                new ResolveFieldsRequest("app-3", List.of(
                        new ResolveFieldsRequest.Field(
                                "i140", "Do you have an approved I-140?", "select", List.of("Yes", "No")))))
                .get(0);

        assertThat(result.status()).isEqualTo(FieldResolution.ResolutionStatus.NEEDS_CONFIRMATION);
        assertThat(result.value()).isEqualTo("No");
        assertThat(result.source()).isEqualTo("encrypted application memory");
    }
}
