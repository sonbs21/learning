# Tuần 11: Compose production-like

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Viết Compose configuration gần production hơn thay vì chỉ start container.
- Dùng healthcheck để mô tả service đã sẵn sàng ở mức application.
- Dùng `depends_on.condition: service_healthy` để giảm startup race.
- Chọn restart policy phù hợp và hiểu giới hạn của nó.
- Đặt memory/CPU limit cho service.
- Chạy container với root filesystem read-only và cấp tmpfs cho thư mục tạm.
- Inject secret bằng Compose secret mount thay vì hardcode vào image/environment.
- Tách `compose.dev.yaml`, `compose.prod.yaml` bằng override file.
- Dùng external network và named volume có chủ đích.
- Phân biệt cấu hình Compose local/production-like với production orchestration thực sự.
- Kiểm tra config, health, resource, restart và data lifecycle trước khi bàn giao stack.

Thời lượng đề xuất: **8–12 giờ** gồm đọc config, chạy stack và thực hiện failure lab.

---

## 2. “Production-like” nghĩa là gì?

Một Compose stack cơ bản chỉ cần start các container. Một stack production-like cần trả lời thêm:

- Service đã thật sự ready chưa?
- Nếu process crash thì chuyện gì xảy ra?
- Memory/CPU có bị giới hạn không?
- Container có cần ghi filesystem root không?
- Secret đi vào container bằng cách nào?
- Database volume có lifecycle/backup rõ không?
- API có nhìn thấy database qua đúng network không?
- Dev config có vô tình được dùng ở production không?
- Khi một dependency chậm hoặc chết, stack phản ứng thế nào?

Production-like không đồng nghĩa production-ready. Compose thường chạy trên một host hoặc Docker Desktop; production có thể cần orchestrator, multi-host networking, managed database, secret manager, rolling deployment, HA và observability đầy đủ.

Mục tiêu tuần này là luyện các nguyên tắc runtime quan trọng trong phạm vi Compose.

---

## 3. Healthcheck

### 3.1. Container running chưa chắc service healthy

`docker compose ps` có thể cho thấy container đang `running`, nhưng process bên trong vẫn đang:

- Migrate database.
- Load cache.
- Mở connection pool.
- Khởi động web server.
- Chờ dependency.
- Lỗi một phần nhưng process chưa exit.

Healthcheck tạo một tín hiệu có cấu trúc hơn `running`:

~~~text
created → running → starting → healthy
                         └────→ unhealthy
~~~

### 3.2. Healthcheck cho PostgreSQL

~~~yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_USER: app
      POSTGRES_PASSWORD: local-only
      POSTGRES_DB: appdb
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app -d appdb"]
      interval: 5s
      timeout: 5s
      retries: 10
      start_period: 10s
~~~

Giải thích:

- `test`: command Docker chạy bên trong container.
- `interval`: khoảng cách giữa các lần check.
- `timeout`: thời gian tối đa cho một check.
- `retries`: số lần fail liên tiếp trước unhealthy.
- `start_period`: grace period khi database mới khởi động.

`pg_isready` chỉ kiểm tra PostgreSQL có accept connection hay chưa; nó không chứng minh migration/schema/app query đúng.

### 3.3. Healthcheck cho Redis

~~~yaml
services:
  redis:
    image: redis:7.4
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 10
      start_period: 5s
~~~

### 3.4. Healthcheck cho API

Nếu image có `wget`:

~~~yaml
services:
  api:
    image: task-api:0.4.0
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://127.0.0.1:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 6
      start_period: 30s
~~~

Nếu runtime image không có `wget`/`curl`, healthcheck sẽ fail dù app hoạt động. Chọn một trong các hướng:

- Dùng binary có sẵn và kiểm tra trước.
- Thêm tool có chủ đích vào debug/runtime image.
- Dùng health endpoint/command phù hợp với base image.
- Để monitoring bên ngoài kiểm tra endpoint.

Health endpoint không nên expose thông tin nhạy cảm. Có thể tách liveness/readiness group và giới hạn access tùy ứng dụng.

### 3.5. Xem health status

~~~bash
docker compose ps
docker compose ps --format json
docker inspect --format '{{json .State.Health}}' <container>
docker compose logs db redis api
~~~

