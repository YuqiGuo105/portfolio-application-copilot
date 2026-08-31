package site.yuqi.career.model;

import jakarta.validation.constraints.Pattern;

public record ResumeCompleteRequest(
        @Pattern(regexp = "[a-fA-F0-9]{64}", message = "sha256 must contain 64 hexadecimal characters") String sha256
) {}
