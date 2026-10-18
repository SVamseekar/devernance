# Backup sidecar: nightly and weekly pg_dump -> gzip -> age -> rclone (Cloudflare R2, EU jurisdiction).
# Build context: repository root
#   docker build -f infra/backup/Dockerfile -t aos-backup .
# pg_dump must match the server's major version (pgvector/pgvector:*-pg16).
FROM alpine:3.22.6

RUN apk add --no-cache bash curl tzdata postgresql16-client age rclone \
  && pg_dump --version | grep -q " 16\."

COPY infra/backup/backup.sh /usr/local/bin/backup.sh
COPY infra/backup/restore.sh /usr/local/bin/restore.sh
COPY infra/backup/entrypoint.sh /usr/local/bin/entrypoint.sh
COPY infra/backup/crontab /etc/crontabs/root
RUN chmod 755 /usr/local/bin/backup.sh /usr/local/bin/restore.sh /usr/local/bin/entrypoint.sh \
  && chmod 600 /etc/crontabs/root

ENV TZ=UTC
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
