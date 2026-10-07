# Tuần 10: Compose cơ bản

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Giải thích Compose dùng để mô tả và chạy một application stack nhiều service.
- Viết `compose.yaml` cho Spring Boot API, PostgreSQL, Redis và Mailpit.
- Phân biệt `image` với `build` trong service.
- Cấu hình ports, environment, `env_file`, volumes và networks.
- Hiểu service name và cách container gọi nhau trong Compose network.
- Biết giới hạn của `depends_on` ở mức Compose cơ bản.
- Override `command` và `entrypoint` đúng mục đích.
- Dùng profiles cho service tùy chọn như Mailpit hoặc admin tool.
- Đặt project name để tránh xung đột giữa nhiều stack.
- Dùng `config`, `up`, `ps`, `logs`, `exec`, `run` và `down` để vận hành stack.
- Tránh xóa nhầm volume hoặc nhầm project khi làm việc với Compose.

Thời lượng đề xuất: **8–12 giờ** gồm đọc file, chạy stack và debug service.

---

## 2. Compose là gì?

Docker Compose là cách khai báo nhiều container/service, network, volume và cấu hình liên quan trong một file YAML. Thay vì chạy nhiều lệnh `docker run` thủ công, bạn mô tả stack rồi dùng Compose để tạo và quản lý các resource đó.

~~~text
compose.yaml
    ↓ docker compose up
Project
├── api container
├── db container
├── redis container
├── mailpit container
├── networks
└── volumes
~~~

Compose phù hợp cho:

- Local development.
- Integration test stack.
- Demo/reproducible environment.
- Application stack nhỏ đến vừa.
- Mô phỏng nhiều service trên một máy.

Compose không tự động là Kubernetes/ECS production orchestrator. Tuần này tập trung vào model và CLI cơ bản; healthcheck, restart policy, resource limit, secrets và production-like configuration sẽ học ở Tuần 11.

### 2.1. `compose.yaml` và `docker-compose.yml`

Tên khuyến nghị hiện nay là:

~~~text
compose.yaml
~~~

Docker cũng hỗ trợ một số tên tương thích như `compose.yml`, `docker-compose.yaml` và `docker-compose.yml` tùy version/config. Trong bài dùng `compose.yaml`.

Không cần thêm top-level `version:` cho Compose Specification hiện đại. Nếu project cũ có `version`, hãy kiểm tra warning và syntax theo Docker Compose đang dùng.

Kiểm tra:

~~~bash
docker compose version
~~~

---

## 3. Mental model: project, service và container

Một Compose project có:

- **Service**: định nghĩa template/chính sách để chạy container.
- **Container**: instance thực tế của service.
- **Project**: nhóm service, network và volume có chung project name.
- **Network**: đường kết nối giữa service.
- **Volume**: storage được khai báo/quản lý trong project.

Nếu service tên `api` được scale thành nhiều container, `api` vẫn là service identity; container name cụ thể có thể có hậu tố project/index.

Mặc định Compose tạo network dạng:

~~~text
<project-name>_default
~~~

Các service trong cùng project có thể gọi nhau bằng service name:

~~~text
api → postgres:5432
api → redis:6379
api → mailpit:1025
~~~

Không dùng `localhost` để gọi service khác trong Compose. `localhost` bên trong API là chính container API.

---

## 4. Compose file tối thiểu

Ví dụ service Nginx:

~~~yaml
services:
  web:
    image: nginx:1.27
    ports:
      - "8080:80"
~~~

Chạy:

~~~bash
docker compose up -d
docker compose ps
curl http://localhost:8080
docker compose down
~~~

Compose sẽ tạo project network, pull image nếu cần, tạo container và attach service vào network.

### 4.1. `services` là phần bắt buộc chính

~~~yaml
services:
  api:
    image: task-api:0.3.0

  redis:
    image: redis:7.4
~~~

Mỗi key dưới `services` là một service name. Service name được dùng trong DNS nội bộ và command Compose:

