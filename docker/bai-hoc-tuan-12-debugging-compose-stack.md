# Tuần 12: Debugging Compose stack

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Debug Compose stack theo quy trình có thứ tự thay vì thử lệnh ngẫu nhiên.
- Phân biệt container `running`, `healthy`, `exited` và `restarting`.
- Điều tra startup race giữa API và database/Redis.
- Debug DNS failure, sai network và lỗi dùng `localhost`.
- Xử lý port host bị chiếm.
- Điều tra volume permission và mount sai path.
- Kiểm tra environment variable đã được Compose resolve/truyền đúng chưa.
- Debug healthcheck bị broken hoặc quá nghiêm ngặt.
- Đọc exit code và log để phân biệt lỗi application/runtime.
- Điều tra migration thất bại và dependency schema.
- Gom log cơ bản theo service/time.
- Rebuild đúng service và xác minh fix mà không xóa dữ liệu tùy tiện.

Thời lượng đề xuất: **8–12 giờ** gồm đọc runbook, thực hiện incident lab và viết postmortem ngắn.

---

## 2. Nguyên tắc debug Compose

Khi stack lỗi, đừng bắt đầu bằng `docker compose down -v` hoặc rebuild tất cả. Hãy giữ evidence trước khi thay đổi state.

### 2.1. Tách triệu chứng và nguyên nhân

Ví dụ:

~~~text
Triệu chứng: API trả connection refused
Có thể là:
- DB chưa ready
- DB sai hostname/port
- API và DB khác network
- DB exited
- Credential sai
- Migration fail
- DB chỉ bind localhost
~~~

Một triệu chứng có nhiều nguyên nhân. Mỗi bước debug phải giảm số giả thuyết bằng evidence.

### 2.2. Ưu tiên trạng thái ít thay đổi

Thứ tự an toàn:

1. Xem trạng thái.
2. Đọc logs.
3. Inspect config/state.
4. Kiểm tra environment/network/volume.
5. Chạy command read-only hoặc command kiểm chứng.
6. Sửa một thay đổi nhỏ.
7. Rebuild/recreate service cần thiết.
8. Chỉ reset volume khi đã xác nhận data có thể xóa hoặc đã backup.

### 2.3. Ghi lại context

Trước khi kết luận, ghi:

- Compose files và profiles đang dùng.
- Project name.
- Git commit/config version.
- Service bị lỗi.
- Thời điểm bắt đầu.
- Lệnh đã chạy.
- Exit code/health/restart count.
- Log liên quan.
- Thay đổi gần nhất.

Debug tốt phải tái hiện và giải thích được, không chỉ làm container chạy lại một lần.

---

## 3. Quy trình debug chuẩn

Roadmap quy định 8 bước:

~~~text
1. docker compose ps
2. docker compose logs
3. docker inspect
4. Kiểm tra environment
5. Kiểm tra network và DNS
6. Kiểm tra volume
7. Chạy command trực tiếp trong container
8. Rebuild đúng service
~~~

Không phải incident nào cũng cần đủ 8 bước, nhưng không nên bỏ qua trạng thái/log/config để nhảy ngay vào sửa.

### 3.1. Bước 1: `docker compose ps`

~~~bash
docker compose ps
docker compose ps -a
docker compose ps --format json
~~~

Tìm:

- Service nào `Exited`.
- Service nào `Restarting`.
- Service nào `unhealthy`.
- Port mapping có đúng không.
- Container có được tạo từ project/file/profile đúng không.

Nếu dùng nhiều file:

~~~bash
docker compose -f compose.yaml -f compose.dev.yaml ps
~~~

Nếu dùng project name:

~~~bash
docker compose -p task-dev ps
~~~

Đừng debug project `task-dev` bằng command trỏ nhầm sang `task-prod-like`.

### 3.2. Bước 2: `docker compose logs`

~~~bash
docker compose logs --tail 100
docker compose logs --tail 200 api db redis
docker compose logs --since 15m api
docker compose logs -f api
~~~

Đọc logs theo timeline:

~~~text
DB start → DB init/migration → DB ready/fail
Redis start → Redis ready/fail
API start → config → connect dependency → migration → web server
~~~

