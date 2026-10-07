# Tuần 6: Multi-stage build và runtime image

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Giải thích build stage, test stage và runtime stage.
- Viết Dockerfile multi-stage cho ứng dụng Java/Spring Boot.
- Dùng `COPY --from` để chỉ đưa artifact cần thiết sang runtime image.
- Phân biệt JDK và JRE/runtime image.
- Hiểu distroless và Alpine ở mức lựa chọn kiến trúc.
- Nhận biết vì sao debug image khác production image.
- Kiểm tra artifact, user, command, dependency footprint và image size.
- Hiểu SBOM là gì và vì sao runtime image vẫn cần được scan.
- Tránh để Maven cache, source code, test tool và secret đi vào runtime image.

Thời lượng đề xuất: **8–12 giờ** gồm lý thuyết, build image, kiểm tra và bài tập.

---

## 2. Vấn đề của một image vừa build vừa chạy

Cách đơn giản là dùng JDK để build và chạy ứng dụng:

~~~dockerfile
FROM eclipse-temurin:21-jdk
WORKDIR /workspace
COPY . .
RUN ./mvnw package -DskipTests
ENTRYPOINT ["java", "-jar", "target/app.jar"]
~~~

Cách này có thể chạy, nhưng runtime image có thể chứa:

- JDK compiler và tool build.
- Maven wrapper và Maven cache.
- Source code.
- Test source và test report.
- Package manager metadata.
- File tạm và build output trung gian.
- Nhiều binary không cần khi chạy service.

Hệ quả:

- Image lớn hơn, pull/deploy chậm hơn.
- Attack surface lớn hơn.
- Scan vulnerability có nhiều thành phần không chạy trong production.
- Khó phân biệt dependency build với dependency runtime.
- Dễ vô tình copy secret hoặc source không cần thiết.

Multi-stage build tách các vai trò:

~~~text
Source + build tools → build stage → JAR/artifact → runtime stage → container production
                         └→ test stage (optional)
~~~

Runtime image chỉ nên chứa những gì cần để process production khởi động và phục vụ workload.

---

## 3. Multi-stage build là gì?

Dockerfile multi-stage có nhiều instruction `FROM`. Mỗi `FROM` mở một stage mới. Stage có thể được đặt tên bằng `AS`:

~~~dockerfile
FROM eclipse-temurin:21-jdk AS build
# compile/package ở đây

FROM eclipse-temurin:21-jre AS runtime
# chỉ copy artifact cần chạy
~~~

`COPY --from=build` copy file từ filesystem của stage `build` sang stage hiện tại:

~~~dockerfile
COPY --from=build /workspace/target/app.jar /app/app.jar
~~~

Các layer và file của stage build không tự xuất hiện trong final image nếu không được copy hoặc tạo lại ở stage cuối.

### 3.1. Final image là stage cuối

Với Dockerfile:

~~~dockerfile
FROM alpine:3.20 AS tools
RUN echo tools > /tools.txt

FROM alpine:3.20 AS runtime
COPY --from=tools /tools.txt /tools.txt
~~~

Image được tag sau build là stage `runtime`, không phải toàn bộ filesystem của `tools`. Stage trước vẫn có thể được builder sử dụng để tạo artifact/cache nhưng không nằm trong final image theo mặc định.

### 3.2. Stage name ổn định hơn index

Có thể dùng stage index:

~~~dockerfile
COPY --from=0 /workspace/target/app.jar /app/app.jar
~~~

Nhưng nên đặt tên:

~~~dockerfile
COPY --from=build /workspace/target/app.jar /app/app.jar
~~~

Nếu chèn thêm stage, index có thể thay đổi; tên stage dễ đọc và ít lỗi hơn.

---

## 4. Build stage, test stage và runtime stage

### 4.1. Build stage

Build stage chứa toolchain:

- JDK.
- Maven/Gradle.
- Wrapper và build plugin.
- Source code.
- Dependency cache.
- Compiler và packaging tool.

Ví dụ:

~~~dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline
COPY src src
RUN ./mvnw package -DskipTests
~~~

### 4.2. Test stage

Test có thể chạy trong stage riêng:

~~~dockerfile
FROM build AS test
RUN ./mvnw test
~~~

Cách đơn giản này tạo target stage `test`, nhưng khi build mặc định Docker vẫn build tới stage cuối. Có thể build riêng:

~~~bash
docker build --target test -t task-api:test .
~~~

Trong CI, test stage có thể là quality gate trước khi build runtime image. Không nên coi việc build runtime image thành công là bằng chứng test đã chạy.