~~~bash
docker compose logs api
docker compose exec redis redis-cli ping
~~~

### 4.2. YAML indentation

YAML phụ thuộc indentation. Dùng spaces, không dùng tab:

~~~yaml
services:
  api:
    image: task-api:0.3.0
    ports:
      - "8080:8080"
~~~

Lỗi indentation thường làm Compose không parse hoặc hiểu sai kiểu dữ liệu. Luôn chạy:

~~~bash
docker compose config
~~~

trước khi debug runtime.

---

## 5. `image` và `build`

### 5.1. Dùng image có sẵn

~~~yaml
services:
  redis:
    image: redis:7.4
~~~

Compose pull image nếu chưa có local. Tag nên pin rõ theo policy; tránh `latest` trong workflow cần reproducibility.

### 5.2. Build image từ Dockerfile

~~~yaml
services:
  api:
    build:
      context: .
      dockerfile: Dockerfile
    image: task-api:dev
~~~

- `context`: build context.
- `dockerfile`: path Dockerfile tương đối với context hoặc project config phù hợp.
- `image`: tên/tag image được tạo.

Chạy build và up:

~~~bash
docker compose build api
docker compose up -d api
~~~

Hoặc:

~~~bash
docker compose up -d --build api
~~~

### 5.3. `build` không có nghĩa auto rebuild mọi lúc

`docker compose up` có thể dùng image hiện có. Khi source/Dockerfile thay đổi, dùng `--build` hoặc build explicit:

~~~bash
docker compose up -d --build
~~~

`--no-cache` là option của build flow, không phải mặc định cho mọi lần chạy. Có thể dùng:

~~~bash
docker compose build --no-cache api
~~~

### 5.4. Context và `.dockerignore`

`build.context` quyết định file Dockerfile được phép `COPY`. `.dockerignore` phải nằm ở root context phù hợp. Nếu `context: .`, `.dockerignore` ở root project; nếu context là `./api`, ignore file cần nằm trong `api/` theo workflow builder.

---

## 6. `ports`

Publish port ra host:

~~~yaml
services:
  api:
    image: task-api:0.3.0
    ports:
      - "8080:8080"
~~~

Format:

~~~text
HOST_PORT:CONTAINER_PORT
~~~

Host dùng `http://localhost:8080`. Service khác trong Compose không cần dùng host port; chúng gọi:

~~~text
http://api:8080
~~~

### 6.1. Bind host interface

Chỉ mở local host:

~~~yaml
services:
  api:
    ports:
      - "127.0.0.1:8080:8080"
~~~

Điều này hữu ích khi service local không nên listen trên mọi interface host.

### 6.2. Database có cần ports không?

Nếu chỉ API trong stack truy cập PostgreSQL:

~~~yaml
services:
  db:
    image: postgres:16
    # Không cần ports nếu host không cần truy cập trực tiếp
~~~

API dùng `db:5432`. Chỉ thêm:

~~~yaml
ports:
  - "5433:5432"
~~~

nếu developer cần dùng psql/GUI trên host. Không publish port chỉ vì Dockerfile có `EXPOSE`.

### 6.3. Kiểm tra ports

~~~bash
docker compose ps
docker compose port api 8080
~~~

---

## 7. `environment` và `env_file`

### 7.1. Inline `environment`

~~~yaml
services:
  api:
    image: task-api:0.3.0
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/appdb
      SPRING_DATASOURCE_USERNAME: app
      SPRING_PROFILES_ACTIVE: docker
      REDIS_HOST: redis
~~~

Environment trong service được truyền vào container runtime. Đây là config, không phải build-time Dockerfile `ARG`.

### 7.2. List syntax

~~~yaml
services:
  api:
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - REDIS_HOST=redis
~~~

Map syntax thường dễ đọc và giảm lỗi quote/type:

~~~yaml
environment:
  REDIS_PORT: "6379"
  FEATURE_ENABLED: "false"
~~~

Một số application parser phân biệt string với boolean; quote khi cần giá trị text chính xác.

