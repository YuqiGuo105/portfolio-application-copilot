package site.yuqi.career.domain;

import jakarta.persistence.*;
import site.yuqi.career.model.ResumeAssetStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "career_resume_assets")
public class ResumeAssetEntity {
    @Id private String id;
    @Column(name = "owner_id", nullable = false, length = 64) private String ownerId;
    @Column(name = "display_name", nullable = false) private String displayName;
    @Column(name = "storage_bucket", nullable = false, length = 120) private String storageBucket;
    @Column(name = "storage_object_key", nullable = false, columnDefinition = "text") private String storageObjectKey;
    @Column(name = "mime_type", nullable = false, length = 120) private String mimeType;
    @Column(name = "size_bytes") private Long sizeBytes;
    @Column(length = 64) private String sha256;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) private ResumeAssetStatus status;
    @Column(nullable = false) private boolean active;
    @Version @Column(name = "record_version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "activated_at") private Instant activatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    @PrePersist void create() {
        if (id == null) id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }
    @PreUpdate void update() { updatedAt = Instant.now(); }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public String getStorageBucket() { return storageBucket; }
    public void setStorageBucket(String value) { this.storageBucket = value; }
    public String getStorageObjectKey() { return storageObjectKey; }
    public void setStorageObjectKey(String value) { this.storageObjectKey = value; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public ResumeAssetStatus getStatus() { return status; }
    public void setStatus(ResumeAssetStatus status) { this.status = status; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getActivatedAt() { return activatedAt; }
    public void setActivatedAt(Instant activatedAt) { this.activatedAt = activatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