Một flow rõ ràng:

~~~text
build source → test → package artifact → runtime image
~~~

### 4.3. Runtime stage

Runtime stage dùng base phù hợp để chạy artifact:

~~~dockerfile
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
~~~

Runtime stage không cần copy `pom.xml`, `mvnw`, `.mvn`, `src` hoặc `.m2` nếu ứng dụng không cần chúng lúc chạy.

---

## 5. Dockerfile multi-stage Spring Boot cơ bản

Đây là phiên bản theo roadmap:

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

Build:

~~~bash
./mvnw package -DskipTests
docker build --pull -t task-api:0.2.0 .
~~~

Nếu build hoàn toàn trong container, không cần chạy Maven trên host trước; lệnh host chỉ hữu ích nếu muốn kiểm tra JAR hoặc project trước build.

Chạy:

~~~bash
docker run --name task-api -d -p 8080:8080 task-api:0.2.0
docker logs -f task-api
~~~

Kiểm tra:

~~~bash
docker ps
docker image ls task-api
docker image inspect task-api:0.2.0
docker inspect --format '{{.Config.User}}' task-api
docker exec task-api id
~~~

Dọn sau lab:

~~~bash
docker stop task-api
docker rm task-api
~~~

---

## 6. `COPY --from` và artifact boundary

`COPY --from` là boundary quan trọng giữa build và runtime:

~~~dockerfile
COPY --from=build /workspace/target/app.jar /app/app.jar
~~~

Chỉ copy những file runtime cần. Tránh:

~~~dockerfile
COPY --from=build /workspace /app
~~~

vì cách này có thể đưa source, Maven cache, test report và file build trung gian vào runtime image.

### 6.1. Wildcard và tên JAR

Nếu Maven tạo tên versioned:

~~~text
target/task-api-0.2.0.jar
target/task-api-0.2.0-plain.jar
~~~

`COPY --from=build /workspace/target/*.jar app.jar` có thể copy nhiều file hoặc chọn không đúng artifact tùy output. Nên cấu hình tên artifact ổn định hoặc copy chính xác:

~~~dockerfile
RUN ./mvnw package -DskipTests \
    && cp target/task-api-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
~~~

Nếu có cả `plain.jar` và executable jar, xác định rõ file nào ứng dụng cần chạy.

### 6.2. Kiểm tra artifact trước khi copy

Trong build stage có thể kiểm tra:

~~~dockerfile
RUN test -s target/app.jar
~~~

Hoặc với tên thay đổi:

~~~dockerfile
RUN find target -maxdepth 1 -type f -name '*.jar' -print
~~~

Build fail sớm tốt hơn việc tạo runtime image rồi chỉ phát hiện lỗi khi start container.

---

## 7. JDK, JRE và runtime image

### 7.1. JDK

JDK thường có:

- Java runtime.
- Java compiler.
- Javadoc/debug tools.
- Tool phục vụ build và development.

JDK cần trong build stage khi compile/package source.

### 7.2. JRE hoặc Java runtime

Runtime image chỉ cần chạy JAR thường không cần compiler và toàn bộ toolchain JDK. Tên package cụ thể thay đổi theo vendor/version; cần kiểm tra image thực tế.

~~~dockerfile
FROM eclipse-temurin:21-jre AS runtime
~~~

JDK/JRE không chỉ là câu chuyện size. Cần kiểm tra:

- Java modules mà app dùng.
- TLS/certificate store.
- Timezone/locale.
- Native library.
- Debug/diagnostic requirement.
- CVE và lifecycle của vendor image.

### 7.3. Runtime image không có nghĩa “càng nhỏ càng tốt”

Image nhỏ có thể giảm transfer và attack surface, nhưng image quá tối giản làm debug khó hoặc thiếu certificate/tool cần thiết. Mục tiêu là runtime image nhỏ, rõ, được scan và vận hành được; không phải tối ưu size bằng mọi giá.

---

## 8. Distroless ở mức khái niệm

Distroless image thường chỉ chứa runtime và filesystem tối thiểu cần thiết, không có shell/package manager đầy đủ.

Ví dụ khái niệm:

~~~dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw package -DskipTests
COPY src src
RUN ./mvnw package -DskipTests

FROM gcr.io/distroless/java21-debian12 AS runtime
WORKDIR /app
COPY --from=build /workspace/target/app.jar app.jar
USER nonroot:nonroot
ENTRYPOINT ["java", "-jar", "app.jar"]
~~~

