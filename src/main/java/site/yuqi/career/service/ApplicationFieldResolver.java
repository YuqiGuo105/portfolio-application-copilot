package site.yuqi.career.service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import site.yuqi.career.model.*;
import site.yuqi.career.service.resolution.*;
import java.util.*;
@Service
public class ApplicationFieldResolver {
    private final CandidateProfileProvider profiles;
    private final SensitiveFieldPolicy policy;
    private final List<ApplicationFieldRule> rules;

    @Autowired
    public ApplicationFieldResolver(CandidateProfileProvider profiles, SensitiveFieldPolicy policy,
            List<ApplicationFieldRule> rules) {
        this.profiles = profiles;
        this.policy = policy;
        this.rules = List.copyOf(rules);
    }

    ApplicationFieldResolver(CandidateProfileProvider profiles) {
        this.profiles = profiles;
        this.policy = new SensitiveFieldPolicy();
        this.rules = List.of(new PrivateMemoryFieldRule(policy), new SensitiveFieldRule(),
                new PublicProfileFieldRule());
    }

    public List<FieldResolution> resolve(ResolveFieldsRequest request) {
        CandidateProfile profile = profiles.get(); return request.fields().stream().map(field -> resolve(field, profile)).toList();
    }
    private FieldResolution resolve(ResolveFieldsRequest.Field field, CandidateProfile profile) {
        String sourceLabel = field.semanticKey() == null || field.semanticKey().isBlank()
                ? field.label() : field.semanticKey();
        String label = policy.normalize(sourceLabel);
        FieldResolutionContext context = new FieldResolutionContext(field, profile, label,
                policy.requiresConfirmation(label));
        for (ApplicationFieldRule rule : rules) {
            Optional<FieldResolution> result = rule.resolve(context);
            if (result.isPresent()) return result.get();
        }
        return result(field, null, FieldResolution.ResolutionStatus.UNSUPPORTED, "none", 0, "No deterministic source matched this field.");
    }
    private FieldResolution result(ResolveFieldsRequest.Field field, Object value, FieldResolution.ResolutionStatus status, String source, double confidence, String reason) {
        return new FieldResolution(field.id(), field.label(), value, status, source, confidence, reason);
    }
}
