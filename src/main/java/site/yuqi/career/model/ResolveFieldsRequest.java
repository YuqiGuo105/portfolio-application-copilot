package site.yuqi.career.model;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
public record ResolveFieldsRequest(
        @NotBlank @Size(max = 100) String applicationId,
        @NotEmpty @Size(max = 80) List<@Valid Field> fields) {
    public record Field(
            @NotBlank @Size(max = 512) String id,
            @NotBlank @Size(max = 4000) String label,
            @Size(max = 256) String semanticKey,
            @Size(max = 128) String type,
            @Size(max = 500) List<@Size(max = 2000) String> options) {
        public Field(String id, String label, String type, List<String> options) {
            this(id, label, null, type, options);
        }
    }
}