Healthcheck là một process/command định kỳ, có cost CPU và có thể tạo log. Interval/timeout cần phù hợp, không đặt quá dày tùy tiện.

---

## 4. `depends_on` với `service_healthy`

Compose cơ bản:

~~~yaml
services:
  api:
    depends_on:
      - db
      - redis
~~~

Chỉ diễn tả thứ tự tạo/start ở mức cơ bản. Để chờ healthcheck:

~~~yaml
services:
  api:
    image: task-api:0.4.0
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_healthy

  db:
    image: postgres:16
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app -d appdb"]
      interval: 5s
      timeout: 5s
      retries: 10

  redis:
    image: redis:7.4
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 10
~~~

Flow:

~~~text
Start db/redis
    ↓
Healthchecks pass
    ↓
Compose starts api
~~~

### 4.1. Condition không phải distributed readiness

`service_healthy` chỉ giúp Compose quyết định start dependency trong project. Nó không xử lý mọi tình huống:

- Database restart sau khi API đã start.
- Network partition.
- Connection bị reset.
- Migration thất bại sau healthcheck đơn giản.
- Service healthy nhưng request cụ thể fail.
- Nhiều replica/host/orchestrator.

Ứng dụng vẫn cần retry/reconnect và xử lý dependency failure runtime.

### 4.2. `service_completed_successfully`

Với migration/job chạy xong thành công, có thể dùng condition tương ứng nếu Compose version hỗ trợ:

~~~yaml
services:
  api:
    depends_on:
      migrate:
        condition: service_completed_successfully

  migrate:
    image: task-api:0.4.0
    command: ["java", "-jar", "app.jar", "--migrate"]
~~~

Cần bảo đảm migration command idempotent, exit code chính xác và không chạy song song ngoài ý muốn.

---

## 5. Restart policy

Restart policy mô tả Compose/Docker nên xử lý container khi process exit:

~~~yaml
services:
  api:
    restart: unless-stopped
~~~

Các giá trị thường gặp:

| Policy | Ý nghĩa |
|---|---|
| `no` | Không tự restart; mặc định trong nhiều config |
| `always` | Restart khi container stop, kể cả daemon restart tùy behavior |
| `on-failure` | Restart khi exit code khác 0 |
| `on-failure:5` | Tối đa số lần retry được chỉ định |
| `unless-stopped` | Restart trừ khi operator chủ động stop |

### 5.1. Chọn policy

- Local one-off job: `no`.
- Service local muốn tự sống lại: `unless-stopped` có thể phù hợp.
- Batch job cần retry lỗi: `on-failure:N`.
- Production orchestrator: thường để platform quản lý restart/rollout theo policy riêng.

Restart không sửa root cause. Nếu config sai, container có thể crash loop:

~~~bash
docker compose ps
docker compose logs --tail 100 api
docker inspect --format '{{.RestartCount}}' <container>
~~~

### 5.2. Restart và data consistency

Restart database/app giữa transaction hoặc migration có thể để lại trạng thái cần recovery. Graceful shutdown, idempotent startup và migration strategy vẫn cần thiết.

### 5.3. Test restart policy

Dùng service test cố ý exit:

~~~yaml
services:
  crash-test:
    image: alpine:3.20
    command: ["sh", "-c", "echo crash; exit 1"]
    restart: on-failure:3
~~~

Theo dõi:

~~~bash
docker compose up -d crash-test
docker compose ps -a
docker compose logs crash-test
~~~

Dọn service test sau khi quan sát.

---

## 6. Resource limits

Một service không nên được phép tiêu thụ không giới hạn tài nguyên host.

### 6.1. CPU và memory trong Compose local

~~~yaml
services:
  api:
    image: task-api:0.4.0
    cpus: 1.0
    mem_limit: 512m
    mem_reservation: 256m
~~~

Các field thực tế được hỗ trợ có thể phụ thuộc Compose implementation/version. Kiểm tra bằng:

~~~bash
docker compose config
docker inspect <container>
docker stats
~~~

### 6.2. `deploy.resources` và portability

Bạn có thể gặp:

~~~yaml
services:
  api:
    deploy:
      resources:
        limits:
          cpus: "1.0"
          memory: 512M
        reservations:
          cpus: "0.25"
          memory: 256M
