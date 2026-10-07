# Tuần 16 — Docker

> Stack thực hành: Java 21, Spring Boot, Maven Wrapper, PostgreSQL, Redis, Prometheus và Grafana. Mục tiêu là đóng gói Task API thành image nhỏ, chạy được bằng Docker Compose và có health check an toàn.

## 1. Mục tiêu bài học

Sau tuần này, bạn có thể:

- Phân biệt image, container, registry, volume và network.
- Viết Dockerfile multi-stage cho ứng dụng Spring Boot Maven.
- Tối ưu Docker layer cache để build nhanh hơn.
- Chạy container bằng user không phải root.
- Truyền cấu hình qua environment variable thay vì hardcode.
- Kết nối Spring Boot với PostgreSQL và Redis qua service name.
- Dùng Docker Compose cho app, database, cache và observability stack.
- Viết health check có timeout và dependency rõ ràng.
- Chọn named volume, bind mount và tmpfs đúng mục đích.
- Giảm attack surface bằng read-only filesystem, cap drop và no-new-privileges.
- Debug log, network, resource và image layer.
- Biết giới hạn của Compose khi triển khai production nhiều instance.

Docker không làm ứng dụng tự động “production-ready”. Nó tạo môi trường đóng gói có thể lặp lại; tính đúng đắn vẫn đến từ cấu hình, health probe, migration, security và observability của ứng dụng.

---

## 2. Image và container

### 2.1. Image

Image là template bất biến gồm filesystem layers và metadata để khởi chạy process. Một image thường có:

- Base image chứa Linux user space và JRE/JDK.
- Application dependency.
- JAR của Spring Boot.
- Entrypoint/command.
- Environment mặc định và metadata.

Image có thể push lên registry. Không đặt password, private key, `.env` hoặc source code không cần thiết vào layer vì layer cũ vẫn có thể tồn tại trong registry/cache.

### 2.2. Container

Container là một process chạy từ image với namespace, filesystem layer ghi tạm và network riêng. Container nên được xem là disposable:

~~~text
image + config + volume/network → container
container bị xóa → image vẫn còn, dữ liệu chỉ còn nếu nằm ở volume/external store
~~~

Một container nên chạy một process chính. App, PostgreSQL, Redis, Prometheus và Grafana là các service riêng; không gom thành một image khổng lồ.

### 2.3. Tag và digest

Tag dễ đọc nhưng có thể bị trỏ sang image khác. Build reproducible cần pin tag cụ thể; production có thể pin digest:

~~~dockerfile
FROM eclipse-temurin:21-jre-jammy@sha256:<digest> AS runtime
~~~

Digest trong bài chỉ là placeholder. Khi dùng thật, lấy digest đã được kiểm tra từ registry tin cậy và cập nhật có chủ đích.

Không dùng `:latest` cho image production. Ghi rõ version trong Compose và cập nhật dependency qua quy trình review.

---

## 3. Dockerfile multi-stage cho Spring Boot

### 3.1. Vì sao cần multi-stage?

Build cần JDK, Maven và source; chạy chỉ cần JRE/JVM và JAR. Multi-stage tách hai môi trường:

~~~text
builder: source + Maven + JDK → app.jar
runtime: JRE + app.jar        → image nhỏ hơn
~~~

Các file build, test report và Maven cache không đi vào runtime image.

### 3.2. Dockerfile mẫu

Đặt tại root project:

~~~dockerfile
# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

# Copy wrapper và descriptor trước để tận dụng layer cache dependency.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package \
    && cp target/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Tạo user hệ thống, không chạy JVM bằng root.
RUN groupadd --system app \
    && useradd --system --gid app --home-dir /app app \
    && apt-get update \
    && apt-get install --no-install-recommends --yes curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build --chown=app:app /workspace/app.jar /app/app.jar

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:InitialRAMPercentage=25"
USER app
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
~~~

Điểm cần hiểu:

- `COPY pom.xml` trước `COPY src/` giúp Docker reuse dependency layer khi chỉ đổi code.
- `--chown` tránh phải chạy `chown` thêm một layer.
- `exec` giúp JVM nhận SIGTERM trực tiếp để graceful shutdown hoạt động.
- `MaxRAMPercentage` giúp JVM không mặc định chiếm bộ nhớ ngoài giới hạn container.
- Health check dùng liveness đơn giản; readiness vẫn nên được kiểm tra ở orchestrator/Compose tùy môi trường.

