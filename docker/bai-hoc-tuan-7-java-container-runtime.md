# Tuần 7: Java container runtime

## 1. Mục tiêu

Sau bài này, bạn có thể:

- Giải thích JVM chạy trong container và container ảnh hưởng JVM như thế nào.
- Phân biệt memory limit của container với Java heap.
- Cấu hình heap theo phần trăm memory và workload thay vì hardcode tùy tiện.
- Hiểu container-aware JVM và cách kiểm tra JVM đang nhận diện CPU/memory.
- Bật và đọc GC log.
- Nhận biết tác động của thread count, metaspace, direct memory và native memory.
- Đảm bảo process Java nhận `SIGTERM`, hiểu PID 1 và graceful shutdown.
- Cấu hình Spring Boot graceful shutdown và kiểm tra khi container stop.
- Hiểu startup của Spring Boot trong container.
- Tách dependency/application layers bằng layered JAR khi phù hợp.
- Điều tra `OOMKilled`, exit code và lỗi startup bằng Docker/JVM logs.

Thời lượng đề xuất: **8–12 giờ** gồm đọc lý thuyết, chạy app Java và quan sát runtime.

---

## 2. Vì sao JVM trong container cần cách nghĩ riêng?

Trước đây, JVM thường nhìn thấy toàn bộ máy host. Khi chạy trong container, process bị giới hạn bởi cgroup:

~~~text
Host resources
    ↓ cgroup limit
Container resources
    ↓ JVM container awareness
Heap + metaspace + threads + native memory
~~~

Nếu container có memory limit `512m`, không có nghĩa Java được dùng toàn bộ `512m` cho heap. Tổng memory process có thể bao gồm:

- Java heap.
- Metaspace và class metadata.
- Thread stacks.
- Code cache và JIT compiler memory.
- Direct/off-heap buffers.
- JNI/native library allocations.
- Memory của agent, profiler hoặc launcher.
- Một phần overhead của process/runtime.

Nếu đặt `-Xmx512m` trong container limit `512m`, JVM gần như không còn headroom cho các vùng ngoài heap. Kết quả có thể là container bị kernel kill dù Java heap chưa đạt `512m`.

Nguyên tắc nền tảng:

> Container memory limit là ngân sách cho toàn bộ process; heap chỉ là một phần của ngân sách đó.

---

## 3. Container-aware JVM

Các JDK hiện đại có khả năng đọc cgroup để điều chỉnh một số giá trị mặc định, thường được gọi là container awareness. Với Java hiện đại, `UseContainerSupport` thường đã bật mặc định, nhưng không nên chỉ tin vào assumption; hãy kiểm tra runtime thật.

Kiểm tra version và flags:

~~~bash
docker run --rm eclipse-temurin:21-jre java -version
docker run --rm eclipse-temurin:21-jre java -XX:+PrintFlagsFinal -version 2>&1 | grep -E 'UseContainerSupport|MaxRAMPercentage|InitialRAMPercentage|ActiveProcessorCount'
~~~

Chạy container có memory limit và in thông tin JVM:

~~~bash
docker run --rm --memory=512m eclipse-temurin:21-jre \
  java -XX:+PrintFlagsFinal -version 2>&1 | grep -E 'MaxHeapSize|InitialHeapSize|MaxRAMPercentage|UseContainerSupport'
~~~

Output và flag có thể khác theo JDK vendor/version. Điều quan trọng là biết JVM đang dùng nguồn thông tin nào để tính heap/CPU.

### 3.1. `UseContainerSupport`

Flag này cho phép JVM sử dụng giới hạn container để tính một số mặc định thay vì dùng toàn bộ host. Không nên tắt nếu không có lý do và test rõ ràng.

~~~bash
java -XX:+PrintFlagsFinal -version 2>&1 | grep UseContainerSupport
~~~

Nếu đang dùng JDK rất cũ, container detection có thể khác. Hãy ưu tiên JDK được hỗ trợ và base image rõ version.

### 3.2. CPU awareness

Container có thể bị giới hạn CPU bằng quota hoặc cpuset. JVM dùng số processor nhận diện để quyết định một số thread pool, GC worker và common pool.

Kiểm tra:

~~~bash
docker run --rm --cpus=0.5 eclipse-temurin:21-jre \
  java -XX:+PrintFlagsFinal -version 2>&1 | grep -E 'ActiveProcessorCount|ParallelGCThreads|ConcGCThreads'
~~~

Nếu framework hoặc thư viện vẫn tạo quá nhiều thread so với CPU limit, có thể xảy ra context switching, latency tăng và memory tăng. Chỉ đặt `ActiveProcessorCount` sau khi hiểu workload và kiểm tra behavior:

~~~bash
java -XX:ActiveProcessorCount=2 -jar app.jar
~~~

---

## 4. Memory limit và heap sizing

### 4.1. Các vùng memory chính

~~~text
Container memory limit
├── Java heap
├── Metaspace/class metadata
├── Thread stacks
├── Code cache/JIT
├── Direct buffers
├── JNI/native allocations
├── Agent/profiler
└── Process/runtime overhead
~~~

Heap chứa object Java thông thường. Nhưng ứng dụng web có thể dùng nhiều memory ngoài heap qua Netty direct buffer, compression, TLS, native library, thread stack hoặc memory-mapped file.

### 4.2. `-Xms` và `-Xmx`

- `-Xms`: heap ban đầu.
- `-Xmx`: heap tối đa.

Ví dụ:

~~~bash
java -Xms256m -Xmx384m -jar app.jar
~~~

Không nên đặt `-Xmx` bằng đúng memory limit container. Cần chừa headroom cho off-heap và biến động workload.

Ví dụ container limit `512m` có thể bắt đầu với heap tối đa theo phần trăm:

~~~bash
docker run --memory=512m \
  -e JAVA_TOOL_OPTIONS='-XX:MaxRAMPercentage=65 -XX:InitialRAMPercentage=25' \
  task-api:0.3.0
~~~

Đây chỉ là điểm bắt đầu, không phải công thức đúng cho mọi app. Cần đo heap, RSS, GC pause và throughput.

### 4.3. `MaxRAMPercentage`

Dùng phần trăm giúp cấu hình thích ứng với nhiều memory limit:

~~~bash
java -XX:MaxRAMPercentage=65.0 -jar app.jar
~~~

Các flag thường gặp:

- `MaxRAMPercentage`: tỷ lệ memory JVM dùng để tính max heap.
- `InitialRAMPercentage`: tỷ lệ memory cho heap ban đầu.
- `MinRAMPercentage`: hành vi cho memory nhỏ theo JVM/version.

Tên và behavior chi tiết nên kiểm tra trên JDK đang dùng. Đừng suy ra `MaxRAMPercentage=65` có nghĩa process chỉ dùng 65%; nó chủ yếu liên quan heap, không bao phủ toàn bộ native memory.

### 4.4. Heap quá lớn và quá nhỏ

Heap quá lớn:

- Ít headroom cho metaspace/native/thread/direct memory.
- Tăng nguy cơ container bị OOM kill.
- GC cycle có thể xử lý lượng heap lớn hơn.

Heap quá nhỏ:

- GC thường xuyên.
- Allocation pressure và CPU tăng.
- Throughput giảm.
- Có thể gặp Java heap `OutOfMemoryError` dù container chưa chạm limit.

Mục tiêu là tìm điểm cân bằng bằng workload thực tế.

### 4.5. Heap OOM và container OOM khác nhau

**Java heap OOM** thường xuất hiện trong log:

~~~text
java.lang.OutOfMemoryError: Java heap space
~~~

Process có thể tự exit hoặc framework xử lý theo cách khác.

**Container OOM kill** thường được Docker báo:

~~~bash
docker inspect --format 'status={{.State.Status}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}}' <container>
~~~

Nếu `OOMKilled=true`, kernel/container runtime đã kill process do vượt memory cgroup. Java có thể chưa kịp ghi exception.

---

## 5. Chạy app với memory limit

Giả sử image `task-api:0.3.0` đã có:

~~~bash
docker run --name task-api-memory \
  --memory=512m \
  --memory-swap=512m \
  -e JAVA_TOOL_OPTIONS='-XX:MaxRAMPercentage=65 -Xlog:gc*:stdout:time,uptime,level,tags' \
  -p 8080:8080 \
  task-api:0.3.0
~~~

Quan sát:

~~~bash
docker stats task-api-memory
docker logs task-api-memory
docker inspect --format 'status={{.State.Status}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}} error={{.State.Error}}' task-api-memory
~~~

