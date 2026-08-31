package site.yuqi.career.domain;

import jakarta.persistence.*;
import site.yuqi.career.model.ApplicationWorkflowState;

import java.time.Instant;

@Entity
@Table(name = "career_application_workflows")
public class ApplicationWorkflowEntity {
    @Id @Column(name = "application_id", length = 64) private String applicationId;
    @Column(nullable = false, length = 64) private String ats;
    @Column(nullable = false, length = 512) private String origin;
    @Column(name = "page_url", nullable = false, length = 2_000) private String pageUrl;
    @Column(name = "job_title", length = 500) private String jobTitle;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private ApplicationWorkflowState state;
    @Column(name = "detected_fields", nullable = false) private int detectedFields;
    @Column(name = "resolved_fields", nullable = false) private int resolvedFields;
    @Column(name = "review_fields", nullable = false) private int reviewFields;
    @Column(name = "approved_fields", nullable = false) private int approvedFields;
    @Column(name = "applied_fields", nullable = false) private int appliedFields;
    @Column(name = "resume_attached", nullable = false) private boolean resumeAttached;
    @Column(name = "detected_action", length = 80) private String detectedAction;
    @Column(name = "success_url", length = 2_000) private String successUrl;
    @Column(name = "external_application_id", length = 255) private String externalApplicationId;
    @Column(name = "submitted_at") private Instant submittedAt;
    @Column(name = "confirmed_at") private Instant confirmedAt;
    @Version @Column(name = "record_version", nullable = false) private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;

    @PrePersist void create() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (state == null) state = ApplicationWorkflowState.SCANNED;
    }
    @PreUpdate void update() { updatedAt = Instant.now(); }

    public void transitionTo(ApplicationWorkflowState target) {
        if (!state.canTransitionTo(target)) {
            throw new IllegalStateException("Invalid application transition: " + state + " -> " + target);
        }
        state = target;
        if (target == ApplicationWorkflowState.SUBMITTED && submittedAt == null) submittedAt = Instant.now();
        if (target == ApplicationWorkflowState.CONFIRMED && confirmedAt == null) confirmedAt = Instant.now();
    }

    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String value) { applicationId = value; }
    public String getAts() { return ats; }
    public void setAts(String value) { ats = value; }
    public String getOrigin() { return origin; }
    public void setOrigin(String value) { origin = value; }
    public String getPageUrl() { return pageUrl; }
    public void setPageUrl(String value) { pageUrl = value; }
    public String getJobTitle() { return jobTitle; }
    public void setJobTitle(String value) { jobTitle = value; }
    public ApplicationWorkflowState getState() { return state; }
    public void setState(ApplicationWorkflowState value) { state = value; }
    public int getDetectedFields() { return detectedFields; }
    public void setDetectedFields(int value) { detectedFields = value; }
    public int getResolvedFields() { return resolvedFields; }
    public void setResolvedFields(int value) { resolvedFields = value; }
    public int getReviewFields() { return reviewFields; }
    public void setReviewFields(int value) { reviewFields = value; }
    public int getApprovedFields() { return approvedFields; }
    public void setApprovedFields(int value) { approvedFields = value; }
    public int getAppliedFields() { return appliedFields; }
    public void setAppliedFields(int value) { appliedFields = value; }
    public boolean isResumeAttached() { return resumeAttached; }
    public void setResumeAttached(boolean value) { resumeAttached = value; }
    public String getDetectedAction() { return detectedAction; }
    public void setDetectedAction(String value) { detectedAction = value; }
    public String getSuccessUrl() { return successUrl; }
    public void setSuccessUrl(String value) { successUrl = value; }
    public String getExternalApplicationId() { return externalApplicationId; }
    public void setExternalApplicationId(String value) { externalApplicationId = value; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
}
