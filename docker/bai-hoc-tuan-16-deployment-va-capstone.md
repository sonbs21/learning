# Tuần 16: Deployment và capstone

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Chọn mô hình triển khai phù hợp giữa Docker Compose một host, ECS/Fargate và Kubernetes.
- Hiểu container orchestration ở mức kiến trúc.
- Mô tả rolling deployment, health/readiness và graceful shutdown.
- Thiết kế logs, metrics và smoke test cho service.
- Lập kế hoạch backup volume, restore và disaster recovery.
- Hoàn thiện capstone Containerized Spring Boot Platform.
- Chạy API cùng PostgreSQL, Redis, RabbitMQ và Mailpit.
- Viết Dockerfile dev/production, Compose config, CI workflow và security gate.
- Chứng minh các bài test bắt buộc của roadmap.
- Viết README/runbook để người khác có thể chạy, debug, backup và rollback.

Thời lượng đề xuất: **12–16 giờ** gồm triển khai, test failure, viết tài liệu và demo capstone.

---

## 2. Từ container chạy được đến hệ thống triển khai được

Một container chạy local chưa phải một deployment. Deployment cần trả lời:

- Image nào đang chạy và digest là gì?
- Service ở đâu, route thế nào?
- Health/readiness được kiểm tra ra sao?
- Nếu process crash, hệ thống xử lý thế nào?
- Nếu host chết, dữ liệu khôi phục ở đâu?
- Deploy version mới có downtime không?
- Rollback về version trước bằng cách nào?
- Logs/metrics đi đâu?
- Secret/config được inject ra sao?
- Ai có quyền deploy và rollback?

Capstone là bài kiểm tra khả năng nối các tuần trước thành một hệ thống có thể vận hành:

~~~text
Code
 ↓
Dockerfile/multi-stage/runtime security
 ↓
Compose/network/volume/health
 ↓
CI build/test/scan/push
 ↓
Staging deploy/smoke test
 ↓
Rollback/backup/DR
~~~

---

## 3. Chọn mô hình triển khai

### 3.1. Docker Compose trên một host

Phù hợp với:

- Local development.
- Demo/internal tool.
- Small deployment trên một VM.
- Workload chưa cần multi-host orchestration.
- Team muốn vận hành đơn giản.

Ưu điểm:

- YAML dễ đọc.
- Chi phí và độ phức tạp thấp.
- Dễ debug bằng Docker CLI.
- Dễ chạy API/database/cache cùng host.

Giới hạn:

- Một host là failure domain lớn.
- Rolling/HA hạn chế.
- Scaling và service discovery không mạnh bằng orchestrator.
- Backup/storage/failover vẫn phải tự thiết kế.
- Secret/identity/policy phụ thuộc host và tooling.

### 3.2. ECS/Fargate ở mức nhập môn

ECS là container orchestrator của AWS; Fargate là compute model quản lý server/host ở mức cao hơn.

Các khái niệm cần nhận biết:

- Task definition: mô tả container, CPU/memory, network, env/secrets.
- Task: instance chạy từ task definition.
- Service: duy trì số lượng task và deployment.
- Cluster: phạm vi quản lý task/service.
- Target group/load balancer: route traffic.
- IAM role: quyền task/execution.
- CloudWatch/log driver: logs/metrics.
- ECR: registry image.

Phù hợp khi:

- Đã dùng AWS.
- Muốn managed orchestration mà không vận hành Kubernetes.
- Cần load balancer, autoscaling, IAM và integration cloud.

Trade-off:

- Phụ thuộc AWS.
- Cần hiểu VPC/IAM/ECR/ALB/logging/cost.
- Database thường nên dùng managed service thay vì nhét vào task production.

### 3.3. Kubernetes ở mức khái niệm

Các object cần nhận biết:

- Pod: đơn vị chạy container.
- Deployment: desired replicas/rolling update.
- Service: stable virtual endpoint/service discovery.
- Ingress/Gateway: HTTP routing.
- ConfigMap/Secret: configuration/secret.
- PersistentVolume/PersistentVolumeClaim: storage abstraction.
- Readiness/liveness/startup probe.
- Namespace/resource limit.

Kubernetes phù hợp workload lớn/phức tạp và multi-team, nhưng có operational cost cao hơn Compose. Không chuyển Compose sang Kubernetes chỉ vì “production”; chọn theo scale, team, reliability requirement và platform capability.

