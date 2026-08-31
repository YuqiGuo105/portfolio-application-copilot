package site.yuqi.career.model;

import java.time.Instant;

public record ResumeAssetView(
        String id,
        String displayName,
        String mimeType,
        Long sizeBytes,
        String sha256,
        ResumeAssetStatus status,
        boolean active,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant activatedAt
) {}
