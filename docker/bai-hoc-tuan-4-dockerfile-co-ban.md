# Tuần 4: Dockerfile cơ bản

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Giải thích Dockerfile là gì và Docker build xử lý từng instruction ra sao.
- Sử dụng đúng `FROM`, `WORKDIR`, `COPY`, `ADD`, `RUN`, `ENV`, `ARG`, `EXPOSE`, `USER`, `ENTRYPOINT`, `CMD`, `SHELL`, `LABEL` và `HEALTHCHECK`.
- Phân biệt build-time configuration với runtime configuration.
- Phân biệt `ENTRYPOINT` và `CMD`, shell form và exec form.
- Viết Dockerfile chạy JAR của ứng dụng Java/Spring Boot.
- Build, chạy, inspect và debug image đã build.
- Chạy ứng dụng dưới user non-root.
- Nhận biết các lỗi phổ biến về path, permission, port, command và healthcheck.

Thời lượng đề xuất: **8–12 giờ** gồm lý thuyết, lab và bài tập.

---

## 2. Dockerfile là gì?

Dockerfile là file text mô tả cách tạo image. Mỗi instruction mô tả một bước trong quá trình build hoặc metadata runtime của image.

~~~text
Dockerfile + build context
          ↓ docker build
      Image gồm layers + metadata
          ↓ docker run
      Container + process
~~~

Ví dụ nhỏ nhất:

~~~dockerfile
FROM nginx:1.27
COPY index.html /usr/share/nginx/html/index.html
~~~

Build và chạy:

~~~bash
docker build -t demo-web:1.0.0 .
docker run --name demo-web -d -p 8080:80 demo-web:1.0.0
curl http://localhost:8080
~~~

Dấu chấm ở cuối `docker build` là build context hiện tại. Dockerfile có thể copy file trong context, nhưng không thể tùy ý copy file nằm ngoài context.

### 2.1. Dockerfile không phải shell script

Dockerfile có cú pháp riêng. Một instruction thường tạo layer hoặc metadata; thứ tự instruction ảnh hưởng kết quả, cache và khả năng tái tạo.

Không nên viết Dockerfile như script cài đặt thủ công rồi sửa container bằng `exec`. Dockerfile cần mô tả trạng thái mong muốn của image để người khác và CI có thể build lại.

### 2.2. Tên file và syntax directive

Tên mặc định là `Dockerfile`. Có thể chọn file khác:

~~~bash
docker build -f Dockerfile.dev -t demo-web:dev .
~~~

Một số Dockerfile dùng syntax directive ở đầu file:

~~~dockerfile
# syntax=docker/dockerfile:1
FROM alpine:3.20
~~~

Syntax directive phải đặt ở phần đầu, trước instruction thông thường. Tuần này chỉ cần biết nó chọn parser/frontend; build cache và BuildKit sẽ học sâu hơn ở tuần 5.

---

## 3. Instruction `FROM`

`FROM` chọn base image và thường là instruction đầu tiên có ý nghĩa.

~~~dockerfile
FROM eclipse-temurin:21-jre
~~~

`FROM` quyết định nhiều đặc điểm:

- OS userspace và filesystem ban đầu.
- Architecture/platform được hỗ trợ.
- Runtime có sẵn.
- Các binary tiện ích có sẵn để debug/healthcheck.
- Kích thước và bề mặt vulnerability.

Có thể dùng `scratch` hoặc image tối giản, nhưng cần tự đảm bảo ứng dụng có đủ thư viện và runtime.

### 3.1. Pin base image

~~~dockerfile
FROM eclipse-temurin:21-jre
~~~

Tag giúp đọc dễ, nhưng tag có thể được cập nhật. Với production cần reproducibility cao, có thể pin digest:

~~~dockerfile
FROM eclipse-temurin:21-jre@sha256:<digest>
~~~

Digest trong ví dụ là placeholder; phải lấy digest thực tế từ registry bạn tin cậy. Pin digest làm update bảo mật ít tự động hơn, nên cần quy trình cập nhật base image định kỳ.

### 3.2. Chọn JDK hay JRE

- JDK phù hợp cho build source.
- JRE hoặc runtime image phù hợp cho image chạy ứng dụng đã build.
- JRE không nhất thiết có compiler và tool debug của JDK.

Tuần này dùng JRE cho runtime JAR. Multi-stage build và tối ưu runtime sẽ học kỹ ở tuần 6.

---