`--memory-swap=512m` trong ví dụ giới hạn tổng memory+swap ở mức tương ứng, nhưng behavior còn phụ thuộc host/runtime. Hãy kiểm tra `docker info` và môi trường thực tế.

Dừng lab:

~~~bash
docker rm -f task-api-memory
~~~

Không nên đặt memory limit trong production mà không có quan sát, load test và alert.

---

## 6. GC trong container

Garbage Collector thu hồi object không còn được tham chiếu trong heap. GC không thu hồi trực tiếp mọi memory native ngoài heap.

### 6.1. Bật GC log trên Java hiện đại

Với JDK hiện đại, Unified Logging thường dùng:

~~~bash
-Xlog:gc*:stdout:time,uptime,level,tags
~~~

Ví dụ:

~~~bash
docker run --rm \
  -e JAVA_TOOL_OPTIONS='-Xlog:gc*:stdout:time,uptime,level,tags' \
  task-api:0.3.0
~~~

Có thể ghi ra file, nhưng stdout/stderr thường thuận tiện hơn cho Docker log collection. Nếu ghi file trong container, cần nghĩ đến rotation, volume và lifecycle.

### 6.2. Đọc GC log

Tìm các tín hiệu:

- Tần suất GC.
- Thời gian pause.
- Heap trước/sau GC.
- Full GC.
- Allocation rate.
- Concurrent cycle.
- `OutOfMemoryError`.

Một log có nhiều GC chưa chắc là lỗi; cần liên hệ với latency, throughput và memory usage.

### 6.3. Chọn GC

JDK có collector mặc định tùy version/hardware. Không đổi collector chỉ vì thấy một blog dùng flag khác. Chỉ thay đổi sau khi:

1. Có baseline.
2. Biết latency/throughput objective.
3. Chạy load test tương tự production.
4. So sánh pause, CPU, heap và error.

### 6.4. GC log không thay thế metrics

GC log giúp chẩn đoán chi tiết nhưng production nên có metrics/observability cho heap used/max, pause time, allocation, threads, process RSS và container memory working set.

---

## 7. Thread count và native memory

Mỗi thread thường cần thread stack. Nhiều thread làm tăng memory ngoài heap và có thể làm container OOM dù heap còn trống.

Xem thread trong container:

~~~bash
docker exec task-api jcmd 1 Thread.print
~~~

Lệnh `jcmd` có thể không có trong JRE runtime image. Khi đó dùng JDK/debug image, JVM endpoint hoặc metrics phù hợp.

Kiểm tra số thread ở mức process:

~~~bash
docker exec task-api sh -c 'ps -o pid,ppid,nlwp,rss,args -p 1'
~~~

Một số nguồn tạo thread:

- Tomcat/Jetty/Netty worker pool.
- Database connection pool.
- Scheduler.
- Async executor.
- ForkJoinPool/common pool.
- Metrics/tracing agent.
- JVM GC/compiler threads.

Không đặt thread pool quá cao chỉ để “chịu được nhiều request”. Thread count cần phù hợp CPU, I/O, connection pool và latency target.

Các thông số Spring Boot cần cân nhắc tùy server:

~~~properties
server.tomcat.threads.max=100
server.tomcat.threads.min-spare=10
spring.task.execution.pool.max-size=20
spring.task.scheduling.pool.size=4
~~~

Đây là ví dụ, không phải giá trị production mặc định. Đo trước khi thay đổi.

### 7.1. Native memory tracking

JDK có Native Memory Tracking, nhưng có overhead:

~~~bash
java -XX:NativeMemoryTracking=summary -jar app.jar
jcmd 1 VM.native_memory summary
~~~

Nó hữu ích khi debug chênh lệch giữa heap và RSS, nhưng runtime image tối giản có thể không có `jcmd`. Bật trong debug/test có chủ đích, không mặc định mọi production workload.

---

## 8. Signal handling, PID 1 và shutdown

### 8.1. Vì sao PID 1 quan trọng?

Process chính của container thường là PID 1 trong namespace. PID 1 có behavior đặc biệt với signal và việc reaping child process.

Kiểm tra:

~~~bash
docker exec task-api ps -o pid,ppid,stat,args
~~~

Nếu Java chạy trực tiếp bằng exec-form:

