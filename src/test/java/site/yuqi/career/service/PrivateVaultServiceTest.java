package site.yuqi.career.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yuqi.career.domain.PrivateProfileEntity;
import site.yuqi.career.domain.PrivateResumeEntity;
import site.yuqi.career.model.PrivateResumeRequest;
import site.yuqi.career.repository.PrivateProfileRepository;
import site.yuqi.career.repository.PrivateResumeRepository;
import site.yuqi.career.repository.VaultAuditRepository;
import site.yuqi.career.security.FieldCipher;
import site.yuqi.career.store.CareerStore;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivateVaultServiceTest {
    @Mock PrivateResumeRepository resumes;
    @Mock PrivateProfileRepository profiles;
    @Mock VaultAuditRepository audits;
    @Mock FieldCipher cipher;
    @Mock CareerStore cache;

    private PrivateVaultService service;

    @BeforeEach
    void setUp() {
        service = new PrivateVaultService(
                resumes, profiles, audits, cipher, new ObjectMapper(), cache);
    }

    @Test
    void fallsBackToPostgresWhenValkeyIsUnavailable() {
        PrivateProfileEntity row = new PrivateProfileEntity();
        row.setOwnerId("portfolio-owner");
        row.setAnswersCiphertext("encrypted");
        when(cache.getPrivateAnswers()).thenThrow(new IllegalStateException("Valkey cold"));
        when(profiles.findById("portfolio-owner")).thenReturn(Optional.of(row));
        when(cipher.decrypt("encrypted")).thenReturn("{\"i140 status\":\"No\"}");

        Map<String, Object> answers = service.getAnswers();

        assertThat(answers).containsEntry("i140 status", "No");
        verify(cache).putPrivateAnswers(answers);
    }

    @Test
    void creatingAnActiveResumeDeactivatesThePreviousResume() {
        PrivateResumeEntity previous = resume("previous", true);
        when(audits.save(any())).thenAnswer(call -> call.getArgument(0));
        when(resumes.findByActiveTrueAndDeletedAtIsNull()).thenReturn(List.of(previous));
        when(cipher.encrypt("Private application resume")).thenReturn("encrypted-resume");
        when(cipher.decrypt("encrypted-resume")).thenReturn("Private application resume");
        when(resumes.save(any(PrivateResumeEntity.class))).thenAnswer(call -> {
            PrivateResumeEntity value = call.getArgument(0);
            if (value.getId() == null) value.setId("new-resume");
            return value;
        });

        var created = service.createResume(new PrivateResumeRequest(
                "Backend application resume", "Private application resume",
                "resume.pdf", "application/pdf", null, true));

        assertThat(previous.isActive()).isFalse();
        assertThat(created.id()).isEqualTo("new-resume");
        assertThat(created.content()).isEqualTo("Private application resume");
        assertThat(created.active()).isTrue();
        verify(resumes).flush();
    }

    @Test
    void deletingAResumeIsRecoverableSoftDelete() {
        PrivateResumeEntity existing = resume("resume-1", true);
        when(audits.save(any())).thenAnswer(call -> call.getArgument(0));
        when(resumes.findById("resume-1")).thenReturn(Optional.of(existing));
        when(resumes.save(existing)).thenReturn(existing);

        service.deleteResume("resume-1");

        assertThat(existing.isActive()).isFalse();
        assertThat(existing.getDeletedAt()).isNotNull();
        verify(resumes).save(existing);
    }

    private static PrivateResumeEntity resume(String id, boolean active) {
        PrivateResumeEntity entity = new PrivateResumeEntity();
        entity.setId(id);
        entity.setLabel("Resume");
        entity.setContentCiphertext("encrypted-resume");
        entity.setActive(active);
        return entity;
    }
}
