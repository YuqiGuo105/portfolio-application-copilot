package site.yuqi.career.service;
import org.springframework.stereotype.Service;
import site.yuqi.career.model.*;
import java.util.*;
@Service
public class ApplicationFieldResolver {
    private static final Set<String> ALWAYS_CONFIRM = Set.of("work authorization", "sponsorship", "visa", "salary", "compensation", "relocation", "background", "signature", "gender", "race", "veteran", "disability");
    private final CandidateProfileProvider profiles;
    public ApplicationFieldResolver(CandidateProfileProvider profiles) { this.profiles = profiles; }
    public List<FieldResolution> resolve(ResolveFieldsRequest request) {
        CandidateProfile profile = profiles.get(); return request.fields().stream().map(field -> resolve(field, profile)).toList();
    }
    private FieldResolution resolve(ResolveFieldsRequest.Field field, CandidateProfile profile) {
        String label = normalize(field.label()); Object exactPrivate = findPrivateAnswer(label, profile.applicationPreferences());
        boolean sensitive = ALWAYS_CONFIRM.stream().anyMatch(label::contains);
        if (exactPrivate != null) return result(field, exactPrivate,
                sensitive ? FieldResolution.ResolutionStatus.NEEDS_CONFIRMATION : FieldResolution.ResolutionStatus.RESOLVED,
                "encrypted application memory", .99,
                sensitive ? "A stored sensitive answer requires final review." : "Matched owner-managed application memory.");
        if (sensitive) return result(field, null, FieldResolution.ResolutionStatus.NEEDS_CONFIRMATION, "policy", 1, "The system never guesses sensitive application answers.");
        if (label.contains("skill") || label.contains("technology")) return result(field, String.join(", ", profile.skills()), FieldResolution.ResolutionStatus.RESOLVED, "yuqi.site MCP profile", .92, "Derived from first-party project and experience records.");
        if (label.contains("summary") || label.contains("about") || label.contains("profile")) return result(field, profile.summary(), FieldResolution.ResolutionStatus.RESOLVED, "yuqi.site MCP profile", .94, "Current cached candidate summary.");
        return result(field, null, FieldResolution.ResolutionStatus.UNSUPPORTED, "none", 0, "No deterministic source matched this field.");
    }
    private FieldResolution result(ResolveFieldsRequest.Field field, Object value, FieldResolution.ResolutionStatus status, String source, double confidence, String reason) {
        return new FieldResolution(field.id(), field.label(), value, status, source, confidence, reason);
    }
    private static String normalize(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim(); }

    private static Object findPrivateAnswer(String label, Map<String, Object> answers) {
        Object exact = answers.get(label);
        if (exact != null) return exact;
        for (Map.Entry<String, Object> entry : answers.entrySet()) {
            String key = normalize(entry.getKey());
            if (label.equals(key) || label.contains(key) || key.contains(label)) return entry.getValue();
        }
        return null;
    }
}