~~~

`deploy` historically gắn với Swarm/deployment semantics; behavior trong `docker compose` local có thể khác theo version. Không giả định mọi field `deploy` được áp dụng y hệt trên mọi runtime. Kiểm tra effective container config và tài liệu môi trường.

### 6.3. JVM trong resource limit

Nếu API Java có memory limit `512m`, heap không nên mặc định bằng `512m`:

~~~yaml
environment:
  JAVA_TOOL_OPTIONS: >-
    -XX:MaxRAMPercentage=65
    -Xlog:gc*:stdout:time,uptime,level,tags
~~~

Chừa headroom cho metaspace, thread stack, direct memory và native allocation. Chi tiết Java runtime đã học ở Tuần 7.

### 6.4. Quan sát limit

~~~bash
docker compose stats
docker stats --no-stream <container>
docker inspect --format 'memory={{.HostConfig.Memory}} nano_cpus={{.HostConfig.NanoCpus}}' <container>
~~~

Limit cần đi cùng load test, alert và capacity planning. Đặt limit quá thấp có thể gây throttling/OOM; không đặt limit chỉ để “đánh dấu production-like”.

---

## 7. Read-only root filesystem và tmpfs

### 7.1. `read_only: true`

~~~yaml
services:
  api:
    image: task-api:0.4.0
    read_only: true
    tmpfs:
      - /tmp
~~~

Root filesystem của container không cho process ghi. Những path app thực sự cần ghi phải được cấp riêng bằng:

- Named volume.
- Bind mount.
- tmpfs.
- External storage.

### 7.2. Tìm path app cần ghi

Các path thường gặp:

- `/tmp` cho temporary file.
- Log file path nếu app không log stdout.
- Upload/cache directory.
- JVM temp directory.
- Framework-generated file.

Ưu tiên cấu hình app log ra stdout/stderr và dùng external log collector thay vì ghi log vào root filesystem.

### 7.3. tmpfs cho temporary path

~~~yaml
services:
  api:
    read_only: true
    tmpfs:
      - /tmp:rw,noexec,nosuid,size=64m
~~~

Option/format có thể phụ thuộc platform. `noexec`/`nosuid` là defense-in-depth, không thay thế application security.

### 7.4. Read-only không phải “app không thể thay đổi gì”

Process vẫn có thể:

- Ghi vào tmpfs/volume writable.
- Ghi network/external systems.
- Dùng memory.
- Khai thác capability còn được cấp.

Kết hợp `read_only` với non-root, cap drop, resource limit, network policy và image scanning.

### 7.5. Debug read-only failure

~~~bash
docker compose logs api
docker compose exec api sh -c 'touch /tmp/test && touch /app/test'
~~~

Image tối giản có thể không có shell; dùng command/tool debug phù hợp. Nếu `/app/test` fail nhưng app cần ghi `/app`, hãy thiết kế lại path hoặc mount volume tối thiểu thay vì bỏ `read_only` toàn bộ.

---

## 8. Secret injection

### 8.1. Không hardcode secret

Không nên:

~~~yaml
services:
  db:
    environment:
      POSTGRES_PASSWORD: super-real-production-password
~~~

trong file committed. Environment cũng có thể xuất hiện qua inspect/process metadata.

### 8.2. Compose secrets

Tạo file local ngoài Git, ví dụ `secrets/db_password.txt`:

~~~text
local-only-password
~~~

Compose:

~~~yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_PASSWORD_FILE: /run/secrets/db_password
    secrets:
      - db_password

secrets:
  db_password:
    file: ./secrets/db_password.txt
~~~

Compose mount secret vào `/run/secrets/db_password`. Application/database image phải hỗ trợ biến dạng `_FILE` hoặc bạn phải có entrypoint đọc file đúng cách.

### 8.3. API đọc secret file

Spring Boot có thể dùng config import/entrypoint để đọc secret file tùy setup. Không giả định mọi app tự đọc `/run/secrets`.

Một entrypoint wrapper có thể đọc file rồi export vào process, nhưng cần:

- Không echo secret.
- `exec` process chính.
- Quyền file phù hợp.
- Xử lý file thiếu/empty.
- Không lưu secret vào image layer.

