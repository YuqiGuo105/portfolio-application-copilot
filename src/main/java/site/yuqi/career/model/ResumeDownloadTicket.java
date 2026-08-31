package site.yuqi.career.model;

import java.time.Instant;

public record ResumeDownloadTicket(
        String assetId,
        String fileName,
        String mimeType,
        long sizeBytes,
        String sha256,
        String downloadUrl,
        Instant expiresAt
) {}
