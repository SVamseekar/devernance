package os.assurance.eu.api.billing;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Persistable;

/**
 * The key is assigned, so without {@link Persistable} Spring Data would merge a new counter: when two pipelines
 * race to create the month's row, the loser's merge finds the winner's row and writes its zero over runs already
 * counted. Persisting instead makes the loser fail on the primary key, which the caller expects.
 */
@Entity
@Table(name = "gate_run_counters")
@IdClass(GateRunCounterEntity.Key.class)
public class GateRunCounterEntity implements Persistable<GateRunCounterEntity.Key> {
  public static class Key implements Serializable {
    private UUID tenantId;
    private String period;

    public Key() {
    }

    public Key(UUID tenantId, String period) {
      this.tenantId = tenantId;
      this.period = period;
    }

    @Override
    public boolean equals(Object o) {
      return o instanceof Key k && Objects.equals(tenantId, k.tenantId) && Objects.equals(period, k.period);
    }

    @Override
    public int hashCode() {
      return Objects.hash(tenantId, period);
    }
  }

  @Id
  private UUID tenantId;

  @Id
  private String period;

  private int runs;

  @Transient
  private boolean isNew = true;

  protected GateRunCounterEntity() {
  }

  public GateRunCounterEntity(UUID tenantId, String period, int runs) {
    this.tenantId = tenantId;
    this.period = period;
    this.runs = runs;
  }

  public int runs() { return runs; }

  @Override
  public Key getId() {
    return new Key(tenantId, period);
  }

  @Override
  public boolean isNew() {
    return isNew;
  }

  @PostLoad
  @PostPersist
  void markPersisted() {
    isNew = false;
  }
}
