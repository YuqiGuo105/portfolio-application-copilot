package site.yuqi.career.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import site.yuqi.career.client.McpGatewayClient;
import site.yuqi.career.model.CandidateProfile;
import site.yuqi.career.store.CareerStore;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
@Service
public class CandidateProfileService implements CandidateProfileProvider {
    private final McpGatewayClient gateway; private final CareerStore store; private final ObjectMapper mapper; private final PrivateVaultService vault;
    public CandidateProfileService(McpGatewayClient gateway, CareerStore store, ObjectMapper mapper, PrivateVaultService vault) { this.gateway = gateway; this.store = store; this.mapper = mapper; this.vault = vault; }
    @Override
    public CandidateProfile get() {
        CandidateProfile publicProfile = store.getProfile().orElseGet(this::refreshPublicProfile);
        return withPrivateAnswers(publicProfile, vault.getAnswers());
    }
    public CandidateProfile refresh() {
        return withPrivateAnswers(refreshPublicProfile(), vault.getAnswers());
    }
    private CandidateProfile refreshPublicProfile() {
        List<Map<String, Object>> experience = gateway.searchContent("Yuqi Guo software engineer", "EXPERIENCE", 20);
        List<Map<String, Object>> projects = gateway.searchContent("distributed systems platform", "PROJECT", 20);
        Set<String> skills = extractSkills(experience, projects);
        CandidateProfile profile = new CandidateProfile(versionOf(experience, projects, skills), Instant.now(), buildSummary(experience, projects, skills),
                List.copyOf(skills), experience, projects, Map.of(),
                new CandidateProfile.Source("yuqi.site MCP cluster", List.of("admin.search_content"), "Valkey cache refreshed from first-party content projection"));
        store.putProfile(profile); return profile;
    }
    public CandidateProfile updatePrivateAnswers(Map<String, Object> updates) {
        Map<String, Object> merged = vault.mergeAnswers(updates);
        CandidateProfile publicProfile = store.getProfile().orElseGet(this::refreshPublicProfile);
        return withPrivateAnswers(publicProfile, Map.copyOf(merged));
    }
    private CandidateProfile withPrivateAnswers(CandidateProfile profile, Map<String, Object> privateAnswers) {
        return new CandidateProfile(profile.profileVersion(), profile.refreshedAt(), profile.summary(), profile.skills(),
                profile.experience(), profile.projects(), privateAnswers, profile.source());
    }
    @SafeVarargs private final Set<String> extractSkills(List<Map<String, Object>>... groups) {
        Set<String> skills = new LinkedHashSet<>();
        for (List<Map<String, Object>> group : groups) for (Map<String, Object> item : group) for (String key : List.of("skills", "technologies", "tags")) {
            Object value = item.get(key); if (value instanceof Iterable<?> values) values.forEach(v -> skills.add(String.valueOf(v)));
        }
        return skills.stream().filter(s -> !s.isBlank()).limit(40).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
    private String buildSummary(List<Map<String, Object>> experience, List<Map<String, Object>> projects, Set<String> skills) {
        String title = firstString(experience, "title", "name", "position"); String project = firstString(projects, "title", "name"); List<String> parts = new ArrayList<>();
        parts.add(title.isBlank() ? "Software engineer focused on backend and distributed systems." : title + ".");
        if (!project.isBlank()) parts.add("Representative system: " + project + ".");
        if (!skills.isEmpty()) parts.add("Core technologies: " + String.join(", ", skills.stream().limit(10).toList()) + ".");
        return String.join(" ", parts);
    }
    private static String firstString(List<Map<String, Object>> items, String... keys) {
        for (Map<String, Object> item : items) for (String key : keys) if (item.get(key) != null && !String.valueOf(item.get(key)).isBlank()) return String.valueOf(item.get(key));
        return "";
    }
    private String versionOf(Object... values) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsString(values).getBytes(StandardCharsets.UTF_8))).substring(0, 16); }
        catch (Exception e) { throw new IllegalStateException("Unable to version candidate profile", e); }
    }
    private static String normalize(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim(); }
}
