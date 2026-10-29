package os.assurance.eu.api.evidence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EvidenceChunkJpaRepository extends JpaRepository<EvidenceChunkEntity, UUID> {
  /** Just what scoring needs; chunk text is loaded afterwards for the few chunks that are cited. */
  @Query("select c.id as id, c.documentId as documentId, c.embedding as embedding, "
      + "c.embeddingProvider as embeddingProvider from EvidenceChunkEntity c where c.documentId in :documentIds")
  List<ChunkVector> findVectorsByDocumentIdIn(@Param("documentIds") List<UUID> documentIds);

  /** Chunk text for the in-memory keyword index (H2 and local runs only; Postgres ranks in SQL). */
  @Query("select c.id as id, c.content as content from EvidenceChunkEntity c where c.documentId in :documentIds")
  List<ChunkText> findTextsByDocumentIdIn(@Param("documentIds") List<UUID> documentIds);

  interface ChunkText {
    UUID getId();

    String getContent();
  }

  interface ChunkVector {
    UUID getId();

    UUID getDocumentId();

    String getEmbedding();

    String getEmbeddingProvider();
  }
}
