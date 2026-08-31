package site.yuqi.career.security;
import org.springframework.stereotype.Component;
import site.yuqi.career.config.CareerProperties;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
@Component
public class FieldCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKeySpec key;
    public FieldCipher(CareerProperties properties) {
        if (properties.ownerKey() == null || properties.ownerKey().isBlank()) throw new IllegalStateException("CAREER_OWNER_KEY must be configured");
        try { key = new SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(properties.ownerKey().getBytes(StandardCharsets.UTF_8)), "AES"); }
        catch (Exception e) { throw new IllegalStateException("Unable to initialize field encryption", e); }
    }
    public String encrypt(String value) { return transform(Cipher.ENCRYPT_MODE, value); }
    public String decrypt(String value) {
        try {
            byte[] packed = Base64.getDecoder().decode(value); byte[] iv = java.util.Arrays.copyOfRange(packed, 0, 12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(java.util.Arrays.copyOfRange(packed, 12, packed.length)), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("Unable to decrypt private application answers", e); }
    }
    private String transform(int mode, String value) {
        try {
            byte[] iv = new byte[12]; RANDOM.nextBytes(iv); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(mode, key, new GCMParameterSpec(128, iv)); byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] packed = new byte[iv.length + encrypted.length]; System.arraycopy(iv, 0, packed, 0, iv.length);
            System.arraycopy(encrypted, 0, packed, iv.length, encrypted.length); return Base64.getEncoder().encodeToString(packed);
        } catch (Exception e) { throw new IllegalStateException("Unable to encrypt private application answers", e); }
    }
}