### 8.4. Compose secret khác secret manager production

Compose file secret phù hợp local/limited deployment, nhưng không tự trở thành Vault/KMS/cloud secret manager. Production cần đánh giá encryption at rest/in transit, rotation, access audit và operator access.

### 8.5. Git hygiene

~~~gitignore
.env
.env.*
secrets/
*.pem
*.key
~~~

Giữ `.env.example`/`secrets.example` chỉ có placeholder. Nếu secret đã commit/push, cần rotate; xóa file khỏi commit mới không làm secret cũ biến mất khỏi Git history.

---

## 9. Override file và tách dev/prod-like

### 9.1. Base Compose file

`compose.yaml` chứa cấu hình chung:

~~~yaml
name: task-stack

services:
  api:
    image: task-api:0.4.0
    environment:
      SPRING_PROFILES_ACTIVE: docker
      DB_HOST: db
      REDIS_HOST: redis
    networks:
      - backend

  db:
    image: postgres:16
    volumes:
      - pgdata:/var/lib/postgresql/data
    networks:
      - backend

  redis:
    image: redis:7.4
    networks:
      - backend

volumes:
  pgdata:

networks:
  backend:
~~~

### 9.2. Dev override

`compose.dev.yaml`:

~~~yaml
services:
  api:
    build:
      context: .
      dockerfile: Dockerfile
    ports:
      - "8080:8080"
    volumes:
      - ./config/dev:/app/config:ro
    environment:
      SPRING_PROFILES_ACTIVE: dev

  db:
    ports:
      - "5433:5432"

  redis:
    ports:
      - "6380:6379"
~~~

Chạy:

~~~bash
docker compose -f compose.yaml -f compose.dev.yaml config
docker compose -f compose.yaml -f compose.dev.yaml up -d --build
~~~

### 9.3. Production-like override

`compose.prod.yaml`:

~~~yaml
services:
  api:
    image: registry.example.com/team/task-api:0.4.0
    read_only: true
    tmpfs:
      - /tmp:rw,noexec,nosuid,size=64m
    mem_limit: 512m
    cpus: 1.0
    restart: unless-stopped
    ports:
      - "127.0.0.1:8080:8080"

  db:
    restart: unless-stopped
    mem_limit: 1g

  redis:
    restart: unless-stopped
    mem_limit: 256m
~~~

Chạy:

~~~bash
docker compose -f compose.yaml -f compose.prod.yaml config
docker compose -f compose.yaml -f compose.prod.yaml up -d
~~~

### 9.4. Merge order

File đứng sau override file trước. Config cuối cần được xem bằng `docker compose config`; không suy luận merge chỉ bằng cách đọc từng file.

~~~bash
docker compose -f compose.yaml -f compose.dev.yaml config > /tmp/compose-dev-rendered.yaml
docker compose -f compose.yaml -f compose.prod.yaml config > /tmp/compose-prod-rendered.yaml
~~~

Không lưu rendered config chứa secret vào artifact/log public.

### 9.5. Khi nào tách file hoàn toàn?

Override giúp reuse, nhưng merge phức tạp có thể khó review. Nếu dev/prod khác quá nhiều về service/network/secret/storage, hai file rõ ràng hoặc template/generator có kiểm soát có thể tốt hơn. Tránh tạo quá nhiều lớp override không ai hiểu effective config.

---

## 10. External network

External network do hệ thống khác tạo và Compose không sở hữu lifecycle:

~~~yaml
networks:
  edge:
    external: true
    name: shared-edge
~~~

Tạo trước:

~~~bash
docker network create shared-edge
docker compose -f compose.prod.yaml up -d
~~~

Dùng external network khi:

- Reverse proxy chạy ngoài project cần route vào service.
- Nhiều Compose project cần kết nối có chủ đích.
- Network lifecycle do platform/operator quản lý.

Rủi ro:

- Giảm isolation giữa project.
- Service names/aliases có thể xung đột.
- `compose down` không xóa external network.
- Debug khó nếu không biết owner/network policy.

Không dùng external network chỉ để “container nhìn thấy nhau” khi default/project network đã đủ.

### 10.1. External named volume

~~~yaml
volumes:
  pgdata:
    external: true
    name: task-prod-pgdata
~~~

Tạo trước:

~~~bash
docker volume create task-prod-pgdata
~~~

Compose không tạo/xóa volume external. Đây là safety boundary nhưng cũng có nghĩa deployment fail nếu operator quên provision volume.

---

## 11. Named volumes và production-like data lifecycle

~~~yaml
services:
  db:
    image: postgres:16
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
    name: task-prod-pgdata
~~~

Explicit name giúp biết volume nào thuộc stack, nhưng có thể gây collision giữa environment. Dùng tên chứa environment/project rõ ràng.

Checklist volume:

- Data directory đúng với image.
- Owner/UID/GID đúng.
- Backup đã có và restore test.
- Retention rõ.
- Không `down -v` nhầm.
- Không `volume prune` trên host dùng chung.
- Disk capacity/alert được theo dõi.
- Major version upgrade có migration plan.

Persistence không thay thế database HA/managed service nếu production availability yêu cầu cao.

---

## 12. Compose production-like hoàn chỉnh

Ví dụ rút gọn với API, DB, Redis và internal backend network:

~~~yaml
name: task-prod-like

services:
  api:
    image: task-api:0.4.0
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/taskdb
      SPRING_DATASOURCE_USERNAME: task
      SPRING_DATASOURCE_PASSWORD_FILE: /run/secrets/db_password
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: "6379"
      JAVA_TOOL_OPTIONS: >-
        -XX:MaxRAMPercentage=65
        -Xlog:gc*:stdout:time,uptime,level,tags
    secrets:
      - db_password
    ports:
      - "127.0.0.1:8080:8080"
    read_only: true
    tmpfs:
      - /tmp:rw,noexec,nosuid,size=64m
    cpus: 1.0
    mem_limit: 512m
    restart: unless-stopped
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://127.0.0.1:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 6
      start_period: 30s
    networks:
      - backend

  db:
    image: postgres:16
    environment:
      POSTGRES_DB: taskdb
      POSTGRES_USER: task
      POSTGRES_PASSWORD_FILE: /run/secrets/db_password
    secrets:
      - db_password
    volumes:
      - pgdata:/var/lib/postgresql/data
    mem_limit: 1g
    restart: unless-stopped
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U task -d taskdb"]
      interval: 5s
      timeout: 5s
      retries: 12
      start_period: 15s
    networks:
      - backend

  redis:
    image: redis:7.4
    mem_limit: 256m
    restart: unless-stopped
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 12
    networks:
      - backend

volumes:
  pgdata:

networks:
  backend:
    internal: true

secrets:
  db_password:
    file: ./secrets/db_password.txt
~~~

Cần điều chỉnh:

- API image có thật sự có `wget` không?
- Spring Boot có đọc `SPRING_DATASOURCE_PASSWORD_FILE` không? Spring mặc định không phải lúc nào tự đọc `_FILE`; có thể cần entrypoint/config mapping.
- `read_only` có làm app fail vì path ghi không?
- Resource fields có được runtime Compose hỗ trợ không?
- `internal: true` có phù hợp network/egress cần thiết không?
- Secret file có tồn tại, permission đúng và không commit không?

Đây là template học tập, không phải copy nguyên xi vào production.

---

## 13. Lab 1: Healthcheck và dependency condition

Tạo file `compose.health.yaml`:

~~~yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: appdb
      POSTGRES_USER: app
      POSTGRES_PASSWORD: local-only
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app -d appdb"]
      interval: 3s
      timeout: 3s
      retries: 10
      start_period: 10s

  api:
    image: task-api:0.4.0
    depends_on:
      db:
        condition: service_healthy
~~~

Chạy:

~~~bash
docker compose -f compose.health.yaml up -d
docker compose -f compose.health.yaml ps
docker compose -f compose.health.yaml logs db api
~~~

Cố ý đổi user/database trong healthcheck để làm health fail, rồi quan sát API không được start theo condition. Sửa lại và kiểm tra lần nữa.

Dọn:

~~~bash
docker compose -f compose.health.yaml down
~~~

---

## 14. Lab 2: Restart và resource limit

~~~yaml
services:
  crash-test:
    image: alpine:3.20
    command: ["sh", "-c", "echo fail; exit 1"]
    restart: on-failure:3
    mem_limit: 32m
    cpus: 0.25