`curl` làm runtime image lớn hơn một chút nhưng giúp healthcheck độc lập. Một lựa chọn khác là dùng image có công cụ probe tối thiểu hoặc kiểm tra từ bên ngoài container.

### 3.3. Maven Wrapper và reproducibility

Commit các file sau vào repository:

- `mvnw`, `mvnw.cmd`.
- `.mvn/wrapper/maven-wrapper.properties`.
- `pom.xml` và lock/version policy của dependency.

Docker build không nên phụ thuộc Maven được cài trên host. Dùng `./mvnw` để CI và local chọn cùng Maven version.

Không bỏ qua test chỉ vì Docker build. `-DskipTests` chỉ phù hợp stage đóng gói khi test đã chạy ở CI; pipeline cần có bước test riêng trước khi push image.

### 3.4. Layer cache và BuildKit

BuildKit có cache mount cho Maven:

~~~dockerfile
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -DskipTests package
~~~

Cache mount chỉ tối ưu build, không chứa trong runtime image. Không đưa credential registry hoặc secret Maven vào Dockerfile; dùng secret mount/CI secret nếu private repository cần xác thực.

### 3.5. `.dockerignore`

~~~text
.git
.github
.idea
.vscode
target
*.log
.env
.env.*
docker-compose.override.yml
coverage
README.private.md
~~~

`target` không cần gửi vào build context vì JAR được build bên trong image. `.env` không được vào context để tránh copy nhầm vào layer.

---

## 4. Build và chạy image

Build:

~~~bash
docker build --tag task-api:local .
~~~

Chạy tối thiểu:

~~~bash
docker run --rm \
  --name task-api \
  --publish 8080:8080 \
  --env SPRING_PROFILES_ACTIVE=docker \
  task-api:local
~~~

Các lệnh kiểm tra:

~~~bash
docker image ls task-api
docker image inspect task-api:local
docker container ls
docker logs --follow task-api
docker exec -it task-api sh
docker stats task-api
~~~

Khi dùng `docker run --rm`, container sẽ bị xóa sau khi dừng; image và named volume không tự bị xóa. Đừng dùng `docker system prune` theo thói quen vì có thể xóa cache/image còn cần cho project khác.

---

## 5. Environment variable và Spring profile

### 5.1. Cấu hình của ứng dụng

`application.yml` nên chứa default an toàn cho local, còn `application-docker.yml` chứa tên service trong Compose:

~~~yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://db:5432/taskdb}
    username: ${SPRING_DATASOURCE_USERNAME:task}
    password: ${SPRING_DATASOURCE_PASSWORD:}
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:redis}
      port: ${SPRING_DATA_REDIS_PORT:6379}

management:
  server:
    port: ${MANAGEMENT_SERVER_PORT:8081}
~~~

Trong YAML, `${NAME:default}` là placeholder của Spring, còn `db` và `redis` là service name được Docker DNS phân giải. Không dùng `localhost` để kết nối PostgreSQL/Redis từ app container; `localhost` là chính container app.

### 5.2. Quy tắc config

- Không hardcode secret trong Dockerfile, Compose committed hoặc image label.
- Dùng `.env.example` chỉ chứa tên biến và giá trị giả.
- `.env` thật phải nằm trong `.gitignore` và secret manager/CI vault.
- Fail fast khi production thiếu secret bắt buộc.
- Không đưa toàn bộ environment vào `/actuator/env` public.
- Dùng tên biến nhất quán giữa local, CI và deployment.

Ví dụ `.env.example`:

~~~dotenv
SPRING_PROFILES_ACTIVE=docker
POSTGRES_DB=taskdb
POSTGRES_USER=task
POSTGRES_PASSWORD=change-me-locally
SPRING_DATASOURCE_USERNAME=task
SPRING_DATASOURCE_PASSWORD=change-me-locally
~~~

Password ví dụ chỉ dành cho local disposable. Không copy file này thành secret production.

---

## 6. Docker network

Các service trong cùng Compose network gọi nhau bằng service name:

~~~text
app → db:5432
app → redis:6379
prometheus → app:8081/actuator/prometheus
grafana → prometheus:9090
~~~

Không hardcode IP container vì IP có thể đổi sau recreate. Docker Compose cung cấp DNS theo service name.

Tách network khi cần giới hạn kết nối:

~~~yaml
services:
  app:
    networks: [frontend, backend]
  prometheus:
    networks: [observability, backend]
  db:
    networks: [backend]

networks:
  frontend:
  backend:
    internal: true
  observability:
~~~

Trong local, cấu hình đơn giản một network mặc định là đủ. Ở production, không publish port database/Redis ra host nếu app là consumer duy nhất.

`ports` ánh xạ host:container và mở đường từ host. `expose` chỉ mô tả port cho network, không publish ra host; service nội bộ thường không cần cả hai.

---

## 7. Volume và dữ liệu bền vững

Container filesystem là tạm thời. PostgreSQL cần named volume:

~~~yaml
volumes:
  postgres-data:
  redis-data:

services:
  db:
    volumes:
      - postgres-data:/var/lib/postgresql/data
  redis:
    volumes:
      - redis-data:/data
~~~

Ba kiểu mount:

| Kiểu | Dùng cho | Lưu ý |
|---|---|---|
| Named volume | Database, Redis | Docker quản lý, tồn tại sau container bị xóa |
| Bind mount | Source code, config local | Phụ thuộc host path, phù hợp dev |
| tmpfs | `/tmp`, file tạm | Nhanh, mất khi container dừng |

`docker compose down -v` xóa named volume và dữ liệu database. Đây là thao tác destructive, chỉ dùng khi chắc chắn đang reset local:

~~~bash
docker compose down -v
~~~

Volume không thay thế backup. Production cần backup, restore test và storage có replication phù hợp.

---

## 8. Docker Compose cho Task API

### 8.1. Compose local

Ví dụ `compose.yaml`:

~~~yaml
services:
  app:
    build:
      context: .
      target: runtime
    image: task-api:local
    ports:
      - "8080:8080"
      - "8081:8081"
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/taskdb
      SPRING_DATASOURCE_USERNAME: task
      SPRING_DATASOURCE_PASSWORD: change-me-locally
      SPRING_DATA_REDIS_HOST: redis
      MANAGEMENT_SERVER_PORT: 8081
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_healthy
    read_only: true
    tmpfs:
      - /tmp
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
    healthcheck:
      test: ["CMD", "curl", "--fail", "--silent", "http://127.0.0.1:8081/actuator/health/readiness"]
      interval: 10s
      timeout: 3s
      start_period: 30s
      retries: 10
    restart: unless-stopped

  db:
    image: postgres:16.4-alpine
    environment:
      POSTGRES_DB: taskdb
      POSTGRES_USER: task
      POSTGRES_PASSWORD: change-me-locally
    volumes:
      - postgres-data:/var/lib/postgresql/data
    ports:
      - "127.0.0.1:5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U task -d taskdb"]
      interval: 5s
      timeout: 3s
      retries: 10

  redis:
    image: redis:7.4-alpine
    command: ["redis-server", "--appendonly", "yes"]
    volumes:
      - redis-data:/data
    ports:
      - "127.0.0.1:6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 10

  prometheus:
    image: prom/prometheus:v2.55.1
    volumes:
      - ./docker/prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - prometheus-data:/prometheus
    ports:
      - "127.0.0.1:9090:9090"
    depends_on:
      app:
        condition: service_started

  grafana:
    image: grafana/grafana:11.4.0
    volumes:
      - grafana-data:/var/lib/grafana
    ports:
      - "127.0.0.1:3000:3000"
    depends_on:
      - prometheus

volumes:
  postgres-data:
  redis-data:
  prometheus-data:
  grafana-data:
~~~

Trong file committed, thay password local bằng `.env` gitignored hoặc Docker secrets phù hợp. Các version ở trên là ví dụ có pin; cập nhật chúng qua review và vulnerability scan.

Compose override healthcheck dùng port quản trị `8081`, vì profile Docker tách management port khỏi API port. Khi chạy image độc lập với management port mặc định `8080`, Dockerfile healthcheck sẽ kiểm tra port `8080`.

