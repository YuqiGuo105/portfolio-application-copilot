package site.yuqi.career.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import site.yuqi.career.model.ResolveFieldsRequest;
import site.yuqi.career.store.CareerStore;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeminiQuestionClassifierTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final CareerStore cache = mock(CareerStore.class);
    private final ResolveFieldsRequest request = new ResolveFieldsRequest("app-1", List.of(
            new ResolveFieldsRequest.Field("gender", "Gender", "", "combobox", List.of("Male", "Female")),
            new ResolveFieldsRequest.Field("race", "Please identify your race", "", "combobox", List.of("Asian", "White"))));

    @Test
    void usesValidatedValkeyClassificationWithoutCallingGemini() {
        when(cache.getQuestionClassification(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.of("""
                {"fields":[
                  {"fieldId":"gender","semanticKey":"gender","category":"EEO","status":"CLASSIFIED","confidence":0.99,"reason":"Visible label"},
                  {"fieldId":"race","semanticKey":"race","category":"EEO","status":"CLASSIFIED","confidence":0.99,"reason":"Visible label"}
                ]}
                """));
        var classifier = new GeminiQuestionClassifier(mapper, cache, "", "https://example.invalid", "test", Duration.ofSeconds(1));

        var result = classifier.classify(request);

        assertThat(result.cached()).isTrue();
        assertThat(result.provider()).isEqualTo("gemini-cache");
        assertThat(result.fields()).extracting("semanticKey").containsExactly("gender", "race");
    }

    @Test
    void failsClosedWhenGeminiIsNotConfiguredAndCacheIsCold() {
        when(cache.getQuestionClassification(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        var classifier = new GeminiQuestionClassifier(mapper, cache, "", "https://example.invalid", "test", Duration.ofSeconds(1));

        assertThatThrownBy(() -> classifier.classify(request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Gemini question fallback is not configured");
    }
}
