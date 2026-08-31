package site.yuqi.career.controller;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import site.yuqi.career.model.*;
import site.yuqi.career.service.ApplicationFieldResolver;
import site.yuqi.career.service.CandidateProfileService;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/internal/v1")
public class CareerController {
    private final CandidateProfileService profiles; private final ApplicationFieldResolver resolver;
    public CareerController(CandidateProfileService profiles, ApplicationFieldResolver resolver) { this.profiles = profiles; this.resolver = resolver; }
    @GetMapping("/candidate-profile") public CandidateProfile profile() { return profiles.get(); }
    @PostMapping("/candidate-profile/refresh") public CandidateProfile refreshProfile() { return profiles.refresh(); }
    @PutMapping("/candidate-profile/private-answers") public CandidateProfile updatePrivateAnswers(@Valid @RequestBody PrivateAnswersUpdate request) { return profiles.updatePrivateAnswers(request.answers()); }
    @PostMapping("/application-fields/resolve") public Map<String, Object> resolveFields(@Valid @RequestBody ResolveFieldsRequest request) {
        List<FieldResolution> fields = resolver.resolve(request);
        return Map.of("applicationId", request.applicationId(), "fields", fields,
                "resolved", fields.stream().filter(f -> f.status() == FieldResolution.ResolutionStatus.RESOLVED).count(),
                "requiresReview", fields.stream().filter(f -> f.status() != FieldResolution.ResolutionStatus.RESOLVED).count());
    }
}