Tìm các mẫu:

- `connection refused`: chưa có listener hoặc route/port sai.
- `could not translate host name`: DNS/service name/network.
- `authentication failed`: credential/config.
- `permission denied`: user/volume/filesystem.
- `address already in use`: process/port conflict.
- `OutOfMemoryError`/`OOMKilled`: memory budget.
- `relation/table does not exist`: migration/schema/version.

Logs là evidence nhưng có thể thiếu context hoặc chứa retry noise. Kết hợp inspect/metrics.

### 3.3. Bước 3: `docker inspect`

Xác định container name từ `ps`, rồi inspect:

~~~bash
docker inspect <container>
docker inspect --format 'status={{.State.Status}} exit={{.State.ExitCode}} error={{.State.Error}}' <container>
docker inspect --format 'restarts={{.RestartCount}} oom={{.State.OOMKilled}}' <container>
~~~

Các phần cần xem:

- Image ID/name.
- Entrypoint/Cmd.
- Environment names và non-secret values.
- Mounts.
- Networks/IP/aliases.
- Health state/log.
- Restart policy.
- Resource limits.
- User.

Không paste toàn bộ inspect ra nơi công khai nếu có secret trong environment.

### 3.4. Bước 4: Kiểm tra environment

Render Compose config:

~~~bash
docker compose config
~~~

Kiểm tra environment đã vào container, lọc key an toàn:

~~~bash
docker compose exec api sh -c 'env | grep -E "SPRING_PROFILES_ACTIVE|DB_HOST|DB_PORT|REDIS_HOST|SERVER_PORT"'
~~~

Nếu image không có shell:

~~~bash
docker inspect <container> --format '{{range .Config.Env}}{{println .}}{{end}}'
~~~

Tránh in password/token. Kiểm tra:

- Tên biến đúng với framework.
- Host là service name, không phải `localhost`.
- Port là container port, không phải host port.
- Profile/config file đúng.
- `.env`, `env_file`, inline `environment` và shell env không override ngoài dự kiến.

### 3.5. Bước 5: Network và DNS

~~~bash
docker network ls
docker network inspect <project>_default
docker inspect --format '{{json .NetworkSettings.Networks}}' <api-container>
docker inspect --format '{{json .NetworkSettings.Networks}}' <db-container>
~~~

Hai service cần gọi nhau phải cùng network phù hợp. Test DNS từ network:

~~~bash
docker compose exec api getent hosts db redis
~~~

Nếu API không có `getent`, chạy helper container:

~~~bash
docker run --rm --network <project>_default busybox nslookup db
docker run --rm --network <project>_default busybox nslookup redis
~~~

Test TCP/application:

~~~bash
docker run --rm --network <project>_default busybox nc -vz db 5432
docker run --rm --network <project>_default redis:7.4 redis-cli -h redis ping
~~~

DNS pass chưa nghĩa protocol/auth pass. Debug theo tầng:

~~~text
DNS → TCP → TLS/protocol → authentication → query/operation
~~~

### 3.6. Bước 6: Volume

~~~bash
docker compose config
docker inspect <container> --format '{{json .Mounts}}'
docker volume ls
docker volume inspect <volume>
~~~

Kiểm tra:

- Source/destination đúng.
- Read-only/writable mode.
- Volume có phải của đúng project không.
- UID/GID process có quyền không.
- Database data directory có đúng image/version không.
- Có lỡ dùng anonymous volume không.

Không xóa volume để “thử lại” trước khi biết data có cần giữ không.

### 3.7. Bước 7: Chạy command trực tiếp

`exec` vào container running:

~~~bash
docker compose exec api sh
docker compose exec db psql -U app -d appdb
docker compose exec redis redis-cli ping
~~~

Chạy one-off không phụ thuộc container đang running:

~~~bash
docker compose run --rm --no-deps api java -version
docker compose run --rm --no-deps api env
~~~

Kiểm tra:

- Java version/command.
- File/path.
- User/permission.
- DNS/TCP.
- Config parser.
- Migration command.

### 3.8. Bước 8: Rebuild đúng service

