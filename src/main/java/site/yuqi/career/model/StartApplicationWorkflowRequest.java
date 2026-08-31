package site.yuqi.career.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StartApplicationWorkflowRequest(
        @NotBlank @Size(max = 64) String applicationId,
        @NotBlank @Size(max = 64) String ats,
        @NotBlank @Pattern(regexp = "https://.*") @Size(max = 512) String origin,
        @NotBlank @Pattern(regexp = "https://.*") @Size(max = 2_000) String pageUrl,
        @Size(max = 500) String jobTitle,
        @Min(0) @Max(500) int detectedFields) {}
