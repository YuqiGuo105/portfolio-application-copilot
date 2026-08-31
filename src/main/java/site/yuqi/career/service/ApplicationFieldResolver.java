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
        String label = normalize(field.label()); Object exactPrivate = profile.applicationPreferences().get(label);
        if (exactPrivate != null) return result(field, exactPrivate, FieldResolution.ResolutionStatus.NEEDS_CONFIRMATION, "private application memory", .99, "User-owned answer requires final review.");
        if (ALWAYS_CONFIRM.stream().anyMatch(label::contains)) return result(field, null, FieldResolution.ResolutionStatus.NEEDS_CONFIRMATION, "policy", 1, "The system never guesses sensitive application answers.");
        if (label.contains("skill") || label.contains("technology")) return result(field, String.join(", ", profile.skills()), FieldResolution.ResolutionStatus.RESOLVED, "yuqi.site MCP profile", .92, "Derived from first-party project and experience records.");
        if (label.contains("summary") || label.contains("about") || label.contains("profile")) return result(field, profile.summary(), FieldResolution.ResolutionStatus.RESOLVED, "yuqi.site MCP profile", .94, "Current cached candidate summary.");
        return result(field, null, FieldResolution.ResolutionStatus.UNSUPPORTED, "none", 0, "No deterministic source matched this field.");
    }
    private FieldResolution result(ResolveFieldsRequest.Field field, Object value, FieldResolution.ResolutionStatus status, String source, double confidence, String reason) {
        return new FieldResolution(field.id(), field.label(), value, status, source, confidence, reason);
    }
    private static String normalize(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim(); }
}