Nếu thay Dockerfile/source API:

~~~bash
docker compose build --no-cache api
docker compose up -d api
docker compose logs -f api
~~~

Nếu chỉ thay config Compose, có thể recreate:

~~~bash
docker compose up -d --force-recreate api
~~~

Nếu thay image tag:

~~~bash
docker compose pull api
docker compose up -d --no-deps api
~~~

Không rebuild toàn bộ stack nếu lỗi chỉ nằm ở API. Build rộng làm debug chậm và có thể thay đổi nhiều biến cùng lúc.

---

## 4. Startup race

### 4.1. Triệu chứng

- API thỉnh thoảng start fail nhưng start lại thì chạy.
- `connection refused` ở lần đầu.
- DB container `running` nhưng API không connect.
- Compose `up` trả về trước khi database init xong.

### 4.2. Nguyên nhân

Container lifecycle:

~~~text
DB container created/running
    ≠ PostgreSQL ready

API starts
    → connect too early
    → failure/retry/crash
~~~

Database có thể đang:

- Tạo data directory.
- Chạy init scripts.
- Apply migration.
- Tạo user/database.
- Replay WAL.
- Chờ disk/resource.

### 4.3. Kiểm tra

~~~bash
docker compose ps
docker compose logs db api
docker inspect --format '{{json .State.Health}}' <db-container>
docker compose config
~~~

### 4.4. Cách sửa

Ở Compose:

~~~yaml
services:
  api:
    depends_on:
      db:
        condition: service_healthy

  db:
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app -d appdb"]
      interval: 5s
      timeout: 5s
      retries: 12
      start_period: 15s
~~~

Ở application:

- Retry với exponential backoff.
- Giới hạn tổng thời gian retry.
- Reconnect khi dependency restart.
- Không retry credential/schema error vô hạn.

Không sửa bằng `sleep 10` cố định. Nó phụ thuộc máy và workload.

### 4.5. Phòng ngừa

- Healthcheck nhẹ và đúng.
- `service_healthy` cho local stack cần order.
- App retry/reconnect.
- Migration idempotent/controlled.
- Alert startup failure/restart loop.

---

## 5. DNS failure và network mismatch

### 5.1. Triệu chứng

~~~text
could not translate host name "db"
Temporary failure in name resolution
Name or service not known
~~~

### 5.2. Nguyên nhân thường gặp

- Gõ sai service name.
- API và DB ở hai network khác nhau.
- Đang debug nhầm project network.
- Container đã bị recreate/removed.
- External network chưa tồn tại hoặc tên sai.
- Dùng host name chỉ có trong môi trường khác.

### 5.3. Kiểm tra

~~~bash
docker compose config --services
docker compose ps
docker network ls
docker network inspect <project>_default
docker inspect <api-container>
docker inspect <db-container>
~~~

Test từ đúng network:

~~~bash
docker run --rm --network <project>_default busybox nslookup db
~~~

### 5.4. Sửa

Dùng service name trong config:

~~~yaml
services:
  api:
    environment:
      DB_HOST: db

  db:
    image: postgres:16
~~~

Không dùng container IP. Nếu service cần network custom, khai báo rõ:

~~~yaml
services:
  api:
    networks: [backend]
  db:
    networks: [backend]

networks:
  backend:
~~~

### 5.5. Negative test

Nếu thiết kế backend boundary, hãy chứng minh container frontend không resolve/connect database. Negative test giúp biết isolation thật sự đang có, không chỉ đọc YAML.

---

## 6. App dùng `localhost` thay vì service name

### Triệu chứng

API trong container cấu hình:

~~~text
jdbc:postgresql://localhost:5432/appdb
~~~

nhưng PostgreSQL nằm ở service `db`.

### Vì sao sai?

`localhost` bên trong API là network namespace của API. Database ở container khác:

~~~text
API container: localhost:5432 → API container
DB container:  db:5432       → PostgreSQL
~~~

### Kiểm tra

~~~bash
docker compose exec api sh -c 'env | grep -E "DB|DATASOURCE|REDIS"'
docker compose exec api getent hosts db
~~~

