package site.yuqi.career.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.yuqi.career.domain.PrivateResumeEntity;
import java.util.List;

public interface PrivateResumeRepository extends JpaRepository<PrivateResumeEntity, String> {
    List<PrivateResumeEntity> findByDeletedAtIsNullOrderByUpdatedAtDesc();
    List<PrivateResumeEntity> findByActiveTrueAndDeletedAtIsNull();
}
