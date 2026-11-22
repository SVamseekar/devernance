package os.assurance.eu.api.contract;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import os.assurance.eu.api.tenant.TenantContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DriftEventRepository {
  private final DriftEventJpaRepository repository;
  private final TenantContext tenantContext;

  public DriftEventRepository(DriftEventJpaRepository repository, TenantContext tenantContext) {
    this.repository = repository;
    this.tenantContext = tenantContext;
  }

  @Transactional(readOnly = true)
  public List<DriftEvent> findAllByContractId(UUID contractId) {
    return repository.findAllByTenantIdAndContractIdOrderByCreatedAtAsc(tenantContext.tenantId(), contractId).stream()
        .map(DriftEventEntity::toDomain)
        .toList();
  }

  @Transactional(readOnly = true)
  /** Drift that is not resolved: acknowledged drift still counts. */
  public List<DriftEvent> findOpenByContractIds(Collection<UUID> contractIds) {
    if (contractIds.isEmpty()) {
      return List.of();
    }
    return repository.findAllByTenantIdAndContractIdInAndStatusNotOrderByCreatedAtAsc(
            tenantContext.tenantId(), contractIds, DriftStatus.RESOLVED)
        .stream()
        .map(DriftEventEntity::toDomain)
        .toList();
  }

  @Transactional(readOnly = true)
  public Optional<DriftEvent> findByContractIdAndId(UUID contractId, UUID eventId) {
    return repository.findByTenantIdAndContractIdAndId(tenantContext.tenantId(), contractId, eventId)
        .map(DriftEventEntity::toDomain);
  }

  @Transactional
  public DriftEvent save(DriftEvent event) {
    return repository.save(new DriftEventEntity(tenantContext.tenantId(), event)).toDomain();
  }
}
