# syntax=docker/dockerfile:1
# Team Ops Board frontend: the built SPA on Nginx (non-root, port 8080). Build from the repository root:
#   docker build -f frontend/Dockerfile -t team-ops-board-nginx .

# --- Build: Node 24, the same lockfile as development. VITE_API_BASE_URL stays "/api" (same origin). ---
FROM node:24-alpine AS build
WORKDIR /src
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build
# The CSP allows the inline theme script of this exact build by its hash.
COPY deploy/nginx/ /deploy/nginx/
RUN node scripts/nginx-conf.ts dist/index.html /deploy/nginx/team-ops.conf.template /deploy/nginx/default.conf

# --- Runtime: unprivileged Nginx (uid 101, listens on 8080, temp files in /tmp). ---
FROM nginxinc/nginx-unprivileged:1.28-alpine AS runtime
COPY --from=build /deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY --from=build /src/dist/ /usr/share/nginx/html/
EXPOSE 8080
