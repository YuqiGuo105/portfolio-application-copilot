package site.yuqi.career.model;

import java.time.Instant;

public record PrivateResumeView(
        String id,
        String label,
        String content,
        String fileName,
        String mimeType,
        String sourceUrl,
        boolean active,
        long version,
        Instant createdAt,
        Instant updatedAt
) {}