Nếu DNS `db` pass nhưng app vẫn dùng localhost, config precedence/variable mapping sai.

### Sửa

~~~yaml
services:
  api:
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/appdb
      SPRING_DATA_REDIS_HOST: redis
~~~

Host port chỉ dùng từ host:

~~~text
Host client → localhost:5433 → db:5432
API container → db:5432
~~~

---

## 7. Port host bị chiếm

### Triệu chứng

~~~text
Bind for 0.0.0.0:8080 failed: port is already allocated
address already in use
~~~

### Phân biệt hai loại conflict

1. Host port conflict: hai service cùng bind host port.
2. Container port/app conflict: nhiều process trong cùng container hoặc app listen sai.

### Kiểm tra Compose

~~~bash
docker compose ps
docker ps --format 'table {{.Names}}\t{{.Ports}}'
docker compose config
~~~

Trên host Linux:

~~~bash
ss -ltnp | grep ':8080'
~~~

Hoặc dùng tool tương đương trên OS.

### Sửa

Đổi host port:

~~~yaml
services:
  api:
    ports:
      - "8081:8080"
~~~

Service khác trong network vẫn gọi `api:8080`, không phải `api:8081`.

Nếu chỉ cần truy cập nội bộ, bỏ `ports` thay vì đổi sang port ngẫu nhiên. `expose`/`EXPOSE` không tạo host binding; service cùng network vẫn có thể gọi container port.

### Phòng ngừa

- Ghi rõ port ownership.
- Dùng project name/port matrix cho nhiều stack.
- Bind `127.0.0.1` nếu chỉ local.
- Không publish DB/Redis nếu không cần.

---

## 8. Volume permission

### Triệu chứng

~~~text
Permission denied
could not open file
read-only file system
initdb: error: could not change permissions
~~~

### Nguyên nhân

- UID/GID container không có quyền trên bind mount.
- Volume thuộc user khác từ lần chạy trước.
- Root filesystem read-only.
- Mount destination sai.
- SELinux/host security policy.
- Database image entrypoint cần quyền root/init.

### Kiểm tra

~~~bash
docker compose config
docker inspect <container> --format '{{json .Mounts}}'
docker inspect <container> --format '{{.Config.User}}'
docker compose logs db
docker volume inspect <volume>
~~~

Với bind mount:

~~~bash
ls -ldn ./data
find ./data -maxdepth 2 -printf '%u:%g %m %p\n'
~~~

Trong container:

~~~bash
docker compose exec api id
docker compose exec api sh -c 'ls -ldn /app /tmp'
~~~

### Sửa an toàn

- Dùng UID/GID đúng với image.
- Chown chính xác thư mục data, không `chmod 777` mặc định.
- Dùng named volume nếu phù hợp.
- Mount writable path tối thiểu.
- Kiểm tra read-only/tmpfs.
- Không xóa volume trước khi backup/approval.

### Database permission

PostgreSQL data directory cần ownership/permission phù hợp với user `postgres`. Không chạy database bằng root để né lỗi permission nếu chưa hiểu hậu quả.

---

## 9. Wrong environment variable

### Triệu chứng

- App connect sai host/port.
- Dùng profile sai.
- Password authentication failed.
- Feature đáng lẽ bật nhưng bị tắt.
- App dùng default local config trong container.

### Quy trình

1. Xem Compose rendered config.
2. Xem environment thực tế, lọc secret.
3. Xem application startup log/config report.
4. Đối chiếu tên biến framework yêu cầu.
5. Kiểm tra host/port theo network context.

~~~bash
docker compose config
docker compose exec api sh -c 'env | sort | grep -E "SPRING_|DB_|REDIS_|MAIL_"'
~~~

Nếu có secret, thay bằng check boolean/path:

~~~bash
docker compose exec api sh -c 'test -n "$DB_HOST" && echo DB_HOST_SET; test -n "$DB_PASSWORD" && echo DB_PASSWORD_SET'
~~~

### Các lỗi hay gặp

