ARG BASE_REGISTRY=docker.io/library
FROM ${BASE_REGISTRY}/postgres:18.6
COPY --chmod=755 infra/init-db.sh /docker-entrypoint-initdb.d/10-growthhub.sh
COPY --chmod=755 infra/backup-loop.sh /ops/backup-loop.sh