~~~

Chạy và quan sát:

~~~bash
docker compose -f compose.restart.yaml up -d crash-test
docker compose -f compose.restart.yaml ps -a
docker compose -f compose.restart.yaml logs crash-test
docker inspect --format '{{.RestartCount}}' <container>
~~~

Ghi nhận restart policy không sửa command lỗi. Dọn:

~~~bash
docker compose -f compose.restart.yaml down
~~~

---

## 15. Lab 3: Read-only root và tmpfs

~~~yaml
services:
  api:
    image: task-api:0.4.0
    read_only: true
    tmpfs:
      - /tmp:rw,noexec,nosuid,size=32m
~~~

Chạy container và kiểm tra app. Nếu app fail, đọc log để xác định path ghi:

~~~bash
docker compose -f compose.readonly.yaml up -d api
docker compose -f compose.readonly.yaml logs api
docker compose -f compose.readonly.yaml exec api sh -c 'touch /tmp/ok; touch /app/should-fail'
~~~

Không tắt `read_only` ngay. Xác định path thật sự cần ghi rồi mount riêng hoặc cấu hình app dùng `/tmp`/external storage.

---

## 16. Lab 4: Dev/prod-like override

Tạo:

- `compose.yaml`: service/common config.
- `compose.dev.yaml`: build local, bind mount/config, host ports.
- `compose.prod.yaml`: image registry, resource, read-only, restart, external resources.

Validate:

~~~bash
docker compose -f compose.yaml -f compose.dev.yaml config
docker compose -f compose.yaml -f compose.prod.yaml config
~~~

So sánh:

- Image/build.
- Ports.
- Volumes.
- Environment.
- Healthcheck.
- Resource limits.
- Network.
- Restart policy.

Chạy riêng từng mode bằng project name khác nhau:

~~~bash
docker compose -p task-dev -f compose.yaml -f compose.dev.yaml up -d
docker compose -p task-prod-like -f compose.yaml -f compose.prod.yaml up -d
~~~

Dọn đúng project:

~~~bash
docker compose -p task-dev -f compose.yaml -f compose.dev.yaml down
docker compose -p task-prod-like -f compose.yaml -f compose.prod.yaml down
~~~

---

## 17. Lỗi thường gặp

### `service_healthy` không hoạt động

Healthcheck thiếu, command không tồn tại, endpoint sai, credentials sai, start period quá ngắn hoặc Compose version không hỗ trợ syntax đang dùng. Dùng `config`, `ps` và inspect health.

### API start trước database dù có `depends_on`

Đang dùng short syntax hoặc healthcheck chưa pass. `depends_on` không chữa runtime reconnect sau khi DB restart; app vẫn cần retry.

### Container restart loop

Kiểm tra logs, exit code, restart count và command cuối. Tạm bỏ restart policy trong debug để thấy failure rõ hơn.

### Memory limit làm Java OOM/OOMKilled

Heap/off-heap budget chưa phù hợp. Kiểm tra JVM flags, `docker stats`, `OOMKilled`, GC/thread metrics.

### Read-only root làm app crash

App ghi log/cache/temp/config vào root filesystem. Dùng stdout, tmpfs hoặc mount writable path tối thiểu.

### Secret file không tồn tại

File path sai, secret chưa được khai báo/attach hoặc Compose project chạy từ thư mục khác. Chạy `config` và kiểm tra secret mount trong inspect mà không in nội dung secret.

### Override merge không như mong đợi

Sai thứ tự `-f`, path tương đối, list merge/replace khác assumption hoặc biến interpolation từ file khác. Xem rendered `docker compose config`.

### External network/volume không tồn tại

Compose không tự tạo resource external. Provision trước và kiểm tra tên chính xác.

### `down -v` xóa data

Đây là behavior có chủ đích. Khôi phục từ backup nếu có; không chạy production data cleanup khi chưa xác nhận.

### Healthcheck quá nặng

Command healthcheck chạy thường xuyên và có thể tạo connection/process liên tục. Giảm tần suất, dùng check nhẹ và tách readiness sâu khỏi liveness đơn giản.

---

## 18. Bài tập tự làm

### Bài 1: PostgreSQL chỉ ở backend network