- `POSTGRES_DB` bị nhầm với JDBC database name.
- `POSTGRES_USER` chỉ dùng init lần đầu, không tự đổi user của volume cũ.
- `SPRING_DATASOURCE_URL` trỏ host port thay vì container port.
- Biến dùng dấu gạch dưới khác tên property dự kiến.
- `env_file` path tính sai.
- `.env` interpolation không giống `env_file` runtime.
- Compose override file đặt giá trị khác file base.

### Sửa và recreate

Environment thay đổi cần recreate container:

~~~bash
docker compose up -d --force-recreate api
~~~

Chỉ restart không phải lúc nào đọc lại config container đã tạo. Nếu image/config thay đổi, dùng đúng `up --build`/recreate.

---

## 10. Broken healthcheck

### Triệu chứng

- Container running nhưng `unhealthy`.
- `depends_on: service_healthy` khiến dependency chain không start.
- Healthcheck timeout/retry liên tục.

### Kiểm tra health log

~~~bash
docker inspect --format '{{json .State.Health}}' <container>
docker compose ps
docker compose logs <service>
~~~

Health log thường cho thấy command, exit code và output gần nhất.

### Chạy command healthcheck thủ công

Copy command từ Compose và chạy trong container:

~~~bash
docker compose exec api wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/actuator/health
~~~

Nếu không có `wget`, kiểm tra:

~~~bash
docker compose exec api sh -c 'command -v wget || command -v curl || true'
~~~

### Các nguyên nhân

- Binary không tồn tại.
- Endpoint path sai.
- App bind port khác.
- App bind `127.0.0.1`/interface không phù hợp.
- Auth yêu cầu nhưng healthcheck không có credential.
- Start period quá ngắn.
- Endpoint health phụ thuộc DB/migration chưa xong.
- Check quá nặng hoặc timeout quá ngắn.

### Sửa

Healthcheck phải:

- Nhẹ.
- Deterministic.
- Có timeout/retry hợp lý.
- Phản ánh signal cần dùng.
- Không leak secret.

Nếu healthcheck chỉ cần process sống, dùng check nhẹ; nếu cần readiness, endpoint phải phản ánh dependency quan trọng và được bảo vệ.

---

## 11. Container exit code

### Xem exit code

~~~bash
docker compose ps -a
docker inspect --format 'status={{.State.Status}} exit={{.State.ExitCode}} error={{.State.Error}}' <container>
~~~

Một số gợi ý:

- `0`: command kết thúc thành công; service có thể đã chạy xong ngoài ý muốn.
- `1`: lỗi application/config chung.
- `2`: command/argument/syntax thường sai.
- `126`: command tồn tại nhưng không execute được.
- `127`: command không tồn tại.
- `137`: thường SIGKILL/OOM/force kill.
- `143`: thường SIGTERM.

Đây là heuristic; đọc log và `OOMKilled` cùng với exit code.

### Container exit ngay vì command sai

~~~yaml
services:
  api:
    image: task-api:0.4.0
    command: ["java", "-jar", "missing.jar"]
~~~

Kiểm tra image `ENTRYPOINT/CMD`, path JAR và override Compose.

### Container exit 0 nhưng Compose service “không chạy”

Container one-off có thể exit thành công. Nếu service là server, process chính phải giữ running và listen port. Đừng thêm `sleep infinity` vào production để che command sai.

---

## 12. Dependency migration

### 12.1. Triệu chứng

- API fail với `relation/table does not exist`.
- Migration chạy hai lần gây conflict.
- DB healthy nhưng schema sai.
- Deploy version mới không tương thích schema cũ.
- Container migration exit nhưng API vẫn start.

### 12.2. Phân biệt DB ready và schema ready

~~~text
Database process ready
    ≠
Required schema/version ready
~~~

`pg_isready` chỉ kiểm tra accept connection. Migration là một concern khác.

### 12.3. Kiểm tra

~~~bash
docker compose logs db api migrate
docker compose exec db psql -U app -d appdb -c '\dt'
docker compose exec db psql -U app -d appdb -c 'SELECT version FROM schema_version;'
~~~

Command bảng/version phụ thuộc migration tool (Flyway/Liquibase/custom).

### 12.4. Quy trình sửa

