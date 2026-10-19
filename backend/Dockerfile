# syntax=docker/dockerfile:1
# Team Ops Board backend. Build from the repository root:
#   docker build -f backend/Dockerfile --target runtime -t team-ops-board-backend .
# Targets: runtime (the app: JRE 21, non-root) and tools (JDK 21 + MySQL client + scripts/, for restores; run on demand).

# --- Build: JDK 21 and the Maven wrapper. Tests run before a release (docs/deployment.md), not in the image build. ---
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /src
COPY backend/.mvn/ .mvn/
COPY backend/mvnw backend/pom.xml ./
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY backend/src/ src/
RUN ./mvnw -B -q -DskipTests package \
 && cp "$(ls target/team-ops-board-backend-*.jar | grep -v '\.original$')" /app.jar

# --- Runtime: JRE 21, MySQL 8.4 client for the nightly mysqldump, a non-root user. ---
FROM eclipse-temurin:21-jre-noble AS runtime
COPY backend/docker/install-mysql-client.sh /tmp/install-mysql-client.sh
RUN sed -i 's/\r$//' /tmp/install-mysql-client.sh && bash /tmp/install-mysql-client.sh && rm /tmp/install-mysql-client.sh \
 && groupadd --system --gid 10001 teamops \
 && useradd --system --uid 10001 --gid teamops --home-dir /app --shell /usr/sbin/nologin teamops \
 && mkdir -p /app /var/lib/team-ops/uploads /var/backups/team-ops \
 && chown -R teamops:teamops /app /var/lib/team-ops /var/backups/team-ops
COPY --from=build --chown=teamops:teamops /app.jar /app/app.jar
ENV SPRING_PROFILES_ACTIVE=prod \
    FILE_STORAGE_DIR=/var/lib/team-ops/uploads \
    BACKUP_LOCAL_PATH=/var/backups/team-ops \
    BACKUP_WORK_DIR=/var/backups/team-ops/.work \
    MYSQLDUMP_PATH=/usr/bin/mysqldump
WORKDIR /app
USER teamops
EXPOSE 8080
# Heap at 60% of the container's memory limit, leaving room for metaspace, threads and the mysqldump child process.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]

# --- Tools: restore-backup.sh needs a JDK (it runs scripts/BackupTool.java from source) and the mysql client. ---
FROM eclipse-temurin:21-jdk-noble AS tools
COPY backend/docker/install-mysql-client.sh /tmp/install-mysql-client.sh
RUN sed -i 's/\r$//' /tmp/install-mysql-client.sh && bash /tmp/install-mysql-client.sh && rm /tmp/install-mysql-client.sh \
 && groupadd --system --gid 10001 teamops \
 && useradd --system --uid 10001 --gid teamops --home-dir /opt/team-ops --shell /bin/bash teamops \
 && mkdir -p /opt/team-ops /var/lib/team-ops/uploads /var/backups/team-ops \
 && chown -R teamops:teamops /opt/team-ops /var/lib/team-ops /var/backups/team-ops
COPY --chown=teamops:teamops scripts/ /opt/team-ops/scripts/
# restore-backup.sh reads the app's DB_URL from ../backend/.env: the live database's name (so restoring over it asks
# you to type the name) and where BACKUP_RESTORED is audited. No secrets: passwords and the key come from the
# environment (MYSQL_PASSWORD, BACKUP_ENCRYPTION_KEY).
RUN sed -i 's/\r$//' /opt/team-ops/scripts/*.sh && chmod +x /opt/team-ops/scripts/*.sh \
 && mkdir -p /opt/team-ops/backend \
 && echo 'DB_URL=jdbc:mysql://mysql:3306/team_ops_board' > /opt/team-ops/backend/.env
WORKDIR /opt/team-ops
USER teamops
CMD ["bash"]
