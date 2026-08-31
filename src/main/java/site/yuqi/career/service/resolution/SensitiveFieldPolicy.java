package site.yuqi.career.service.resolution;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/** Hard safety policy: these answers may be recalled but are never silently applied. */
@Component
public class SensitiveFieldPolicy {
    private static final Set<String> REVIEW_TERMS = Set.of(
            "work authorization", "sponsorship", "visa", "h1b", "h 1b", "i140", "i 140",
            "immigration", "salary", "compensation", "relocation", "background", "signature",
            "gender", "race", "veteran", "disability", "government", "security clearance");

    public boolean requiresConfirmation(String normalizedLabel) {
        return REVIEW_TERMS.stream().anyMatch(normalizedLabel::contains);
    }

    public String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
