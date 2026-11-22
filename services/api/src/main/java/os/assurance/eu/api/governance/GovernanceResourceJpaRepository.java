package os.assurance.eu.api.governance;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GovernanceResourceJpaRepository extends JpaRepository<GovernanceResourceEntity, UUID> {
  Optional<GovernanceResourceEntity> findByTenantIdAndKindAndNameAndActiveTrue(
      UUID tenantId, GovernanceResourceKind kind, String name);

  List<GovernanceResourceEntity> findAllByTenantIdAndSystemIdAndKindAndActiveTrue(
      UUID tenantId, UUID systemId, GovernanceResourceKind kind);

  List<GovernanceResourceEntity> findAllByTenantIdAndKindAndActiveTrueOrderByNameAsc(
      UUID tenantId, GovernanceResourceKind kind);

  List<GovernanceResourceEntity> findAllByTenantIdAndSystemIdOrderByCreatedAtDesc(UUID tenantId, UUID systemId);

  List<GovernanceResourceEntity> findAllByTenantIdAndIdIn(UUID tenantId, List<UUID> ids);

  @Query("select coalesce(max(r.version), 0) from GovernanceResourceEntity r "
      + "where r.tenantId = :tenantId and r.kind = :kind and r.name = :name")
  int latestVersion(
      @Param("tenantId") UUID tenantId, @Param("kind") GovernanceResourceKind kind, @Param("name") String name);
}
