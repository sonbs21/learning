# Roadmap học Docker trong 16 tuần

Lộ trình này dành cho developer backend, đặc biệt phù hợp với Java/Spring Boot. Mục tiêu là hiểu Docker từ container cơ bản đến xây image production, chạy multi-service với Compose, bảo mật, CI/CD và triển khai.

Thời lượng đề xuất: **8–12 giờ/tuần** trong 16 tuần.

## Mục tiêu cuối lộ trình

Sau 16 tuần, bạn có thể:

- Giải thích image, container, registry, volume, network và Docker daemon.
- Dùng Docker CLI thành thạo.
- Viết Dockerfile cho ứng dụng Java/Spring Boot.
- Tối ưu image bằng layer cache và multi-stage build.
- Chạy PostgreSQL, Redis và app bằng Docker Compose.
- Thiết kế network và volume đúng mục đích.
- Dùng healthcheck, dependency condition và graceful shutdown.
- Không đưa secret vào image hoặc Git.
- Chạy container non-root, read-only và giới hạn resource.
- Scan image, ký image và kiểm soát supply chain ở mức cơ bản.
- Build/push image trong CI/CD.
- Debug container, network, storage và process.
- Biết giới hạn của Docker khi chuyển sang Kubernetes/ECS.

## Điều kiện đầu vào

- Linux command line cơ bản.
- Git.
- HTTP, REST và port.
- Java/Spring Boot cơ bản để làm project xuyên suốt.
- Hiểu database và Redis ở mức sử dụng.

## Cài đặt

Chọn một:

- Docker Desktop cho macOS/Windows.
- Docker Engine + Docker Compose plugin cho Linux.

Kiểm tra:

~~~bash
docker version
docker compose version
docker run --rm hello-world
~~~

