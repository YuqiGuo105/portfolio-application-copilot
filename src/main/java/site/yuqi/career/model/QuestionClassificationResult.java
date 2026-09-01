package site.yuqi.career.model;

import java.util.List;

public record QuestionClassificationResult(String provider, boolean cached, List<FieldClassification> fields) {
}