~~~dockerfile
ENTRYPOINT ["java", "-jar", "app.jar"]
~~~

Java thường là process chính và nhận signal từ Docker.

Nếu dùng shell form hoặc wrapper không `exec`:

~~~dockerfile
ENTRYPOINT sh -c "java -jar app.jar"
~~~

shell có thể trở thành PID 1, signal không đến Java như mong đợi hoặc child process không được xử lý đúng.

Wrapper đúng hướng:

~~~sh
#!/bin/sh
set -eu
exec java -jar /app/app.jar "$@"
~~~

`exec` thay shell bằng Java process, giúp signal và exit code đi đến process chính rõ hơn.

### 8.2. `docker stop` và SIGTERM

Khi stop container, Docker thường gửi signal yêu cầu kết thúc và chờ timeout trước khi force kill:

~~~bash
docker stop --time 30 task-api
~~~

Ứng dụng cần:

1. Nhận signal.
2. Ngừng nhận request mới.
3. Cho request đang xử lý thời gian hoàn tất phù hợp.
4. Đóng connection/pool.
5. Flush log/metric nếu cần.
6. Exit trước timeout.

Nếu không exit kịp, Docker có thể force kill và request/dữ liệu đang xử lý có thể bị cắt.

### 8.3. Kiểm tra signal

Theo dõi log trong terminal:

~~~bash
docker logs -f task-api
~~~

Ở terminal khác:

~~~bash
time docker stop --time 30 task-api
~~~

Quan sát thời gian stop và log shutdown của Spring Boot. Đừng dùng `docker kill` cho bài test graceful shutdown vì nó bỏ qua flow bình thường.

---

## 9. Spring Boot graceful shutdown

Với Spring Boot hỗ trợ graceful shutdown, cấu hình:

~~~properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=20s
~~~

Hoặc environment:

~~~bash
docker run --rm \
  -e SERVER_SHUTDOWN=graceful \
  -e SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=20s \
  task-api:0.3.0
~~~

Khi nhận shutdown, server ngừng nhận request mới và chờ request đang xử lý trong giới hạn cấu hình. Cần đồng bộ:

~~~text
Docker stop timeout
    ≥ Spring shutdown timeout
    ≥ thời gian request cần hoàn tất
~~~

Ví dụ Docker chờ 10 giây nhưng Spring cần 20 giây thì Docker có thể kill trước khi Spring hoàn tất. Chọn timeout theo SLA và workload, không copy số ngẫu nhiên.

### 9.1. Kiểm thử với request chậm

Tạo endpoint hoặc test request mất vài giây ở môi trường local. Gửi request rồi stop container:

~~~bash
time docker stop --time 30 task-api
~~~

Xác minh:

- Request có hoàn tất không?
- Log có ghi graceful shutdown không?
- Connection tới database/Redis có đóng không?
- Container exit code là gì?
- Stop timeout có đủ không?

### 9.2. Shutdown không phải backup

Graceful shutdown giúp process dừng có trật tự; nó không tạo snapshot database hay bảo đảm mọi dữ liệu chưa commit được lưu. Transaction/backup cần cơ chế riêng.

---

## 10. Spring Boot container startup

Startup time của Spring Boot gồm nhiều phần:

~~~text
Container process start
→ JVM initialization
→ class loading/JIT
→ Spring context
→ auto-configuration
→ connection pool
→ migrations/cache warm-up
→ web server listen
→ ready
~~~

Không nên đánh giá startup chỉ bằng thời gian `docker run` trả về, đặc biệt khi chạy detached. Container running không đồng nghĩa application đã ready.

### 10.1. Startup log và readiness

~~~bash
docker logs -f task-api
curl http://localhost:8080/actuator/health
~~~

Nếu có Spring Boot Actuator, endpoint health cần được bảo vệ và expose đúng scope. Healthcheck/readiness nên phản ánh khả năng phục vụ thật, không chỉ process còn sống.

### 10.2. Startup timeout

Khi app khởi động chậm vì migration, remote dependency hoặc JIT, healthcheck cần `start-period` đủ dài. Nếu check quá sớm, container có thể bị đánh dấu unhealthy dù app vẫn khởi động bình thường.

### 10.3. JVM startup options

Đặt JVM options qua `JAVA_TOOL_OPTIONS` hoặc command strategy có kiểm soát:

~~~bash
docker run --rm \
  -e JAVA_TOOL_OPTIONS='-XX:MaxRAMPercentage=65 -Xlog:gc*:stdout:time,uptime,level,tags' \
  task-api:0.3.0
~~~

Không nối chuỗi shell phức tạp nếu không cần. Nếu dùng entrypoint wrapper để expand biến, wrapper phải xử lý quote và `exec` đúng.

---

## 11. Layered JAR

Một fat JAR Spring Boot thường chứa:

- Dependencies.
- Spring Boot loader.
- Application classes.
- Application resources.

Nếu mỗi lần sửa source đều copy lại toàn bộ JAR vào image, Docker không tận dụng tốt layer cache cho dependency. Layered JAR tách nội dung ổn định và thay đổi thường xuyên:

~~~text
Dependencies (ít đổi)
Spring Boot loader
Snapshot dependencies
Application classes/resources (đổi thường xuyên)
~~~

Khi chỉ sửa code app, các layer dependency có thể được reuse.

### 11.1. Bật layered jar

Spring Boot Maven plugin có hỗ trợ layered archive trong các version phù hợp. Kiểm tra `pom.xml` và version Spring Boot của project trước khi dùng.

Khái niệm cấu hình:

~~~xml
<plugin>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-maven-plugin</artifactId>
  <configuration>
    <layers>
      <enabled>true</enabled>
    </layers>
  </configuration>
</plugin>
~~~

Nhiều version Spring Boot đã bật hoặc hỗ trợ layered jar theo cấu hình mặc định. Không thêm cấu hình mù quáng; hãy kiểm tra output JAR và tài liệu đúng version.

### 11.2. Extract layers trong Dockerfile

Cách extract phụ thuộc Spring Boot version/tooling. Một số version dùng:

~~~bash
java -Djarmode=layertools -jar app.jar extract
~~~

Các version mới có thể dùng tools jarmode với syntax khác, chẳng hạn `-Djarmode=tools` và command `extract --layers`. Hãy kiểm tra command được version Spring Boot của project hỗ trợ.

Mẫu Dockerfile theo cách phổ biến:

~~~dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw dependency:go-offline
COPY src src
RUN ./mvnw package -DskipTests

