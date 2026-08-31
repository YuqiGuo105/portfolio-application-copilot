package site.yuqi.career.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yuqi.career.domain.ApplicationWorkflowEntity;
import site.yuqi.career.model.ApplicationResolutionRequest;
import site.yuqi.career.model.ApplicationWorkflowState;
import site.yuqi.career.model.StartApplicationWorkflowRequest;
import site.yuqi.career.model.SubmissionReceiptRequest;
import site.yuqi.career.repository.ApplicationWorkflowEventRepository;
import site.yuqi.career.repository.ApplicationWorkflowRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApplicationWorkflowServiceTest {
    @Mock private ApplicationWorkflowRepository workflows;
    @Mock private ApplicationWorkflowEventRepository events;
    private ApplicationWorkflowService service;

    @BeforeEach
    void setUp() {
        service = new ApplicationWorkflowService(workflows, events, new ObjectMapper());
    }

    @Test
    void startsWorkflowWithCanonicalUrlAndNoQuerySecrets() {
        when(workflows.findById("application-1")).thenReturn(Optional.empty());
        when(events.findByApplicationIdOrderByOccurredAtAsc("application-1")).thenReturn(List.of());

        var view = service.start(new StartApplicationWorkflowRequest("application-1", "Workable",
                "https://apply.workable.com", "https://apply.workable.com/company/job?token=secret#step",
                "Backend Engineer", 8));

        assertThat(view.origin()).isEqualTo("https://apply.workable.com");
        assertThat(view.pageUrl()).isEqualTo("https://apply.workable.com/company/job");
        assertThat(view.state()).isEqualTo(ApplicationWorkflowState.SCANNED);
        verify(workflows).save(any(ApplicationWorkflowEntity.class));
        verify(events).save(any());
    }

    @Test
    void rejectsCrossOriginPageAndInvalidStageCounts() {
        assertThatThrownBy(() -> service.start(new StartApplicationWorkflowRequest("application-2", "generic",
                "https://jobs.example.com", "https://attacker.example/path", null, 1)))
                .isInstanceOf(IllegalArgumentException.class);

        ApplicationWorkflowEntity workflow = workflow(ApplicationWorkflowState.SCANNED, 2);
        when(workflows.findById("application-3")).thenReturn(Optional.of(workflow));
        assertThatThrownBy(() -> service.recordResolution("application-3",
                new ApplicationResolutionRequest(2, 1, 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceed detected");
    }

    @Test
    void cannotRecordSubmissionBeforeOwnerReviewAndFill() {
        ApplicationWorkflowEntity workflow = workflow(ApplicationWorkflowState.SCANNED, 3);
        when(workflows.findById("application-3")).thenReturn(Optional.of(workflow));

        assertThatThrownBy(() -> service.recordSubmission("application-3",
                new SubmissionReceiptRequest("https://apply.workable.com/company/job?success", null,
                        "Application submitted")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SCANNED -> SUBMITTED");
    }

    @Test
    void rejectsApplicationIdReuseWithDifferentPayload() {
        ApplicationWorkflowEntity workflow = workflow(ApplicationWorkflowState.SCANNED, 3);
        when(workflows.findById("application-3")).thenReturn(Optional.of(workflow));

        assertThatThrownBy(() -> service.start(new StartApplicationWorkflowRequest("application-3", "workable",
                "https://apply.workable.com", "https://apply.workable.com/company/other-job",
                "Different job", 3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("reused with a different scan payload");
    }

    private static ApplicationWorkflowEntity workflow(ApplicationWorkflowState state, int detectedFields) {
        ApplicationWorkflowEntity workflow = new ApplicationWorkflowEntity();
        workflow.setApplicationId("application-3");
        workflow.setAts("WORKABLE");
        workflow.setOrigin("https://apply.workable.com");
        workflow.setPageUrl("https://apply.workable.com/company/job");
        workflow.setState(state);
        workflow.setDetectedFields(detectedFields);
        return workflow;
    }
}
