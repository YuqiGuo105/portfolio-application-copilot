package site.yuqi.career.model;

import jakarta.validation.constraints.NotBlank;

public record SiteCredentialRequest(
        @NotBlank String origin,
        @NotBlank String username) {}