Lợi ích:

- Ít binary và package không cần thiết.
- Attack surface nhỏ hơn về mặt lý thuyết.
- Không có shell giúp giảm thao tác tùy tiện trong runtime.

Trade-off:

- `docker exec ... sh` có thể không dùng được.
- Debug cần logs, metrics, JFR/diagnostic endpoint hoặc ephemeral debug container.
- Cần hiểu user, certificate, timezone và file path của base image.
- Tool scan và policy của tổ chức phải hỗ trợ image đó.

Distroless không tự động an toàn. Vẫn phải pin/scan/update base image, chạy non-root và giới hạn runtime.

---

## 9. Alpine và compatibility

Alpine thường nhỏ và dùng musl libc thay vì glibc. Điều này có thể ảnh hưởng:

- Native library.
- JNI.
- DNS behavior.
- Font/rendering.
- TLS/certificate.
- Agent/monitoring binary.
- Thư viện bên thứ ba giả định glibc.

Với Java, image Alpine có thể hoạt động tốt cho workload cụ thể, nhưng phải test. Không chọn Alpine chỉ vì con số size trên Docker Hub.

Checklist khi thử Alpine:

- Ứng dụng khởi động và chạy integration test.
- Dependency native/JNI hoạt động.
- TLS và DNS hoạt động trong môi trường thật.
- Timezone/locale đúng.
- Monitoring/profiling agent tương thích.
- Hiệu năng và memory profile chấp nhận được.

Nếu không có lý do rõ ràng, dùng runtime image chính thức tương thích tốt với app thường dễ vận hành hơn.

---

## 10. Debug image và production image

### 10.1. Production image

Production image nên ưu tiên:

- Runtime tối thiểu nhưng đủ dùng.
- Non-root.
- Không có source/toolchain không cần.
- Command và signal rõ ràng.
- Health/metrics/logging phù hợp.
- Base image được cập nhật và scan.

### 10.2. Debug image

Debug image có thể chứa thêm shell, diagnostic tool hoặc package cần điều tra. Không nên mặc định đưa tất cả tool debug vào production image.

Có thể tạo debug stage:

~~~dockerfile
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER 10001
ENTRYPOINT ["java", "-jar", "app.jar"]