### 8.2. Prometheus config

Tạo `docker/prometheus/prometheus.yml`:

~~~yaml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: task-api
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["app:8081"]
~~~

Prometheus phải gọi `app:8081`, không phải `localhost:8081`; trong container `localhost` là Prometheus container.

### 8.3. Khởi động stack

~~~bash
docker compose config --quiet
docker compose up --build -d
docker compose ps
docker compose logs -f app
~~~

Mở:

- API: `http://localhost:8080`.
- Actuator: `http://localhost:8081/actuator/health`.
- Prometheus: `http://localhost:9090`.
- Grafana: `http://localhost:3000`.

Kiểm tra metrics từ host:

~~~bash
curl --fail http://localhost:8081/actuator/prometheus
~~~

Dừng stack nhưng giữ volume:

~~~bash
docker compose down
~~~

---

## 9. Health check và startup ordering

`depends_on` chỉ kiểm soát thứ tự hoặc điều kiện start trong Compose; nó không chứng minh app đã sẵn sàng mãi mãi. App vẫn phải retry kết nối hoặc fail rõ ràng khi dependency chưa sẵn sàng.

Health check tốt cần:

- Command chạy nhanh.
- Timeout hữu hạn.
- `start_period` đủ cho JVM startup/migration.
- Số `retries` phù hợp.
- Exit code 0 chỉ khi thực sự healthy.

Phân biệt:

~~~text
container started ≠ process ready ≠ dependency ready ≠ request can succeed
~~~

Không gọi endpoint business nặng trong Docker healthcheck. Dùng Actuator health/probe nhẹ và để integration test kiểm tra business flow.

Nếu app có migration lâu, không biến healthcheck thành migration runner lặp lại. Migration nên có lock/idempotency và bước deploy riêng khi cần.

---

## 10. Non-root và hardening container

### 10.1. Chạy bằng user không phải root

Dockerfile phải tạo user và đặt `USER app`. Kiểm tra:

~~~bash
docker run --rm task-api:local id
docker inspect --format "{{.Config.User}}" task-api:local
~~~

Nếu output là root hoặc rỗng, image chưa đạt yêu cầu. Port 8080 không cần capability `NET_BIND_SERVICE`.

### 10.2. Filesystem và capability

Compose local có thể dùng:

~~~yaml
services:
  app:
    read_only: true
    tmpfs:
      - /tmp
    cap_drop: [ALL]
    security_opt:
      - no-new-privileges:true
~~~

`read_only` có thể làm app lỗi nếu framework ghi log/file tạm ngoài `/tmp`. Kiểm tra thực tế và chỉ mount writable path tối thiểu. Không thêm `privileged: true` để chữa lỗi permission; tìm đúng path/user/capability.

### 10.3. Secrets

Không dùng:

~~~dockerfile
ENV API_KEY=real-production-secret
~~~

Không đặt secret thật trong `ARG`, vì build history/cache có thể giữ lại giá trị. Dùng runtime secret injection từ CI, secret manager, Docker Swarm/Kubernetes secret hoặc cloud secret service.

`.env` chỉ là tiện ích local, không phải secret manager. Hạn chế quyền file và không commit nó.

### 10.4. Image scan

Trước khi publish:

~~~bash
docker scout cves task-api:local
docker history --no-trunc task-api:local
~~~

Nếu môi trường dùng Trivy hoặc scanner khác, scan cả OS packages và Java dependencies. Có policy cho critical/high CVE, exception có thời hạn và owner rõ ràng.

---

## 11. Resource và JVM trong container

Container không phải lúc nào cũng thấy toàn bộ RAM/CPU của host. Đặt giới hạn và quan sát:

~~~yaml
services:
  app:
    deploy:
      resources:
        limits:
          cpus: "1.0"
          memory: 768M
        reservations:
          cpus: "0.25"
          memory: 256M
~~~

`deploy.resources` được Docker Compose hỗ trợ tùy phiên bản/runtime; kiểm tra `docker compose config` và runtime thực tế. Có thể dùng flags/resource config của Docker Engine khi cần.

JVM flags cần phù hợp limit:

~~~text
-XX:MaxRAMPercentage=75
-XX:InitialRAMPercentage=25
~~~

