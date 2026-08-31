package site.yuqi.career.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.yuqi.career.domain.ApplicationWorkflowEntity;

public interface ApplicationWorkflowRepository extends JpaRepository<ApplicationWorkflowEntity, String> {}