### 7.3. `env_file`

Tạo file local, ví dụ `.env.docker`:

~~~dotenv
SPRING_PROFILES_ACTIVE=docker
SPRING_DATASOURCE_USERNAME=app
SPRING_DATASOURCE_PASSWORD=local-only
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/appdb
REDIS_HOST=redis
~~~

Compose:

~~~yaml
services:
  api:
    env_file:
      - .env.docker
~~~

`env_file` inject biến vào service container. Không commit file chứa production secret. Dùng `.env.example` để document tên biến:

~~~dotenv
SPRING_DATASOURCE_USERNAME=app
SPRING_DATASOURCE_PASSWORD=change-me-locally
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/appdb
~~~

### 7.4. `.env` interpolation khác `env_file`

Có hai khái niệm dễ nhầm:

1. `.env` cạnh Compose file thường cung cấp biến để Compose interpolate `${VAR}`.
2. `env_file` khai báo biến được truyền vào container service.

Ví dụ:

~~~yaml
services:
  api:
    image: ${API_IMAGE:-task-api:dev}
    environment:
      APP_ENV: ${APP_ENV:-local}
    env_file:
      - .env.docker
~~~

Kiểm tra config sau interpolation:

~~~bash
docker compose config
~~~

### 7.5. Precedence cần kiểm tra

Nguồn config có thể gồm shell environment, `.env`, `--env-file`, `env_file`, inline `environment` và image defaults. Precedence cụ thể phụ thuộc cách khai báo/Compose version. Không đoán khi debug; dùng:

~~~bash
docker compose config
docker compose run --rm api env | sort
~~~

Không in toàn bộ environment nếu có secret trong output hoặc log CI.

### 7.6. Secret không nên để plain text tùy tiện

Tuần này dùng password local để học. Production-like secret injection sẽ học ở Tuần 11. Tối thiểu:

- Không commit `.env.docker` có password thật.
- Không paste secret vào `compose.yaml` committed.
- Không đưa secret vào image build args/layers.
- Mask secret trong CI logs.

---

## 8. `volumes`

Named volume trong Compose:

~~~yaml
services:
  db:
    image: postgres:16
    volumes:
      - pgdata:/var/lib/postgresql/data

volumes:
  pgdata:
~~~

`pgdata` là volume được Compose project quản lý. Tên vật lý thường có project prefix:

~~~text
<project>_pgdata
~~~

Kiểm tra:

~~~bash
docker compose config
docker compose ps
docker volume ls
docker volume inspect <project>_pgdata
~~~

### 8.1. Bind mount trong development

~~~yaml
services:
  api:
    volumes:
      - ./config:/app/config:ro
~~~

`./config` tính từ project/Compose file context theo Compose behavior. Bind mount tiện local nhưng phụ thuộc host path và permission.

### 8.2. Anonymous volume

~~~yaml
services:
  db:
    volumes:
      - /var/lib/postgresql/data
~~~

Dùng được nhưng khó quản lý tên/backup. Database nên dùng named volume rõ ràng.

### 8.3. Không xóa volume vô tình

~~~bash
docker compose down
~~~

khác:

~~~bash
docker compose down -v
~~~

`down -v` có thể xóa named/anonymous volumes của project. Chỉ dùng khi muốn reset dữ liệu lab và đã xác nhận volume có thể xóa.

---

## 9. `networks`

Mặc định Compose tạo một network cho project. Có thể khai báo network explicit:

~~~yaml
services:
  api:
    networks:
      - frontend
      - backend

  db:
    networks:
      - backend

networks:
  frontend:
  backend:
    internal: true
~~~

Service `api` có thể gọi `db:5432` vì cùng `backend`. Database không nằm trong `frontend`.

### 9.1. Service name là hostname

Trong Compose stack:

~~~yaml
services:
  api:
    environment:
      DB_HOST: db
      REDIS_HOST: redis

  db:
    image: postgres:16

  redis:
    image: redis:7.4
~~~

