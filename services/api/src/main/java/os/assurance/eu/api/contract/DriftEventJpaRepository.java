package os.assurance.eu.api.contract;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DriftEventJpaRepository extends JpaRepository<DriftEventEntity, UUID> {
  List<DriftEventEntity> findAllByTenantIdAndContractIdOrderByCreatedAtAsc(UUID tenantId, UUID contractId);

  List<DriftEventEntity> findAllByTenantIdAndContractIdInAndStatusNotOrderByCreatedAtAsc(
      UUID tenantId,
      Collection<UUID> contractIds,
      DriftStatus status);

  Optional<DriftEventEntity> findByTenantIdAndContractIdAndId(UUID tenantId, UUID contractId, UUID id);
}
