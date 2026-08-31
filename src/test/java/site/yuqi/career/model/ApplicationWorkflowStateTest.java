package site.yuqi.career.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationWorkflowStateTest {
    @Test
    void permitsOnlyTheExplicitHappyPathAndFailureEdges() {
        assertThat(ApplicationWorkflowState.SCANNED.canTransitionTo(ApplicationWorkflowState.RESOLVED)).isTrue();
        assertThat(ApplicationWorkflowState.RESOLVED.canTransitionTo(ApplicationWorkflowState.REVIEWED)).isTrue();
        assertThat(ApplicationWorkflowState.REVIEWED.canTransitionTo(ApplicationWorkflowState.READY_TO_SUBMIT)).isTrue();
        assertThat(ApplicationWorkflowState.READY_TO_SUBMIT.canTransitionTo(ApplicationWorkflowState.SUBMITTED)).isTrue();
        assertThat(ApplicationWorkflowState.SUBMITTED.canTransitionTo(ApplicationWorkflowState.CONFIRMED)).isTrue();
        assertThat(ApplicationWorkflowState.SCANNED.canTransitionTo(ApplicationWorkflowState.SUBMITTED)).isFalse();
        assertThat(ApplicationWorkflowState.CANCELLED.canTransitionTo(ApplicationWorkflowState.RESOLVED)).isFalse();
    }

    @Test
    void identifiesTerminalStatesWithoutDependingOnEnumOrder() {
        assertThat(ApplicationWorkflowState.CONFIRMED.isTerminal()).isTrue();
        assertThat(ApplicationWorkflowState.FAILED.isTerminal()).isTrue();
        assertThat(ApplicationWorkflowState.CANCELLED.isTerminal()).isTrue();
        assertThat(ApplicationWorkflowState.SUBMITTED.isTerminal()).isFalse();
    }
}
