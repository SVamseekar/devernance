-- Postgres only: H2 has no full-text search types and no pgvector, so local runs rank evidence in memory.
--
-- Keyword ranking: an English tsvector of each chunk, kept in step with the content by Postgres itself, and the
-- number of lexemes it holds (the chunk length BM25 needs, read without detoasting the tsvector).
alter table evidence_chunks
  add column content_tsv tsvector generated always as (to_tsvector('english', content)) stored;
alter table evidence_chunks
  add column content_lexemes integer generated always as (length(to_tsvector('english', content))) stored;
create index idx_evidence_chunks_content_tsv on evidence_chunks using gin (content_tsv);

-- Meaning ranking: chunk embeddings as pgvector values, so cosine distance runs in the database. V4 added a
-- 64-dimension column and an HNSW index that nothing filled; the column becomes dimension-free because each
-- embedding provider has its own dimension (queries always filter on embedding_provider), and the index goes:
-- one system's chunks are found through the document_id B-tree and scanned exactly, with full recall.
do $migrate$
begin
  if exists (select 1 from pg_available_extensions where name = 'vector') then
    create extension if not exists vector;
    drop index if exists idx_evidence_chunks_embedding_hnsw;
    alter table evidence_chunks add column if not exists embedding_vector vector;
    alter table evidence_chunks alter column embedding_vector type vector;
    update evidence_chunks set embedding_vector = ('[' || embedding || ']')::vector;

    create or replace function evidence_chunks_set_embedding_vector() returns trigger
    language plpgsql as $fn$
    begin
      new.embedding_vector := ('[' || new.embedding || ']')::vector;
      return new;
    end
    $fn$;
    drop trigger if exists trg_evidence_chunks_embedding_vector on evidence_chunks;
    create trigger trg_evidence_chunks_embedding_vector
      before insert or update of embedding on evidence_chunks
      for each row execute function evidence_chunks_set_embedding_vector();
  else
    raise notice 'pgvector is not available; evidence meaning ranking will run in the API';
  end if;
end $migrate$;
