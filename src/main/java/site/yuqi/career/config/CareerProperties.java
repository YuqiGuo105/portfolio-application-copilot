package site.yuqi.career.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
@ConfigurationProperties(prefix = "career")
public record CareerProperties(String internalToken, String ownerKey, Duration profileTtl, McpGateway mcpGateway) {
    public CareerProperties {
        profileTtl = profileTtl == null ? Duration.ofHours(6) : profileTtl;
        mcpGateway = mcpGateway == null ? new McpGateway("http://localhost:8091", "", Duration.ofSeconds(12)) : mcpGateway;
    }
    public record McpGateway(String baseUrl, String token, Duration timeout) {
        public McpGateway { timeout = timeout == null ? Duration.ofSeconds(12) : timeout; }
    }
}