- Xác định migration nào fail.
- Đọc error SQL và constraint.
- Kiểm tra database version/schema hiện tại.
- Không chạy migration thủ công ngẫu nhiên trên data cần giữ.
- Sửa migration/tool/config trong source.
- Rebuild/re-run migration ở database test.
- Backup trước thay đổi destructive.
- Kiểm tra backward/forward compatibility giữa app và schema.

Migration nên idempotent hoặc được migration tool quản lý version/lock. Không dùng `docker compose down -v` để “sửa” migration production.

### 12.5. Migration service riêng

Mô hình:

~~~yaml
services:
  migrate:
    image: task-api:0.4.0
    command: ["java", "-jar", "app.jar", "migrate"]
    depends_on:
      db:
        condition: service_healthy

  api:
    image: task-api:0.4.0
    depends_on:
      migrate:
        condition: service_completed_successfully
~~~

Cần kiểm tra Compose implementation hỗ trợ condition và migration command thực sự exit đúng code.

---

## 13. Log aggregation cơ bản

### 13.1. Log theo service

~~~bash
docker compose logs --tail 100 api db redis
docker compose logs --since 30m --timestamps api
~~~

Log Compose có thể trộn nhiều service. Dùng service prefix/time để dựng timeline.

### 13.2. Đọc log theo incident timeline

Ví dụ:

~~~text
10:00:00 db created
10:00:01 db init started
10:00:04 api started
10:00:04 api connection refused
10:00:08 db ready
10:00:20 api restarted
~~~

Kết luận ban đầu là startup race, nhưng vẫn kiểm tra health/config.

### 13.3. Structured log

App nên log JSON/field ổn định khi cần aggregate:

- Timestamp.
- Service.
- Level.
- Message.
- Trace/request ID.
- Dependency/operation.
- Error type.

Không log password, token, full connection string có credential hoặc dữ liệu nhạy cảm.

### 13.4. `docker logs` giới hạn gì?

`docker compose logs` đọc stdout/stderr của container. Nó không thay thế log retention/central aggregation. Production cần driver/collector, retention, access control và alert phù hợp.

---

## 14. Incident lab 1: Database khởi động chậm

### Tạo scenario

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
      start_period: 15s

  api:
    image: task-api:0.4.0
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/appdb
    depends_on:
      - db
~~~

### Điều tra

~~~bash
docker compose up -d
docker compose ps
docker compose logs --tail 100 db api
docker inspect --format '{{json .State.Health}}' <db-container>
~~~

### Sửa

Đổi `depends_on` sang `condition: service_healthy`, giữ retry trong app, rồi recreate API:

~~~bash
docker compose up -d --force-recreate api
docker compose logs -f api
~~~

---

## 15. Incident lab 2: Redis không resolve được

### Tạo scenario

Đặt API vào `frontend` network và Redis vào `backend` network mà không nối chung.

~~~yaml
services:
  api:
    image: task-api:0.4.0
    networks: [frontend]
    environment:
      REDIS_HOST: redis

  redis:
    image: redis:7.4
    networks: [backend]

networks:
  frontend:
  backend:
~~~

### Điều tra

~~~bash
docker compose ps
docker compose logs api
docker network inspect <project>_frontend
docker network inspect <project>_backend
docker compose exec api getent hosts redis
~~~

### Sửa

Nối API vào network backend hoặc khai báo cả hai service trong đúng network boundary:

~~~yaml
services:
  api:
    networks: [frontend, backend]
  redis:
    networks: [backend]
~~~

Recreate API và test protocol `redis-cli ping` từ helper container.

---

## 16. Incident lab 3: App dùng localhost

### Scenario

~~~yaml
services:
  api:
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://localhost:5432/appdb
  db:
    image: postgres:16
~~~

### Điều tra

~~~bash
docker compose logs api
docker compose exec api sh -c 'env | grep SPRING_DATASOURCE_URL'
docker compose exec api getent hosts db
~~~

DNS `db` có thể pass nhưng app dùng `localhost`. Sửa URL thành `jdbc:postgresql://db:5432/appdb`, recreate API và kiểm tra logs.

---

## 17. Incident lab 4: Host port bị chiếm

### Scenario

Khai báo hai service cùng:

~~~yaml
ports:
  - "8080:8080"
~~~

### Điều tra

~~~bash
docker compose up -d
docker compose ps -a
docker ps --format 'table {{.Names}}\t{{.Ports}}'
ss -ltnp | grep ':8080'
~~~

### Sửa

Chọn service cần public, bỏ `ports` khỏi service nội bộ hoặc đổi host port; giữ container port/service DNS không đổi.

---

## 18. Incident lab 5: Migration thất bại

### Scenario

Chạy migration với column/table đã tồn tại hoặc credential sai.

### Điều tra

~~~bash
docker compose ps -a
docker compose logs migrate db api
docker compose exec db psql -U app -d appdb -c '\dt'
~~~

### Sửa

- Xác định migration version và SQL lỗi.
- Không xóa volume nếu data cần giữ.
- Sửa source/migration/config.
- Chạy lại trên database test.
- Backup và plan rollback/forward migration.

---

## 19. Incident lab 6: Volume mount sai path/permission

### Scenario

Mount `./data:/var/lib/postgresql/wrong-path` hoặc chạy API non-root nhưng bind mount path không writable.

### Điều tra

~~~bash
docker compose config
docker inspect <container> --format '{{json .Mounts}}'
docker compose logs db api
docker compose exec api id
ls -ldn ./data
~~~

### Sửa

Sửa destination đúng theo image/app contract; chown UID/GID chính xác hoặc dùng named volume; không reset volume trước khi backup.

---

## 20. Rebuild và recreate đúng cách

### Chỉ thay source/Dockerfile API

~~~bash
docker compose build api
docker compose up -d api
docker compose logs -f api
~~~

### Muốn bỏ cache

~~~bash
docker compose build --no-cache api
docker compose up -d api
~~~

### Chỉ thay environment/Compose config

~~~bash
docker compose up -d --force-recreate api
~~~

### Pull image mới

~~~bash
docker compose pull api
docker compose up -d --no-deps api
~~~

### Khi nào dùng `down`?

Dùng khi cần teardown stack hoặc đổi project/config lớn. Không dùng như first response vì nó phá state/debug context và có thể đi kèm volume deletion nếu thêm `-v`.

### Xác minh sau fix

~~~bash
docker compose config
docker compose ps
docker compose logs --tail 100 api db redis
docker compose exec api <safe-check>
~~~

Fix chỉ hoàn tất khi symptom biến mất và không tạo regression ở dependency/data.

---

## 21. Quy trình incident hoàn chỉnh

### Bước 0: Giữ evidence

Không xóa/recreate ngay nếu cần logs/state. Ghi project/file/profile/commit.

### Bước 1: Xác định blast radius

Service nào fail? Host access hay chỉ internal call? Data có nguy cơ không?

### Bước 2: Kiểm tra trạng thái

~~~bash
docker compose ps -a
~~~

### Bước 3: Đọc logs theo timeline

~~~bash
docker compose logs --since 15m --timestamps
~~~

### Bước 4: Inspect container/health/restart

~~~bash
docker inspect <container>
~~~

### Bước 5: Render config/environment

~~~bash
docker compose config
docker compose exec <service> env
~~~

Lọc secret.

### Bước 6: Kiểm tra network/DNS/port

~~~bash
docker network inspect <network>
docker port <container>
~~~

### Bước 7: Kiểm tra volume/permission/data

~~~bash
docker inspect <container> --format '{{json .Mounts}}'
docker volume inspect <volume>
~~~

### Bước 8: Chạy command trực tiếp

Dùng `exec`/`run --rm --no-deps` để phân biệt app config với Docker runtime.

### Bước 9: Sửa một biến một lần

Tránh đổi image, network, env, volume và command cùng lúc.

### Bước 10: Rebuild/recreate đúng service

Xác định cần build, pull, recreate hay chỉ restart.

### Bước 11: Verify và ghi lại

Test positive/negative path, health, logs, data và restart behavior.

### Bước 12: Postmortem ngắn

Ghi:

- Impact.
- Root cause.
- Contributing factors.
- Detection.
- Fix.
- Prevention.
- Follow-up owner.

---