FROM eclipse-temurin:21-jre AS extract
WORKDIR /layers
COPY --from=build /workspace/target/*.jar app.jar
RUN java -Djarmode=layertools -jar app.jar extract

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
COPY --from=extract /layers/dependencies/ ./
COPY --from=extract /layers/spring-boot-loader/ ./
COPY --from=extract /layers/snapshot-dependencies/ ./
COPY --from=extract /layers/application/ ./
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
~~~

Tên layer, launcher class và jarmode có thể khác giữa Spring Boot major versions. Nếu không chắc, dùng fat JAR multi-stage cơ bản trước; tối ưu layered JAR sau khi có build/test đo được.

### 11.3. Layered JAR có phải luôn tốt hơn?

Không nhất thiết. Trade-off:

- Dockerfile phức tạp hơn.
- Phụ thuộc Spring Boot plugin/version.
- Debug path và launcher khác.
- Build có thêm bước extract.
- Lợi ích lớn hơn khi dependency ổn định và source thay đổi thường xuyên.

Đo thời gian build/push và image layer trước/sau, không tối ưu chỉ vì “nhiều layer hơn”.

---

## 12. Lab 1: Heap sizing và OOMKilled

Dùng một image Java có command giữ process sống hoặc image app thật:

~~~bash
docker run --name java-memory \
  --memory=256m \
  --memory-swap=256m \
  -e JAVA_TOOL_OPTIONS='-XX:MaxRAMPercentage=60 -Xlog:gc*:stdout:time,uptime,level,tags' \
  task-api:0.3.0
~~~

Quan sát:

~~~bash
docker stats java-memory
docker logs java-memory
docker inspect --format 'status={{.State.Status}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}}' java-memory
~~~

Tạo workload memory tăng dần trong môi trường test, không dùng production. Khi container bị kill, ghi lại:

- Memory limit.
- Container memory usage trước khi chết.
- Heap max JVM.
- RSS/working set nếu có.
- `OOMKilled`.
- Exit code.
- GC log cuối cùng.

Dọn:

~~~bash
docker rm -f java-memory
~~~

---

## 13. Lab 2: SIGTERM và graceful shutdown

Chạy app có graceful shutdown:

~~~bash
docker run --name java-shutdown -d \
  -e SERVER_SHUTDOWN=graceful \
  -e SPRING_LIFECYCLE_TIMEOUT_PER_SHUTDOWN_PHASE=20s \
  -p 8080:8080 \
  task-api:0.3.0
~~~

Theo dõi log và stop:

~~~bash
docker logs -f java-shutdown
# terminal khác
time docker stop --time 30 java-shutdown
~~~

Kiểm tra:

~~~bash
docker inspect --format 'status={{.State.Status}} exit={{.State.ExitCode}} error={{.State.Error}}' java-shutdown
~~~

Lặp lại với entrypoint shell wrapper không có `exec` trong môi trường test để quan sát signal behavior, sau đó sửa wrapper bằng `exec`. Không đưa wrapper lỗi vào production image.

Dọn:

~~~bash
docker rm java-shutdown
~~~

---

## 14. Lab 3: Startup và healthcheck

Chạy app với health endpoint và healthcheck phù hợp với tool có trong image:

~~~dockerfile
HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/actuator/health || exit 1
~~~

Build và chạy:

~~~bash
docker build -t task-api:health .
docker run --name java-health -d -p 8080:8080 task-api:health
docker ps
docker inspect --format '{{json .State.Health}}' java-health
~~~

Nếu image không có `wget`, healthcheck sẽ fail. Không cài tool chỉ để làm ví dụ mà không cân nhắc size/security; chọn tool/base phù hợp hoặc dùng cơ chế health bên ngoài.

---

## 15. Lab 4: Layered JAR

1. Kiểm tra Spring Boot version và plugin.
2. Build JAR.
3. Kiểm tra JAR có hỗ trợ layered tools.
4. Extract thử trong build stage.
5. Build image layered.
6. Sửa một class, build lại với progress rõ.
7. So sánh layer rebuild với fat JAR image.

Các lệnh khám phá tùy version:

~~~bash
./mvnw package -DskipTests
java -Djarmode=layertools -jar target/app.jar list
# hoặc command jarmode tools tương ứng với Spring Boot version
~~~

Không coi command trên là universal. Nếu không hỗ trợ, quay lại multi-stage fat JAR; đó vẫn là runtime image đúng và dễ bảo trì.

---

## 16. Lỗi thường gặp

### Container bị OOM dù heap chưa đầy

Memory ngoài heap có thể cao: thread stack, metaspace, direct buffer, native library hoặc agent. Xem `OOMKilled`, container stats và JVM metrics cùng nhau.

### `-Xmx` bằng memory limit

Đây là cấu hình rủi ro vì không chừa headroom. Giảm heap hoặc tăng limit sau khi đo workload; đừng chỉ tăng limit vô hạn.

### App không nhận SIGTERM

Thường do shell wrapper giữ PID 1 nhưng không `exec`, hoặc process tree không được quản lý đúng. Dùng exec-form hoặc wrapper có `exec`.

### `docker stop` luôn mất đủ timeout

Ứng dụng có thể không thoát, shutdown hook bị block, connection pool chờ lâu hoặc signal không tới Java. Kiểm tra PID 1, logs, thread dump và timeout từng layer.

### `jcmd`/`jmap` không có trong runtime image

Đây có thể là chủ đích của JRE/distroless. Dùng debug image, JDK tool, JMX/metrics/JFR hoặc ephemeral diagnostic tooling phù hợp.

### Healthcheck unhealthy khi app vẫn khởi động

`start-period` quá ngắn, endpoint sai, tool không tồn tại, bind port sai hoặc app chưa ready. Kiểm tra command healthcheck từ bên trong container.

### Layered JAR không chạy

Nguyên nhân có thể là jarmode/launcher sai version, layer path sai hoặc copy thiếu layer. Xác minh Spring Boot version, plugin output và command chính xác.

### Thread count tăng làm container chậm/OOM

Framework pool, connection pool, scheduler, agent và JVM thread đều góp vào memory/CPU. Đừng chỉ tăng heap; kiểm tra thread dump và giới hạn pool.

---

## 17. Bài tập tự làm

### Bài 1: Heap budget

Chạy app với memory limit `256m`, `512m` và `1g`. Dùng `MaxRAMPercentage` khác nhau, ghi lại heap max, RSS, GC và latency. Đề xuất một budget có headroom.

### Bài 2: OOMKilled vs Java heap OOM

Tạo hai thử nghiệm: một làm heap đầy, một làm container vượt cgroup limit. So sánh log, exit code, `OOMKilled` và cách chẩn đoán.

### Bài 3: Graceful shutdown

Cấu hình Spring Boot graceful shutdown, tạo request chậm, gửi `docker stop --time 30` trong lúc request chạy và xác minh request có hoàn tất.

### Bài 4: PID 1

So sánh exec-form Java với shell wrapper có và không có `exec`. Gửi stop và quan sát log/shutdown behavior.

### Bài 5: Runtime footprint

So sánh image JDK, JRE và debug image: size, package, user, shell/tool, startup và scan result.

### Bài 6: Layered JAR

Nếu Spring Boot version hỗ trợ, bật layered JAR, build image, sửa source và đo layer/cache reuse. Nếu không hỗ trợ command mẫu, ghi lại nguyên nhân và dùng fat JAR multi-stage.

---

## 18. Câu hỏi kiểm tra và đáp án gợi ý

1. Vì sao memory limit không thể dùng toàn bộ cho Java heap?
2. Container-aware JVM làm gì?
3. `-Xmx` khác `MaxRAMPercentage` thế nào?
4. `OOMKilled=true` khác Java heap `OutOfMemoryError` ra sao?
5. Vì sao thread count ảnh hưởng memory container?
6. Vì sao Java nên là PID 1 hoặc được wrapper `exec` đúng cách?
7. `docker stop` cần liên hệ thế nào với Spring shutdown timeout?
8. Vì sao container running chưa chắc Spring Boot đã ready?
9. Layered JAR tách những nhóm layer nào?
10. Vì sao debug image nên khác production image?
11. Runtime JRE/distroless có hạn chế gì khi debug?
12. Vì sao runtime image vẫn cần scan và SBOM?

Đáp án ngắn: memory còn gồm native/off-heap/thread/runtime; JVM đọc cgroup để tính resource; `-Xmx` là heap tuyệt đối còn percentage thích ứng theo limit; OOMKilled do cgroup kill còn heap OOM do JVM; mỗi thread cần stack; PID 1 nhận signal; Docker timeout phải đủ cho Spring shutdown; process sống không đồng nghĩa readiness; layered JAR tách dependency/loader/application; debug tool làm tăng footprint; runtime tối giản thiếu shell/tool; runtime vẫn có base/dependency vulnerability nên cần scan/SBOM.

---

## 19. Tiêu chí hoàn thành tuần 7

- [ ] Giải thích được JVM container awareness.
- [ ] Phân biệt memory limit, heap, off-heap và native memory.
- [ ] Cấu hình được `MaxRAMPercentage` hoặc heap limit có headroom.
- [ ] Quan sát được CPU, memory, heap và GC log.
- [ ] Phân biệt Java heap OOM với container `OOMKilled`.
- [ ] Kiểm tra được thread count và biết các nguồn tạo thread.
- [ ] Đảm bảo Java nhận SIGTERM qua exec-form hoặc wrapper có `exec`.
- [ ] Cấu hình và test Spring Boot graceful shutdown.
- [ ] Phân biệt process startup với application readiness.
- [ ] Hiểu layered JAR và kiểm tra compatibility theo Spring Boot version.
- [ ] Biết runtime image không nhất thiết có `jcmd`, shell hoặc tool debug.
- [ ] So sánh được JDK/JRE/debug image và dependency footprint.
- [ ] Biết runtime image vẫn cần scan/SBOM.

Kiểm tra cuối buổi:

~~~bash
docker inspect --format 'status={{.State.Status}} exit={{.State.ExitCode}} oom={{.State.OOMKilled}}' <container>
docker stats --no-stream <container>
docker logs <container>
docker inspect --format '{{.Config.Entrypoint}} {{.Config.Cmd}}' <container>
~~~

Mục tiêu của tuần này là chạy Java ổn định trong ngân sách tài nguyên hữu hạn, dừng đúng cách và có đủ dữ liệu để điều tra khi runtime gặp vấn đề.
