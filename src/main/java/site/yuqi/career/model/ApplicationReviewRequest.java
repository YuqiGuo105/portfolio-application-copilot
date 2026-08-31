package site.yuqi.career.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record ApplicationReviewRequest(@Min(0) @Max(500) int approvedFields) {}
