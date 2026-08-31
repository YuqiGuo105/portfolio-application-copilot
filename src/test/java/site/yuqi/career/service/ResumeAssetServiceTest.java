package site.yuqi.career.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yuqi.career.client.SupabaseStorageClient;
import site.yuqi.career.config.CareerProperties;
import site.yuqi.career.domain.ResumeAssetEntity;
import site.yuqi.career.model.ResumeAssetStatus;
import site.yuqi.career.model.ResumeCompleteRequest;
import site.yuqi.career.model.ResumeUploadRequest;
import site.yuqi.career.repository.ResumeAssetRepository;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeAssetServiceTest {
    @Mock ResumeAssetRepository assets;
    @Mock ResumeAssetStateService state;
    @Mock SupabaseStorageClient storage;
    private ResumeAssetService service;

    @BeforeEach
    void setUp() {
        CareerProperties.ResumeStorage resumeStorage = new CareerProperties.ResumeStorage(
                "https://project.supabase.co", "service-role", "career-resumes", 2_097_152, Duration.ofMinutes(2));
        CareerProperties properties = new CareerProperties("token", "owner", Duration.ofHours(1), null, resumeStorage);
        service = new ResumeAssetService(assets, state, storage, properties);
    }

    @Test
    void preparesPrivateSignedUploadWithSanitizedObjectKey() {
        when(storage.createSignedUpload(anyString())).thenReturn(new SupabaseStorageClient.SignedUrl(
                URI.create("https://project.supabase.co/upload"), Instant.parse("2026-08-31T20:00:00Z")));
        when(state.createUpload(anyString(), eq("Yuqi-Guo-Resume.pdf"), eq("career-resumes"),
                anyString(), eq("application/pdf"))).thenAnswer(invocation -> asset(
                        invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(3)));

        var ticket = service.prepareUpload(new ResumeUploadRequest("Yuqi Guo Resume.pdf", "application/pdf"));

        assertEquals("Yuqi-Guo-Resume.pdf", ticket.fileName());
        assertEquals("https://project.supabase.co/upload", ticket.uploadUrl());
        verify(storage).createSignedUpload(matches("resumes/portfolio-owner/.+/Yuqi-Guo-Resume\\.pdf"));
    }

    @Test
    void validatesPdfAndActivatesUsingServerComputedChecksum() {
        byte[] pdf = "%PDF-1.7\nresume".getBytes(StandardCharsets.US_ASCII);
        ResumeAssetEntity pending = asset("asset-1", "resume.pdf", "resumes/owner/asset-1/resume.pdf");
        ResumeAssetEntity active = asset("asset-1", "resume.pdf", pending.getStorageObjectKey());
        active.setStatus(ResumeAssetStatus.ACTIVE);
        active.setActive(true);
        active.setSizeBytes((long) pdf.length);
        active.setSha256("7758d050272876c5af8fb42aec794a63bc84def5b09312292a5b566702401e65");
        when(state.get("asset-1")).thenReturn(pending);
        when(storage.download(pending.getStorageObjectKey())).thenReturn(pdf);
        when(state.activateValidated(eq("asset-1"), eq((long) pdf.length), anyString())).thenReturn(active);

        var result = service.complete("asset-1", new ResumeCompleteRequest(null));

        assertTrue(result.active());
        verify(state).activateValidated(eq("asset-1"), eq((long) pdf.length), matches("[a-f0-9]{64}"));
        verify(state, never()).reject(anyString(), anyString());
    }

    @Test
    void rejectsNonPdfObjectBeforeActivation() {
        ResumeAssetEntity pending = asset("asset-2", "resume.pdf", "resumes/owner/asset-2/resume.pdf");
        when(state.get("asset-2")).thenReturn(pending);
        when(storage.download(pending.getStorageObjectKey())).thenReturn("not a pdf".getBytes(StandardCharsets.UTF_8));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.complete("asset-2", new ResumeCompleteRequest(null)));

        assertEquals("Uploaded object is not a valid PDF file", error.getMessage());
        verify(state).reject("asset-2", error.getMessage());
        verify(state, never()).activateValidated(anyString(), anyLong(), anyString());
    }

    @Test
    void returnsShortLivedTicketForActiveResume() {
        ResumeAssetEntity active = asset("asset-3", "Yuqi_Guo_Resume_SDE2.pdf", "resumes/owner/asset-3/resume.pdf");
        active.setStatus(ResumeAssetStatus.ACTIVE);
        active.setActive(true);
        active.setSizeBytes(127_000L);
        active.setSha256("37c0e1b8a07e9ff70e1a69debd5d8077c88f6c46569083092a06cf41b2ab2724");
        when(assets.findFirstByOwnerIdAndActiveTrueAndDeletedAtIsNull("portfolio-owner")).thenReturn(Optional.of(active));
        when(storage.createSignedDownload(active.getStorageObjectKey(), active.getDisplayName()))
                .thenReturn(new SupabaseStorageClient.SignedUrl(URI.create("https://signed.example/resume"),
                        Instant.parse("2026-08-31T20:02:00Z")));

        var ticket = service.activeDownload();

        assertEquals(active.getSha256(), ticket.sha256());
        assertEquals("https://signed.example/resume", ticket.downloadUrl());
    }

    private static ResumeAssetEntity asset(String id, String fileName, String objectKey) {
        ResumeAssetEntity asset = new ResumeAssetEntity();
        asset.setId(id);
        asset.setOwnerId("portfolio-owner");
        asset.setDisplayName(fileName);
        asset.setStorageBucket("career-resumes");
        asset.setStorageObjectKey(objectKey);
        asset.setMimeType("application/pdf");
        asset.setStatus(ResumeAssetStatus.UPLOADING);
        return asset;
    }
}
