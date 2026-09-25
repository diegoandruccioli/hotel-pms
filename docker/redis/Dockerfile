# Patch Alpine OS packages on top of the upstream image (same pattern as
# docker/postgres/Dockerfile and the 8 Java service Dockerfiles): the
# redis:8.8.2-alpine tag still ships libcrypto3/libssl3 3.5.7-r0
# (CVE-2026-14456, HIGH) — the tag bump alone doesn't refresh Alpine
# packages baked in at the base image's own build time.
FROM redis:8.8.2-alpine

RUN apk update && apk upgrade --no-cache