### 3.4. So sánh nhanh

| Tiêu chí | Compose một host | ECS/Fargate | Kubernetes |
|---|---|---|---|
| Đơn giản | Cao | Trung bình | Thấp hơn |
| Multi-host | Không/giới hạn | Có | Có |
| HA/rolling | Tự thiết kế | Có service support | Có nhiều primitive |
| Cloud coupling | Thấp | AWS | Thấp hơn nhưng vận hành phức tạp |
| Storage production | Tự quản lý | Thường managed/external | PVC/storage class |
| Phù hợp | Local/small | AWS managed deployment | Platform lớn/multi-team |

---

## 4. Container orchestration

Orchestrator duy trì desired state:

~~~text
Desired state: 3 replicas API, image digest D, port 8080
                         ↓
Orchestrator observes actual state
                         ↓
Create/replace/route/stop containers
~~~

Các khả năng thường có:

- Scheduling lên host phù hợp.
- Restart container/task/pod.
- Health/readiness routing.
- Rolling update.
- Scale replicas.
- Secret/config injection.
- Resource placement/limit.
- Service discovery.
- Log/metric integration.

Docker Compose một host chỉ cung cấp một phần các khả năng này. Khi deployment vượt quá một host, cần platform có control plane hoặc service managed tương ứng.

---

## 5. Capstone architecture

Kiến trúc theo roadmap:

~~~text
Reverse proxy
      ↓
Spring Boot API
  ├── PostgreSQL
  ├── Redis
  ├── RabbitMQ
  └── Mailpit
~~~

### 5.1. Vai trò service

- **Reverse proxy**: entrypoint HTTP/TLS/routing trong local/staging nếu cần.
- **Spring Boot API**: business API.
- **PostgreSQL**: source of truth relational data.
- **Redis**: cache/session/temporary coordination tùy app.
- **RabbitMQ**: asynchronous messaging.
- **Mailpit**: local/staging email sink để không gửi email thật.

Mailpit chỉ nên dùng local/staging. Production cần email provider/SMTP service/managed mail system có auth, delivery, bounce handling và compliance phù hợp.

### 5.2. Network boundary

~~~text
external/edge network
        │
reverse proxy ─── frontend network ─── API
                                      │
                                backend network
                              ├── PostgreSQL
                              ├── Redis
                              └── RabbitMQ
~~~

- Chỉ reverse proxy/API cần publish host/external port.
- PostgreSQL, Redis, RabbitMQ không publish nếu API là client duy nhất.
- API dùng service name trong Compose.
- Mailpit web UI chỉ mở ở local/staging nếu cần.

### 5.3. Data ownership

- PostgreSQL: named/external volume + backup/restore.
- Redis: xác định cache hay dữ liệu cần persist; không mặc định coi Redis là source of truth.
- RabbitMQ: quyết định durability/volume/managed broker tùy workload.
- Mailpit: dữ liệu local/test, có thể reset.

---

## 6. Dockerfile và image contract

Capstone cần:

- Multi-stage build.
- Runtime image không chứa tool build.
- Non-root.
- Entrypoint exec form.
- Health endpoint/healthcheck phù hợp.
- Base image pin/update policy.
- `.dockerignore`.
- Không secret trong layer.

Ví dụ runtime Dockerfile:

~~~dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline
COPY src src
RUN ./mvnw test package

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd --system --uid 10001 appuser
COPY --from=build /workspace/target/app.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
~~~

Nếu JAR có tên versioned, dùng tên artifact rõ hoặc copy chính xác. Không dùng wildcard mơ hồ nếu có nhiều JAR.

Image contract cần document:

- Listen port.
- Health endpoint.
- Required env vars.
- Required secret files.
- Writable paths.
- Shutdown behavior.
- CPU/memory baseline.
- Supported architectures.

---

## 7. Compose local và production-like

### 7.1. `compose.yaml` base

~~~yaml
name: task-platform

