package os.assurance.eu.api.evidence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import os.assurance.eu.api.tenant.TenantContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class EvidenceRepository {
  private final EvidenceDocumentJpaRepository documents;
  private final EvidenceChunkJpaRepository chunks;
  private final TenantContext tenantContext;

  public EvidenceRepository(
      EvidenceDocumentJpaRepository documents,
      EvidenceChunkJpaRepository chunks,
      TenantContext tenantContext) {
    this.documents = documents;
    this.chunks = chunks;
    this.tenantContext = tenantContext;
  }

  @Transactional
  public EvidenceDocument saveDocument(EvidenceDocument document) {
    return documents.save(new EvidenceDocumentEntity(tenantContext.tenantId(), document)).toDomain();
  }

  @Transactional
  public void saveChunks(List<EvidenceChunk> evidenceChunks) {
    chunks.saveAll(evidenceChunks.stream().map(EvidenceChunkEntity::new).toList());
  }

  @Transactional(readOnly = true)
  public List<EvidenceDocument> findDocumentsBySystemId(UUID systemId) {
    return documents.findAllByTenantIdAndSystemIdOrderByCreatedAtDesc(tenantContext.tenantId(), systemId).stream()
        .map(EvidenceDocumentEntity::toDomain)
        .toList();
  }

  @Transactional(readOnly = true)
  public Optional<EvidenceDocument> findDocumentByIdForSystem(UUID systemId, UUID documentId) {
    return findDocumentsBySystemId(systemId).stream()
        .filter(document -> document.id().equals(documentId))
        .findFirst();
  }

  /** Embeddings of every chunk of these documents, without the chunk text. Callers pass only this tenant's documents. */
  @Transactional(readOnly = true)
  public List<EvidenceChunkJpaRepository.ChunkVector> findChunkVectors(List<UUID> documentIds) {
    if (documentIds.isEmpty()) {
      return List.of();
    }
    return chunks.findVectorsByDocumentIdIn(documentIds);
  }

  @Transactional(readOnly = true)
  public List<EvidenceChunk> findChunksByIds(List<UUID> chunkIds) {
    if (chunkIds.isEmpty()) {
      return List.of();
    }
    return chunks.findAllById(chunkIds).stream().map(EvidenceChunkEntity::toDomain).toList();
  }
}
