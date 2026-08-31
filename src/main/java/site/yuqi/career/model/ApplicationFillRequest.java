package site.yuqi.career.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record ApplicationFillRequest(
        @Min(0) @Max(500) int appliedFields,
        boolean resumeAttached,
        @Size(max = 80) String detectedAction) {}
