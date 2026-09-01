package site.yuqi.career.service.resolution;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import site.yuqi.career.model.FieldResolution;

import java.util.Optional;
import java.util.Map;

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
        boolean urlField = "url".equalsIgnoreCase(context.field().type()) || label.contains("linkedin") ||
                label.contains("website");
        if (!urlField && (label.contains("summary") || label.contains("about") || label.contains("profile"))) {
            return Optional.of(result(context, context.profile().summary(), .94,
                    "Current cached candidate summary."));
        }
        if (label.equals("current company")) {
            String company = firstString(context.profile().experience(), "company", "title", "name");
            if (!company.isBlank()) {
                return Optional.of(result(context, company, .96,
                        "Current employer from the first-party experience record."));
            }
        }
        return Optional.empty();
    }

    private String firstString(Iterable<Map<String, Object>> items, String... keys) {
        for (Map<String, Object> item : items) {
            for (String key : keys) {
                Object value = item.get(key);
                if (value != null && !String.valueOf(value).isBlank()) return String.valueOf(value);
            }
        }
        return "";
    }

    private FieldResolution result(FieldResolutionContext context, Object value, double confidence, String reason) {
        return new FieldResolution(context.field().id(), context.field().label(), value,
                FieldResolution.ResolutionStatus.RESOLVED, "yuqi.site MCP profile", confidence, reason);
    }
}
