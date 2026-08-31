package site.yuqi.career.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SubmissionReceiptRequest(
        @NotBlank @Pattern(regexp = "https://.*") @Size(max = 2_000) String successUrl,
        @Size(max = 255) String externalApplicationId,
        @Size(max = 1_000) String confirmationText) {}
