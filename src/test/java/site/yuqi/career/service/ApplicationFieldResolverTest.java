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

    @Test void resolvesUpstartCanonicalQuestionsFromAliasesAndOriginalLabels() {
        String familiarityQuestion = "Before applying, how familiar were you with Upstart?";
        CandidateProfile profile = new CandidateProfile("v1", Instant.now(), "Backend engineer", List.of(),
                List.of(), List.of(), Map.of(
                        "location preference", "Remote",
                        "visa sponsorship", "Yes",
                        familiarityQuestion, "I was already familiar with Upstart",
                        "how did you hear", "LinkedIn company page",
                        "employed by company before", "No"),
                new CandidateProfile.Source("mcp", List.of("get_profile"), "fresh"));

        List<FieldResolution> result = new ApplicationFieldResolver(() -> profile).resolve(
                new ResolveFieldsRequest("app-upstart", List.of(
                        new ResolveFieldsRequest.Field("locations", "Location Preference", "work_location_preference", "checkbox-group", List.of("Remote")),
                        new ResolveFieldsRequest.Field("sponsor", "Do you need immigration-related support or sponsorship?", "sponsorship_required", "combobox", List.of("Yes", "No")),
                        new ResolveFieldsRequest.Field("familiar", familiarityQuestion, "company_familiarity", "combobox", List.of()),
                        new ResolveFieldsRequest.Field("source", "Before applying, how did you hear about Upstart?", "application_source", "combobox", List.of()),
                        new ResolveFieldsRequest.Field("former", "Have you been employed by Upstart before?", "previously_employed_by_company", "combobox", List.of("Yes", "No")))));

        assertThat(result).extracting(FieldResolution::value).containsExactly(
                "Remote", "Yes", "I was already familiar with Upstart", "LinkedIn company page", "No");
        assertThat(result.get(1).status()).isEqualTo(FieldResolution.ResolutionStatus.NEEDS_CONFIRMATION);
        assertThat(result).filteredOn(field -> !field.fieldId().equals("sponsor"))
                .allMatch(field -> field.status() == FieldResolution.ResolutionStatus.RESOLVED);
    }

    @Test void doesNotUseCurrentLocationAsWorkLocationPreference() {
        CandidateProfile profile = new CandidateProfile("v1", Instant.now(), "Backend engineer", List.of(),
                List.of(), List.of(), Map.of(
                        "location", "Salt Lake City, UT",
                        "city", "Salt Lake City",
                        "country", "United States"),
                new CandidateProfile.Source("mcp", List.of("get_profile"), "fresh"));

        FieldResolution result = new ApplicationFieldResolver(() -> profile).resolve(
                new ResolveFieldsRequest("app-location-preference", List.of(
                        new ResolveFieldsRequest.Field("locations", "Location Preference",
                                "work_location_preference", "checkbox-group", List.of("Remote")))))
                .get(0);

        assertThat(result.status()).isEqualTo(FieldResolution.ResolutionStatus.UNSUPPORTED);
        assertThat(result.value()).isNull();
    }

    @Test void resolvesShortRaceLabelFromExplicitPrivateMemoryAlias() {
        CandidateProfile profile = new CandidateProfile("v1", Instant.now(), "Backend engineer", List.of(),
                List.of(), List.of(), Map.of("please identify your race", "Asian"),
                new CandidateProfile.Source("mcp", List.of("get_profile"), "fresh"));

        FieldResolution result = new ApplicationFieldResolver(() -> profile).resolve(
                new ResolveFieldsRequest("app-race", List.of(
                        new ResolveFieldsRequest.Field("race", "Race", "race", "combobox", List.of("Asian")))))
                .get(0);

        assertThat(result.value()).isEqualTo("Asian");
        assertThat(result.status()).isEqualTo(FieldResolution.ResolutionStatus.NEEDS_CONFIRMATION);
    }

    @Test void neverUsesProfileSummaryAsAUrlValue() {
        CandidateProfile profile = new CandidateProfile("v1", Instant.now(), "Backend engineer", List.of(),
                List.of(), List.of(), Map.of(),
                new CandidateProfile.Source("mcp", List.of("get_profile"), "fresh"));

        FieldResolution result = new ApplicationFieldResolver(() -> profile).resolve(
                new ResolveFieldsRequest("app-url", List.of(
                        new ResolveFieldsRequest.Field("linkedin", "LinkedIn Profile", "profile", "url", List.of()))))
                .get(0);

        assertThat(result.status()).isEqualTo(FieldResolution.ResolutionStatus.UNSUPPORTED);
        assertThat(result.value()).isNull();
    }
}
