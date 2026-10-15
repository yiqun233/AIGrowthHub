ARG BASE_REGISTRY=docker.io/library
FROM ${BASE_REGISTRY}/node:24.18.1-alpine AS build
WORKDIR /src
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build
FROM ${BASE_REGISTRY}/caddy:2.11.7-alpine
COPY --from=build /src/dist /srv
COPY infra/Caddyfile /etc/caddy/Caddyfile
COPY infra/Caddyfile.local /etc/caddy/Caddyfile.local
COPY infra/common.caddy /etc/caddy/common.caddy
EXPOSE 80 443
