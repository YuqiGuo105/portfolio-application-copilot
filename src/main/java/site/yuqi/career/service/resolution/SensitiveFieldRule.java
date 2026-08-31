package site.yuqi.career.service.resolution;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import site.yuqi.career.model.FieldResolution;

import java.util.Optional;

@Component
@Order(20)
public class SensitiveFieldRule implements ApplicationFieldRule {
    @Override
    public Optional<FieldResolution> resolve(FieldResolutionContext context) {
        if (!context.sensitive()) return Optional.empty();
        return Optional.of(new FieldResolution(context.field().id(), context.field().label(), null,
                FieldResolution.ResolutionStatus.NEEDS_CONFIRMATION, "policy", 1,
                "The system never guesses sensitive application answers."));
    }
}
