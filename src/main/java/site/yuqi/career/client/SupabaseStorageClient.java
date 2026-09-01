package site.yuqi.career.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import site.yuqi.career.config.CareerProperties;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

@Component
public class SupabaseStorageClient {
    private final CareerProperties.ResumeStorage properties;
    private final RestClient client;

    public SupabaseStorageClient(CareerProperties properties) {
        this.properties = properties.resumeStorage();
        this.client = RestClient.builder().build();
    }

    public SignedUrl createSignedUpload(String objectKey) {
        JsonNode response = client.post()
                .uri(storageUri("object", "upload", "sign", properties.bucket(), objectKey))
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("upsert", false))
                .retrieve().body(JsonNode.class);
        String url = requiredUrl(response, "url", "signedURL", "signedUrl");
        return new SignedUrl(absolute(url), Instant.now().plusSeconds(2 * 60 * 60));
    }

    public SignedUrl createSignedDownload(String objectKey, String fileName) {
        long seconds = Math.max(30, properties.downloadTtl().toSeconds());
        JsonNode response = client.post()
                .uri(storageUri("object", "sign", properties.bucket(), objectKey))
                .headers(this::authorize)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("expiresIn", seconds, "download", fileName))
                .retrieve().body(JsonNode.class);
        String url = requiredUrl(response, "signedURL", "signedUrl", "url");
        return new SignedUrl(absolute(url), Instant.now().plusSeconds(seconds));
    }

    public byte[] download(String objectKey) {
        return client.get()
                .uri(storageUri("object", properties.bucket(), objectKey))
                .headers(this::authorize)
                .retrieve().body(byte[].class);
    }

    private void authorize(org.springframework.http.HttpHeaders headers) {
        ensureConfigured();
        headers.setBearerAuth(properties.serviceRoleKey());
        headers.set("apikey", properties.serviceRoleKey());
    }

    private URI storageUri(String... segments) {
        ensureConfigured();
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.baseUrl())
                .pathSegment("storage", "v1");
        for (String segment : segments) {
            if (segment.contains("/")) {
                for (String part : segment.split("/")) builder.pathSegment(part);
            } else {
                builder.pathSegment(segment);
            }
        }
        return builder.build().encode().toUri();
    }

    private URI absolute(String value) {
        return resolveSignedUrl(properties.baseUrl(), value);
    }

    static URI resolveSignedUrl(String baseUrl, String value) {
        if (value.startsWith("http://") || value.startsWith("https://")) return URI.create(value);
        String origin = baseUrl.replaceAll("/+$", "");
        String path = value.startsWith("/") ? value : "/" + value;
        if (!path.startsWith("/storage/v1/")) path = "/storage/v1" + path;
        return URI.create(origin + path);
    }

    private void ensureConfigured() {
        if (properties.baseUrl() == null || properties.baseUrl().isBlank()
                || properties.serviceRoleKey() == null || properties.serviceRoleKey().isBlank()) {
            throw new IllegalStateException("Private resume storage is not configured");
        }
    }

    private static String requiredUrl(JsonNode response, String... names) {
        if (response != null) {
            for (String name : names) {
                String value = response.path(name).asText("");
                if (!value.isBlank()) return value;
            }
        }
        throw new IllegalStateException("Supabase Storage did not return a signed URL");
    }

    public record SignedUrl(URI url, Instant expiresAt) {}
}
