package site.yuqi.career.controller;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import site.yuqi.career.model.*;
import site.yuqi.career.service.ApplicationFieldResolver;
import site.yuqi.career.service.CandidateProfileService;
import site.yuqi.career.service.SiteCredentialService;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/internal/v1")
public class CareerController {
    private final CandidateProfileService profiles; private final ApplicationFieldResolver resolver;
    private final SiteCredentialService credentials;
    public CareerController(CandidateProfileService profiles, ApplicationFieldResolver resolver,
            SiteCredentialService credentials) {
        this.profiles = profiles; this.resolver = resolver; this.credentials = credentials;
    }
    @GetMapping("/candidate-profile") public CandidateProfile profile() { return profiles.get(); }
    @PostMapping("/candidate-profile/refresh") public CandidateProfile refreshProfile() { return profiles.refresh(); }
    @PutMapping("/candidate-profile/private-answers") public CandidateProfile updatePrivateAnswers(@Valid @RequestBody PrivateAnswersUpdate request) { return profiles.updatePrivateAnswers(request.answers()); }
    @PostMapping("/application-fields/resolve") public Map<String, Object> resolveFields(@Valid @RequestBody ResolveFieldsRequest request) {
        List<FieldResolution> fields = resolver.resolve(request);
        return Map.of("applicationId", request.applicationId(), "fields", fields,
                "resolved", fields.stream().filter(f -> f.status() == FieldResolution.ResolutionStatus.RESOLVED).count(),
                "requiresReview", fields.stream().filter(f -> f.status() != FieldResolution.ResolutionStatus.RESOLVED).count());
    }
    @GetMapping("/site-credentials") public SiteCredential siteCredential(@RequestParam String origin) {
        return credentials.get(origin).orElseThrow(() -> new SiteCredentialNotFoundException(origin));
    }
    @PostMapping("/site-credentials/prepare") public SiteCredential prepareSiteCredential(
            @Valid @RequestBody SiteCredentialRequest request) {
        return credentials.prepare(request.origin(), request.username());
    }

    @ResponseStatus(org.springframework.http.HttpStatus.NOT_FOUND)
    private static class SiteCredentialNotFoundException extends RuntimeException {
        SiteCredentialNotFoundException(String origin) { super("No managed account exists for " + origin); }
    }
}
