package site.yuqi.career.service.resolution;

import site.yuqi.career.model.CandidateProfile;
import site.yuqi.career.model.ResolveFieldsRequest;

public record FieldResolutionContext(
        ResolveFieldsRequest.Field field,
        CandidateProfile profile,
        String normalizedLabel,
        boolean sensitive) {}