Không dùng `localhost` cho `DB_HOST` hoặc `REDIS_HOST` trong API container.

### 9.2. External network

Có thể nối service vào network đã tạo ngoài project:

~~~yaml
networks:
  shared-net:
    external: true
~~~

Network phải tồn tại trước:

~~~bash
docker network create shared-net
docker compose up -d
~~~

External network làm giảm isolation giữa project; dùng khi có lý do rõ như reverse proxy chung. Không dùng tùy tiện.

---

## 10. `depends_on`

Cấu hình cơ bản:

~~~yaml
services:
  api:
    depends_on:
      - db
      - redis

  db:
    image: postgres:16

  redis:
    image: redis:7.4
~~~

Ở mức cơ bản, `depends_on` giúp Compose biết thứ tự start/stop. Nó **không bảo đảm database/Redis đã ready để nhận connection**.

Có thể xảy ra:

~~~text
Compose start db
→ container db running
→ Compose start api
→ PostgreSQL còn init
→ API connection fail
~~~

Cách xử lý ở cấp application:

- Retry/backoff khi connect.
- Migration retry có giới hạn.
- Health/readiness ở Compose (học Tuần 11).
- Startup script chỉ khi thật sự cần và có timeout.

Không dùng `sleep 10` như readiness guarantee. Máy chậm hoặc database migration lâu hơn sẽ làm cách này không ổn định.

### 10.1. Stop order

Compose thường dừng service theo dependency order ngược lại. Ứng dụng vẫn cần shutdown graceful và xử lý connection pool; order không thay thế cleanup đúng cách.

---

## 11. `command` và `entrypoint`

### 11.1. Override command

~~~yaml
services:
  worker:
    image: task-api:0.3.0
    command: ["java", "-jar", "app.jar", "--worker"]
~~~

`command` thường override default `CMD` của image, nhưng không nhất thiết override `ENTRYPOINT`.

### 11.2. Override entrypoint

~~~yaml
services:
  debug:
    image: task-api:0.3.0
    entrypoint: ["sh", "-c"]
    command: ["id; ls -la /app; exec java -jar app.jar"]
~~~

Dùng cho debug/test, không nên tùy tiện override production process.

### 11.3. Exec form

Ưu tiên list syntax cho process chính:

~~~yaml
command: ["java", "-jar", "app.jar"]
~~~

String syntax có thể được xử lý khác tùy image/shell:

~~~yaml
command: java -jar app.jar
~~~

Nếu cần shell variable expansion, hiểu rõ signal/PID 1 và dùng `exec` trong wrapper:

~~~yaml
command: ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
~~~

Không đưa secret vào command vì command có thể quan sát qua inspect/process metadata.

---

## 12. Profiles

Profiles dùng cho service tùy chọn:

~~~yaml
services:
  api:
    image: task-api:0.3.0

  db:
    image: postgres:16

  mailpit:
    image: axllent/mailpit:v1.21
    profiles:
      - tools
    ports:
      - "8025:8025"
      - "1025:1025"
~~~

Mặc định `mailpit` không được start nếu profile không active. Bật profile:

~~~bash
docker compose --profile tools up -d
~~~

Liệt kê config sau profile:

~~~bash
docker compose --profile tools config
~~~

Profiles phù hợp cho:

- Mailpit/MailHog.
- Admin UI.
- Debug tools.
- Load test worker.
- Optional monitoring local.

Không dùng profile để che giấu dependency bắt buộc của API. Nếu API cần Redis để chạy, Redis không nên bị đặt trong profile khiến stack mặc định thiếu service.

---

## 13. Project name

Compose project name ảnh hưởng tên container, network và volume. Ví dụ:

~~~text
learning_default
learning_pgdata
~~~

Đặt project name qua:

~~~bash
docker compose -p learning up -d
~~~

Hoặc biến môi trường:

~~~bash
COMPOSE_PROJECT_NAME=learning docker compose up -d
~~~

Hoặc `name` trong Compose file tùy Compose Specification/version:

~~~yaml
name: learning

