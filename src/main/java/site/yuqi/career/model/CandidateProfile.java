package site.yuqi.career.model;
import java.time.Instant;
import java.util.List;
import java.util.Map;
public record CandidateProfile(String profileVersion, Instant refreshedAt, String summary, List<String> skills,
        List<Map<String, Object>> experience, List<Map<String, Object>> projects,
        Map<String, Object> applicationPreferences, Source source) {
    public record Source(String system, List<String> tools, String freshness) {}
}
