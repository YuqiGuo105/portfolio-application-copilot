package site.yuqi.career.service.resolution;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import site.yuqi.career.model.FieldResolution;

import java.util.Map;
import java.util.Optional;

@Component
@Order(10)
public class PrivateMemoryFieldRule implements ApplicationFieldRule {
    private final SensitiveFieldPolicy policy;

    public PrivateMemoryFieldRule(SensitiveFieldPolicy policy) { this.policy = policy; }

    @Override
    public Optional<FieldResolution> resolve(FieldResolutionContext context) {
        Object value = find(context.normalizedLabel(), context.profile().applicationPreferences());
        if (value == null) return Optional.empty();
        FieldResolution.ResolutionStatus status = context.sensitive()
                ? FieldResolution.ResolutionStatus.NEEDS_CONFIRMATION
                : FieldResolution.ResolutionStatus.RESOLVED;
        return Optional.of(new FieldResolution(context.field().id(), context.field().label(), value, status,
                "encrypted application memory", .99,
                context.sensitive() ? "A stored sensitive answer requires final review."
                        : "Matched owner-managed application memory."));
    }

    private Object find(String label, Map<String, Object> answers) {
        Object exact = answers.get(label);
        if (exact != null) return exact;
        for (Map.Entry<String, Object> entry : answers.entrySet()) {
            String key = policy.normalize(entry.getKey());
            if (label.equals(key) || label.contains(key) || key.contains(label)) return entry.getValue();
        }
        return null;
    }
}
