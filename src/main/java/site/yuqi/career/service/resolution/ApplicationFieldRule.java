package site.yuqi.career.service.resolution;

import site.yuqi.career.model.FieldResolution;

import java.util.Optional;

/** One deterministic rule in the field-resolution chain. */
public interface ApplicationFieldRule {
    Optional<FieldResolution> resolve(FieldResolutionContext context);
}