Tài liệu bắt đầu chính thức: [Docker Get Started](https://docs.docker.com/get-started/).

---

# Giai đoạn 1 — Mô hình Docker và CLI

## Tuần 1: Container fundamentals
**Bài học chi tiết:** [Tuần 1: Container fundamentals](bai-hoc-tuan-1-container-fundamentals.md)

### Học

- Virtual machine khác container.
- Linux process và namespace ở mức khái niệm.
- Container image.
- Container runtime.
- Docker daemon và Docker CLI.
- Registry.
- Container lifecycle.
- Ephemeral container.
- Một container, một concern chính.
- Immutability.

### Thực hành

~~~bash
docker run --name web -d -p 8080:80 nginx:1.27
docker ps
docker ps -a
docker logs web
docker exec -it web sh
docker inspect web
docker stop web
docker rm web
~~~

### Tiêu chí hoàn thành

- Biết container không phải virtual machine đầy đủ.
- Biết container có thể bị xóa và tạo lại.
- Biết image là template còn container là instance đang chạy.

## Tuần 2: Docker CLI và lifecycle
**Bài học chi tiết:** [Tuần 2: Docker CLI và lifecycle](bai-hoc-tuan-2-docker-cli-va-lifecycle.md)

### Học

- run, create, start, stop, restart.
- kill và pause.
- exec và attach.
- logs.
- inspect.
- cp.
- diff.
- top.
- stats.
- rename.
- labels.
- exit code.
- foreground và detached mode.

### Bài tập

- Chạy PostgreSQL và Redis.
- Đọc log khi service khởi động lỗi.
- Vào container bằng exec.
- Copy file log ra host.
- Giới hạn memory và CPU rồi quan sát docker stats.

### Cần tránh

- Dùng docker exec như cách cấu hình lâu dài.
- Sửa file trong container rồi tưởng thay đổi đã nằm trong image.
- Dùng container đang chạy làm backup.

## Tuần 3: Images, layers và registry
**Bài học chi tiết:** [Tuần 3: Images, layers và registry](bai-hoc-tuan-3-images-layers-va-registry.md)

### Học

- Image manifest.
- Layer.
- Image tag và digest.
- Local image store.
- Pull, tag, push.
- Docker Hub/private registry.
- Immutable tag.
- Multi-architecture image ở mức nhận biết.
- Image garbage collection.

### Thực hành

~~~bash
docker images
docker pull nginx:1.27
docker image inspect nginx:1.27
docker tag nginx:1.27 username/nginx-learning:1.0.0
docker history nginx:1.27
docker image prune
~~~

### Quy tắc

- Không dùng latest trong production.
- Pin major/minor hoặc digest theo yêu cầu reproducibility.
- Đọc image origin và scan vulnerabilities.
- Không push image chứa secret.

---

# Giai đoạn 2 — Dockerfile và image build

## Tuần 4: Dockerfile cơ bản
**Bài học chi tiết:** [Tuần 4: Dockerfile cơ bản](bai-hoc-tuan-4-dockerfile-co-ban.md)

### Học

- FROM.
- WORKDIR.
- COPY.
- ADD và vì sao thường ưu tiên COPY.
- RUN.
- ENV.
- ARG.
- EXPOSE.
- USER.
- ENTRYPOINT.
- CMD.
- SHELL.
- LABEL.
- HEALTHCHECK.

### Thực hành Spring Boot

Viết Dockerfile chạy JAR:

~~~dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/app.jar app.jar
EXPOSE 8080
USER 10001
ENTRYPOINT ["java", "-jar", "app.jar"]
~~~

Build và chạy:

~~~bash
./mvnw package -DskipTests
docker build -t task-api:0.1.0 .
docker run --rm -p 8080:8080 task-api:0.1.0
~~~

### Tiêu chí hoàn thành

- Tự viết Dockerfile cho Spring Boot.
- Biết ENTRYPOINT và CMD khác nhau.
- Biết EXPOSE không tự publish port ra host.

## Tuần 5: Build context, .dockerignore và layer cache
**Bài học chi tiết:** [Tuần 5: Build context, .dockerignore và layer cache](bai-hoc-tuan-5-build-context-dockerignore-layer-cache.md)

### Học

- Build context.
- Layer cache.
- Cache invalidation.
- Thứ tự COPY dependency trước source.
- .dockerignore.
- BuildKit.
- docker build --pull.
- docker build --no-cache.
- Build output và image size.

### Dockerfile tối ưu dependency

~~~dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline
COPY src src
RUN ./mvnw package -DskipTests
~~~

### Bài tập

- Build trước và sau khi sắp xếp lại COPY.
- Sửa một source file và quan sát layer nào bị rebuild.
- Đưa .git, target, .env, log và IDE folder vào .dockerignore.
- Đo build time và image size.

Docker khuyến nghị tận dụng build cache, loại bỏ file không cần và pin base image. [Docker build best practices](https://docs.docker.com/build/building/best-practices/)

## Tuần 6: Multi-stage build và runtime image
**Bài học chi tiết:** [Tuần 6: Multi-stage build và runtime image](bai-hoc-tuan-6-multi-stage-build-va-runtime-image.md)

### Học

- Build stage.
- Test stage.
- Runtime stage.
- COPY --from.
- JDK khác JRE.
- Distroless ở mức khái niệm.
- Alpine và compatibility.
- Debug image khác production image.
- SBOM và dependency footprint.

### Dockerfile multi-stage

~~~dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline
COPY src src
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd --system --uid 10001 appuser
COPY --from=build /workspace/target/*.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
~~~

### Tiêu chí hoàn thành

- Final image không chứa Maven, source code hoặc test artifact.
- Biết multi-stage giảm image size và attack surface.
- Có health endpoint để dùng cho HEALTHCHECK.

## Tuần 7: Java container runtime
**Bài học chi tiết:** [Tuần 7: Java container runtime](bai-hoc-tuan-7-java-container-runtime.md)

### Học

- JVM trong container.
- CPU và memory limit.
- Heap sizing.
- Container-aware JVM.
- GC log.
- Thread count.
- Signal handling.
- PID 1.
- Graceful shutdown.
- Spring Boot container startup.
- Layered JAR.

### Thực hành

- Chạy app với memory limit.
- Quan sát OOMKilled.
- Gửi SIGTERM và kiểm tra app shutdown.
- Cấu hình server shutdown graceful.
- Tách dependency và application layers khi phù hợp.

---

# Giai đoạn 3 — Networking và storage

## Tuần 8: Docker networking
**Bài học chi tiết:** [Tuần 8: Docker networking](bai-hoc-tuan-8-docker-networking.md)

### Học

- Network namespace.
- Bridge network.
- Host network ở mức nhận biết.
- Container DNS.
- Service name.
- Port publishing.
- EXPOSE.
- Internal-only network.
- Custom network.
- Frontend/backend network.
- localhost bên trong container.

### Thực hành

~~~bash
docker network create app-net
docker run -d --name redis --network app-net redis:7.4
docker run --rm --network app-net redis:7.4 redis-cli -h redis ping
~~~

Trong Compose, service gọi nhau bằng service name, không dùng localhost. Docker Compose tạo default network và DNS theo tên service. [Compose networking](https://docs.docker.com/compose/how-tos/networking/)

### Bài tập

- API chỉ kết nối được database qua backend network.
- Frontend không truy cập trực tiếp database.
- Chỉ publish port của API.
- Test DNS và connectivity giữa container.

## Tuần 9: Volumes và dữ liệu persistent
**Bài học chi tiết:** [Tuần 9: Volumes và dữ liệu persistent](bai-hoc-tuan-9-volumes-va-du-lieu-persistent.md)

### Học

- Container writable layer.
- Named volume.
- Bind mount.
- Anonymous volume.
- tmpfs.
- Volume backup.
- Permission và UID/GID.
- Database data directory.
- Storage driver ở mức khái niệm.

### Thực hành

~~~bash
docker volume create pgdata
docker run -d --name postgres \
  -e POSTGRES_PASSWORD=postgres \
  -v pgdata:/var/lib/postgresql/data \
  -p 5432:5432 postgres:16
~~~

### Phân biệt

| Loại | Dùng cho |
|---|---|
| Bind mount | Source code, config dev |
| Named volume | Database và dữ liệu cần persist |
| Anonymous volume | Bảo vệ thư mục container |
| tmpfs | Dữ liệu tạm, secret tạm thời |

Không dùng docker compose down -v nếu chưa xác nhận volume có thể xóa.

---

# Giai đoạn 4 — Docker Compose

## Tuần 10: Compose cơ bản
**Bài học chi tiết:** [Tuần 10: Compose cơ bản](bai-hoc-tuan-10-compose-co-ban.md)

### Học

- compose.yaml.
- services.
- image và build.
- ports.
- environment.
- env_file.
- volumes.
- networks.
- depends_on.
- command và entrypoint.
- profiles.
- project name.
- logs, exec, ps.

### Project

Chạy stack:

~~~text
Spring Boot API
PostgreSQL
Redis
Mailpit
~~~

Lệnh:

~~~bash
docker compose up -d
docker compose ps
docker compose logs -f api
docker compose exec db psql -U postgres
docker compose down
~~~

## Tuần 11: Compose production-like
**Bài học chi tiết:** [Tuần 11: Compose production-like](bai-hoc-tuan-11-compose-production-like.md)

### Học

- Healthcheck.
- depends_on condition service_healthy.
- Restart policy.
- Resource limits.
- Read-only root filesystem.
- tmpfs.
- Secret injection.
- Override file.
- Dev/prod Compose config.
- External network.
- Named volumes.

### Bài tập

- PostgreSQL chỉ reachable trong backend network.
- API chờ database healthy.
- Redis healthcheck.
- App tự restart khi crash.
- Cấu hình resource limit.
- Tách compose.dev.yaml và compose.prod.yaml.

Compose Quickstart chính thức trình bày healthcheck, named volumes, service discovery và debugging stack. [Compose Quickstart](https://docs.docker.com/compose/gettingstarted/)

## Tuần 12: Debugging Compose stack
**Bài học chi tiết:** [Tuần 12: Debugging Compose stack](bai-hoc-tuan-12-debugging-compose-stack.md)

### Học

- Startup race.
- DNS failure.
- Port conflict.
- Volume permission.
- Wrong environment variable.
- Broken healthcheck.
- Container exit code.
- Dependency migration.
- Log aggregation cơ bản.

### Incident lab

- Database khởi động chậm.
- Redis không resolve được.
- App dùng localhost thay vì db.
- Port host bị chiếm.
- Migration thất bại.
- Volume bị mount sai path.

### Quy trình debug

1. docker compose ps.
2. docker compose logs.
3. docker inspect.
4. Kiểm tra environment.
5. Kiểm tra network và DNS.
6. Kiểm tra volume.
7. Chạy command trực tiếp trong container.
8. Rebuild đúng service.

---

# Giai đoạn 5 — Security và supply chain

## Tuần 13: Container security
**Bài học chi tiết:** [Tuần 13: Container security](bai-hoc-tuan-13-container-security.md)

### Học

- Root trong container.
- Non-root USER.
- Linux capabilities.
- no-new-privileges.
- Read-only filesystem.
- Seccomp và AppArmor ở mức nhận biết.
- Pids limit.
- CPU/memory limit.
- Network egress.
- Secret không nằm trong layer.
- Vulnerability scanning.

### Compose hardening

~~~yaml
services:
  api:
    read_only: true
    tmpfs:
      - /tmp
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
    user: "10001:10001"
~~~

### Cần tránh

- Chạy app bằng root.
- Mount Docker socket vào container tùy tiện.
- Đưa .env vào image.
- Dùng privileged: true mà không có lý do.
- Publish database port production ra Internet.

## Tuần 14: Image security và reproducible build
**Bài học chi tiết:** [Tuần 14: Image security và reproducible build](bai-hoc-tuan-14-image-security-va-reproducible-build.md)

### Học

- Base image trusted source.
- Tag mutable và digest.
- Docker Scout hoặc scanner tương đương.
- CVE triage.
- SBOM.
- Image signing ở mức khái niệm.
- Provenance.
- Dependabot cho Docker base image.
- Rebuild định kỳ.
- Registry access control.

### Thực hành

- Scan image trước khi push.
- Tìm package không cần thiết.
- Pin base image.
- Tạo non-root runtime.
- Kiểm tra image history để chắc chắn không lộ secret.
- Tạo CI job fail khi có vulnerability severity cao theo policy.

Docker khuyến nghị multi-stage build, base image đáng tin, non-root, .dockerignore và pin version/digest khi cần reproducibility. [Docker build best practices](https://docs.docker.com/build/building/best-practices/)

---

# Giai đoạn 6 — CI/CD và production

## Tuần 15: Build, registry và CI/CD
**Bài học chi tiết:** [Tuần 15: Build, registry và CI/CD](bai-hoc-tuan-15-build-registry-va-ci-cd.md)

### Học

- Docker Buildx.
- Multi-platform build.
- Cache-to/cache-from.
- Image tag theo commit SHA.
- Registry login.
- Push/pull.
- GitHub Actions.
- Test container.
- Scan image.
- Deploy staging.
- Rollback image.

### Pipeline

~~~text
Checkout
  ↓
Unit test
  ↓
Build image
  ↓
Integration test
  ↓
Security scan
  ↓
Push immutable tag
  ↓
Deploy staging
  ↓
Smoke test
  ↓
Promote production
~~~

### Bài tập

- Build image trên pull request.
- Tag theo commit SHA.
- Push chỉ khi merge main.
- Chạy integration test với PostgreSQL/Redis.
- Rollback về image tag trước đó.

## Tuần 16: Deployment và capstone
**Bài học chi tiết:** [Tuần 16: Deployment và capstone](bai-hoc-tuan-16-deployment-va-capstone.md)

### Học

- Docker Compose trên một host.
- ECS/Fargate ở mức nhập môn.
- Kubernetes ở mức khái niệm.
- Container orchestration.
- Rolling deployment.
- Health/readiness.
- Logs và metrics.
- Graceful shutdown.
- Backup volume.
- Disaster recovery.

Docker Compose phù hợp cho local và deployment nhỏ; production multi-service lớn thường cần orchestrator như ECS hoặc Kubernetes.

---

# Capstone: Containerized Spring Boot Platform

## Kiến trúc

~~~text
Reverse proxy
      ↓
Spring Boot API
  ├── PostgreSQL
  ├── Redis
  ├── RabbitMQ
  └── Mailpit
~~~

## Yêu cầu

- Spring Boot API chạy bằng multi-stage Dockerfile.
- Runtime image non-root.
- PostgreSQL dùng named volume.
- Redis dùng internal network.
- RabbitMQ không publish port nếu không cần.
- Compose có healthcheck.
- App lấy config từ environment.
- Không có secret trong image.
- Có dev và production Compose profile.
- Có resource limit.
- Có integration test.
- Có CI build/scan/push.
- Có rollback plan.
- Có README từ zero đến running.

## Bài test bắt buộc

- Rebuild sau khi sửa source.
- Rebuild sau khi sửa dependency.
- Database vẫn giữ dữ liệu sau container restart.
- API chờ database healthy.
- Redis unavailable thì app xử lý lỗi rõ ràng.
- Container bị kill rồi tự restart.
- Image chạy bằng UID non-root.
- Read-only root filesystem vẫn hoạt động.
- Scan image không có lỗi nghiêm trọng theo policy.
- Deploy image tag mới rồi rollback tag cũ.

## Deliverables

- Dockerfile dev và production.
- .dockerignore.
- compose.yaml.
- compose.prod.yaml.
- Environment example không chứa secret.
- CI workflow.
- Security scan report.
- Image size report.
- Startup/debugging runbook.
- Backup/restore runbook.
- Architecture diagram.

---

# Checklist sau 16 tuần

## Fundamentals

- Giải thích image, container, volume, network và registry.
- Dùng Docker CLI không cần tra mọi lệnh.
- Hiểu lifecycle và exit code.

## Dockerfile

- Viết được Dockerfile cho Spring Boot.
- Tối ưu layer cache.
- Dùng multi-stage build.
- Runtime image không chứa tool build.
- Chạy non-root.
- Có healthcheck phù hợp.

## Compose

- Chạy được API, PostgreSQL, Redis và queue.
- Dùng service discovery.
- Dùng named volume.
- Có healthcheck và dependency condition.
- Tách dev/prod config.

## Security

- Không hardcode secret.
- Pin base image.
- Scan image.
- Drop capability không cần thiết.
- Dùng read-only filesystem khi có thể.
- Không mount Docker socket tùy tiện.

## Operations

- Debug log, network, volume và process.
- Đo resource bằng docker stats.
- Build và push image trong CI.
- Rollback image.
- Có runbook khi service không start.

---

# Nguồn học chính thức

- [Docker Get Started](https://docs.docker.com/get-started/)
- [Docker CLI reference](https://docs.docker.com/reference/cli/docker/)
- [Dockerfile reference](https://docs.docker.com/reference/dockerfile/)
- [Build best practices](https://docs.docker.com/build/building/best-practices/)
- [Multi-stage builds](https://docs.docker.com/build/building/multi-stage/)
- [Build cache](https://docs.docker.com/get-started/docker-concepts/building/using-the-build-cache/)
- [Docker Compose Quickstart](https://docs.docker.com/compose/gettingstarted/)
- [Compose networking](https://docs.docker.com/compose/how-tos/networking/)
- [Compose file reference](https://docs.docker.com/reference/compose-file/)
- [Docker security](https://docs.docker.com/engine/security/)
- [Docker Scout](https://docs.docker.com/scout/)

Docker production-ready không chỉ là chạy được container. Bạn phải xây image nhỏ và tái lập được, tách service đúng cách, bảo vệ secret, kiểm soát resource, quan sát runtime và có khả năng rollback.