## 4. Instruction `WORKDIR`

`WORKDIR` đặt thư mục hiện tại cho các instruction sau và cho process runtime.

~~~dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/app.jar app.jar
~~~

Nếu `/app` chưa tồn tại, Docker thường tạo nó. Dùng `WORKDIR` tốt hơn việc lặp lại `cd /app` trong nhiều `RUN` hoặc `CMD`.

~~~dockerfile
WORKDIR /app
RUN pwd
COPY . .
~~~

Các `WORKDIR` liên tiếp có thể là path tương đối, nhưng nên dùng path tuyệt đối ở điểm đầu để tránh khó đọc:

~~~dockerfile
WORKDIR /app
WORKDIR config
~~~

Kết quả cuối là `/app/config`. Tránh lạm dụng vì làm người đọc phải suy luận path.

---

## 5. Instruction `COPY` và `ADD`

### 5.1. `COPY`

`COPY` copy file hoặc thư mục từ build context vào image:

~~~dockerfile
WORKDIR /app
COPY target/app.jar app.jar
COPY config/ config/
~~~

Đây thường là lựa chọn mặc định vì hành vi rõ ràng: copy dữ liệu từ context.

Path nguồn được tính từ root của build context, không phải từ vị trí Dockerfile nếu bạn dùng `-f` với context khác.

### 5.2. `ADD`

`ADD` có thêm một số behavior như tự xử lý local tar archive và hỗ trợ source URL theo cách Docker định nghĩa. Vì behavior nhiều hơn, nên ưu tiên `COPY` khi chỉ cần copy file.

~~~dockerfile
# Thường ưu tiên
COPY app.jar /app/app.jar

# Chỉ dùng ADD khi behavior archive là chủ đích
ADD application.tar.gz /opt/application/
~~~

Không dùng `ADD` để tải dependency hoặc source từ URL một cách tùy tiện. URL remote có thể làm build khó tái tạo và khó kiểm soát integrity.

### 5.3. Destination và ownership

Với image hỗ trợ option phù hợp, có thể đặt ownership khi copy:

~~~dockerfile
COPY --chown=10001:10001 target/app.jar /app/app.jar
~~~

Nếu image/base chưa có group/user tương ứng, dùng numeric UID/GID hoặc tạo user trước. Chi tiết user sẽ ở phần `USER`.

---

## 6. Instruction `RUN`

`RUN` thực thi command trong quá trình build và thường tạo layer mới:

~~~dockerfile
FROM ubuntu:24.04
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
~~~

Trong một `RUN` cài package, thường nên xóa package index sau khi cài để giảm file không cần thiết. Tối ưu layer/cache sẽ học sâu hơn ở tuần 5.

### 6.1. `RUN` chạy lúc nào?

`RUN` chạy lúc build, không phải mỗi lần container start:

~~~dockerfile
RUN echo 'created during build' > /build-time.txt
~~~

Khi container chạy, file đã nằm trong image. Nếu muốn tạo giá trị mỗi lần start, dùng command/entrypoint hoặc application runtime.

### 6.2. Shell form và command chaining

~~~dockerfile
RUN echo hello && echo world
~~~

Trên Linux, command thường chạy qua shell mặc định. `&&` giúp bước sau chỉ chạy khi bước trước thành công. Không nên dùng `;` cho các bước cần fail fast vì command trước lỗi nhưng build vẫn có thể tiếp tục.

### 6.3. Không đưa secret vào `RUN`

Không làm:

~~~dockerfile
ARG TOKEN
RUN curl -H "Authorization: Bearer ${TOKEN}" https://example.invalid/file
~~~

Build arg và command có thể xuất hiện trong metadata, history hoặc log build. Dùng secret mount của BuildKit/CI secret mechanism khi thật sự cần, hoặc thiết kế pipeline để secret không đi vào image layer. Chi tiết supply chain sẽ học sau.

---

## 7. `ENV` và `ARG`

### 7.1. `ENV`: giá trị runtime mặc định

`ENV` ghi environment variable vào image config, container sẽ nhận giá trị đó nếu không override:

~~~dockerfile
ENV APP_HOME=/app
ENV APP_PORT=8080
WORKDIR ${APP_HOME}
~~~

Override khi chạy:

~~~bash
docker run --rm -e APP_PORT=9090 demo-web:1.0.0
~~~