Tạo `frontend` và `backend` network. API nối cả hai; PostgreSQL chỉ backend. Chứng minh API kết nối được DB nhưng container chỉ ở frontend không resolve được DB.

### Bài 2: API chờ database healthy

Thêm PostgreSQL healthcheck và `depends_on.condition: service_healthy`. Làm healthcheck fail có chủ ý, quan sát API, sửa và chạy lại.

### Bài 3: Redis healthcheck

Thêm `redis-cli ping` healthcheck, kiểm tra `docker compose ps` hiển thị healthy và thử restart Redis.

### Bài 4: App tự restart

Cấu hình `restart: on-failure:3` cho service test crash. Ghi nhận restart count và lý do policy không sửa root cause.

### Bài 5: Resource limit

Đặt CPU/memory limit cho API, dùng `docker compose stats`, gửi workload và quan sát latency/memory/GC. Chọn limit có headroom.

### Bài 6: Read-only root

Bật `read_only: true`, cấp tmpfs `/tmp`, chạy app, tìm path ghi và sửa config mà không mở writable root toàn bộ.

### Bài 7: Dev/prod override

Tách `compose.dev.yaml` và `compose.prod.yaml`, render bằng `docker compose config`, so sánh effective config và chạy bằng hai project name.

### Bài 8: Secret injection

Dùng secret file local cho password database, mount vào `/run/secrets`, xác nhận file tồn tại mà không in nội dung vào log/Git.

---

## 19. Câu hỏi kiểm tra và đáp án gợi ý

1. Container running khác healthy thế nào?
2. `service_healthy` cần điều kiện gì?
3. Vì sao healthcheck pass không bảo đảm mọi query ứng dụng thành công?
4. Restart policy có sửa crash root cause không?
5. Vì sao resource limit cần đi cùng JVM tuning?
6. `read_only: true` yêu cầu app thay đổi thế nào?
7. tmpfs phù hợp với loại dữ liệu nào?
8. Compose secret khác environment plain text thế nào?
9. Override file merge theo nguyên tắc nào?
10. External network/volume khác resource do Compose sở hữu ra sao?
11. Vì sao `down -v` nguy hiểm?
12. Production-like Compose vẫn thiếu những khả năng nào so với orchestrator?

Đáp án ngắn: running chỉ là process tồn tại còn healthy là health command pass; cần healthcheck đúng; health đơn giản không kiểm tra mọi dependency/query; restart chỉ lặp lại process; JVM còn heap/off-heap/native; path ghi phải chuyển sang tmpfs/volume/external; tmpfs cho dữ liệu tạm; secret mount tránh hardcode/env nhưng vẫn cần secret manager; file sau override base; external lifecycle do bên ngoài quản lý; `down -v` có thể xóa volume; Compose một host không tự cung cấp HA/rolling/multi-host orchestration/secret governance đầy đủ.

---

## 20. Tiêu chí hoàn thành tuần 11

- [ ] Viết healthcheck cho API, PostgreSQL và Redis.
- [ ] Dùng được `depends_on.condition: service_healthy`.
- [ ] Hiểu healthcheck không thay thế retry/readiness runtime.
- [ ] Cấu hình và test restart policy.
- [ ] Đặt CPU/memory limit và quan sát bằng stats.
- [ ] Chạy app với read-only root filesystem.
- [ ] Cấp tmpfs hoặc writable mount tối thiểu cho path cần thiết.
- [ ] Inject secret bằng file/mount, không hardcode vào image/Compose committed file.
- [ ] Tách dev/prod-like bằng override files.
- [ ] Dùng external network/volume có chủ đích.
- [ ] Giữ PostgreSQL chỉ reachable trong backend network.
- [ ] Biết `down -v`/prune có thể destructive.
- [ ] Render và review effective config bằng `docker compose config`.
- [ ] Phân biệt production-like Compose với production orchestrator.

Kiểm tra cuối buổi:

~~~bash
docker compose config
docker compose ps
docker compose logs --tail 100
docker compose stats --no-stream
docker inspect --format '{{json .State.Health}}' <container>
~~~

Mục tiêu của tuần này là biến Compose stack thành một hệ thống có health signal, giới hạn tài nguyên, boundary network/storage và cách inject config/secret có kiểm soát.
