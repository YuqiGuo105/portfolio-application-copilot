package site.yuqi.career.model;

public record FieldClassification(
        String fieldId,
        String semanticKey,
        String category,
        String status,
        double confidence,
        String reason) {
}
