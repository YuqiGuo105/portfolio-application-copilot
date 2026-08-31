package site.yuqi.career.service;

import site.yuqi.career.model.CandidateProfile;

@FunctionalInterface
public interface CandidateProfileProvider {
    CandidateProfile get();
}
