package site.yuqi.career.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ApplicationResolutionRequest(
        @Min(0) @Max(500) int resolved,
        @Min(0) @Max(500) int requiresReview,
        @Min(0) @Max(500) int unsupported) {}
