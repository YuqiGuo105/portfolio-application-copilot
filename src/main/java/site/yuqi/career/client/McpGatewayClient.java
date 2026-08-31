package site.yuqi.career.client;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import site.yuqi.career.config.CareerProperties;
import java.util.List;
import java.util.Map;
@Component
public class McpGatewayClient {
    private final RestClient client;
    private final String token;
    public McpGatewayClient(RestClient.Builder builder, CareerProperties properties) {
        if (properties.mcpGateway().token() == null || properties.mcpGateway().token().isBlank()) {
            throw new IllegalStateException("MCP_GATEWAY_INTERNAL_TOKEN must be configured");
        }
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(properties.mcpGateway().timeout());
        requests.setReadTimeout(properties.mcpGateway().timeout());
        this.client = builder.baseUrl(properties.mcpGateway().baseUrl()).requestFactory(requests).build();
        this.token = properties.mcpGateway().token();
    }
    public List<Map<String, Object>> searchContent(String keyword, String sourceType, int limit) {
        Map<?, ?> response = client.post().uri("/api/tools/admin.search_content/invoke")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("X-Actor", "application-copilot:profile-refresh").header("X-Role", "VIEWER")
                .body(Map.of("keyword", keyword, "sourceType", sourceType, "limit", limit))
                .retrieve().body(Map.class);
        if (response == null) return List.of();
        for (String key : List.of("items", "content", "results", "data")) {
            Object value = response.get(key);
            if (value instanceof List<?> list) return castList(list);
            if (value instanceof Map<?, ?> nested && nested.get("items") instanceof List<?> list) return castList(list);
        }
        return List.of(castMap(response));
    }
    private static List<Map<String, Object>> castList(List<?> values) {
        return values.stream().filter(Map.class::isInstance).map(v -> castMap((Map<?, ?>) v)).toList();
    }
    private static Map<String, Object> castMap(Map<?, ?> map) {
        java.util.LinkedHashMap<String, Object> result = new java.util.LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value)); return result;
    }
}
