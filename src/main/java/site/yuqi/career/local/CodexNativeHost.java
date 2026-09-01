package site.yuqi.career.local;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class CodexNativeHost {
    private CodexNativeHost() {}

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        NativeMessageProtocol protocol = new NativeMessageProtocol(mapper);
        CodexFieldAdvisor advisor = new CodexFieldAdvisor(mapper);
        JsonNode request;
        while ((request = protocol.read(System.in)) != null) {
            ObjectNode response;
            try {
                response = switch (request.path("type").asText()) {
                    case "resolve_fields" -> advisor.advise(request);
                    case "classify_fields" -> advisor.classify(request);
                    default -> throw new IllegalArgumentException("Unsupported native operation");
                };
            } catch (Exception error) {
                System.err.println("Codex native advisor failed: " + error.getMessage());
                response = mapper.createObjectNode();
                response.put("ok", false);
                response.put("error", safeError(error));
            }
            response.put("requestId", request.path("requestId").asText());
            protocol.write(System.out, response);
        }
    }

    private static String safeError(Exception error) {
        if (error instanceof java.io.IOException && error.getMessage() != null) return error.getMessage();
        return "Local Codex advisor is unavailable";
    }
}