services:
  api:
    build:
      context: .
      dockerfile: Dockerfile
    image: task-api:dev
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/taskdb
      SPRING_DATASOURCE_USERNAME: task
      SPRING_DATASOURCE_PASSWORD: local-only
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: "6379"
      SPRING_RABBITMQ_HOST: rabbitmq
      SPRING_RABBITMQ_PORT: "5672"
      MAIL_HOST: mailpit
      MAIL_PORT: "1025"
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      rabbitmq:
        condition: service_healthy
    networks:
      - backend

  postgres:
    image: postgres:16
    environment:
      POSTGRES_DB: taskdb
      POSTGRES_USER: task
      POSTGRES_PASSWORD: local-only
    volumes:
      - pgdata:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U task -d taskdb"]
      interval: 5s
      timeout: 5s
      retries: 12
    networks:
      - backend

  redis:
    image: redis:7.4
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 12
    networks:
      - backend

  rabbitmq:
    image: rabbitmq:3.13-management
    healthcheck:
      test: ["CMD", "rabbitmq-diagnostics", "-q", "ping"]
      interval: 10s
      timeout: 5s
      retries: 12
    networks:
      - backend

  mailpit:
    image: axllent/mailpit:v1.21
    profiles: [tools]
    ports:
      - "8025:8025"
      - "1025:1025"
    networks:
      - backend

volumes:
  pgdata:

networks:
  backend:
~~~

Lưu ý: healthcheck command phải tồn tại trong image version thực tế. Kiểm tra trước khi dùng.

### 7.2. Production-like override

~~~yaml
services:
  api:
    image: registry.example.com/team/task-api:${IMAGE_TAG}
    build: null
    environment:
      SPRING_PROFILES_ACTIVE: production
    read_only: true
    tmpfs:
      - /tmp:rw,noexec,nosuid,size=64m
    security_opt:
      - no-new-privileges:true
    cap_drop:
      - ALL
    user: "10001:10001"
    cpus: 1.0
    mem_limit: 512m
    pids_limit: 128
    restart: unless-stopped

  postgres:
    restart: unless-stopped
    mem_limit: 1g

  redis:
    restart: unless-stopped
    mem_limit: 256m

  rabbitmq:
    restart: unless-stopped
    mem_limit: 768m
~~~

`build: null`/merge behavior cần kiểm tra Compose version. Alternative là file production hoàn chỉnh chỉ dùng `image`, tránh override phức tạp. Luôn render:

~~~bash
docker compose -f compose.yaml -f compose.prod.yaml config
~~~

### 7.3. Secret production

Không dùng password inline. Dùng secret manager/platform injection. Nếu local Compose secret:

~~~yaml
services:
  api:
    secrets:
      - db_password

secrets:
  db_password:
    file: ./secrets/db_password.txt
~~~

Production managed platform nên dùng AWS Secrets Manager/SSM, Kubernetes Secret + external secret manager hoặc system tương đương, theo policy tổ chức.

---

## 8. Reverse proxy và traffic flow

Reverse proxy có thể làm:

- TLS termination.
- Host/path routing.
- Request size/timeout.
- Access log.
- Basic rate limiting.
- Forward headers.
- Health route.

Flow local:

~~~text
Browser → localhost:8080 (proxy)
                   ↓
             api:8080
~~~

Nếu chưa có nhu cầu TLS/proxy, có thể publish API trực tiếp trong capstone local. Đừng thêm reverse proxy chỉ để tăng số container; hãy document lý do và test forwarding headers/health.

---

## 9. Health, readiness và liveness

### 9.1. Health categories

- **Liveness**: process có còn sống không?
- **Readiness**: service có nhận traffic được không?
- **Startup**: app đã khởi động xong chưa?
- **Dependency health**: DB/Redis/RabbitMQ có phục vụ được không?

Một endpoint `/actuator/health` có thể quá đơn giản hoặc quá sâu. Tách nhóm/endpoint theo mục tiêu và không leak credential/error chi tiết.

### 9.2. Compose healthcheck

~~~yaml
services:
  api:
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://127.0.0.1:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 6
      start_period: 30s
~~~

### 9.3. Orchestrator mapping

| Compose | ECS/Kubernetes concept |
|---|---|
| `healthcheck` | container health/probe |
| `depends_on` | startup dependency hạn chế |
| `ports` | load balancer/service mapping |
| `restart` | service/task restart |
| `read_only` | readonly root filesystem |
| named volume | task volume/PVC/external storage |

Mapping không phải 1:1. Kubernetes readiness probe có ý nghĩa route traffic; Compose healthcheck không tự tạo load balancer routing.

---

## 10. Graceful shutdown

