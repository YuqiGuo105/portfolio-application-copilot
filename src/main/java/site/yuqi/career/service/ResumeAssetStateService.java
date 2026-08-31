package site.yuqi.career.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yuqi.career.domain.ResumeAssetEntity;
import site.yuqi.career.domain.VaultAuditEntity;
import site.yuqi.career.model.ResumeAssetStatus;
import site.yuqi.career.repository.ResumeAssetRepository;
import site.yuqi.career.repository.VaultAuditRepository;

import java.time.Instant;
import java.util.Map;

@Service
public class ResumeAssetStateService {
    static final String OWNER_ID = "portfolio-owner";
    private final ResumeAssetRepository assets;
    private final VaultAuditRepository audits;
    private final ObjectMapper mapper;

    public ResumeAssetStateService(ResumeAssetRepository assets, VaultAuditRepository audits, ObjectMapper mapper) {
        this.assets = assets;
        this.audits = audits;
        this.mapper = mapper;
    }

    @Transactional
    public ResumeAssetEntity createUpload(String id, String displayName, String bucket, String objectKey, String mimeType) {
        ResumeAssetEntity entity = new ResumeAssetEntity();
        entity.setId(id);
        entity.setOwnerId(OWNER_ID);
        entity.setDisplayName(displayName);
        entity.setStorageBucket(bucket);
        entity.setStorageObjectKey(objectKey);
        entity.setMimeType(mimeType);
        entity.setStatus(ResumeAssetStatus.UPLOADING);
        entity.setActive(false);
        ResumeAssetEntity saved = assets.save(entity);
        audit("PREPARE_UPLOAD", saved, Map.of("fileName", displayName, "mimeType", mimeType));
        return saved;
    }

    @Transactional
    public ResumeAssetEntity activateValidated(String id, long sizeBytes, String sha256) {
        ResumeAssetEntity current = get(id);
        if (current.getStatus() != ResumeAssetStatus.UPLOADING && current.getStatus() != ResumeAssetStatus.READY) {
            throw new IllegalStateException("Resume asset cannot be completed from state " + current.getStatus());
        }
        archiveCurrentExcept(id);
        current.setSizeBytes(sizeBytes);
        current.setSha256(sha256);
        current.setStatus(ResumeAssetStatus.ACTIVE);
        current.setActive(true);
        current.setActivatedAt(Instant.now());
        ResumeAssetEntity saved = assets.save(current);
        audit("VALIDATE_AND_ACTIVATE", saved, Map.of("sizeBytes", sizeBytes, "sha256", sha256));
        return saved;
    }

    @Transactional
    public ResumeAssetEntity activateExisting(String id) {
        ResumeAssetEntity current = get(id);
        if (current.getStatus() == ResumeAssetStatus.UPLOADING || current.getStatus() == ResumeAssetStatus.REJECTED) {
            throw new IllegalStateException("Only a validated resume version can be activated");
        }
        archiveCurrentExcept(id);
        current.setStatus(ResumeAssetStatus.ACTIVE);
        current.setActive(true);
        current.setActivatedAt(Instant.now());
        ResumeAssetEntity saved = assets.save(current);
        audit("ACTIVATE_VERSION", saved, Map.of("sha256", saved.getSha256()));
        return saved;
    }

    @Transactional
    public void reject(String id, String reason) {
        ResumeAssetEntity current = get(id);
        current.setStatus(ResumeAssetStatus.REJECTED);
        current.setActive(false);
        assets.save(current);
        audit("REJECT_UPLOAD", current, Map.of("reason", reason));
    }

    @Transactional(readOnly = true)
    public ResumeAssetEntity get(String id) {
        return assets.findByIdAndOwnerIdAndDeletedAtIsNull(id, OWNER_ID)
                .orElseThrow(() -> new EntityNotFoundException("Resume asset not found: " + id));
    }

    private void archiveCurrentExcept(String id) {
        assets.findFirstByOwnerIdAndActiveTrueAndDeletedAtIsNull(OWNER_ID)
                .filter(active -> !active.getId().equals(id))
                .ifPresent(active -> {
                    active.setActive(false);
                    active.setStatus(ResumeAssetStatus.ARCHIVED);
                    assets.save(active);
                    audit("ARCHIVE_VERSION", active, Map.of("replacementId", id));
                });
        assets.flush();
    }

    private void audit(String action, ResumeAssetEntity asset, Map<String, ?> metadata) {
        try {
            VaultAuditEntity event = new VaultAuditEntity();
            event.setActor("owner-mcp");
            event.setAction(action);
            event.setResourceType("RESUME_ASSET");
            event.setResourceId(asset.getId());
            event.setMetadataJson(mapper.writeValueAsString(metadata));
            audits.save(event);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to audit resume asset mutation", error);
        }
    }
}