`ENV` phù hợp với default không nhạy cảm như timezone, mode mặc định, đường dẫn hoặc port logic. Không đặt password, API token hay private key vào `ENV` trong Dockerfile.

### 7.2. `ARG`: giá trị build-time

`ARG` chỉ tồn tại trong quá trình build, trừ khi được copy sang `ENV`:

~~~dockerfile
ARG APP_VERSION=dev
LABEL org.example.version=${APP_VERSION}
~~~

Build với giá trị khác:

~~~bash
docker build --build-arg APP_VERSION=1.0.0 -t demo-web:1.0.0 .
~~~

`ARG` không tự xuất hiện trong container runtime. Nhưng không nên coi `ARG` là secret an toàn; giá trị có thể lộ qua history/log/metadata tùy cách dùng.

### 7.3. So sánh

| Đặc điểm | `ARG` | `ENV` |
|---|---|---|
| Có hiệu lực | Khi build | Khi container chạy |
| Có trong image runtime config | Không nhất thiết | Có |
| Override bằng | `docker build --build-arg` | `docker run -e`/Compose |
| Dùng cho secret? | Không | Không |
| Ví dụ | version, target platform | port mặc định, app mode |

### 7.4. Biến trong instruction

~~~dockerfile
ARG APP_DIR=/app
ENV APP_DIR=${APP_DIR}
WORKDIR ${APP_DIR}
~~~

Hãy kiểm tra giá trị cuối bằng `docker inspect`; đừng giả định shell host sẽ expand biến bên trong Dockerfile.

---

## 8. `EXPOSE` và publish port

`EXPOSE` ghi metadata rằng ứng dụng trong image dự kiến listen port nào:

~~~dockerfile
EXPOSE 8080
~~~

Nó không mở firewall và không publish port ra host. Khi chạy cần:

~~~bash
docker run --rm -p 8080:8080 demo-web:1.0.0
~~~

Flow:

~~~text
EXPOSE 8080: metadata/documentation
-p 8080:8080: host port mapping lúc docker run
~~~

Có thể expose nhiều port:

~~~dockerfile
EXPOSE 8080
EXPOSE 8443
~~~

Nhưng chỉ expose port mà ứng dụng thực sự dùng. `EXPOSE` là documentation/metadata, không phải security boundary.

---

## 9. `USER` và chạy non-root

Nếu không chỉ định, nhiều image chạy process bằng root. Nên chạy application bằng user non-root khi có thể:

~~~dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/app.jar app.jar
USER 10001
ENTRYPOINT ["java", "-jar", "app.jar"]
~~~

Cần bảo đảm UID có quyền đọc JAR và ghi vào các thư mục cần thiết. Nếu app cần ghi `/tmp` thì dùng `/tmp` hoặc tạo thư mục riêng với ownership phù hợp, thay vì cấp quyền rộng.

Tạo user rõ ràng trong image Debian/Ubuntu:

~~~dockerfile
RUN groupadd --system app && useradd --system --gid app app
USER app
~~~

Tên command phụ thuộc distro. Numeric UID có thể giảm khác biệt tên user giữa image, nhưng cần quản lý ownership cẩn thận.

Kiểm tra user runtime:

~~~bash
docker run --rm demo-web:1.0.0 id
docker inspect --format '{{.Config.User}}' demo-web:1.0.0
~~~

Không dùng `USER` như thay thế cho security review. Vẫn cần giới hạn capability, filesystem, network và secret.

---

## 10. `ENTRYPOINT` và `CMD`

Đây là phần gây nhầm lẫn nhiều nhất khi viết Dockerfile.

### 10.1. Exec form

~~~dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]
~~~

Exec form chạy trực tiếp process, không qua shell mặc định. Đây thường là lựa chọn tốt cho service vì signal đến process rõ hơn.

### 10.2. Shell form

~~~dockerfile
ENTRYPOINT java -jar app.jar
~~~

Shell form thường chạy qua shell. Điều này có thể ảnh hưởng signal handling, variable expansion và process tree.

### 10.3. Vai trò của `ENTRYPOINT`

`ENTRYPOINT` định nghĩa executable chính của image:

~~~dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]
~~~

Khi chạy thêm arguments:

~~~bash
docker run --rm demo-web:1.0.0 --server.port=9090
~~~

argument thường được nối vào entrypoint.

### 10.4. Vai trò của `CMD`

`CMD` cung cấp command mặc định hoặc default arguments:

~~~dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]
CMD ["--server.port=8080"]
~~~

