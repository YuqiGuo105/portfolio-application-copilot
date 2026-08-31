package site.yuqi.career.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "career_private_profile")
public class PrivateProfileEntity {
    @Id @Column(name = "owner_id", length = 64) private String ownerId;
    @Column(name = "answers_ciphertext", nullable = false, columnDefinition = "text") private String answersCiphertext;
    @Version @Column(name = "record_version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "deleted_at") private Instant deletedAt;

    @PrePersist void create() { Instant now = Instant.now(); if (createdAt == null) createdAt = now; if (updatedAt == null) updatedAt = now; }
    @PreUpdate void update() { updatedAt = Instant.now(); }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }
    public String getAnswersCiphertext() { return answersCiphertext; }
    public void setAnswersCiphertext(String value) { this.answersCiphertext = value; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