Đừng tăng heap lên 100% memory limit; JVM còn cần metaspace, thread stack, direct buffer và native memory. Theo dõi `docker stats`, heap metric và OOM kill.

Executor async ở Tuần 14 cũng phải tính vào CPU/memory limit. Queue quá lớn có thể làm container OOM trước khi có thể tự recover.

---

## 12. Dev, test và production khác nhau

### Development

- Bind mount source để sửa nhanh.
- Expose PostgreSQL/Redis ra `127.0.0.1`.
- Password local disposable.
- Log mức DEBUG khi cần.
- Có thể dùng Compose override.

### CI

- Build image từ clean checkout.
- Chạy unit/integration test trước khi push.
- Scan image/dependency.
- Tag bằng commit SHA hoặc immutable version.
- Không dùng credential host không cần thiết.

### Production

- Push image đã scan lên registry private.
- Pin digest.
- Non-root, read-only filesystem nếu tương thích.
- Secret manager, không `.env` trên image.
- Không publish database/Redis.
- Orchestrator quản lý restart, rolling update, readiness và replica.
- Backup database/volume và test restore.

Docker Compose tốt cho local và môi trường nhỏ. Nó không tự cung cấp đầy đủ rolling deployment, autoscaling, secret rotation, multi-node scheduling hoặc service discovery production như Kubernetes/ECS/Nomad.

---

## 13. Debugging thực tế

### Container không start

~~~bash
docker compose ps
docker compose logs --tail=100 app
docker inspect task-api
~~~

Kiểm tra command, image architecture, env bắt buộc và permission của user.

### App không kết nối database

~~~bash
docker compose exec app sh
docker compose exec db pg_isready -U task -d taskdb
~~~

Từ app container, host phải là `db`, port là `5432`; không dùng port publish `127.0.0.1:5432` để app container đi vòng qua host.

### Prometheus không scrape được

~~~bash
docker compose logs prometheus
docker compose exec prometheus wget -qO- http://app:8081/actuator/prometheus
~~~

Kiểm tra app expose đúng management port, endpoint được expose, network chung và health/readiness.

### Permission denied sau khi bật read-only

Tìm path ghi file từ log/stack trace. Cho phép đúng directory qua tmpfs hoặc volume nhỏ; không tắt `read_only` toàn bộ ngay lập tức.

### Container bị OOMKilled

~~~bash
docker stats
docker inspect --format "{{.State.OOMKilled}}" task-api
~~~

Xem heap, thread pool, queue, direct memory và giới hạn container trước khi chỉ tăng memory.

---

## 14. Lỗi thường gặp

- Dùng `latest` khiến build hôm nay khác build ngày mai.
- Copy toàn bộ repository vào image trước khi copy dependency descriptor.
- Dùng JDK đầy đủ ở runtime dù chỉ cần JRE.
- Chạy JVM bằng root.
- Hardcode secret trong Dockerfile/Compose.
- Dùng `localhost` để app gọi database/Redis container.
- Publish PostgreSQL/Redis ra `0.0.0.0` không cần thiết.
- Không có named volume nên mất dữ liệu khi recreate container.
- Dùng `docker compose down -v` và xóa nhầm database local.
- Nghĩ `depends_on` là readiness hoàn chỉnh.
- Healthcheck không có timeout hoặc gọi endpoint nặng.
- Không dùng `exec` trong entrypoint nên JVM không nhận SIGTERM đúng.
- Cho app ghi filesystem bất kỳ khi đã bật read-only.
- Dùng một container chứa app, DB, Redis và Prometheus.
- Không pin version image hoặc không scan CVE.
- Đưa `.env`, `target`, log và `.git` vào build context.
- Dùng Compose như hệ thống orchestration production mà không đánh giá giới hạn.

---

## 15. Bài thực hành bắt buộc

