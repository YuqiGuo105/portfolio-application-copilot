package site.yuqi.career.service.resolution;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import site.yuqi.career.model.FieldResolution;

import java.util.Optional;

@Component
@Order(30)
public class PublicProfileFieldRule implements ApplicationFieldRule {
    @Override
    public Optional<FieldResolution> resolve(FieldResolutionContext context) {
        String label = context.normalizedLabel();
        if (label.contains("skill") || label.contains("technology")) {
            return Optional.of(result(context, String.join(", ", context.profile().skills()), .92,
                    "Derived from first-party project and experience records."));
        }
        if (label.contains("summary") || label.contains("about") || label.contains("profile")) {
            return Optional.of(result(context, context.profile().summary(), .94,
                    "Current cached candidate summary."));
        }
        return Optional.empty();
    }

    private FieldResolution result(FieldResolutionContext context, Object value, double confidence, String reason) {
        return new FieldResolution(context.field().id(), context.field().label(), value,
                FieldResolution.ResolutionStatus.RESOLVED, "yuqi.site MCP profile", confidence, reason);
    }
}
