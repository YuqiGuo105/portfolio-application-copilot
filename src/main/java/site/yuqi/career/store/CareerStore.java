package site.yuqi.career.store;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import site.yuqi.career.config.CareerProperties;
import site.yuqi.career.model.CandidateProfile;
import site.yuqi.career.security.FieldCipher;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
@Component
public class CareerStore {
    private static final String PROFILE_KEY = "career:profile:current";
    private static final String ANSWERS_KEY = "career:private-answers:v1";
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
}