API cần xử lý SIGTERM:

~~~properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=20s
~~~

Docker/Compose stop timeout phải đủ:

~~~yaml
services:
  api:
    stop_grace_period: 30s
~~~

Flow rolling deployment:

~~~text
New instance starts
→ new instance ready
→ traffic shifts
→ old instance receives SIGTERM
→ requests drain
→ old instance exits
~~~

Nếu entrypoint là shell wrapper, phải dùng `exec` để Java nhận signal. Test bằng `docker compose stop` và log shutdown.

---

## 11. Rolling deployment

Rolling deployment thay instance từng phần thay vì dừng tất cả:

~~~text
Version A: A1 A2 A3
Start B1 → health/readiness pass
Route traffic B1
Stop A1
Start B2 → pass
Stop A2
Start B3 → pass
Stop A3
Version B: B1 B2 B3
~~~

### 11.1. Điều kiện cần

- Có ít nhất hai instance/replica hoặc platform support.
- Health/readiness chính xác.
- Backward-compatible API/schema trong giai đoạn overlap.
- Graceful shutdown.
- Image immutable.
- Capacity đủ chạy old + new tạm thời.
- Rollback trigger rõ.

Compose trên một host thường không cung cấp rolling deployment tự động đầy đủ. Có thể mô phỏng bằng reverse proxy/2 project hoặc dùng orchestrator.

### 11.2. Database migration expand/contract

Để old/new app chạy đồng thời:

1. Expand: thêm column/table nullable/backward-compatible.
2. Deploy app mới đọc/ghi tương thích.
3. Backfill dữ liệu.
4. Contract: xóa field cũ sau khi không còn app cũ dùng.

Không deploy migration destructive trước khi old replica đã stop nếu muốn rolling/rollback an toàn.

---

## 12. Logs và metrics

### 12.1. Logs

Container nên log stdout/stderr:

~~~bash
docker compose logs --timestamps --tail 100 api postgres redis rabbitmq
~~~

Production cần:

- Central log aggregation.
- Retention.
- Search/correlation ID.
- Access control.
- PII/secret masking.
- Alert trên error rate/crash loop.

### 12.2. Metrics

Tối thiểu theo dõi:

Application:

- Request count/error rate.
- Latency p50/p95/p99.
- In-flight requests.
- JVM heap/GC/thread.
- DB pool usage.
- Queue publish/consume/failure.

Container/host:

- CPU/memory/throttling.
- Restart count.
- OOMKilled.
- Disk/volume usage.
- Network I/O.
- File descriptors.

Dependency:

- PostgreSQL connections/locks/replication/space.
- Redis memory/eviction/hit ratio.
- RabbitMQ queue depth/consumer state.
- SMTP/mail delivery result.

Metrics endpoint không nên public không auth. Dùng internal monitoring network/agent.

### 12.3. Logs không thay metrics

Log giải thích event; metric cho trend/alert. Cần cả hai để quyết định rollback và capacity.

---

## 13. Backup volume và database

### 13.1. PostgreSQL backup

Named volume giúp survive container recreation nhưng không thay backup. Cần logical/physical backup:

~~~bash
docker compose exec -T postgres \
  pg_dump -U task -d taskdb > backup/taskdb.sql
~~~

Bảo vệ file backup:

- Permission hạn chế.
- Encryption.
- Không commit Git.
- Retention.
- Off-host copy.
- Restore test.

### 13.2. Redis

Xác định Redis là cache hay data store. Nếu cache, mất data có thể chấp nhận nhưng app phải rebuild. Nếu data quan trọng, bật persistence/backup/managed Redis theo requirement.

### 13.3. RabbitMQ

Message durability, queue policy, consumer ack và backup khác database dump. Không coi copy volume khi broker đang chạy là backup nhất quán mặc định.

### 13.4. RPO/RTO

- **RPO**: mất tối đa bao nhiêu dữ liệu.
- **RTO**: phục hồi trong bao lâu.

Ví dụ:

~~~text
RPO: 15 phút
RTO: 1 giờ
~~~

Backup schedule/storage/restore runbook phải đạt target thật, không chỉ tồn tại file `.sql`.

---

## 14. Disaster recovery

DR không chỉ là backup. Cần kế hoạch:

1. Phát hiện sự cố.
2. Xác định blast radius.
3. Chọn vùng/host/registry còn hoạt động.
4. Lấy image digest đã biết tốt.
5. Provision network/volume/secret.
6. Restore database/queue data theo thứ tự.
7. Deploy service.
8. Chạy health/smoke/integrity test.
9. Mở traffic.
10. Theo dõi và ghi postmortem.

### 14.1. Dependency của DR

- Registry còn pull được image.
- Credentials/secret manager còn truy cập được.
- Backup ở ngoài host bị lỗi.
- DNS/TLS/load balancer có thể chuyển route.
- Version/schema tương thích.
- Người vận hành có quyền cần thiết.
- Runbook được test.

### 14.2. Restore drill

Định kỳ thực hiện trên môi trường riêng:

- Tạo host/Compose project mới.
- Pull image bằng digest.
- Tạo volume mới.
- Restore backup.
- Deploy service.
- Chạy query/integrity/smoke.
- Đo thời gian và ghi lỗi.

Backup chưa từng restore là giả định, chưa phải bằng chứng.

---

## 15. ECS/Fargate deployment flow ở mức khái niệm

Flow:

~~~text
Build/push image → ECR
        ↓
Task definition revision
        ↓
ECS service desired count
        ↓
Fargate tasks
        ↓
ALB target group health
        ↓
Traffic
~~~

Cần map:

- Docker image digest → task definition image.
- Compose env → task environment/secrets.
- Compose ports → container port/ALB listener.
- Healthcheck → container/target health.
- Named volume → EFS/managed DB/storage (không mặc định local disk).
- Logs → CloudWatch/log destination.
- Resource limits → task CPU/memory.
- Restart → ECS service maintains desired count.

Production thường tách PostgreSQL/Redis/RabbitMQ sang managed services nếu requirement về HA/backup/scale cao.

---

## 16. Kubernetes deployment flow ở mức khái niệm

Flow:

~~~text
Image registry
    ↓
Deployment → ReplicaSet → Pods
    ↓
Service → stable DNS/virtual IP
    ↓
Ingress/Gateway → external traffic
~~~

Compose mapping sơ bộ:

| Compose | Kubernetes |
|---|---|
| Service | Deployment/StatefulSet/Job tùy workload |
| `ports` | Service/Ingress |
| `environment` | ConfigMap/Secret |
| `healthcheck` | probes |
| volume | PVC/StorageClass |
| `restart` | controller desired state |
| project network | cluster network/policy |

Đừng copy Compose YAML vào Kubernetes mà không hiểu:

- Readiness vs liveness.
- Stateful workload/storage.
- Resource request/limit.
- Service account/RBAC.
- NetworkPolicy.
- Secret handling.
- Rolling strategy.

---

## 17. Capstone requirements

Capstone phải có:

- Spring Boot API chạy bằng multi-stage Dockerfile.
- Runtime image non-root.
- PostgreSQL dùng named volume.
- Redis dùng internal network.
- RabbitMQ không publish port nếu không cần.
- Compose healthcheck.
- App lấy config từ environment.
- Không có secret trong image.
- Dev và production Compose profile/config.
- Resource limit.
- Integration test.
- CI build/scan/push.
- Rollback plan.
- README từ zero đến running.

### 17.1. Definition of done

Không chỉ “container start được”. Capstone done khi:

- Người mới có thể chạy theo README.
- Build sạch thành công.
- Source change rebuild được.
- Dependency change rebuild được.
- Data persist/recover được.
- Dependency unhealthy được phản ánh.
- API xử lý dependency failure rõ.
- Security baseline được kiểm chứng.
- CI tạo artifact có digest/report.
- Staging deploy/smoke/rollback được demo.
- Runbook đủ để người khác xử lý incident cơ bản.

---

## 18. Capstone repository layout

Gợi ý:

~~~text
.
├── Dockerfile
├── Dockerfile.dev
├── compose.yaml
├── compose.prod.yaml
├── compose.test.yaml
├── .dockerignore
├── .env.example
├── secrets.example/
│   └── README.md
├── .github/workflows/
│   └── container-ci.yaml
├── src/
├── pom.xml
├── mvnw
├── README.md
└── docs/
    ├── architecture.md
    ├── debugging-runbook.md
    ├── backup-restore-runbook.md
    └── deployment-runbook.md
~~~

Không commit `secrets/` thật, `.env` production, backup database hoặc registry token.

