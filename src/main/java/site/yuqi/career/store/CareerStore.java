package site.yuqi.career.store;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import site.yuqi.career.config.CareerProperties;
import site.yuqi.career.model.CandidateProfile;
import site.yuqi.career.model.SiteCredential;
import site.yuqi.career.security.FieldCipher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
@Component
public class CareerStore {
    private static final String PROFILE_KEY = "career:profile:current";
    private static final String ANSWERS_KEY = "career:private-answers:v1";
    private static final String CREDENTIAL_KEY_PREFIX = "career:site-credential:v1:";
    private static final String QUESTION_CLASSIFICATION_KEY_PREFIX = "career:question-classification:v1:";
    private final StringRedisTemplate redis; private final ObjectMapper mapper; private final FieldCipher cipher; private final Duration profileTtl;
    public CareerStore(StringRedisTemplate redis, ObjectMapper mapper, FieldCipher cipher, CareerProperties properties) {
        this.redis = redis; this.mapper = mapper; this.cipher = cipher; this.profileTtl = properties.profileTtl();
    }
    public Optional<CandidateProfile> getProfile() {
        try { String value = redis.opsForValue().get(PROFILE_KEY); return value == null ? Optional.empty() : Optional.of(mapper.readValue(value, CandidateProfile.class)); }
        catch (Exception ignored) { return Optional.empty(); }
    }
    public void putProfile(CandidateProfile profile) {
        try { redis.opsForValue().set(PROFILE_KEY, mapper.writeValueAsString(profile), profileTtl); }
        catch (Exception e) { throw new IllegalStateException("Valkey profile cache unavailable", e); }
    }
    public Map<String, Object> getPrivateAnswers() {
        try { String value = redis.opsForValue().get(ANSWERS_KEY); return value == null ? Map.of() : mapper.readValue(cipher.decrypt(value), new TypeReference<>() {}); }
        catch (Exception e) { throw new IllegalStateException("Private application memory unavailable", e); }
    }
    public void putPrivateAnswers(Map<String, Object> answers) {
        try { redis.opsForValue().set(ANSWERS_KEY, cipher.encrypt(mapper.writeValueAsString(answers))); }
        catch (Exception e) { throw new IllegalStateException("Unable to persist private application memory", e); }
    }

    public Optional<SiteCredential> getSiteCredential(String origin) {
        try {
            String value = redis.opsForValue().get(credentialKey(origin));
            return value == null ? Optional.empty()
                    : Optional.of(mapper.readValue(cipher.decrypt(value), SiteCredential.class));
        } catch (Exception e) {
            throw new IllegalStateException("Site credential memory unavailable", e);
        }
    }

    public SiteCredential putSiteCredentialIfAbsent(SiteCredential credential) {
        try {
            String key = credentialKey(credential.origin());
            String encrypted = cipher.encrypt(mapper.writeValueAsString(credential));
            Boolean created = redis.opsForValue().setIfAbsent(key, encrypted);
            if (Boolean.TRUE.equals(created)) return credential;
            return getSiteCredential(credential.origin())
                    .orElseThrow(() -> new IllegalStateException("Credential creation lost without a stored winner"));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to persist site credential", e);
        }
    }

    public Optional<String> getQuestionClassification(String fingerprint) {
        try { return Optional.ofNullable(redis.opsForValue().get(QUESTION_CLASSIFICATION_KEY_PREFIX + fingerprint)); }
        catch (Exception ignored) { return Optional.empty(); }
    }

    public void putQuestionClassification(String fingerprint, String classification) {
        try { redis.opsForValue().set(QUESTION_CLASSIFICATION_KEY_PREFIX + fingerprint, classification, Duration.ofDays(30)); }
        catch (Exception ignored) { }
    }

    private static String credentialKey(String origin) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(origin.getBytes(StandardCharsets.UTF_8));
            return CREDENTIAL_KEY_PREFIX + java.util.HexFormat.of().formatHex(digest);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to key site credential", error);
        }
    }
}
