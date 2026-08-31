package site.yuqi.career.model;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
public record PrivateAnswersUpdate(@NotEmpty Map<String, Object> answers) {}