---

## 19. Capstone Compose architecture

### 19.1. Service matrix

| Service | Image/build | Network | Publish | Storage | Health |
|---|---|---|---|---|---|
| Reverse proxy | image | edge/frontend | 80/443 | config/cert external | HTTP |
| API | build/image | frontend/backend | chỉ qua proxy/API | tmpfs/volume tối thiểu | actuator |
| PostgreSQL | image | backend | không production | named/external volume | pg_isready |
| Redis | image | backend | không production | cache/persistence tùy policy | ping |
| RabbitMQ | image | backend | không nếu không cần | volume nếu durable | diagnostics |
| Mailpit | image | tools/backend | local/staging UI | ephemeral | optional |

### 19.2. Config matrix

Document cho từng service:

- Required env.
- Default/local value.
- Production source.
- Secret/non-secret.
- Healthcheck.
- Dependency.
- Writable path.
- CPU/memory.
- Port/network.

---

## 20. Bài test bắt buộc

### 20.1. Rebuild sau khi sửa source

~~~bash
# sửa một source file
docker compose build api
docker compose up -d api
docker compose logs -f api
~~~

Xác nhận image/container mới có behavior source thay đổi.

### 20.2. Rebuild sau khi sửa dependency

Sửa `pom.xml`, build lại và kiểm tra dependency layer/cache bị invalidated hợp lý:

~~~bash
docker compose build --progress=plain api
~~~

### 20.3. Database giữ dữ liệu sau restart

~~~bash
docker compose exec postgres psql -U task -d taskdb -c 'SELECT count(*) FROM notes;'
docker compose restart postgres
docker compose exec postgres psql -U task -d taskdb -c 'SELECT count(*) FROM notes;'
~~~

Restart không phải backup; cần test remove/recreate với named volume và backup/restore riêng.

### 20.4. API chờ database healthy

Cố ý làm DB init chậm/health fail trong test, quan sát API không start sai thứ tự rồi sửa health/depends/retry.

### 20.5. Redis unavailable

Dừng Redis:

~~~bash
docker compose stop redis
docker compose logs api
~~~

API phải xử lý lỗi rõ: retry/timeout/fallback/error response phù hợp, không treo vô hạn.

### 20.6. Container bị kill và tự restart

Dùng service test hoặc kill process trong lab:

~~~bash
docker compose kill api
docker compose ps
~~~

Xác nhận restart policy/health/orchestrator behavior. Không dùng `kill` để kết luận graceful shutdown.

### 20.7. Non-root

~~~bash
docker compose exec api id
docker inspect <api-container> --format '{{.Config.User}}'
~~~

Expected UID không phải 0.

### 20.8. Read-only root filesystem

~~~bash
docker inspect <api-container> --format '{{.HostConfig.ReadonlyRootfs}}'
docker compose logs api
~~~

App vẫn start và health pass với tmpfs/path writable tối thiểu.

### 20.9. Scan policy

Scan final runtime image. Không có finding nghiêm trọng chưa được policy chấp thuận. Lưu report gắn image digest.

### 20.10. Deploy tag mới và rollback tag cũ

~~~text
Deploy digest D2
→ smoke test fail
→ rollback digest D1
→ smoke test pass
~~~

Ghi thời gian rollback và vấn đề schema/config nếu có.

---

## 21. CI capstone pipeline

### 21.1. Pull request

~~~text
Checkout PR
  ↓
Unit test
  ↓
Build image (không push production)
  ↓
Start PostgreSQL/Redis/RabbitMQ test stack
  ↓
Integration test
  ↓
Scan runtime image
  ↓
Upload test/report
~~~

### 21.2. Merge main

~~~text
Checkout main
  ↓
Build immutable sha tag
  ↓
Integration/security gate
  ↓
Push registry
  ↓
Record digest/SBOM/provenance
  ↓
Deploy staging
  ↓
Smoke test
  ↓
Approve/promote/rollback
~~~

### 21.3. Artifact metadata

Lưu:

- Commit SHA.
- Image tag.
- Image digest.
- Base image digest.
- Build ID.
- SBOM path/digest.
- Scan report/policy result.
- Signature/provenance reference.
- Deploy environment/time.
- Previous digest để rollback.

---

## 22. Deployment runbook

### Pre-deploy

