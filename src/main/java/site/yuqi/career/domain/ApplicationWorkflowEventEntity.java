package site.yuqi.career.domain;

import jakarta.persistence.*;
import site.yuqi.career.model.ApplicationWorkflowState;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "career_application_workflow_events")
public class ApplicationWorkflowEventEntity {
    @Id private String id;
    @Column(name = "application_id", nullable = false, length = 64) private String applicationId;
    @Enumerated(EnumType.STRING) @Column(name = "from_state", length = 40) private ApplicationWorkflowState fromState;
    @Enumerated(EnumType.STRING) @Column(name = "to_state", nullable = false, length = 40) private ApplicationWorkflowState toState;
    @Column(name = "event_type", nullable = false, length = 80) private String eventType;
    @Column(name = "metadata_json", nullable = false, columnDefinition = "text") private String metadataJson;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;

    @PrePersist void create() {
        if (id == null) id = UUID.randomUUID().toString();
        if (occurredAt == null) occurredAt = Instant.now();
    }
    public String getId() { return id; }
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String value) { applicationId = value; }
    public ApplicationWorkflowState getFromState() { return fromState; }
    public void setFromState(ApplicationWorkflowState value) { fromState = value; }
    public ApplicationWorkflowState getToState() { return toState; }
    public void setToState(ApplicationWorkflowState value) { toState = value; }
    public String getEventType() { return eventType; }
    public void setEventType(String value) { eventType = value; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String value) { metadataJson = value; }
    public Instant getOccurredAt() { return occurredAt; }
}
