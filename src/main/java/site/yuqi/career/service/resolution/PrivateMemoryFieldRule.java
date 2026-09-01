package site.yuqi.career.service.resolution;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import site.yuqi.career.model.FieldResolution;

import java.util.Map;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Order(10)
public class PrivateMemoryFieldRule implements ApplicationFieldRule {
    private static final Set<String> WEAK_TOKENS = Set.of(
            "a", "an", "and", "are", "do", "does", "have", "identify", "is", "of", "or",
            "please", "question", "select", "status", "the", "to", "you", "your");
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
        Set<String> aliases = aliases(label);
        for (String alias : aliases) {
            Object exact = answers.get(alias);
            if (exact != null) return exact;
        }
        for (Map.Entry<String, Object> entry : answers.entrySet()) {
            String key = policy.normalize(entry.getKey());
            if (aliases.contains(key) || aliases.stream().anyMatch(alias -> semanticallyMatches(alias, key))) {
                return entry.getValue();
            }
        }
        return null;
    }

    private boolean semanticallyMatches(String left, String right) {
        if (left.contains(right) || right.contains(left)) return true;
        Set<String> leftTokens = semanticTokens(left);
        Set<String> rightTokens = semanticTokens(right);
        return !leftTokens.isEmpty() && !rightTokens.isEmpty()
                && (leftTokens.containsAll(rightTokens) || rightTokens.containsAll(leftTokens));
    }

    private Set<String> semanticTokens(String value) {
        return Arrays.stream(policy.normalize(value).split("\\s+"))
                .map(token -> token.toLowerCase(Locale.ROOT))
                .filter(token -> !token.isBlank() && !WEAK_TOKENS.contains(token))
                .collect(Collectors.toUnmodifiableSet());
    }

    private Set<String> aliases(String label) {
        return switch (label) {
            case "first name" -> Set.of("first name", "given name", "firstname");
            case "last name" -> Set.of("last name", "family name", "surname", "lastname");
            case "email" -> Set.of("email", "email address", "e mail");
            case "phone" -> Set.of("phone", "phone number", "telephone", "mobile");
            case "current company" -> Set.of("current company", "current employer", "company", "employer");
            case "linkedin url" -> Set.of("linkedin url", "linkedin link", "linkedin");
            case "website url" -> Set.of("website url", "website link", "website", "portfolio url");
            default -> Set.of(label);
        };
    }
}