- [ ] CI pipeline pass.
- [ ] Image digest đã scan/sign/approve.
- [ ] Migration plan/backward compatibility rõ.
- [ ] Backup gần nhất đạt RPO.
- [ ] Rollback digest đã biết.
- [ ] Config/secret tồn tại đúng environment.
- [ ] Capacity đủ cho overlap/rollback.
- [ ] Smoke test sẵn sàng.
- [ ] On-call/owner biết thời gian deploy.

### Deploy

1. Pull/verify digest.
2. Apply config/secret.
3. Start new instance/replica.
4. Chờ startup/readiness.
5. Chuyển traffic từng phần.
6. Theo dõi logs/metrics/errors.
7. Drain/stop old instance.
8. Ghi release metadata.

### Post-deploy

- Health/readiness pass.
- Error/latency không tăng bất thường.
- DB migration/schema pass.
- Queue/Redis hoạt động.
- Smoke test pass.
- Không có restart/OOM loop.
- Document result.

### Rollback

1. Xác định trigger/blast radius.
2. Chọn previous digest.
3. Kiểm tra schema compatibility.
4. Deploy previous digest.
5. Chờ readiness.
6. Smoke test.
7. Theo dõi metrics/logs.
8. Mở incident/postmortem.

---

## 23. Backup/restore runbook

### Backup

- Xác định database/volume owner.
- Kiểm tra storage capacity.
- Chạy logical/physical backup phù hợp.
- Encrypt và lưu off-host.
- Ghi timestamp/size/checksum.
- Kiểm tra exit code/log.

### Restore

- Provision volume/host mới.
- Dùng image/database version tương thích.
- Restore vào môi trường cô lập.
- Chạy integrity/query check.
- Đo RTO.
- Chỉ chuyển traffic sau smoke/validation.

### Khi nào không được xóa volume

- Chưa có backup verified.
- Chưa biết owner.
- Migration đang fail chưa xác định.
- Đang điều tra data corruption.
- Không có rollback/restore plan.

---

## 24. Debugging capstone

Quy trình theo Tuần 12:

~~~bash
docker compose ps -a
docker compose logs --tail 100 api postgres redis rabbitmq
docker compose config
docker inspect <container>
docker network ls
docker volume ls
docker compose exec api env
~~~

Điều tra theo thứ tự:

1. State/health/exit.
2. Logs timeline.
3. Effective config.
4. Environment non-secret.
5. Network/DNS/port.
6. Volume/permission.
7. Command trực tiếp.
8. Rebuild/recreate đúng service.

Không reset toàn bộ stack khi chỉ một service lỗi.

---

## 25. Capstone demo checklist

Trong demo, cần trình bày được:

1. `docker compose up` từ README.
2. API health và một business endpoint.
3. Service discovery tới PostgreSQL/Redis/RabbitMQ.
4. Mailpit nhận email local/staging.
5. Database data persist sau recreate.
6. Healthcheck/dependency behavior.
7. API xử lý Redis unavailable.
8. Non-root/read-only/resource hardening.
9. CI build/scan/push và image digest.
10. Deploy image mới.
11. Smoke test.
12. Rollback image cũ.
13. Backup/restore test.
14. Architecture diagram và runbook.

---

## 26. Bài tập tự làm

### Bài 1: Chọn deployment target

Dựa trên số service, HA, team skill, budget và data requirement, chọn Compose một host, ECS/Fargate hoặc Kubernetes. Viết ADR ngắn nêu lý do và giới hạn.

### Bài 2: Hoàn thiện capstone Compose

Chạy API/PostgreSQL/Redis/RabbitMQ/Mailpit, tách network/volume/health/profile và kiểm tra config render.

### Bài 3: CI pipeline

Tạo workflow PR/main với unit test, integration test, build, scan, immutable tag và digest output.

### Bài 4: Staging deploy

Deploy digest vào staging, chạy smoke test, ghi logs/metrics và lưu release metadata.

### Bài 5: Rollback

Tạo lỗi có kiểm soát ở version mới, rollback digest cũ và chứng minh API/database compatibility.

### Bài 6: DR drill

Xóa staging host/project lab, provision lại, restore DB backup, deploy image digest và chạy smoke/integrity test. Đo RTO.

### Bài 7: Runbook review

Nhờ người khác chạy README từ máy sạch/working directory sạch. Ghi lại mọi bước thiếu hoặc assumption chưa document.

