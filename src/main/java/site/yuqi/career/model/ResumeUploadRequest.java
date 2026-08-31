package site.yuqi.career.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ResumeUploadRequest(
        @NotBlank @Size(max = 255) String fileName,
        @NotBlank @Pattern(regexp = "application/pdf", message = "Only PDF resumes are supported") String mimeType
) {}