## 22. Bài tập tự làm

### Bài 1: Startup race

Làm DB healthcheck fail trong vài lần đầu, kiểm tra API behavior, thêm `service_healthy` và retry. Ghi rõ đâu là fix Compose, đâu là fix application.

### Bài 2: DNS failure

Đặt API/Redis khác network, dùng quy trình 8 bước để tìm lỗi và sửa. Chứng minh DNS pass sau fix.

### Bài 3: Localhost bug

Cho API dùng `localhost` thay vì service name, đọc log và environment, sửa thành `db`/`redis`.

### Bài 4: Port conflict

Chiếm host port bằng container test, chạy Compose và điều tra. Sửa bằng bỏ publish hoặc đổi host port.

### Bài 5: Volume permission

Mount bind directory bằng UID không có quyền, quan sát lỗi, sửa ownership least privilege. Không dùng `chmod 777`.

### Bài 6: Broken healthcheck

Gọi binary không tồn tại hoặc endpoint sai, xem health log, chạy command thủ công và sửa.

### Bài 7: Migration failure

Cố ý làm migration fail, kiểm tra schema/log/exit code, sửa migration trên database test và verify restore/rollback plan.

### Bài 8: Log timeline

Gom logs của API/DB/Redis theo timestamp để xác định startup race hoặc dependency failure. Viết postmortem 5 dòng.

---

## 23. Câu hỏi kiểm tra và đáp án gợi ý

1. Vì sao `docker compose ps` phải là bước đầu?
2. `running` khác `healthy` thế nào?
3. Làm sao phân biệt DNS failure với TCP connection failure?
4. Vì sao `localhost` trong API không trỏ tới DB service?
5. `depends_on` short syntax giải quyết được gì và không giải quyết gì?
6. Khi nào dùng `--force-recreate`, khi nào dùng `build`?
7. Vì sao không nên dùng `down -v` để xử lý mọi lỗi?
8. Cần kiểm tra gì khi volume permission fail?
9. Exit code `137`/`143` gợi ý gì?
10. Vì sao DB ready chưa đồng nghĩa schema ready?
11. Healthcheck broken có thể làm dependency chain như thế nào?
12. Vì sao phải debug đúng project name/Compose files?

Đáp án ngắn: `ps` cho blast radius/state; healthy là command pass; DNS kiểm tra resolve còn TCP kiểm tra connect; localhost là namespace hiện tại; short depends_on chỉ order; force recreate áp dụng config mới còn build tạo image mới; `down -v` có thể xóa data; kiểm tra mount/UID/GID/path; 137 thường SIGKILL/OOM, 143 thường SIGTERM; DB accept connection chưa có schema; broken healthcheck có thể ngăn service phụ thuộc start; project/config sai dẫn tới debug nhầm resource.

---

## 24. Tiêu chí hoàn thành tuần 12

- [ ] Thực hiện được quy trình 8 bước debug Compose.
- [ ] Đọc `ps`, logs, inspect và exit code để xác định service lỗi.
- [ ] Debug được startup race bằng healthcheck/condition/retry.
- [ ] Debug được DNS failure và network membership.
- [ ] Sửa được lỗi app dùng `localhost` thay service name.
- [ ] Xử lý được host port conflict.
- [ ] Điều tra được volume mount/permission/path.
- [ ] Kiểm tra environment sau interpolation mà không leak secret.
- [ ] Debug được broken healthcheck.
- [ ] Phân tích được migration failure mà không xóa data tùy tiện.
- [ ] Dùng `exec`, `run --rm --no-deps`, rebuild và recreate đúng service.
- [ ] Ghi được timeline/root cause/postmortem ngắn.
- [ ] Biết khi nào không được dùng `down -v`, `volume prune` hoặc cleanup diện rộng.

Kiểm tra cuối buổi:

~~~bash
docker compose ps -a
docker compose logs --tail 100
docker compose config
docker compose config --services
docker network ls
docker volume ls
~~~

Mục tiêu của tuần này là biến việc debug từ thử-sai thành một quy trình có evidence, giảm blast radius và bảo vệ dữ liệu trong lúc sửa sự cố.
