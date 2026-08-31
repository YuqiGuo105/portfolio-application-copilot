package site.yuqi.career.repository;
import org.springframework.data.jpa.repository.JpaRepository;
import site.yuqi.career.domain.VaultAuditEntity;
public interface VaultAuditRepository extends JpaRepository<VaultAuditEntity, String> {}
