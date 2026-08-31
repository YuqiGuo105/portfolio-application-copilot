package site.yuqi.career.service;

import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yuqi.career.client.SupabaseStorageClient;
import site.yuqi.career.config.CareerProperties;
import site.yuqi.career.domain.ResumeAssetEntity;
import site.yuqi.career.model.*;
import site.yuqi.career.repository.ResumeAssetRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ResumeAssetService {
    private static final byte[] PDF_MAGIC = "%PDF".getBytes(StandardCharsets.US_ASCII);
    private final ResumeAssetRepository assets;
    private final ResumeAssetStateService state;
    private final SupabaseStorageClient storage;
    private final CareerProperties.ResumeStorage properties;

    public ResumeAssetService(ResumeAssetRepository assets, ResumeAssetStateService state,
            SupabaseStorageClient storage, CareerProperties properties) {
        this.assets = assets;
        this.state = state;
        this.storage = storage;
        this.properties = properties.resumeStorage();
    }

    @Transactional(readOnly = true)
    public List<ResumeAssetView> list() {
        return assets.findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(ResumeAssetStateService.OWNER_ID)
                .stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public ResumeAssetView active() { return view(activeEntity()); }

    public ResumeUploadTicket prepareUpload(ResumeUploadRequest request) {
        String fileName = sanitizeFileName(request.fileName());
        String id = UUID.randomUUID().toString();
        String objectKey = "resumes/" + ResumeAssetStateService.OWNER_ID + "/" + id + "/" + fileName;
        SupabaseStorageClient.SignedUrl signed = storage.createSignedUpload(objectKey);
        ResumeAssetEntity asset = state.createUpload(id, fileName, properties.bucket(), objectKey, request.mimeType());
        return new ResumeUploadTicket(asset.getId(), fileName, request.mimeType(), signed.url().toString(), signed.expiresAt());
    }

    public ResumeAssetView complete(String id, ResumeCompleteRequest request) {
        ResumeAssetEntity asset = state.get(id);
        byte[] content = storage.download(asset.getStorageObjectKey());
        try {
            validatePdf(content);
            String sha256 = sha256(content);
            if (request.sha256() != null && !request.sha256().equalsIgnoreCase(sha256)) {
                throw new IllegalArgumentException("Uploaded resume checksum does not match the expected SHA-256");
            }
            return view(state.activateValidated(id, content.length, sha256));
        } catch (RuntimeException error) {
            try { state.reject(id, safeReason(error)); } catch (RuntimeException ignored) { }
            throw error;
        }
    }

    public ResumeAssetView activate(String id) { return view(state.activateExisting(id)); }

    public ResumeDownloadTicket activeDownload() {
        ResumeAssetEntity active = activeEntity();
        SupabaseStorageClient.SignedUrl signed = storage.createSignedDownload(
                active.getStorageObjectKey(), active.getDisplayName());
        return new ResumeDownloadTicket(active.getId(), active.getDisplayName(), active.getMimeType(),
                active.getSizeBytes(), active.getSha256(), signed.url().toString(), signed.expiresAt());
    }

    private ResumeAssetEntity activeEntity() {
        return assets.findFirstByOwnerIdAndActiveTrueAndDeletedAtIsNull(ResumeAssetStateService.OWNER_ID)
                .orElseThrow(() -> new EntityNotFoundException("No active managed resume is available"));
    }

    private void validatePdf(byte[] content) {
        if (content == null || content.length < PDF_MAGIC.length) throw new IllegalArgumentException("Uploaded resume is empty");
        if (content.length > properties.maxBytes()) throw new IllegalArgumentException("Uploaded resume exceeds the configured size limit");
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (content[i] != PDF_MAGIC[i]) throw new IllegalArgumentException("Uploaded object is not a valid PDF file");
        }
    }

    private static String sha256(byte[] content) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content)); }
        catch (Exception error) { throw new IllegalStateException("SHA-256 is unavailable", error); }
    }

    private static String sanitizeFileName(String value) {
        String normalized = value.trim().replaceAll("[^A-Za-z0-9._-]+", "-");
        if (!normalized.toLowerCase(Locale.ROOT).endsWith(".pdf")) normalized += ".pdf";
        if (normalized.length() > 180) normalized = normalized.substring(normalized.length() - 180);
        return normalized;
    }

    private static String safeReason(RuntimeException error) {
        String reason = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        return reason.length() > 240 ? reason.substring(0, 240) : reason;
    }

    private ResumeAssetView view(ResumeAssetEntity row) {
        return new ResumeAssetView(row.getId(), row.getDisplayName(), row.getMimeType(), row.getSizeBytes(),
                row.getSha256(), row.getStatus(), row.isActive(), row.getVersion(), row.getCreatedAt(),
                row.getUpdatedAt(), row.getActivatedAt());
    }
}
