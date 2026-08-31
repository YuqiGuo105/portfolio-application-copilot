package site.yuqi.career.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.yuqi.career.domain.ResumeAssetEntity;

import java.util.List;
import java.util.Optional;

public interface ResumeAssetRepository extends JpaRepository<ResumeAssetEntity, String> {
    List<ResumeAssetEntity> findByOwnerIdAndDeletedAtIsNullOrderByUpdatedAtDesc(String ownerId);
    Optional<ResumeAssetEntity> findFirstByOwnerIdAndActiveTrueAndDeletedAtIsNull(String ownerId);
    Optional<ResumeAssetEntity> findByIdAndOwnerIdAndDeletedAtIsNull(String id, String ownerId);
}