services:
  api:
    image: task-api:0.3.0
~~~

Dùng project name khác nhau để chạy nhiều branch/stack cùng lúc, nhưng lưu ý host port không thể trùng.

Kiểm tra service/container thuộc project nào:

~~~bash
docker compose ls
docker compose -p learning ps
~~~

Khi cleanup, luôn dùng đúng `-p`/Compose file. Không chạy `down` ở thư mục nhầm.

---

## 14. CLI vận hành cơ bản

### 14.1. Validate/render config

~~~bash
docker compose config
docker compose config --services
docker compose config --profiles
~~~

Đây là bước đầu tiên khi gặp lỗi YAML, variable interpolation, profile hoặc merge config.

### 14.2. Start/up

~~~bash
docker compose up
docker compose up -d
docker compose up -d --build
docker compose up -d api db
~~~

- Không `-d`: foreground, thấy logs trực tiếp.
- `-d`: detached.
- `--build`: build image trước khi start.
- Service list: chỉ start phần được chọn và dependency liên quan theo config.

### 14.3. Status

~~~bash
docker compose ps
docker compose ps -a
docker compose images
~~~

### 14.4. Logs

~~~bash
docker compose logs
docker compose logs -f api
docker compose logs --tail 100 db redis
docker compose logs --since 10m
~~~

Không paste log chứa password/token vào issue/chat.

### 14.5. Exec và run

`exec` vào container đang running:

~~~bash
docker compose exec api sh
docker compose exec db psql -U postgres
docker compose exec redis redis-cli ping
~~~

`run` tạo container one-off từ service config, thường dùng debug/migration:

~~~bash
docker compose run --rm api java -version
docker compose run --rm --no-deps api sh
~~~

`run` có thể tạo container khác tên và không giống hoàn toàn behavior của service đang chạy. Dùng `--no-deps` nếu không muốn start dependency.

### 14.6. Stop/down

~~~bash
docker compose stop
docker compose down
docker compose down --remove-orphans
~~~

- `stop`: dừng container, giữ resource để start lại.
- `down`: dừng và remove container/network do project tạo.
- `--remove-orphans`: remove container cùng project label nhưng không còn trong file hiện tại; kiểm tra trước.
- `down -v`: thêm xóa volume, destructive với database data.

---

## 15. Project mẫu: API + PostgreSQL + Redis + Mailpit

Tạo `compose.yaml`:

~~~yaml
name: task-learning

services:
  api:
    build:
      context: .
      dockerfile: Dockerfile
    image: task-api:dev
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/taskdb
      SPRING_DATASOURCE_USERNAME: task
      SPRING_DATASOURCE_PASSWORD: local-only
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: "6379"
      MAIL_HOST: mailpit
      MAIL_PORT: "1025"
    ports:
      - "8080:8080"
    depends_on:
      - db
      - redis
      - mailpit
    networks:
      - app-net

  db:
    image: postgres:16
    environment:
      POSTGRES_DB: taskdb
      POSTGRES_USER: task
      POSTGRES_PASSWORD: local-only
    volumes:
      - pgdata:/var/lib/postgresql/data
    networks:
      - app-net
    ports:
      - "5433:5432"

  redis:
    image: redis:7.4
    networks:
      - app-net
    ports:
      - "6380:6379"

  mailpit:
    image: axllent/mailpit:v1.21
    profiles:
      - tools
    ports:
      - "8025:8025"
      - "1025:1025"
    networks:
      - app-net

volumes:
  pgdata:

networks:
  app-net:
~~~

Chạy core services:

~~~bash
docker compose up -d --build api db redis
~~~

Chạy thêm Mailpit:

~~~bash
docker compose --profile tools up -d mailpit
~~~

Kiểm tra:

~~~bash
docker compose ps
docker compose logs -f api
docker compose exec db psql -U task -d taskdb
docker compose exec redis redis-cli ping
~~~

Truy cập:

- API: `http://localhost:8080`.
- PostgreSQL từ host: `localhost:5433`.
- Redis từ host: `localhost:6380`.
- Mailpit UI: `http://localhost:8025`.

