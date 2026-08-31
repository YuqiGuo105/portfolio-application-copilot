package site.yuqi.career.model;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Explicit lifecycle for one application attempt. */
public enum ApplicationWorkflowState {
    SCANNED,
    RESOLVED,
    REVIEWED,
    READY_TO_SUBMIT,
    SUBMITTED,
    CONFIRMED,
    FAILED,
    CANCELLED;

    private static final Map<ApplicationWorkflowState, Set<ApplicationWorkflowState>> TRANSITIONS = Map.of(
            SCANNED, EnumSet.of(RESOLVED, FAILED, CANCELLED),
            RESOLVED, EnumSet.of(REVIEWED, FAILED, CANCELLED),
            REVIEWED, EnumSet.of(READY_TO_SUBMIT, FAILED, CANCELLED),
            READY_TO_SUBMIT, EnumSet.of(SUBMITTED, FAILED, CANCELLED),
            SUBMITTED, EnumSet.of(CONFIRMED, FAILED),
            CONFIRMED, EnumSet.noneOf(ApplicationWorkflowState.class),
            FAILED, EnumSet.noneOf(ApplicationWorkflowState.class),
            CANCELLED, EnumSet.noneOf(ApplicationWorkflowState.class));

    public boolean canTransitionTo(ApplicationWorkflowState target) {
        return this == target || TRANSITIONS.getOrDefault(this, Set.of()).contains(target);
    }

    public boolean isTerminal() {
        return this == CONFIRMED || this == FAILED || this == CANCELLED;
    }
}
