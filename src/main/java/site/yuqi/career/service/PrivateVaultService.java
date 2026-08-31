package site.yuqi.career.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yuqi.career.domain.PrivateProfileEntity;
import site.yuqi.career.domain.PrivateResumeEntity;
import site.yuqi.career.domain.VaultAuditEntity;
import site.yuqi.career.model.PrivateResumeRequest;
import site.yuqi.career.model.PrivateResumeView;
import site.yuqi.career.repository.PrivateProfileRepository;
import site.yuqi.career.repository.PrivateResumeRepository;
import site.yuqi.career.repository.VaultAuditRepository;
import site.yuqi.career.security.FieldCipher;
import site.yuqi.career.store.CareerStore;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PrivateVaultService {
    private static final String OWNER_ID = "portfolio-owner";
    private final PrivateResumeRepository resumes;
    private final PrivateProfileRepository profiles;
    private final VaultAuditRepository audits;
    private final FieldCipher cipher;
    private final ObjectMapper mapper;
    private final CareerStore cache;

    public PrivateVaultService(PrivateResumeRepository resumes, PrivateProfileRepository profiles,
            VaultAuditRepository audits, FieldCipher cipher, ObjectMapper mapper, CareerStore cache) {
        this.resumes = resumes; this.profiles = profiles; this.audits = audits;
        this.cipher = cipher; this.mapper = mapper; this.cache = cache;
    }

    @Transactional(readOnly = true)
    public List<PrivateResumeView> listResumes() {
        return resumes.findByDeletedAtIsNullOrderByUpdatedAtDesc().stream().map(this::view).toList();
    }

    @Transactional(readOnly = true)
    public PrivateResumeView getResume(String id) { return view(activeResume(id)); }

    @Transactional
    public PrivateResumeView createResume(PrivateResumeRequest request) {
        PrivateResumeEntity entity = new PrivateResumeEntity();
        apply(entity, request);
        PrivateResumeEntity saved = resumes.save(entity);
        audit("CREATE", "PRIVATE_RESUME", saved.getId(), Map.of("active", saved.isActive()));
        return view(saved);
    }

    @Transactional
    public PrivateResumeView updateResume(String id, PrivateResumeRequest request) {
        PrivateResumeEntity entity = activeResume(id);
        apply(entity, request);
        PrivateResumeEntity saved = resumes.save(entity);
        audit("UPDATE", "PRIVATE_RESUME", saved.getId(), Map.of("active", saved.isActive(), "version", saved.getVersion()));
        return view(saved);
    }

    @Transactional
    public void deleteResume(String id) {
        PrivateResumeEntity entity = activeResume(id);
        entity.setActive(false);
        entity.setDeletedAt(Instant.now());
        resumes.save(entity);
        audit("SOFT_DELETE", "PRIVATE_RESUME", id, Map.of("recoverable", true));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getAnswers() {
        try {
            Map<String, Object> cached = cache.getPrivateAnswers();
            if (!cached.isEmpty()) return cached;
        } catch (RuntimeException ignored) {
            // PostgreSQL remains authoritative when Valkey is cold or unavailable.
        }
        Map<String, Object> durable = profiles.findById(OWNER_ID)
                .filter(row -> row.getDeletedAt() == null)
                .map(this::decryptAnswers).orElseGet(Map::of);
        if (!durable.isEmpty()) {
            try { cache.putPrivateAnswers(durable); } catch (RuntimeException ignored) { }
        }
        return durable;
    }

    @Transactional
    public Map<String, Object> mergeAnswers(Map<String, Object> updates) {
        Map<String, Object> merged = new LinkedHashMap<>(getAnswers());
        updates.forEach((key, value) -> {
            String normalized = normalize(key);
            if (value == null) merged.remove(normalized); else merged.put(normalized, value);
        });
        persistAnswers(merged);
        audit("UPSERT", "PRIVATE_ANSWERS", OWNER_ID, Map.of("updatedKeys", updates.keySet()));
        return Map.copyOf(merged);
    }

    @Transactional
    public Map<String, Object> putAnswer(String key, Object value) { return mergeAnswers(Map.of(normalize(key), value)); }

    @Transactional
    public Map<String, Object> deleteAnswer(String key) {
        Map<String, Object> merged = new LinkedHashMap<>(getAnswers());
        merged.remove(normalize(key));
        persistAnswers(merged);
        audit("DELETE_FIELD", "PRIVATE_ANSWERS", normalize(key), Map.of("recoverableFromAudit", false));
        return Map.copyOf(merged);
    }

    private void apply(PrivateResumeEntity entity, PrivateResumeRequest request) {
        if (request.active()) {
            resumes.findByActiveTrueAndDeletedAtIsNull().stream()
                    .filter(other -> entity.getId() == null || !other.getId().equals(entity.getId()))
                    .forEach(other -> { other.setActive(false); resumes.save(other); });
            resumes.flush();
        }
        entity.setLabel(request.label().trim());
        entity.setContentCiphertext(cipher.encrypt(request.content()));
        entity.setFileName(blankToNull(request.fileName()));
        entity.setMimeType(blankToNull(request.mimeType()));
        entity.setSourceUrl(blankToNull(request.sourceUrl()));
        entity.setActive(request.active());
        entity.setDeletedAt(null);
    }

    private PrivateResumeEntity activeResume(String id) {
        return resumes.findById(id).filter(row -> row.getDeletedAt() == null)
                .orElseThrow(() -> new EntityNotFoundException("Private resume not found: " + id));
    }

    private PrivateResumeView view(PrivateResumeEntity row) {
        return new PrivateResumeView(row.getId(), row.getLabel(), cipher.decrypt(row.getContentCiphertext()),
                row.getFileName(), row.getMimeType(), row.getSourceUrl(), row.isActive(), row.getVersion(),
                row.getCreatedAt(), row.getUpdatedAt());
    }

    private Map<String, Object> decryptAnswers(PrivateProfileEntity row) {
        try { return mapper.readValue(cipher.decrypt(row.getAnswersCiphertext()), new TypeReference<>() {}); }
        catch (Exception e) { throw new IllegalStateException("Unable to read private application profile", e); }
    }

    private void persistAnswers(Map<String, Object> answers) {
        try {
            PrivateProfileEntity row = profiles.findById(OWNER_ID).orElseGet(() -> {
                PrivateProfileEntity created = new PrivateProfileEntity(); created.setOwnerId(OWNER_ID); return created;
            });
            row.setAnswersCiphertext(cipher.encrypt(mapper.writeValueAsString(answers)));
            row.setDeletedAt(null);
            profiles.save(row);
            try { cache.putPrivateAnswers(Map.copyOf(answers)); } catch (RuntimeException ignored) { }
        } catch (Exception e) { throw new IllegalStateException("Unable to persist private application profile", e); }
    }

    private void audit(String action, String resourceType, String resourceId, Map<String, ?> metadata) {
        try {
            VaultAuditEntity event = new VaultAuditEntity();
            event.setActor("owner-mcp"); event.setAction(action); event.setResourceType(resourceType);
            event.setResourceId(resourceId); event.setMetadataJson(mapper.writeValueAsString(metadata)); audits.save(event);
        } catch (Exception e) { throw new IllegalStateException("Unable to audit private vault mutation", e); }
    }

    private static String normalize(String value) { return String.valueOf(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim(); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
