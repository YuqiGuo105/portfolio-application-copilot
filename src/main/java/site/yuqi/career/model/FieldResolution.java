package site.yuqi.career.model;
public record FieldResolution(String fieldId, String label, Object value, ResolutionStatus status,
        String source, double confidence, String reason) {
    public enum ResolutionStatus { RESOLVED, NEEDS_CONFIRMATION, UNSUPPORTED }
}
