package site.yuqi.career.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class CodexFieldAdvisor {
    private static final int MAX_FIELDS = 80;
    private static final int MAX_TEXT_LENGTH = 2_000;
    private static final String ADVISOR_SYSTEM_INSTRUCTION = """
            You are a local, read-only job application field advisor. Return JSON matching the supplied schema.
            Resolve only from the provided sanitized candidate summary and visible form metadata.
            Never infer or invent immigration, work authorization, sponsorship, I-140, compensation, EEO,
            disability, veteran, government, security-clearance, signature, legal-attestation, password, or
            account-security answers. Mark uncertain or sensitive fields UNRESOLVED. Do not browse, execute tools,
            inspect files, or recommend submitting the form. Keep reasons concise.
            """;
    private static final String CLASSIFIER_SYSTEM_INSTRUCTION = """
            You are a local, read-only job application question normalizer. Return JSON matching the supplied schema.
            Classify every supplied field from its visible label, control type, and options. Produce a concise,
            stable snake_case semanticKey describing the question's meaning. For common questions, use these exact
            canonical keys when applicable: first_name, last_name, email, phone, current_company, location,
            linkedin_url, website_url, gender, race, hispanic_latino, veteran_status, disability_status,
            sms_consent, work_location_preference, sponsorship_required, company_familiarity,
            application_source, previously_employed_by_company. Use a natural snake_case normalization only for unfamiliar questions. Do not answer any
            question and do not infer candidate data.
            Mark a field UNRESOLVED only when its meaning is genuinely ambiguous. Use category EEO for demographic,
            veteran, or disability questions; IMMIGRATION for authorization, sponsorship, visa, or I-140 questions;
            LEGAL for attestations, signatures, background, government, or security-clearance questions;
            CONTACT, EXPERIENCE, EDUCATION, COMPENSATION, CONSENT, or OTHER otherwise. Do not browse or execute tools.
            """;

    private final ObjectMapper mapper;
    private final Duration timeout;
    private final String codexPath;
    private final String reasoningEffort;
    private final String model;

    CodexFieldAdvisor(ObjectMapper mapper) {
        this(mapper, Duration.ofSeconds(readPositiveInt("YUQI_CODEX_TIMEOUT_SECONDS", 35)),
                System.getenv().getOrDefault("YUQI_CODEX_PATH", "/Applications/ChatGPT.app/Contents/Resources/codex"),
                readReasoningEffort(), System.getenv().getOrDefault("YUQI_CODEX_MODEL", "gpt-5.3-codex-spark"));
    }

    CodexFieldAdvisor(ObjectMapper mapper, Duration timeout, String codexPath, String reasoningEffort) {
        this(mapper, timeout, codexPath, reasoningEffort, "gpt-5.3-codex-spark");
    }

    CodexFieldAdvisor(ObjectMapper mapper, Duration timeout, String codexPath, String reasoningEffort, String model) {
        this.mapper = mapper;
        this.timeout = timeout;
        this.codexPath = codexPath;
        this.reasoningEffort = reasoningEffort;
        this.model = model;
    }

    ObjectNode advise(JsonNode request) throws IOException, InterruptedException {
        ObjectNode sanitized = sanitize(request);
        JsonNode result = runCodex(sanitized, ADVISOR_SYSTEM_INSTRUCTION, outputSchema());
        validateResult(result, sanitized.path("fields"));
        return success(result);
    }

    ObjectNode classify(JsonNode request) throws IOException, InterruptedException {
        ObjectNode sanitized = sanitizeClassification(request);
        JsonNode result = runCodex(sanitized, CLASSIFIER_SYSTEM_INSTRUCTION, classificationSchema());
        validateClassification(result, sanitized.path("fields"));
        return success(result);
    }

    private JsonNode runCodex(ObjectNode sanitized, String instruction, String schemaJson)
            throws IOException, InterruptedException {
        Path schema = Files.createTempFile("yuqi-codex-fields-", ".schema.json");
        Path output = Files.createTempFile("yuqi-codex-fields-", ".output.json");
        try {
            Files.writeString(schema, schemaJson, StandardCharsets.UTF_8);
            ProcessBuilder builder = new ProcessBuilder(codexPath, "exec", "--ephemeral", "--ignore-user-config",
                    "--ignore-rules", "--sandbox", "read-only", "--model", model,
                    "-c", "model_reasoning_effort=\"" + reasoningEffort + "\"",
                    "--skip-git-repo-check", "--output-schema", schema.toString(), "--output-last-message",
                    output.toString(), "-");
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process process = builder.start();
            try (var stdin = process.getOutputStream()) {
                stdin.write((instruction + "\nINPUT:\n" + mapper.writeValueAsString(sanitized))
                        .getBytes(StandardCharsets.UTF_8));
            }
            boolean completed = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                throw new IOException("Local Codex request timed out");
            }
            if (process.exitValue() != 0) throw new IOException("Local Codex exited with code " + process.exitValue());
            return mapper.readTree(Files.readString(output));
        } finally {
            Files.deleteIfExists(schema);
            Files.deleteIfExists(output);
        }
    }

    private ObjectNode success(JsonNode result) {
        ObjectNode response = mapper.createObjectNode();
        response.put("ok", true);
        response.put("provider", "local-codex");
        response.set("result", result);
        return response;
    }

    ObjectNode sanitize(JsonNode request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("requestId", truncate(request.path("requestId").asText(), 100));
        ObjectNode page = root.putObject("page");
        page.put("origin", truncate(request.path("page").path("origin").asText(), 300));
        page.put("title", truncate(request.path("page").path("title").asText(), 300));
        page.put("pageType", truncate(request.path("page").path("pageType").asText(), 40));

        JsonNode profileInput = request.path("profile");
        ObjectNode profile = root.putObject("profile");
        profile.put("summary", truncate(profileInput.path("summary").asText(), MAX_TEXT_LENGTH));
        copyStringArray(profileInput.path("skills"), profile.putArray("skills"), 40, 120);
        copyNamedItems(profileInput.path("experience"), profile.putArray("experience"));
        copyNamedItems(profileInput.path("projects"), profile.putArray("projects"));

        ArrayNode fields = root.putArray("fields");
        int count = 0;
        for (JsonNode field : request.path("fields")) {
            if (count++ >= MAX_FIELDS) break;
            ObjectNode target = fields.addObject();
            target.put("id", truncate(field.path("id").asText(), 180));
            target.put("label", truncate(field.path("label").asText(), 500));
            target.put("type", truncate(field.path("type").asText(), 60));
            target.put("required", field.path("required").asBoolean(false));
            copyStringArray(field.path("options"), target.putArray("options"), 100, 300);
        }
        return root;
    }

    ObjectNode sanitizeClassification(JsonNode request) {
        ObjectNode root = mapper.createObjectNode();
        root.put("requestId", truncate(request.path("requestId").asText(), 100));
        ObjectNode page = root.putObject("page");
        page.put("origin", truncate(request.path("page").path("origin").asText(), 300));
        page.put("title", truncate(request.path("page").path("title").asText(), 300));
        page.put("pageType", truncate(request.path("page").path("pageType").asText(), 40));
        copyFields(request.path("fields"), root.putArray("fields"));
        return root;
    }

    private void copyFields(JsonNode source, ArrayNode fields) {
        int count = 0;
        for (JsonNode field : source) {
            if (count++ >= MAX_FIELDS) break;
            ObjectNode target = fields.addObject();
            target.put("id", truncate(field.path("id").asText(), 180));
            target.put("label", truncate(field.path("label").asText(), 500));
            target.put("type", truncate(field.path("type").asText(), 60));
            target.put("required", field.path("required").asBoolean(false));
            copyStringArray(field.path("options"), target.putArray("options"), 100, 300);
        }
    }

    private void validateResult(JsonNode result, JsonNode requestedFields) throws IOException {
        if (!result.isObject() || !result.path("fields").isArray()) throw new IOException("Codex returned an invalid payload");
        List<String> allowedIds = new ArrayList<>();
        requestedFields.forEach(field -> allowedIds.add(field.path("id").asText()));
        for (JsonNode field : result.path("fields")) {
            if (!allowedIds.contains(field.path("fieldId").asText())) throw new IOException("Codex returned an unknown field id");
            if (!List.of("SUGGESTION", "UNRESOLVED").contains(field.path("status").asText())) {
                throw new IOException("Codex returned an unsupported field status");
            }
        }
    }

    private void validateClassification(JsonNode result, JsonNode requestedFields) throws IOException {
        if (!result.isObject() || !result.path("fields").isArray()) {
            throw new IOException("Codex returned an invalid classification payload");
        }
        List<String> allowedIds = new ArrayList<>();
        requestedFields.forEach(field -> allowedIds.add(field.path("id").asText()));
        for (JsonNode field : result.path("fields")) {
            if (!allowedIds.contains(field.path("fieldId").asText())) {
                throw new IOException("Codex returned an unknown classification field id");
            }
            if (!List.of("CLASSIFIED", "UNRESOLVED").contains(field.path("status").asText())) {
                throw new IOException("Codex returned an unsupported classification status");
            }
        }
    }

    private void copyNamedItems(JsonNode source, ArrayNode target) {
        int count = 0;
        for (JsonNode item : source) {
            if (count++ >= 12) break;
            ObjectNode value = target.addObject();
            for (String key : List.of("title", "name", "position", "company", "organization")) {
                if (item.hasNonNull(key)) value.put(key, truncate(item.path(key).asText(), 200));
            }
        }
    }

    private static void copyStringArray(JsonNode source, ArrayNode target, int maxItems, int maxLength) {
        int count = 0;
        for (JsonNode item : source) {
            if (count++ >= maxItems) break;
            target.add(truncate(item.asText(), maxLength));
        }
    }

    private static String truncate(String value, int max) {
        String sanitized = value == null ? "" : value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
        return sanitized.length() <= max ? sanitized : sanitized.substring(0, max);
    }

    private static int readPositiveInt(String name, int fallback) {
        try { return Math.max(1, Integer.parseInt(System.getenv().getOrDefault(name, String.valueOf(fallback)))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static String readReasoningEffort() {
        String value = System.getenv().getOrDefault("YUQI_CODEX_REASONING_EFFORT", "low").toLowerCase();
        return List.of("minimal", "low", "medium", "high").contains(value) ? value : "low";
    }

    private static String outputSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["fields"],"properties":{"fields":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["fieldId","value","status","confidence","reason"],"properties":{"fieldId":{"type":"string"},"value":{"type":["string","boolean","null"]},"status":{"type":"string","enum":["SUGGESTION","UNRESOLVED"]},"confidence":{"type":"number","minimum":0,"maximum":1},"reason":{"type":"string"}}}}}}
                """;
    }

    private static String classificationSchema() {
        return """
                {"type":"object","additionalProperties":false,"required":["fields"],"properties":{"fields":{"type":"array","items":{"type":"object","additionalProperties":false,"required":["fieldId","semanticKey","category","status","confidence","reason"],"properties":{"fieldId":{"type":"string"},"semanticKey":{"type":"string"},"category":{"type":"string","enum":["CONTACT","EXPERIENCE","EDUCATION","IMMIGRATION","EEO","COMPENSATION","CONSENT","LEGAL","OTHER"]},"status":{"type":"string","enum":["CLASSIFIED","UNRESOLVED"]},"confidence":{"type":"number","minimum":0,"maximum":1},"reason":{"type":"string"}}}}}}
                """;
    }
}
