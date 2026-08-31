package site.yuqi.career.model;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
public record ResolveFieldsRequest(@NotBlank String applicationId, @NotEmpty List<@Valid Field> fields) {
    public record Field(@NotBlank String id, @NotBlank String label, String type, List<String> options) {}
}