Override `CMD` khi chạy:

~~~bash
docker run --rm demo-web:1.0.0 --server.port=9090
~~~

Trong mô hình này, `ENTRYPOINT` vẫn là Java app còn `CMD` bị thay bằng argument mới.

### 10.5. Bảng hành vi

| Dockerfile | `docker run image` | `docker run image custom` |
|---|---|---|
| Chỉ `CMD` | Chạy CMD | Thay toàn bộ CMD |
| Chỉ `ENTRYPOINT` | Chạy ENTRYPOINT | Nối custom vào ENTRYPOINT |
| Cả hai | ENTRYPOINT + CMD args | ENTRYPOINT + custom args |

### 10.6. Quy tắc thực tế

- Dùng exec form cho service chính.
- Dùng `ENTRYPOINT` cho executable cố định.
- Dùng `CMD` cho default argument dễ override.
- Không dùng shell wrapper nếu chưa hiểu signal và exit code.
- Nếu bắt buộc dùng wrapper, wrapper nên `exec` process chính.

Ví dụ wrapper đúng hướng:

~~~sh
#!/bin/sh
set -eu
# chuẩn bị ngắn gọn nếu cần
exec java -jar /app/app.jar "$@"
~~~

---

## 11. `SHELL`

`SHELL` thay shell mặc định cho shell-form instruction:

~~~dockerfile
FROM windows/servercore:ltsc2022
SHELL ["powershell", "-Command"]
RUN Write-Host 'building image'
~~~

Trên Linux, thường không cần đổi `SHELL`. Nếu dùng Bash-specific syntax, hãy khai báo rõ base image có Bash và cân nhắc:

~~~dockerfile
SHELL ["/bin/bash", "-o", "pipefail", "-c"]
~~~

Không nên dùng syntax của Bash trong image chỉ có `/bin/sh` mà không kiểm tra.

---

## 12. `LABEL`

`LABEL` thêm metadata vào image:

~~~dockerfile
LABEL org.opencontainers.image.title="task-api" \
      org.opencontainers.image.description="Spring Boot API" \
      org.opencontainers.image.version="1.0.0" \
      org.opencontainers.image.source="https://example.invalid/repo"
~~~

Label hữu ích cho:

- Tên và version artifact.
- Source repository và revision.
- Owner/team.
- Tooling và inventory.
- Traceability trong registry.

Không đặt secret vào label. Metadata có thể được đọc bằng `docker image inspect` hoặc registry UI.

---

## 13. `HEALTHCHECK`

`HEALTHCHECK` định nghĩa cách Docker kiểm tra process trong container có phản hồi đúng hay không:

~~~dockerfile
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/actuator/health || exit 1
~~~

Các option:

- `--interval`: khoảng cách giữa các lần kiểm tra.
- `--timeout`: thời gian tối đa mỗi lần.
- `--start-period`: thời gian grace khi app khởi động.
- `--retries`: số lần fail liên tiếp trước khi unhealthy.
- `CMD`: command thực hiện healthcheck.

Kiểm tra:

~~~bash
docker inspect --format '{{json .Config.Healthcheck}}' demo-web:1.0.0
docker ps
~~~

### 13.1. Bẫy công cụ healthcheck

Image JRE tối giản có thể không có `curl`, `wget` hoặc shell đầy đủ. Dockerfile build được nhưng container có thể luôn `unhealthy` nếu healthcheck gọi binary không tồn tại.

Cách xử lý:

- Dùng endpoint/command và binary chắc chắn có trong image.
- Cài tool cần thiết có chủ đích, chấp nhận trade-off size/vulnerability.
- Để ứng dụng hoặc sidecar/monitoring kiểm tra nếu healthcheck không nên nằm trong image.
- Test `docker exec <container> which curl` hoặc `which wget` trước khi dùng.

Healthcheck không thay thế readiness/liveness strategy của orchestration platform. Nó chỉ cung cấp tín hiệu container-level.

---

## 14. Dockerfile Spring Boot cơ bản

Giả sử Maven đã build JAR tại `target/app.jar`:

~~~dockerfile
FROM eclipse-temurin:21-jre

WORKDIR /app

COPY target/app.jar app.jar

EXPOSE 8080

USER 10001

ENTRYPOINT ["java", "-jar", "app.jar"]
~~~

Build:

~~~bash
./mvnw package -DskipTests
docker build -t task-api:0.1.0 .
~~~

