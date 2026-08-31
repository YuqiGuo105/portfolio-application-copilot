package site.yuqi.career.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yuqi.career.domain.ApplicationWorkflowEntity;
import site.yuqi.career.domain.ApplicationWorkflowEventEntity;
import site.yuqi.career.model.*;
import site.yuqi.career.repository.ApplicationWorkflowEventRepository;
import site.yuqi.career.repository.ApplicationWorkflowRepository;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class ApplicationWorkflowService {
    private static final EnumSet<ApplicationWorkflowState> RESOLUTION_RECORDED = EnumSet.of(
            ApplicationWorkflowState.RESOLVED, ApplicationWorkflowState.REVIEWED,
            ApplicationWorkflowState.READY_TO_SUBMIT, ApplicationWorkflowState.SUBMITTED,
            ApplicationWorkflowState.CONFIRMED);
    private static final EnumSet<ApplicationWorkflowState> REVIEW_RECORDED = EnumSet.of(
            ApplicationWorkflowState.REVIEWED, ApplicationWorkflowState.READY_TO_SUBMIT,
            ApplicationWorkflowState.SUBMITTED, ApplicationWorkflowState.CONFIRMED);
    private static final EnumSet<ApplicationWorkflowState> FILL_RECORDED = EnumSet.of(
            ApplicationWorkflowState.READY_TO_SUBMIT, ApplicationWorkflowState.SUBMITTED,
            ApplicationWorkflowState.CONFIRMED);
    private final ApplicationWorkflowRepository workflows;
    private final ApplicationWorkflowEventRepository events;
    private final ObjectMapper mapper;

    public ApplicationWorkflowService(ApplicationWorkflowRepository workflows,
            ApplicationWorkflowEventRepository events, ObjectMapper mapper) {
        this.workflows = workflows;
        this.events = events;
        this.mapper = mapper;
    }

    @Transactional
    public ApplicationWorkflowView start(StartApplicationWorkflowRequest request) {
        ApplicationWorkflowEntity existing = workflows.findById(request.applicationId()).orElse(null);
        if (existing != null) {
            assertSameOrigin(existing, request.origin());
            assertSameStartRequest(existing, request);
            return view(existing);
        }
        ApplicationWorkflowEntity workflow = new ApplicationWorkflowEntity();
        String origin = canonicalOrigin(request.origin());
        String pageUrl = safePageUrl(request.pageUrl());
        if (!origin.equals(canonicalOrigin(pageUrl))) {
            throw new IllegalArgumentException("Application page URL does not match its declared origin");
        }
        workflow.setApplicationId(request.applicationId());
        workflow.setAts(normalizeAts(request.ats()));
        workflow.setOrigin(origin);
        workflow.setPageUrl(pageUrl);
        workflow.setJobTitle(blankToNull(request.jobTitle()));
        workflow.setDetectedFields(request.detectedFields());
        workflow.setState(ApplicationWorkflowState.SCANNED);
        workflows.save(workflow);
        append(workflow.getApplicationId(), null, ApplicationWorkflowState.SCANNED, "APPLICATION_SCANNED",
                Map.of("detectedFields", request.detectedFields(), "ats", workflow.getAts()));
        return view(workflow);
    }

    @Transactional(readOnly = true)
    public ApplicationWorkflowView get(String applicationId) { return view(required(applicationId)); }

    @Transactional
    public ApplicationWorkflowView recordResolution(String applicationId, ApplicationResolutionRequest request) {
        ApplicationWorkflowEntity workflow = required(applicationId);
        if (RESOLUTION_RECORDED.contains(workflow.getState())) return view(workflow);
        int classified = request.resolved() + request.requiresReview() + request.unsupported();
        if (classified > workflow.getDetectedFields()) {
            throw new IllegalArgumentException("Resolved field counts exceed detected fields");
        }
        workflow.setResolvedFields(request.resolved());
        workflow.setReviewFields(request.requiresReview());
        transition(workflow, ApplicationWorkflowState.RESOLVED, "FIELDS_RESOLVED", Map.of(
                "resolved", request.resolved(), "requiresReview", request.requiresReview(),
                "unsupported", request.unsupported()));
        return view(workflow);
    }

    @Transactional
    public ApplicationWorkflowView recordReview(String applicationId, ApplicationReviewRequest request) {
        ApplicationWorkflowEntity workflow = required(applicationId);
        if (REVIEW_RECORDED.contains(workflow.getState())) return view(workflow);
        if (request.approvedFields() > workflow.getResolvedFields() + workflow.getReviewFields()) {
            throw new IllegalArgumentException("Approved field count exceeds resolvable fields");
        }
        workflow.setApprovedFields(request.approvedFields());
        transition(workflow, ApplicationWorkflowState.REVIEWED, "FIELDS_REVIEWED",
                Map.of("approvedFields", request.approvedFields()));
        return view(workflow);
    }

    @Transactional
    public ApplicationWorkflowView recordFill(String applicationId, ApplicationFillRequest request) {
        ApplicationWorkflowEntity workflow = required(applicationId);
        if (FILL_RECORDED.contains(workflow.getState())) return view(workflow);
        if (request.appliedFields() > workflow.getApprovedFields()) {
            throw new IllegalArgumentException("Applied field count exceeds approved fields");
        }
        workflow.setAppliedFields(request.appliedFields());
        workflow.setResumeAttached(request.resumeAttached());
        workflow.setDetectedAction(blankToNull(request.detectedAction()));
        transition(workflow, ApplicationWorkflowState.READY_TO_SUBMIT, "APPLICATION_FILLED", Map.of(
                "appliedFields", request.appliedFields(), "resumeAttached", request.resumeAttached(),
                "detectedAction", String.valueOf(request.detectedAction())));
        return view(workflow);
    }

    @Transactional
    public ApplicationWorkflowView recordSubmission(String applicationId, SubmissionReceiptRequest request) {
        ApplicationWorkflowEntity workflow = required(applicationId);
        assertSameOrigin(workflow, request.successUrl());
        if (workflow.getState() == ApplicationWorkflowState.SUBMITTED
                || workflow.getState() == ApplicationWorkflowState.CONFIRMED) return view(workflow);
        workflow.setSuccessUrl(safePageUrl(request.successUrl()));
        workflow.setExternalApplicationId(blankToNull(request.externalApplicationId()));
        transition(workflow, ApplicationWorkflowState.SUBMITTED, "ATS_SUBMISSION_ACCEPTED", Map.of(
                "successUrl", workflow.getSuccessUrl(),
                "externalApplicationId", String.valueOf(request.externalApplicationId()),
                "confirmationEvidence", request.confirmationText() == null || request.confirmationText().isBlank()
                        ? "MISSING" : "PRESENT"));
        return view(workflow);
    }

    @Transactional
    public ApplicationWorkflowView confirm(String applicationId, ApplicationConfirmationRequest request) {
        ApplicationWorkflowEntity workflow = required(applicationId);
        if (workflow.getState() == ApplicationWorkflowState.CONFIRMED) return view(workflow);
        transition(workflow, ApplicationWorkflowState.CONFIRMED, "SUBMISSION_CONFIRMED", Map.of(
                "channel", request.channel(), "externalMessageId", String.valueOf(request.externalMessageId())));
        return view(workflow);
    }

    private void transition(ApplicationWorkflowEntity workflow, ApplicationWorkflowState target,
            String eventType, Map<String, ?> metadata) {
        ApplicationWorkflowState from = workflow.getState();
        workflow.transitionTo(target);
        workflows.save(workflow);
        append(workflow.getApplicationId(), from, target, eventType, metadata);
    }

    private void append(String applicationId, ApplicationWorkflowState from, ApplicationWorkflowState to,
            String eventType, Map<String, ?> metadata) {
        try {
            ApplicationWorkflowEventEntity event = new ApplicationWorkflowEventEntity();
            event.setApplicationId(applicationId);
            event.setFromState(from);
            event.setToState(to);
            event.setEventType(eventType);
            event.setMetadataJson(mapper.writeValueAsString(metadata));
            events.save(event);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to persist application workflow event", error);
        }
    }

    private ApplicationWorkflowEntity required(String applicationId) {
        return workflows.findById(applicationId)
                .orElseThrow(() -> new EntityNotFoundException("Application workflow not found: " + applicationId));
    }

    private ApplicationWorkflowView view(ApplicationWorkflowEntity row) {
        List<ApplicationWorkflowView.Event> timeline = events.findByApplicationIdOrderByOccurredAtAsc(row.getApplicationId())
                .stream().map(event -> new ApplicationWorkflowView.Event(event.getId(), event.getFromState(),
                        event.getToState(), event.getEventType(), metadata(event.getMetadataJson()), event.getOccurredAt()))
                .toList();
        return new ApplicationWorkflowView(row.getApplicationId(), row.getAts(), row.getOrigin(), row.getPageUrl(),
                row.getJobTitle(), row.getState(), row.getDetectedFields(), row.getResolvedFields(),
                row.getReviewFields(), row.getApprovedFields(), row.getAppliedFields(), row.isResumeAttached(),
                row.getDetectedAction(), row.getSuccessUrl(), row.getExternalApplicationId(), row.getVersion(),
                row.getCreatedAt(), row.getUpdatedAt(), row.getSubmittedAt(), row.getConfirmedAt(), timeline);
    }

    private Map<String, Object> metadata(String json) {
        try { return mapper.readValue(json, new TypeReference<>() {}); }
        catch (Exception error) { return Map.of("unreadable", true); }
    }

    private static void assertSameOrigin(ApplicationWorkflowEntity workflow, String url) {
        String actual = canonicalOrigin(url);
        if (!workflow.getOrigin().equals(actual)) {
            throw new IllegalArgumentException("Submission receipt origin does not match the scanned application");
        }
    }

    private static void assertSameStartRequest(ApplicationWorkflowEntity workflow,
            StartApplicationWorkflowRequest request) {
        boolean matches = workflow.getAts().equals(normalizeAts(request.ats()))
                && workflow.getPageUrl().equals(safePageUrl(request.pageUrl()))
                && workflow.getDetectedFields() == request.detectedFields();
        if (!matches) {
            throw new IllegalStateException("Application id was reused with a different scan payload");
        }
    }

    private static String canonicalOrigin(String value) {
        URI uri = URI.create(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalArgumentException("Application origin must use HTTPS");
        }
        int port = uri.getPort();
        return "https://" + uri.getHost().toLowerCase(Locale.ROOT) + (port > 0 && port != 443 ? ":" + port : "");
    }

    /** Persist no query parameters, fragments or user-info from third-party ATS URLs. */
    private static String safePageUrl(String value) {
        URI uri = URI.create(value);
        canonicalOrigin(value);
        try {
            return new URI("https", null, uri.getHost().toLowerCase(Locale.ROOT), uri.getPort(),
                    uri.getRawPath() == null || uri.getRawPath().isBlank() ? "/" : uri.getRawPath(),
                    null, null).toASCIIString();
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("Application URL is invalid", error);
        }
    }

    private static String normalizeAts(String value) {
        return value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "_");
    }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
