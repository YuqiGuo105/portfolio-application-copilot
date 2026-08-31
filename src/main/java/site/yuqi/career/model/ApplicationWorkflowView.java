package site.yuqi.career.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ApplicationWorkflowView(
        String applicationId,
        String ats,
        String origin,
        String pageUrl,
        String jobTitle,
        ApplicationWorkflowState state,
        int detectedFields,
        int resolvedFields,
        int reviewFields,
        int approvedFields,
        int appliedFields,
        boolean resumeAttached,
        String detectedAction,
        String successUrl,
        String externalApplicationId,
        long version,
        Instant createdAt,
        Instant updatedAt,
        Instant submittedAt,
        Instant confirmedAt,
        List<Event> events) {

    public record Event(String id, ApplicationWorkflowState fromState, ApplicationWorkflowState toState,
            String eventType, Map<String, Object> metadata, Instant occurredAt) {}
}
