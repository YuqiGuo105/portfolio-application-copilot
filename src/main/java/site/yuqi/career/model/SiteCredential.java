package site.yuqi.career.model;

import java.time.Instant;

public record SiteCredential(
        String origin,
        String username,
        String password,
        Instant createdAt) {}
