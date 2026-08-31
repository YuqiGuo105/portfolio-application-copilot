package site.yuqi.career.service;

import org.springframework.stereotype.Service;
import site.yuqi.career.model.SiteCredential;
import site.yuqi.career.store.CareerStore;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Optional;

@Service
public class SiteCredentialService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final char[] LOWER = "abcdefghijkmnopqrstuvwxyz".toCharArray();
    private static final char[] DIGITS = "23456789".toCharArray();
    private static final char[] SYMBOLS = "!@#$%*-_+".toCharArray();
    private static final char[] ALL = (new String(UPPER) + new String(LOWER) + new String(DIGITS)
            + new String(SYMBOLS)).toCharArray();

    private final CareerStore store;

    public SiteCredentialService(CareerStore store) {
        this.store = store;
    }

    public Optional<SiteCredential> get(String rawOrigin) {
        return store.getSiteCredential(normalizeOrigin(rawOrigin));
    }

    public SiteCredential prepare(String rawOrigin, String username) {
        String origin = normalizeOrigin(rawOrigin);
        String normalizedUsername = username == null ? "" : username.trim();
        if (normalizedUsername.isBlank()) {
            throw new IllegalArgumentException("A reviewed account email or username is required.");
        }
        Optional<SiteCredential> existing = store.getSiteCredential(origin);
        if (existing.isPresent()) {
            return requireMatchingUsername(existing.get(), normalizedUsername);
        }
        SiteCredential candidate = new SiteCredential(
                origin, normalizedUsername, generatePassword(), Instant.now());
        SiteCredential stored = store.putSiteCredentialIfAbsent(candidate);
        return requireMatchingUsername(stored, normalizedUsername);
    }

    static String normalizeOrigin(String rawOrigin) {
        try {
            URI uri = URI.create(rawOrigin);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                throw new IllegalArgumentException("Only HTTPS application origins are supported.");
            }
            int port = uri.getPort();
            return "https://" + uri.getHost().toLowerCase() + (port == -1 || port == 443 ? "" : ":" + port);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("A valid HTTPS application origin is required.", error);
        }
    }

    static String generatePassword() {
        char[] password = new char[24];
        password[0] = random(UPPER);
        password[1] = random(LOWER);
        password[2] = random(DIGITS);
        password[3] = random(SYMBOLS);
        for (int index = 4; index < password.length; index += 1) password[index] = random(ALL);
        for (int index = password.length - 1; index > 0; index -= 1) {
            int swap = RANDOM.nextInt(index + 1);
            char value = password[index];
            password[index] = password[swap];
            password[swap] = value;
        }
        return new String(password);
    }

    static SiteCredential requireMatchingUsername(SiteCredential credential, String requestedUsername) {
        if (!credential.username().equalsIgnoreCase(requestedUsername)) {
            throw new IllegalArgumentException(
                    "This application origin already has a managed account with a different username.");
        }
        return credential;
    }

    private static char random(char[] alphabet) {
        return alphabet[RANDOM.nextInt(alphabet.length)];
    }
}
