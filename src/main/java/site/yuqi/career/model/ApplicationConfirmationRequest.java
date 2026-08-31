package site.yuqi.career.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApplicationConfirmationRequest(
        @NotBlank @Size(max = 80) String channel,
        @Size(max = 255) String externalMessageId) {}
