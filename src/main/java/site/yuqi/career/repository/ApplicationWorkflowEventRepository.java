package site.yuqi.career.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.yuqi.career.domain.ApplicationWorkflowEventEntity;

import java.util.List;

public interface ApplicationWorkflowEventRepository extends JpaRepository<ApplicationWorkflowEventEntity, String> {
    List<ApplicationWorkflowEventEntity> findByApplicationIdOrderByOccurredAtAsc(String applicationId);
}