Nếu project không có Maven wrapper:

~~~bash
mvn package -DskipTests
docker build -t task-api:0.1.0 .
~~~

Chạy:

~~~bash
docker run --name task-api -d -p 8080:8080 task-api:0.1.0
docker logs -f task-api
~~~

Kiểm tra:

~~~bash
docker ps
docker inspect task-api
docker inspect --format '{{.Config.User}}' task-api
docker exec task-api id
curl http://localhost:8080
~~~

Nếu Spring Boot dùng port khác, cần đồng bộ ba phần:

1. `server.port` của ứng dụng.
2. `EXPOSE` trong Dockerfile, chủ yếu là metadata.
3. Host mapping trong `docker run -p host:container`.

Dừng và xóa sau lab:

~~~bash
docker stop task-api
docker rm task-api
~~~

---

## 15. Spring Boot với environment runtime

Không hardcode môi trường vào image. Để Spring nhận port từ environment:

~~~dockerfile
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/app.jar app.jar
ENV JAVA_OPTS=""
EXPOSE 8080
USER 10001
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
~~~

Chạy:

~~~bash
docker run --rm -p 8080:8080 \
  -e JAVA_OPTS='-Xms128m -Xmx256m' \
  task-api:0.1.0
~~~

Ví dụ này dùng shell để expand `JAVA_OPTS`, nên phải hiểu trade-off về quoting và signal. Với command đơn giản, exec form trực tiếp thường an toàn và rõ hơn:

~~~dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]
~~~

Nếu cần nhiều runtime option, có thể truyền argument trực tiếp hoặc dùng entrypoint script được kiểm thử và có `exec`.

Không đưa database password hoặc token vào Dockerfile. Truyền bằng runtime secret/config mechanism.

---

## 16. Build và inspect image

~~~bash
docker build --pull -t task-api:0.1.0 .
docker image ls task-api
docker image inspect task-api:0.1.0
docker history task-api:0.1.0
~~~

Kiểm tra các metadata quan trọng:

~~~bash
docker image inspect task-api:0.1.0 \
  --format 'user={{.Config.User}} workdir={{.Config.WorkingDir}} entrypoint={{json .Config.Entrypoint}} cmd={{json .Config.Cmd}} exposed={{json .Config.ExposedPorts}}'
~~~

Chạy shell override để debug image:

~~~bash
docker run --rm -it --entrypoint sh task-api:0.1.0
~~~

Nếu image không có `sh`, hãy debug bằng command/binary có sẵn hoặc dùng container tool phù hợp. Override `--entrypoint` chỉ áp dụng cho container đang tạo; nó không sửa image.

---

## 17. Lỗi thường gặp

### `COPY failed: file not found`

Nguyên nhân thường là JAR chưa build, path sai hoặc file nằm ngoài build context:

~~~bash
ls -la target/
docker build -f docker/Dockerfile .
~~~

Nhớ rằng context trong ví dụ là `.`; path `COPY target/app.jar` được tính từ context.

### Container thoát ngay

~~~bash
docker ps -a --filter name=task-api
docker logs task-api
docker inspect --format 'exit={{.State.ExitCode}} error={{.State.Error}}' task-api
~~~

Kiểm tra JAR, Java version, command, working directory và environment.

### Permission denied khi chạy non-root

Nguyên nhân có thể là JAR hoặc thư mục cần ghi không cho UID chạy container đọc/ghi. Kiểm tra:

~~~bash
docker run --rm --entrypoint sh task-api:0.1.0 -c 'id; ls -la /app; touch /tmp/write-test'
~~~

Chỉ cấp quyền cho đúng thư mục cần ghi; không giải quyết bằng cách chạy root mặc định nếu không cần.

### Port không truy cập được

~~~bash
docker ps
docker port task-api
docker logs task-api
docker inspect --format '{{json .Config.ExposedPorts}}' task-api
~~~

`EXPOSE` không tự publish port. Kiểm tra mapping và port ứng dụng đang listen.

### Healthcheck luôn unhealthy

~~~bash
docker ps
docker inspect --format '{{json .State.Health}}' task-api
docker exec task-api sh -c 'command -v curl || command -v wget || true'
~~~

Có thể endpoint sai, app chưa ready, timeout quá ngắn hoặc tool healthcheck không tồn tại.

### `CMD` không override như mong đợi