Bên trong Compose, API dùng `db:5432`, `redis:6379`, `mailpit:1025`, không dùng host port và không dùng `localhost`.

### 15.1. Ghi chú về Mailpit profile

Nếu API luôn cần Mailpit để start nhưng `mailpit` ở profile `tools`, dependency behavior có thể không như mong đợi khi chỉ chạy core services. Trong local project, chọn một trong hai:

- Mailpit là service mặc định, bỏ profile.
- API có fallback/no-op mail sender khi profile tools không active.
- Bật profile trong command chạy API.

Profile phải phản ánh dependency thật, không chỉ mục tiêu “ẩn service cho gọn”.

### 15.2. Dừng stack

~~~bash
docker compose down
~~~

Volume `pgdata` thường còn. Nếu muốn reset database lab:

~~~bash
docker compose down -v
~~~

Chỉ chạy sau khi xác nhận dữ liệu có thể xóa.

---

## 16. Compose config và environment debugging

### 16.1. Xem config đã resolve

~~~bash
docker compose config
~~~

Kiểm tra:

- Image/build path.
- Ports.
- Environment sau interpolation.
- Network/volume.
- Profile.
- Dependency.

Không gửi nguyên output vào public issue nếu có secret.

### 16.2. Xem environment trong container

~~~bash
docker compose exec api env | sort
~~~

Chỉ dùng trong local hoặc filter key không nhạy cảm:

~~~bash
docker compose exec api sh -c 'env | grep -E "SPRING_PROFILES_ACTIVE|REDIS_HOST|MAIL_HOST"'
~~~

### 16.3. So sánh service DNS

~~~bash
docker compose exec api getent hosts db redis mailpit
~~~

Image tối giản có thể không có `getent`; dùng tool debug phù hợp hoặc `docker run --rm --network <project>_app-net busybox nslookup db`.

### 16.4. Inspect labels/project

~~~bash
docker inspect task-learning-api-1
docker network ls
docker volume ls
~~~

Tên container có thể khác theo Compose version/scale. Dùng `docker compose ps` làm nguồn định vị service.

---

## 17. Lỗi thường gặp

### `docker compose` không tìm thấy file

Chạy từ thư mục chứa `compose.yaml` hoặc chỉ rõ file:

~~~bash
docker compose -f path/to/compose.yaml config
~~~

### Port is already allocated

Đổi host port hoặc dừng workload đang dùng port. Container port không nhất thiết phải đổi:

~~~yaml
ports:
  - "8081:8080"
~~~

### API dùng `localhost` để gọi DB

Đổi hostname thành service name:

~~~text
jdbc:postgresql://db:5432/taskdb
~~~

### `depends_on` nhưng API vẫn connection refused

Service đã start container nhưng DB chưa ready. Dùng retry trong app và healthcheck/condition ở Tuần 11.

### Biến environment không có giá trị mong đợi

Chạy `docker compose config`, kiểm tra `.env`, `--env-file`, `env_file`, inline `environment` và shell environment. Đừng đoán precedence.

### `docker compose exec` báo service không running

~~~bash
docker compose ps -a
docker compose logs <service>
~~~

Sửa startup error trước khi exec.

### Volume data “mất” sau down

Kiểm tra có dùng `down -v`, volume name/project name khác hoặc mount path sai không. `docker volume ls` và `docker volume inspect` giúp xác định.

### Service profile không start

Bật profile:

~~~bash
docker compose --profile tools up -d
~~~

Hoặc kiểm tra `docker compose config --profiles`.

### Build API dùng Dockerfile/context sai

Kiểm tra `build.context`, `dockerfile`, `.dockerignore` và chạy:

~~~bash
docker compose config
~~~

### `command` override làm mất command app

Inspect image `ENTRYPOINT`/`CMD`, rồi hiểu Compose `command` đang override phần nào. Dùng list syntax và test với `docker compose run --rm`.

---