FROM runtime AS debug
USER root
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl procps \
    && rm -rf /var/lib/apt/lists/*
USER 10001
~~~

Build stage cụ thể:

~~~bash
docker build --target runtime -t task-api:prod .
docker build --target debug -t task-api:debug .
~~~

Chỉ publish runtime image vào production registry/deployment. Debug image cần tag/policy rõ và không chứa secret.

### 10.3. Debug bằng ephemeral container

Với runtime image không có shell, có thể dùng công cụ orchestration hoặc Docker network để chạy một container debug riêng cùng network/volume phù hợp. Không copy shell vào production image chỉ vì một lần debug.

---

## 11. Test stage và quality gate

Dockerfile có thể tách test stage:

~~~dockerfile
FROM build AS test
RUN ./mvnw test

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=build /workspace/target/*.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
~~~

Build test stage:

~~~bash
docker build --target test -t task-api:test .
~~~

Nếu test fail, build command fail. Trong CI, có thể chạy test stage trước runtime build hoặc cấu hình pipeline riêng để test report được thu thập.

Một điểm cần rõ: `test` stage ở trên dùng artifact/build output của `build` tùy vị trí instruction. Nếu test cần chạy sau package, đặt test command và package command theo lifecycle Maven chính xác. Không giả định thứ tự stage tự động chạy test nếu Dockerfile không có `RUN ./mvnw test`.

Ví dụ Maven đơn giản:

~~~dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline
COPY src src
RUN ./mvnw test package
~~~

Cách này chạy test trước package trong cùng stage. Với project lớn, có thể tách các stage/test command để cache và báo cáo phù hợp hơn.

---

## 12. SBOM và dependency footprint

### 12.1. SBOM là gì?

SBOM, Software Bill of Materials, là danh sách thành phần phần mềm trong artifact/image, gồm package, version và đôi khi relationship/license.

SBOM giúp:

- Biết runtime image chứa package nào.
- Đối chiếu vulnerability với version cụ thể.
- Hỗ trợ incident response.
- Theo dõi license và supply chain.
- So sánh build/runtime footprint.

### 12.2. Build stage và runtime stage có footprint khác nhau

Nếu scan cả build image, kết quả có thể chứa JDK, Maven và tool chỉ dùng lúc build. Runtime image multi-stage nên nhỏ hơn, nhưng vẫn phải scan vì JRE/base OS cũng có vulnerability.

Không kết luận “runtime image an toàn” chỉ vì nó nhỏ. Cần:

- Scan image cuối.
- Scan dependency ứng dụng.
- Tạo SBOM cho artifact/image nếu pipeline hỗ trợ.
- Theo dõi base image và dependency update.
- Ghi image digest/source commit để truy vết.

### 12.3. Kiểm tra bằng tooling

Tool cụ thể tùy tổ chức. Một số Docker setup có lệnh SBOM/scanning qua Docker Scout hoặc scanner khác. Trước khi dùng, kiểm tra command/version và policy của project:

~~~bash
docker scout quickview task-api:0.2.0
~~~

Nếu máy không có Docker Scout, dùng scanner được tổ chức phê duyệt. Không bỏ qua scan chỉ vì command mẫu không có sẵn.

---

## 13. Lab 1: So sánh single-stage và multi-stage

### 13.1. Single-stage

~~~dockerfile
FROM eclipse-temurin:21-jdk AS single
WORKDIR /workspace
COPY . .
RUN ./mvnw package -DskipTests
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "target/app.jar"]
~~~

### 13.2. Multi-stage

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
COPY --from=build /workspace/target/app.jar app.jar
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
~~~

Build và so sánh:

~~~bash
docker build -f Dockerfile.single -t task-api:single .
docker build -f Dockerfile.multi -t task-api:multi .
docker image ls task-api
docker history task-api:single
docker history task-api:multi
~~~

Ghi nhận:

- Image size.
- Số layer và layer lớn.
- Có JDK/source/Maven cache trong runtime không.
- User runtime.
- Thời gian build.
- Khả năng start và health.

Không chỉ nhìn size; xác minh cả behavior và security footprint.

---

## 14. Lab 2: Build target debug và runtime

Dùng Dockerfile có stage `runtime` và `debug`:

~~~bash
docker build --target runtime -t task-api:runtime .
docker build --target debug -t task-api:debug .

docker image ls task-api
docker run --rm task-api:runtime id
docker run --rm task-api:debug id
~~~

Thử kiểm tra shell:

~~~bash
docker run --rm -it --entrypoint sh task-api:debug
# với runtime, lệnh này có thể fail nếu image không có sh
docker run --rm -it --entrypoint sh task-api:runtime
~~~

Kết luận khi debug image không có shell: dùng logs, metrics, JVM diagnostics, debug stage hoặc ephemeral debug tooling, không mặc định làm production image lớn hơn.

---

## 15. Lab 3: Kiểm tra runtime image không chứa source/toolchain

Chạy các kiểm tra:

~~~bash
docker run --rm --entrypoint sh task-api:runtime -c 'find /app -maxdepth 2 -type f -print'
docker image inspect task-api:runtime --format 'user={{.Config.User}} workdir={{.Config.WorkingDir}} entrypoint={{json .Config.Entrypoint}}'
docker history task-api:runtime
~~~

Nếu runtime image không có shell, dùng `docker export` chỉ khi hiểu rõ mục đích, hoặc inspect metadata/layer bằng scanner/tool. Không biến debug workaround thành workflow production.

Các câu hỏi cần trả lời:

- Runtime có `pom.xml`, `src`, `.m2` hoặc `mvnw` không?
- Runtime có chạy bằng root không?
- Runtime command có phải process Java trực tiếp không?
- JAR có tồn tại đúng path không?
- Base runtime có certificate store và timezone cần thiết không?

---

## 16. Lỗi thường gặp

### `COPY --from` không tìm thấy artifact

Nguyên nhân: build stage chưa tạo JAR, path sai, wildcard không match hoặc artifact name khác dự kiến.

~~~bash
docker build --progress=plain -t task-api:debug-build .
# kiểm tra Maven output trong build log
~~~

Thêm `RUN ls -la target` hoặc `RUN test -s ...` trong build stage để fail rõ hơn.

### Runtime image thiếu class/library

JAR có thể là thin JAR cần dependency ngoài, hoặc app cần file/config/native library chưa copy. Kiểm tra loại JAR, Spring Boot packaging và runtime dependency.

Không copy toàn bộ build workspace ngay lập tức; xác định artifact contract trước.

### Runtime không start vì user/permission

Kiểm tra UID, ownership `/app`, thư mục temp, log directory và certificate path. App nên ghi vào path được cấp quyền, không giả định root.

### Dùng JRE nhưng app cần tool JDK

Một số agent, startup script hoặc diagnostic workflow cần tool không có trong JRE. Quyết định rõ tool đó là runtime requirement hay chỉ là debug requirement. Nếu chỉ debug, dùng debug image/stage.

### Alpine chạy lỗi dù image nhỏ

Kiểm tra musl/glibc, JNI/native dependency, DNS, timezone, font và agent. Test trên platform thật trước khi chọn Alpine production.

### Distroless không có shell

Đây là behavior chủ đích. Dùng logs/metrics/debug image/ephemeral tooling; không cố `apt-get` hoặc `docker exec sh` vào distroless.

### Scan vẫn báo vulnerability

Multi-stage giảm build footprint, không loại mọi vulnerability. Scan runtime image, xác định package bị ảnh hưởng, cập nhật base/dependency và đánh giá severity/exposure.

---

## 17. Bài tập tự làm

### Bài 1: Multi-stage Spring Boot

Viết Dockerfile có `build` và `runtime` stage theo mẫu roadmap. Build JAR trong container, copy đúng artifact, chạy non-root và kiểm tra endpoint.

### Bài 2: Thêm test stage

Thêm stage `test`, chạy unit test bằng Maven, build với `--target test` và ghi nhận behavior khi test fail.

### Bài 3: So sánh JDK/JRE

Build hai runtime image: một dùng JDK, một dùng JRE. So sánh size, layer, process, user và khả năng chạy app. Không kết luận chỉ từ size; ghi lại compatibility.

### Bài 4: Debug image

Tạo target `debug` có tool cần thiết, giữ target `runtime` tối giản. Chứng minh debug image không được dùng làm production image mặc định.

### Bài 5: SBOM/footprint

Chạy scanner/SBOM tool được project cho phép trên build image và runtime image. So sánh package list, ghi nhận dependency runtime và vulnerability khác nhau.

---

## 18. Câu hỏi kiểm tra và đáp án gợi ý

1. Multi-stage build giải quyết vấn đề gì?
2. `COPY --from=build` làm gì?
3. Vì sao runtime image không nên chứa Maven cache và source code?
4. JDK khác JRE/runtime image như thế nào?
5. Distroless có ưu và nhược điểm gì?
6. Vì sao Alpine cần kiểm tra compatibility thay vì chỉ nhìn size?
7. Test stage có tự chạy nếu Dockerfile chỉ được build tới runtime không?
8. Vì sao debug image nên khác production image?
9. Runtime image có cần scan không?
10. SBOM giúp ích gì?
11. Vì sao wildcard `target/*.jar` có thể nguy hiểm?
12. Chạy non-root liên quan gì đến runtime image?

Đáp án ngắn: multi-stage tách toolchain khỏi runtime; `COPY --from` lấy artifact giữa stage; runtime không cần build tool/cache/source; JDK có tool build còn runtime/JRE tập trung chạy; distroless nhỏ nhưng khó debug; Alpine dùng musl và có compatibility trade-off; chỉ chạy test nếu có instruction/test target trong flow; debug tool làm tăng footprint; runtime vẫn phải scan; SBOM liệt kê thành phần; wildcard có thể chọn nhầm nhiều JAR; non-root giảm quyền khi process bị khai thác.

---

## 19. Tiêu chí hoàn thành tuần 6

- [ ] Giải thích được build, test và runtime stage.
- [ ] Viết được Dockerfile multi-stage cho Spring Boot.
- [ ] Dùng được `COPY --from` với artifact path rõ ràng.
- [ ] Phân biệt được JDK, JRE/runtime, distroless và Alpine.
- [ ] Chạy runtime image bằng user non-root.
- [ ] Xác minh runtime image không chứa source, Maven cache và toolchain không cần thiết.
- [ ] Tạo được debug target riêng khi cần shell/tool.
- [ ] Biết test stage phải được gọi trong build/CI flow.
- [ ] So sánh được image size, layer và dependency footprint.
- [ ] Hiểu runtime image vẫn phải scan và tạo SBOM khi pipeline hỗ trợ.
- [ ] Điều tra được lỗi artifact, permission, missing library và compatibility.

Kiểm tra cuối buổi:

~~~bash
docker build --target runtime -t task-api:week6 .
docker image inspect task-api:week6
docker history task-api:week6
docker image ls task-api
docker run --rm task-api:week6 id
~~~

Mục tiêu của multi-stage build là tạo runtime image đủ nhỏ, đủ an toàn, dễ truy vết và vẫn vận hành/debug được bằng quy trình phù hợp.
