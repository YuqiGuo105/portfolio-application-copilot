package site.yuqi.career.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "career_vault_audit_log")
public class VaultAuditEntity {
    @Id private String id;
    @Column(nullable = false) private String actor;
    @Column(nullable = false) private String action;
    @Column(name = "resource_type", nullable = false) private String resourceType;
    @Column(name = "resource_id", nullable = false) private String resourceId;
    @Column(name = "metadata_json", nullable = false, columnDefinition = "text") private String metadataJson;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @PrePersist void create() { if (id == null) id = UUID.randomUUID().toString(); if (occurredAt == null) occurredAt = Instant.now(); }
    public void setActor(String actor) { this.actor = actor; }
    public void setAction(String action) { this.action = action; }
    public void setResourceType(String resourceType) { this.resourceType = resourceType; }
    public void setResourceId(String resourceId) { this.resourceId = resourceId; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
}
