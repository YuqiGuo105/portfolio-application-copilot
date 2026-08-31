package site.yuqi.career.model;

import java.time.Instant;

public record ResumeUploadTicket(
        String assetId,
        String fileName,
        String mimeType,
        String uploadUrl,
        Instant expiresAt
) {}