1. Viết Dockerfile multi-stage cho Task API.
2. Build JAR trong builder stage bằng Maven Wrapper.
3. Chạy runtime bằng JRE và user không phải root.
4. Tạo `.dockerignore` không chứa `.git`, `.env` và `target`.
5. Tạo `compose.yaml` cho app, PostgreSQL, Redis, Prometheus và Grafana.
6. Kết nối app tới `db` và `redis` bằng service name.
7. Thêm named volume cho PostgreSQL và Redis.
8. Thêm healthcheck cho app, PostgreSQL và Redis.
9. Expose Actuator management port riêng ở local.
10. Bật `read_only`, `/tmp` tmpfs, cap drop và no-new-privileges cho app.
11. Dừng/restart stack và xác nhận dữ liệu volume còn nguyên.
12. Xóa đúng volume local rồi seed lại database.
13. Scan image và kiểm tra không có secret trong layer/history.
14. Tạo dashboard Grafana đọc Prometheus metrics từ Task API.

---

## 16. Câu hỏi tự kiểm tra

1. Image khác container ở điểm nào?
2. Vì sao multi-stage làm runtime image an toàn và nhỏ hơn?
3. Tại sao `COPY pom.xml` trước `COPY src` giúp build nhanh?
4. Khi nào dùng named volume và khi nào dùng bind mount?
5. Vì sao app container không được dùng `localhost` cho PostgreSQL?
6. `ports` khác `expose` thế nào?
7. Health check khác readiness probe ra sao?
8. `depends_on` có đảm bảo database luôn sẵn sàng không?
9. Vì sao Dockerfile phải dùng non-root user?
10. Read-only filesystem ảnh hưởng những path nào của JVM/Spring?
11. Tại sao secret trong `ARG` hoặc image layer vẫn có thể bị lộ?
12. Vì sao `exec java ...` quan trọng khi nhận SIGTERM?
13. Khi nào `docker compose down -v` nguy hiểm?
14. Compose phù hợp production ở mức nào?
15. Làm sao điều tra app không kết nối được Redis trong Compose?

---

## 17. Tiêu chí hoàn thành tuần 16

- [ ] Có Dockerfile multi-stage cho Spring Boot.
- [ ] Runtime chỉ chứa JRE và artifact cần thiết.
- [ ] Image chạy bằng user không phải root.
- [ ] Có `.dockerignore` phù hợp.
- [ ] Cấu hình app đi qua environment/profile.
- [ ] App kết nối DB/Redis bằng service name.
- [ ] PostgreSQL và Redis dùng named volume.
- [ ] Có healthcheck với timeout và retry.
- [ ] Có Compose stack app, PostgreSQL, Redis, Prometheus, Grafana.
- [ ] Actuator/metrics được scrape qua network nội bộ.
- [ ] App dùng read-only filesystem nếu có thể.
- [ ] Có cap drop và no-new-privileges.
- [ ] Không có secret trong repository, image layer hoặc command history.
- [ ] Image đã được scan và tag immutable.
- [ ] Đã thử restart, log, exec, stats và failure scenario.

---

## 18. Gợi ý lịch học 7 ngày

| Ngày | Nội dung | Kết quả |
|---|---|---|
| 1 | Image, container, tag, registry | Hiểu vòng đời image |
| 2 | Dockerfile multi-stage và layer cache | Build được image Spring Boot |
| 3 | Environment, network, volume | App gọi DB/Redis đúng cách |
| 4 | Compose app + PostgreSQL + Redis | Chạy được Task API local |
| 5 | Actuator, healthcheck, Prometheus, Grafana | Có metrics và dashboard |
| 6 | Non-root, secrets, read-only, scan | Image an toàn hơn |
| 7 | Debug, restart, backup volume, review | Hoàn thiện checklist |

Tuần 17 theo roadmap chuyển sang CI/CD và deployment: CI pipeline, build/test/scan, registry, môi trường dev/staging/production, migration, rolling deployment, rollback và backup/restore.

## 19. Tài liệu tham khảo

- Dockerfile reference: https://docs.docker.com/reference/dockerfile/
- Docker Compose file reference: https://docs.docker.com/reference/compose-file/
- Docker build cache: https://docs.docker.com/build/cache/
- Docker volumes: https://docs.docker.com/engine/storage/volumes/
- Docker networking: https://docs.docker.com/engine/network/
- Docker Compose secrets: https://docs.docker.com/compose/how-tos/use-secrets/
- Spring Boot container images: https://docs.spring.io/spring-boot/reference/packaging/container-images/
- Spring Boot Docker Compose support: https://docs.spring.io/spring-boot/reference/features/dev-services.html
