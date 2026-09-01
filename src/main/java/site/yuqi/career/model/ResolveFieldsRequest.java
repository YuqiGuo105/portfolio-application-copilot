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
            @NotBlank @Size(max = 180) String id,
            @NotBlank @Size(max = 500) String label,
            @Size(max = 160) String semanticKey,
            @Size(max = 60) String type,
            @Size(max = 100) List<@Size(max = 300) String> options) {
        public Field(String id, String label, String type, List<String> options) {
            this(id, label, null, type, options);
        }
    }
}