## 18. Bài tập tự làm

### Bài 1: Stack cơ bản

Tạo Compose project gồm Nginx, Redis và PostgreSQL. Chạy bằng `up -d`, xem `ps`, logs và dừng bằng `down`.

### Bài 2: Spring Boot stack

Thêm API image/build vào stack. Cấu hình API dùng `db`, `redis` và `mailpit` bằng service name. Chỉ publish API, không publish DB/Redis nếu host không cần truy cập.

### Bài 3: Volume persistence

Tạo named volume cho PostgreSQL, tạo table, `docker compose down`, `up -d` lại và xác nhận data còn. Sau đó chỉ trong project lab riêng thử `down -v` và ghi nhận data bị reset.

### Bài 4: Profile Mailpit

Đưa Mailpit vào profile `tools`, chạy core stack không profile rồi bật profile. Kiểm tra API behavior nếu email dependency không có.

### Bài 5: Network boundary

Tạo `frontend` và `backend` networks. API nối cả hai; DB/Redis chỉ backend. Dùng `docker compose exec` hoặc helper container test DNS/connectivity.

### Bài 6: Environment debugging

Đưa config local vào `.env.docker`, dùng `${VAR}` và `env_file`, chạy `docker compose config`, rồi kiểm tra environment đã vào container chưa. Không commit password thật.

### Bài 7: Project name

Chạy cùng Compose file với hai project name khác nhau. Quan sát network/volume/container names và xử lý host port conflict.

---

## 19. Câu hỏi kiểm tra và đáp án gợi ý

1. Compose project, service và container khác nhau thế nào?
2. `image` và `build` khác nhau thế nào?
3. Service trong cùng Compose network gọi nhau bằng hostname nào?
4. Container-to-container traffic có cần `ports` không?
5. `EXPOSE` khác `ports` thế nào?
6. `env_file` khác `.env` interpolation thế nào?
7. `depends_on` có bảo đảm database ready không?
8. `command` và `entrypoint` override phần nào?
9. Profile dùng cho mục đích gì?
10. `docker compose down` khác `down -v` thế nào?
11. Vì sao project name quan trọng khi chạy nhiều stack?
12. Vì sao cần chạy `docker compose config` trước khi debug?

Đáp án ngắn: project là nhóm resource, service là định nghĩa, container là instance; `image` dùng image có sẵn còn `build` tạo image; service name là hostname; cùng network không cần host publish; `EXPOSE` chỉ metadata còn `ports` publish; `.env` thường hỗ trợ interpolation còn `env_file` truyền biến vào service; `depends_on` không bảo đảm readiness; `command` thường override CMD còn entrypoint override executable; profile bật service tùy chọn; `down -v` có thể xóa volume; project name tránh collision; `config` render config cuối sau interpolation/merge.

---

## 20. Tiêu chí hoàn thành tuần 10

- [ ] Viết được `compose.yaml` có nhiều services.
- [ ] Phân biệt được `image` và `build`.
- [ ] Cấu hình được ports, environment, `env_file`, volumes và networks.
- [ ] Cho service gọi nhau bằng service name.
- [ ] Hiểu `depends_on` cơ bản không phải readiness guarantee.
- [ ] Dùng được command/entrypoint override.
- [ ] Dùng profiles cho service tùy chọn.
- [ ] Đặt và kiểm tra project name.
- [ ] Dùng được `config`, `up`, `ps`, `logs`, `exec`, `run`, `stop` và `down`.
- [ ] Chạy được stack API + PostgreSQL + Redis + Mailpit.
- [ ] Biết `down -v` có thể xóa database data.
- [ ] Không commit secret trong Compose file hoặc env file.

Kiểm tra cuối buổi:

~~~bash
docker compose config
docker compose ps
docker compose logs --tail 50
docker compose exec db psql -U postgres
docker compose down
~~~

Mục tiêu của tuần này là biến một stack nhiều container thành một project có cấu hình rõ, network/service discovery đúng và lifecycle có thể lặp lại.
