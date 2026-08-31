package site.yuqi.career.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PrivateResumeRequest(
        @NotBlank @Size(max = 160) String label,
        @NotBlank @Size(max = 1_500_000) String content,
        @Size(max = 255) String fileName,
        @Size(max = 120) String mimeType,
        @Size(max = 2_000) String sourceUrl,
        boolean active
) {}
