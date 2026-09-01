package site.yuqi.career.controller;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import site.yuqi.career.model.*;
import site.yuqi.career.service.ApplicationFieldResolver;
import site.yuqi.career.service.ApplicationWorkflowService;
import site.yuqi.career.service.CandidateProfileService;
import site.yuqi.career.service.GeminiQuestionClassifier;
import site.yuqi.career.service.SiteCredentialService;
import site.yuqi.career.service.PrivateVaultService;
import site.yuqi.career.service.ResumeAssetService;
import java.util.List;
import java.util.Map;
@RestController @RequestMapping("/internal/v1")
public class CareerController {
    private final CandidateProfileService profiles; private final ApplicationFieldResolver resolver;
    private final SiteCredentialService credentials;
    private final PrivateVaultService vault;
    private final ApplicationWorkflowService workflows;
    private final ResumeAssetService resumeAssets;
    private final GeminiQuestionClassifier questionClassifier;
    public CareerController(CandidateProfileService profiles, ApplicationFieldResolver resolver,
            SiteCredentialService credentials, PrivateVaultService vault, ApplicationWorkflowService workflows,
            ResumeAssetService resumeAssets, GeminiQuestionClassifier questionClassifier) {
        this.profiles = profiles; this.resolver = resolver; this.credentials = credentials; this.vault = vault;
        this.workflows = workflows;
        this.resumeAssets = resumeAssets;
        this.questionClassifier = questionClassifier;
    }
    @GetMapping("/candidate-profile") public CandidateProfile profile() { return profiles.get(); }
    @PostMapping("/candidate-profile/refresh") public CandidateProfile refreshProfile() { return profiles.refresh(); }
    @PutMapping("/candidate-profile/private-answers") public CandidateProfile updatePrivateAnswers(@Valid @RequestBody PrivateAnswersUpdate request) { return profiles.updatePrivateAnswers(request.answers()); }
    @PostMapping("/application-fields/resolve") public Map<String, Object> resolveFields(@Valid @RequestBody ResolveFieldsRequest request) {
        List<FieldResolution> fields = resolver.resolve(request);
        return Map.of("applicationId", request.applicationId(), "fields", fields,
                "resolved", fields.stream().filter(f -> f.status() == FieldResolution.ResolutionStatus.RESOLVED).count(),
                "requiresReview", fields.stream().filter(f -> f.status() != FieldResolution.ResolutionStatus.RESOLVED).count());
    }
    @PostMapping("/application-fields/classify")
    public QuestionClassificationResult classifyFields(@Valid @RequestBody ResolveFieldsRequest request) {
        return questionClassifier.classify(request);
    }
    @GetMapping("/site-credentials") public SiteCredential siteCredential(@RequestParam String origin) {
        return credentials.get(origin).orElseThrow(() -> new SiteCredentialNotFoundException(origin));
    }
    @PostMapping("/site-credentials/prepare") public SiteCredential prepareSiteCredential(
            @Valid @RequestBody SiteCredentialRequest request) {
        return credentials.prepare(request.origin(), request.username());
    }

    @GetMapping("/private-resumes") public List<PrivateResumeView> privateResumes() { return vault.listResumes(); }
    @GetMapping("/private-resumes/{id}") public PrivateResumeView privateResume(@PathVariable String id) { return vault.getResume(id); }
    @PostMapping("/private-resumes") public PrivateResumeView createPrivateResume(@Valid @RequestBody PrivateResumeRequest request) { return vault.createResume(request); }
    @PutMapping("/private-resumes/{id}") public PrivateResumeView updatePrivateResume(@PathVariable String id, @Valid @RequestBody PrivateResumeRequest request) { return vault.updateResume(id, request); }
    @DeleteMapping("/private-resumes/{id}") @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void deletePrivateResume(@PathVariable String id) { vault.deleteResume(id); }
    @GetMapping("/private-answers") public Map<String, Object> privateAnswers() { return vault.getAnswers(); }
    @PutMapping("/private-answers/{key}") public Map<String, Object> putPrivateAnswer(@PathVariable String key, @Valid @RequestBody PrivateAnswerRequest request) { return vault.putAnswer(key, request.value()); }
    @DeleteMapping("/private-answers/{key}") public Map<String, Object> deletePrivateAnswer(@PathVariable String key) { return vault.deleteAnswer(key); }

    @GetMapping("/resume-assets") public List<ResumeAssetView> resumeAssets() { return resumeAssets.list(); }
    @GetMapping("/resume-assets/active") public ResumeAssetView activeResumeAsset() { return resumeAssets.active(); }
    @PostMapping("/resume-assets/uploads") public ResumeUploadTicket prepareResumeUpload(
            @Valid @RequestBody ResumeUploadRequest request) { return resumeAssets.prepareUpload(request); }
    @PostMapping("/resume-assets/{id}/complete") public ResumeAssetView completeResumeUpload(
            @PathVariable String id, @Valid @RequestBody ResumeCompleteRequest request) {
        return resumeAssets.complete(id, request);
    }
    @PostMapping("/resume-assets/{id}/activate") public ResumeAssetView activateResumeAsset(@PathVariable String id) {
        return resumeAssets.activate(id);
    }
    @GetMapping("/resume-assets/active/download") public ResumeDownloadTicket activeResumeDownload() {
        return resumeAssets.activeDownload();
    }

    @PostMapping("/application-workflows")
    public ApplicationWorkflowView startWorkflow(@Valid @RequestBody StartApplicationWorkflowRequest request) {
        return workflows.start(request);
    }
    @GetMapping("/application-workflows/{applicationId}")
    public ApplicationWorkflowView workflow(@PathVariable String applicationId) { return workflows.get(applicationId); }
    @PutMapping("/application-workflows/{applicationId}/resolution")
    public ApplicationWorkflowView recordResolution(@PathVariable String applicationId,
            @Valid @RequestBody ApplicationResolutionRequest request) {
        return workflows.recordResolution(applicationId, request);
    }
    @PutMapping("/application-workflows/{applicationId}/review")
    public ApplicationWorkflowView recordReview(@PathVariable String applicationId,
            @Valid @RequestBody ApplicationReviewRequest request) {
        return workflows.recordReview(applicationId, request);
    }
    @PutMapping("/application-workflows/{applicationId}/fill")
    public ApplicationWorkflowView recordFill(@PathVariable String applicationId,
            @Valid @RequestBody ApplicationFillRequest request) {
        return workflows.recordFill(applicationId, request);
    }
    @PutMapping("/application-workflows/{applicationId}/submission")
    public ApplicationWorkflowView recordSubmission(@PathVariable String applicationId,
            @Valid @RequestBody SubmissionReceiptRequest request) {
        return workflows.recordSubmission(applicationId, request);
    }
    @PutMapping("/application-workflows/{applicationId}/confirmation")
    public ApplicationWorkflowView confirmSubmission(@PathVariable String applicationId,
            @Valid @RequestBody ApplicationConfirmationRequest request) {
        return workflows.confirm(applicationId, request);
    }

    @ResponseStatus(org.springframework.http.HttpStatus.NOT_FOUND)
    private static class SiteCredentialNotFoundException extends RuntimeException {
        SiteCredentialNotFoundException(String origin) { super("No managed account exists for " + origin); }
    }
}