Kiểm tra image có shell form hay exec form, có `ENTRYPOINT` hay không và command runtime đang thay phần nào. Dùng `docker image inspect` để xem config cuối.

---

## 18. Bài tập tự làm

### Bài 1: Image Nginx tùy biến

Tạo `index.html`, viết Dockerfile từ `nginx:1.27`, copy file vào document root, build `demo-web:1.0.0`, chạy port `8080` và kiểm tra bằng curl.

### Bài 2: Environment và ARG

Tạo Dockerfile có `ARG APP_VERSION` và `ENV APP_MODE`. Build với version khác nhau, chạy với `-e APP_MODE=production`, rồi dùng `inspect` giải thích biến nào tồn tại lúc build và biến nào tồn tại lúc runtime.

### Bài 3: ENTRYPOINT/CMD

Tạo image nhỏ dùng `ENTRYPOINT ["echo"]` và `CMD ["default"]`. Chạy image không argument và có argument. Viết kết luận về phần nào bị thay thế và phần nào được nối thêm.

### Bài 4: Spring Boot

Build một JAR, viết Dockerfile theo mẫu, chạy bằng UID non-root, kiểm tra bằng `id`, xem logs và truy cập endpoint. Cố ý map sai port rồi dùng `ps`, `port`, `inspect` và logs để điều tra.

### Bài 5: Healthcheck

Thêm healthcheck cho endpoint health. Trước khi build, xác minh image có `curl` hoặc `wget`; nếu không có, chọn cách xử lý phù hợp và giải thích trade-off.

---

## 19. Câu hỏi kiểm tra và đáp án gợi ý

1. `FROM` ảnh hưởng những gì đến image?
2. Vì sao nên dùng `WORKDIR` thay vì nhiều lệnh `cd`?
3. Khi nào ưu tiên `COPY` hơn `ADD`?
4. `RUN` chạy lúc build hay lúc container start?
5. `ARG` và `ENV` khác nhau thế nào?
6. `EXPOSE` có tự publish port không?
7. Vì sao nên chạy app bằng `USER` non-root?
8. `ENTRYPOINT` và `CMD` kết hợp ra sao?
9. Vì sao exec form thường tốt hơn shell form cho process chính?
10. Vì sao healthcheck có thể luôn unhealthy dù Dockerfile build thành công?
11. Vì sao secret không nên đặt trong `ARG`, `ENV` hoặc `RUN`?
12. `LABEL` dùng cho mục đích gì?

Đáp án ngắn: `FROM` chọn base/runtime; `WORKDIR` làm path nhất quán; `COPY` rõ behavior hơn; `RUN` lúc build; `ARG` build-time còn `ENV` runtime; `EXPOSE` chỉ metadata; non-root giảm tác động khi app bị khai thác; `ENTRYPOINT` là executable còn `CMD` là default command/args; exec form truyền signal rõ hơn; healthcheck có thể gọi binary không tồn tại hoặc endpoint chưa sẵn sàng; secret có thể lộ trong metadata/layer/log; label là metadata traceability/tooling.

---

## 20. Tiêu chí hoàn thành tuần 4

- [ ] Viết được Dockerfile cơ bản có `FROM`, `WORKDIR`, `COPY`, `RUN` và `CMD`/`ENTRYPOINT`.
- [ ] Giải thích đúng vai trò của `ENV`, `ARG`, `EXPOSE`, `USER`, `LABEL` và `HEALTHCHECK`.
- [ ] Phân biệt `COPY` và `ADD`.
- [ ] Phân biệt build-time với runtime configuration.
- [ ] Giải thích được shell form và exec form.
- [ ] Giải thích được `ENTRYPOINT` và `CMD` khi override command.
- [ ] Build được image chạy JAR Spring Boot.
- [ ] Chạy ứng dụng bằng user non-root.
- [ ] Dùng được `image inspect`, `history`, `logs`, `port` và `exec` để debug.
- [ ] Biết `EXPOSE` không tự publish port.
- [ ] Biết healthcheck phải dùng tool có thật trong image.
- [ ] Không đưa secret vào Dockerfile hoặc image.

Kiểm tra cuối buổi:

~~~bash
docker build -t task-api:0.1.0 .
docker image inspect task-api:0.1.0
docker history task-api:0.1.0
docker run --rm task-api:0.1.0 --help
~~~

Chỉ dùng `--help` nếu ứng dụng hỗ trợ argument này; nếu không, thay bằng command kiểm tra phù hợp với image của bạn.
