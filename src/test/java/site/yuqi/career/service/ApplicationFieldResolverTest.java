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

    @Test void resolvesCanonicalAtsSemanticsFromPrivateMemoryAliases() {
        CandidateProfile profile = new CandidateProfile("v1", Instant.now(), "Backend engineer", List.of(),
                List.of(), List.of(), Map.of(
                        "given name", "Yuqi",
                        "surname", "Guo",
                        "phone number", "+1 555 0100",
                        "linkedin link", "https://www.linkedin.com/in/example/"),
                new CandidateProfile.Source("mcp", List.of("get_profile"), "fresh"));

        List<FieldResolution> result = new ApplicationFieldResolver(() -> profile).resolve(
                new ResolveFieldsRequest("app-4", List.of(
                        new ResolveFieldsRequest.Field("first", "Candidate legal first name", "first_name", "text", List.of()),
                        new ResolveFieldsRequest.Field("last", "Candidate legal last name", "last_name", "text", List.of()),
                        new ResolveFieldsRequest.Field("phone", "Telephone", "phone", "tel", List.of()),
                        new ResolveFieldsRequest.Field("linkedin", "Profile URL", "linkedin_url", "url", List.of()))));

        assertThat(result).extracting(FieldResolution::value)
                .containsExactly("Yuqi", "Guo", "+1 555 0100", "https://www.linkedin.com/in/example/");
        assertThat(result).allMatch(field -> field.status() == FieldResolution.ResolutionStatus.RESOLVED);
    }

    @Test void resolvesCurrentCompanyFromPublicExperienceWhenPrivateMemoryIsEmpty() {
        CandidateProfile profile = new CandidateProfile("v1", Instant.now(), "Backend engineer", List.of(),
                List.of(Map.of("title", "Goldman Sachs")), List.of(), Map.of(),
                new CandidateProfile.Source("mcp", List.of("get_profile"), "fresh"));

        FieldResolution result = new ApplicationFieldResolver(() -> profile).resolve(
                new ResolveFieldsRequest("app-5", List.of(
                        new ResolveFieldsRequest.Field("company", "Current employer", "current_company", "text", List.of()))))
                .get(0);

        assertThat(result.status()).isEqualTo(FieldResolution.ResolutionStatus.RESOLVED);
        assertThat(result.value()).isEqualTo("Goldman Sachs");
    }

    @Test void resolvesCanonicalEeoSemanticsButKeepsThemReviewable() {
        CandidateProfile profile = new CandidateProfile("v1", Instant.now(), "Backend engineer", List.of(),
                List.of(), List.of(), Map.of(
                        "gender", "Male",
                        "please identify your race", "Asian",
                        "are you hispanic latino", "No"),
                new CandidateProfile.Source("mcp", List.of("get_profile"), "fresh"));

        List<FieldResolution> result = new ApplicationFieldResolver(() -> profile).resolve(
                new ResolveFieldsRequest("app-eeo", List.of(
                        new ResolveFieldsRequest.Field("gender", "Gender", "gender", "combobox", List.of("Male", "Female")),
                        new ResolveFieldsRequest.Field("race", "Please identify your race", "race", "combobox", List.of("Asian", "White")),
                        new ResolveFieldsRequest.Field("hispanic", "Are you Hispanic/Latino?", "hispanic_or_latino_ethnicity", "combobox", List.of("Yes", "No")))));

        assertThat(result).extracting(FieldResolution::value).containsExactly("Male", "Asian", "No");
        assertThat(result).allMatch(field -> field.status() == FieldResolution.ResolutionStatus.NEEDS_CONFIRMATION);
    }
}
