package site.yuqi.career.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
@ConfigurationProperties(prefix = "career")
public record CareerProperties(String internalToken, String ownerKey, Duration profileTtl,
        McpGateway mcpGateway, ResumeStorage resumeStorage) {
    public CareerProperties {
        profileTtl = profileTtl == null ? Duration.ofHours(6) : profileTtl;
        mcpGateway = mcpGateway == null ? new McpGateway("http://localhost:8091", "", Duration.ofSeconds(12)) : mcpGateway;
        resumeStorage = resumeStorage == null
                ? new ResumeStorage("", "", "career-resumes", 2_097_152L, Duration.ofMinutes(2))
                : resumeStorage;
    }
    public record McpGateway(String baseUrl, String token, Duration timeout) {
        public McpGateway { timeout = timeout == null ? Duration.ofSeconds(12) : timeout; }
    }
    public record ResumeStorage(String baseUrl, String serviceRoleKey, String bucket,
            long maxBytes, Duration downloadTtl) {
        public ResumeStorage {
            bucket = bucket == null || bucket.isBlank() ? "career-resumes" : bucket;
            maxBytes = maxBytes <= 0 ? 2_097_152L : maxBytes;
            downloadTtl = downloadTtl == null ? Duration.ofMinutes(2) : downloadTtl;
        }
    }
}