---

## 27. Câu hỏi kiểm tra và đáp án gợi ý

1. Khi nào Compose một host còn phù hợp?
2. ECS/Fargate giải quyết vấn đề gì so với Compose?
3. Kubernetes Deployment/Service/Probe tương ứng khái niệm nào?
4. Vì sao readiness quan trọng trong rolling deployment?
5. Vì sao graceful shutdown cần đi cùng rolling deployment?
6. Tại sao deploy bằng digest tốt hơn tag mutable?
7. Vì sao rollback image có thể không đủ sau migration?
8. Backup volume khác backup database thế nào?
9. RPO và RTO là gì?
10. Mailpit có nên dùng production không?
11. Vì sao metrics cần ngoài logs?
12. Capstone “done” cần bằng chứng nào?

Đáp án ngắn: Compose phù hợp local/small single-host; ECS/Fargate managed task/service/infra; Kubernetes dùng Deployment/Service/probes; readiness ngăn traffic vào instance chưa sẵn sàng; graceful shutdown drain request; digest định danh artifact; schema mới có thể không tương thích app cũ; volume persist/file copy không tự consistency; RPO là lượng data mất cho phép, RTO là thời gian phục hồi; Mailpit chỉ local/staging; metrics cho trend/alert; cần test/build/scan/deploy/rollback/backup/runbook evidence.

---

## 28. Tiêu chí hoàn thành tuần 16 và capstone

### Deployment

- [ ] Chọn và giải thích deployment target.
- [ ] Hiểu Compose một host, ECS/Fargate và Kubernetes ở mức khái niệm.
- [ ] Có health/readiness/startup strategy.
- [ ] Có graceful shutdown.
- [ ] Có logs/metrics cơ bản.
- [ ] Có rollback bằng image digest.
- [ ] Có backup/restore và DR runbook.

### Capstone implementation

- [ ] Spring Boot API chạy bằng multi-stage Dockerfile.
- [ ] Runtime image non-root.
- [ ] PostgreSQL dùng named volume.
- [ ] Redis dùng internal network.
- [ ] RabbitMQ không publish port không cần thiết.
- [ ] Compose healthcheck/dependency condition.
- [ ] Config từ environment/secret mechanism.
- [ ] Không có secret trong image/Git.
- [ ] Dev/prod Compose config/profile.
- [ ] Resource/read-only/security hardening.
- [ ] Integration test với dependencies.
- [ ] CI build/scan/push.
- [ ] Staging deploy/smoke test.
- [ ] Rollback plan đã thực hành.

### Deliverables

- [ ] `Dockerfile` production.
- [ ] `Dockerfile.dev` nếu cần.
- [ ] `.dockerignore`.
- [ ] `compose.yaml`.
- [ ] `compose.prod.yaml`.
- [ ] `.env.example` không chứa secret thật.
- [ ] CI workflow.
- [ ] Security scan report.
- [ ] Image size/runtime footprint report.
- [ ] Startup/debugging runbook.
- [ ] Backup/restore runbook.
- [ ] Deployment/rollback runbook.
- [ ] Architecture diagram.
- [ ] README từ zero đến running.

### Bài test bắt buộc

- [ ] Rebuild sau khi sửa source.
- [ ] Rebuild sau khi sửa dependency.
- [ ] Database vẫn giữ dữ liệu sau container restart/recreate đúng volume.
- [ ] API chờ database healthy.
- [ ] Redis unavailable thì app xử lý lỗi rõ ràng.
- [ ] Container bị kill rồi tự restart theo policy.
- [ ] Image chạy bằng UID non-root.
- [ ] Read-only root filesystem vẫn hoạt động.
- [ ] Scan image không có lỗi nghiêm trọng theo policy.
- [ ] Deploy image tag/digest mới rồi rollback artifact cũ.
- [ ] Backup restore được trên môi trường test.
- [ ] README được người khác chạy thành công.

Kiểm tra cuối lộ trình:

~~~bash
docker compose config
docker compose ps
docker compose logs --tail 100
docker image inspect <image>
docker history --no-trunc <image>
docker volume ls
docker network ls
~~~

Mục tiêu cuối cùng không phải có một YAML dài, mà là có một platform containerized có thể build, test, deploy, quan sát, rollback và phục hồi với bằng chứng rõ ràng.
