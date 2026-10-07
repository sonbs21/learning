# Tuần 15 — Observability và resilience

> Stack: Java 21, Spring Boot 4.x, Spring Boot Actuator, Micrometer, Prometheus, OpenTelemetry và Resilience4j. Bài này nối tiếp Task API, authentication, database, cache và background job từ các tuần trước.

## 1. Mục tiêu bài học

Sau tuần này, bạn có thể:

- Expose Actuator có kiểm soát thay vì mở toàn bộ endpoint.
- Phân biệt liveness, readiness và health dependency.
- Xuất metrics bằng Micrometer và Prometheus.
- Đặt tên metric, tag và cardinality an toàn.
- Viết structured logging có correlation ID.
- Hiểu trace ID, span và context propagation qua request/async.
- Đặt timeout ở client, server và database.
- Retry có giới hạn cho lỗi transient và operation idempotent.
- Dùng circuit breaker, bulkhead và fallback đúng chỗ.
- Thực hiện graceful shutdown mà không nhận request mới nhưng hoàn tất request đang chạy.
- Thiết kế dashboard, alert và runbook có hành động cụ thể.
- Test failure mode thay vì chỉ test happy path.

Observability trả lời câu hỏi “hệ thống đang xảy ra chuyện gì?”. Resilience trả lời “khi dependency chậm hoặc hỏng, hệ thống còn phục vụ được thế nào?”. Hai phần phải đi cùng nhau: timeout/retry mà không có metric và trace sẽ rất khó vận hành.

---

## 2. Ba tín hiệu observability

### 2.1. Logs

Log là sự kiện có ngữ cảnh:

~~~text
request_id=... trace_id=... method=GET route=/api/tasks status=200 duration_ms=42
~~~

Log phù hợp để điều tra một request hoặc một lỗi cụ thể. Log không nên là nơi duy nhất để đếm tỷ lệ lỗi; hãy dùng metric cho số liệu tổng hợp.

### 2.2. Metrics

Metric là chuỗi thời gian được tổng hợp:

- Counter: số request, số lỗi, số login thất bại.
- Gauge: số task đang chờ, số connection đang dùng.
- Timer: latency và phân bố thời gian xử lý.
- Distribution summary: kích thước payload hoặc giá trị đo không nhất thiết là thời gian.

Metric giúp dashboard và alert. Tránh đưa `userId`, `taskId`, email hoặc URL đầy đủ vào tag vì mỗi giá trị mới có thể tạo một time series mới.

### 2.3. Traces

Trace nối các span của một request qua nhiều service:

~~~text
HTTP request
 ├─ controller span
 ├─ database query span
 └─ HTTP call to notification-provider span
~~~

Trace giúp tìm “nút thắt” trong distributed system. Không ghi token, password hoặc dữ liệu nhạy cảm vào span attribute.

---

## 3. Spring Boot Actuator

### 3.1. Dependency và endpoint cơ bản

~~~xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
~~~

Chỉ expose endpoint cần dùng:

~~~yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      show-details: never
  server:
    # Có thể tách port quản trị khỏi port API.
    port: 8081
~~~

Endpoint nguy hiểm như `env`, `beans`, `heapdump`, `loggers` và `shutdown` không được public Internet. Nếu cần dùng, bảo vệ bằng network policy, mTLS hoặc authentication riêng; không chỉ dựa vào việc đổi base path.

Actuator không thay thế authorization của API. Nếu management port nằm chung với API port, cấu hình security rule cho `/actuator/**` rõ ràng và kiểm tra bằng integration test.

### 3.2. Health, liveness và readiness

- **Liveness**: tiến trình còn sống và có thể tự khôi phục không? Không nên fail chỉ vì database tạm thời lỗi.
- **Readiness**: instance có sẵn sàng nhận traffic không? Có thể fail khi database bắt buộc chưa kết nối hoặc app đang drain.
- **Health**: trạng thái tổng hợp của app và dependency.

~~~yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        readiness:
          include: readinessState,db
        liveness:
          include: livenessState
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
~~~

Trong Kubernetes, readiness fail thì Service ngừng gửi request tới pod; liveness fail có thể khiến container bị restart. Không đưa dependency dễ lỗi vào liveness, nếu không database outage có thể làm Kubernetes restart toàn bộ pod và gây cascading failure.

Ví dụ custom health indicator:

~~~java
@Component("notificationProvider")
public class NotificationProviderHealth implements HealthIndicator {

    private final NotificationClient client;

    public NotificationProviderHealth(NotificationClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            client.ping(Duration.ofMillis(300));
            return Health.up().build();
        } catch (Exception exception) {
            return Health.down()
                    .withDetail("reason", "provider_unreachable")
                    .build();
        }
    }
}
~~~

Health detail không nên chứa hostname nội bộ, stack trace, credentials hoặc response từ provider. Health check phải nhanh, có timeout và không tạo tải lớn.

### 3.3. Info và build metadata

`/actuator/info` chỉ nên trả thông tin không nhạy cảm:

~~~yaml
info:
  app:
    name: task-api
    version: ${APP_VERSION:local}
  build:
    commit: ${GIT_COMMIT:unknown}
~~~

Không đưa environment variable toàn bộ, database URL, branch private hoặc secret vào `info`.

---

## 4. Metrics với Micrometer và Prometheus

### 4.1. Metrics endpoint

~~~yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    prometheus:
      enabled: true
  metrics:
    tags:
      application: task-api
    distribution:
      percentiles-histogram:
        http:
          server:
            requests: true
~~~

Prometheus scrape endpoint thường là `/actuator/prometheus`. Chỉ cho Prometheus network được phép truy cập endpoint này; endpoint không phải một API public.

Ví dụ scrape:

~~~yaml
scrape_configs:
  - job_name: task-api
    metrics_path: /actuator/prometheus
    static_configs:
      - targets: ["task-api:8081"]
~~~

### 4.2. Custom counter và timer

~~~java
@Service
public class TaskMetrics {

    private final Counter tasksCreated;
    private final Timer createLatency;

    public TaskMetrics(MeterRegistry registry) {
        tasksCreated = Counter.builder("task.created")
                .description("Number of tasks created")
                .tag("source", "api")
                .register(registry);
        createLatency = Timer.builder("task.create.duration")
                .description("Task creation duration")
                .publishPercentileHistogram()
                .register(registry);
    }

    public void recordCreated() {
        tasksCreated.increment();
    }

    public <T> T recordCreate(Supplier<T> operation) {
        return createLatency.record(operation);
    }
}
~~~

Tên metric nên ổn định, dùng tag cho chiều phân tích nhỏ và có allowlist:

~~~java
Timer.builder("task.http.request")
        .tag("operation", "create")
        .tag("status", "success")
        .register(registry);
~~~

Không dùng:

~~~java
// Sai: cardinality tăng theo dữ liệu người dùng.
registry.counter("task.request", "taskId", taskId.toString()).increment();
~~~

Đưa `taskId`, `userId`, email, exception message hoặc URL có query string vào tag có thể làm Prometheus tốn RAM và chậm query. Những dữ liệu đó phù hợp với log/trace có sampling và redaction.

### 4.3. Các metric cho Task API

Tối thiểu nên có:

| Metric | Loại | Tag an toàn |
|---|---|---|
| `task.created.total` | Counter | `source` |
| `http.server.requests` | Timer | route, method, status |
| `task.create.duration` | Timer | outcome |
| `auth.login.failures.total` | Counter | reason đã chuẩn hóa |
| `background.job.duration` | Timer | job_name, outcome |
| `background.job.failures.total` | Counter | job_name |
| `resilience4j_circuitbreaker_state` | Gauge | breaker_name |
| `resilience4j_circuitbreaker_calls` | Counter | breaker_name, kind |

Metric name cụ thể phụ thuộc registry và naming convention. Kiểm tra output thực tế trên `/actuator/prometheus`, không đoán tên từ code.

### 4.4. Alert hữu ích

Alert nên có ngưỡng, thời gian duy trì và runbook:

- Tỷ lệ HTTP 5xx > 2% trong 5 phút.
- p95 latency route tạo task vượt 500 ms trong 10 phút.
- Readiness fail trên nhiều pod.
- Circuit breaker mở quá 2 phút.
- Queue async gần đầy hoặc rejection tăng.
- Background job trễ hơn SLA.
- Login failure tăng đột biến theo account/IP đã được rate-limit.

Đừng alert cho mọi exception đơn lẻ. Alert quá nhạy sẽ tạo fatigue và bị bỏ qua.

---

## 5. Structured logging

### 5.1. Log event, không nối chuỗi tùy tiện

~~~java
private static final Logger log =
        LoggerFactory.getLogger(TaskService.class);

log.info("task_created taskId={} actorId={} durationMs={}",
        taskId, actorId, durationMs);

log.warn("notification_timeout provider={} timeoutMs={}",
        "email", timeout.toMillis());

log.error("task_create_failed actorId={} reason={}",
        actorId, "database_error", exception);
~~~

Production nên xuất JSON để log collector parse được:

~~~xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
</dependency>
~~~

Ví dụ `logback-spring.xml` tối giản:

~~~xml
<configuration>
    <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="net.logstash.logback.encoder.LogstashEncoder"/>
    </appender>
    <root level="INFO">
        <appender-ref ref="JSON"/>
    </root>
</configuration>
~~~

Không log:

- Authorization header, cookie, JWT hoặc refresh token.
- Password, API key, private key.
- Toàn bộ request/response body có dữ liệu riêng tư.
- SQL bind value nhạy cảm.

Redaction phải nằm ở logger/collector và ở code producer; không đợi đến dashboard mới che.

### 5.2. Correlation ID

Correlation ID là ID dùng để nối log của một request hoặc một workflow. Nếu client gửi `X-Request-Id`, chỉ chấp nhận giá trị hợp lệ, giới hạn độ dài và tạo mới nếu thiếu. Không dùng giá trị tùy ý làm log injection.

~~~java
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Request-Id";
    private static final int MAX_LENGTH = 100;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain
    ) throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = isSafe(incoming)
                ? incoming
                : UUID.randomUUID().toString();

        try (MDC.MDCCloseable ignored = MDC.putCloseable("request_id", requestId)) {
            response.setHeader(HEADER, requestId);
            chain.doFilter(request, response);
        }
    }

    private boolean isSafe(String value) {
        return value != null
                && value.length() <= MAX_LENGTH
                && value.matches("[A-Za-z0-9._-]+");
    }
}
~~~

Nếu dùng tracing, trace ID từ tracing context nên là nguồn chính; correlation ID vẫn hữu ích khi nối với hệ thống không hỗ trợ trace.

MDC là thread-local. Xóa MDC sau request như ví dụ `try` để tránh dữ liệu của request trước rò sang thread tái sử dụng.

### 5.3. Async và context

MDC, SecurityContext và request scope không tự động truyền sang executor. Với async:

- Truyền `requestId`, `actorId` và `jobId` trong immutable command.
- Dùng `TaskDecorator` chỉ để copy context đã allowlist.
- Xóa context sau khi task kết thúc.
- Không dùng SecurityContext cũ để cấp quyền lâu dài cho background job.

---

## 6. Trace ID và distributed tracing

Spring Boot có thể tích hợp Micrometer Observation và OpenTelemetry để tạo trace/span. Một trace nên đi qua:

~~~text
client → API gateway → task-api → PostgreSQL
                              └→ notification-service
~~~

Dependencies thường dùng:

~~~xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>
~~~

Tên artifact có thể thay đổi theo Spring Boot BOM. Luôn để Spring Boot quản lý version và kiểm tra compatibility thay vì tự ghép version.

Ví dụ cấu hình exporter:

~~~yaml
management:
  tracing:
    sampling:
      probability: ${TRACING_SAMPLE_PROBABILITY:0.1}
  otlp:
    tracing:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://otel-collector:4318/v1/traces}
~~~

Sampling 100% có thể quá đắt ở production. Có thể sample cao hơn cho lỗi và thấp hơn cho traffic thành công tùy collector. Dù sampling thấp, metric và log lỗi vẫn phải đầy đủ.

Không đưa PII vào span attributes. Dùng các thuộc tính ổn định như `route`, `dependency`, `outcome`, `tenant_type` đã chuẩn hóa.

---

## 7. Timeout: giới hạn thời gian ở mọi boundary

Không có timeout nghĩa là một dependency chậm có thể giữ thread, connection và request vô hạn. Đặt timeout ở:

1. HTTP client: connect, read/response và overall call.
2. Database: query timeout, transaction timeout và pool acquisition.
3. Cache/Redis: command và connection timeout.
4. Async worker: deadline của job.
5. Server/load balancer: request timeout phù hợp.

Ví dụ `RestClient` có timeout bằng JDK HTTP client:

~~~java
@Bean
RestClient restClient(RestClient.Builder builder) {
    HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(300))
            .build();
    JdkClientHttpRequestFactory factory =
            new JdkClientHttpRequestFactory(httpClient);
    factory.setReadTimeout(Duration.ofSeconds(2));
    return builder.requestFactory(factory).build();
}
~~~

Timeout của caller phải lớn hơn tổng deadline của dependency và nhỏ hơn timeout của upstream. Đừng retry sau khi caller đã hết deadline; request chỉ tạo thêm tải vô ích.

Timeout không tự hủy side effect phía server. Với command quan trọng, cần idempotency key hoặc job status để client retry an toàn.

---

## 8. Retry có kiểm soát

Retry chỉ phù hợp với lỗi tạm thời:

- Connection reset.
- Timeout trước khi side effect chắc chắn xảy ra.
- HTTP 502/503/504 theo contract.
- Rate limit nếu tôn trọng `Retry-After`.

Không retry validation, 400, 401, 403, duplicate business key hoặc lỗi logic. Với POST gửi email/payment, chỉ retry khi có idempotency key và provider hỗ trợ.

Nếu dùng Resilience4j:

~~~xml
<!-- Chọn artifact Resilience4j tương thích với Spring Boot đang dùng.
     Với Spring Boot 3.x, starter phổ biến là resilience4j-spring-boot3. -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
~~~

`resilience4j-spring-boot3` là tên starter cho Spring Boot 3.x. Nếu project dùng Spring Boot 4.x, phải kiểm tra release tương thích của Resilience4j hoặc dùng integration/API tương ứng; không ép starter Boot 3 vào ứng dụng Boot 4. Nếu dùng annotation như `@Retry`, `@CircuitBreaker` hoặc `@TimeLimiter`, cần AOP để aspect được áp dụng. Luôn để BOM quản lý version thay vì tự ghép version. Ví dụ cấu hình giới hạn:

~~~yaml
resilience4j:
  retry:
    instances:
      notificationProvider:
        max-attempts: 3
        wait-duration: 200ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        enable-randomized-wait: true
        randomized-wait-factor: 0.5
        retry-exceptions:
          - java.net.SocketTimeoutException
          - java.io.IOException
  timelimiter:
    instances:
      notificationProvider:
        timeout-duration: 2s
        cancel-running-future: true
~~~

Backoff cần jitter để nhiều instance không retry cùng một thời điểm. Tổng thời gian retry phải nằm trong request/job deadline.

---

## 9. Circuit breaker

Circuit breaker có ba trạng thái:

~~~text
CLOSED → lỗi vượt ngưỡng → OPEN
OPEN → hết wait duration → HALF_OPEN
HALF_OPEN → probe thành công → CLOSED
HALF_OPEN → probe lỗi → OPEN
~~~

Khi breaker `OPEN`, request fail fast hoặc dùng fallback thay vì tiếp tục gọi dependency đang hỏng. Breaker không sửa dependency; nó bảo vệ tài nguyên của service.

~~~yaml
resilience4j:
  circuitbreaker:
    instances:
      notificationProvider:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 20
        minimum-number-of-calls: 10
        failure-rate-threshold: 50
        slow-call-rate-threshold: 50
        slow-call-duration-threshold: 1s
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
~~~

~~~java
@Service
public class NotificationGateway {

    private final NotificationClient client;

    @CircuitBreaker(name = "notificationProvider", fallbackMethod = "fallback")
    @Retry(name = "notificationProvider")
    @TimeLimiter(name = "notificationProvider")
    public CompletableFuture<DeliveryResult> send(NotificationCommand command) {
        return client.sendAsync(command);
    }

    private CompletableFuture<DeliveryResult> fallback(
            NotificationCommand command,
            Throwable cause
    ) {
        return CompletableFuture.completedFuture(
                DeliveryResult.queued(command.id())
        );
    }
}
~~~

Thứ tự decorator và behavior phụ thuộc integration. Fallback phải có semantics rõ: trả cached data, enqueue durable job, hoặc trả lỗi có thể hiểu được. Không dùng fallback giả thành công nếu notification chưa được lưu để xử lý lại.

---

## 10. Bulkhead và giới hạn tài nguyên

Một dependency chậm không được phép chiếm toàn bộ thread pool. Bulkhead tách tài nguyên theo dependency hoặc loại workload:

- Executor riêng cho notification.
- Connection pool riêng nếu thật sự cần.
- Semaphore giới hạn số call đồng thời.
- Queue capacity hữu hạn.

Ví dụ Resilience4j semaphore:

~~~yaml
resilience4j:
  bulkhead:
    instances:
      notificationProvider:
        max-concurrent-calls: 20
        max-wait-duration: 50ms
~~~

Bulkhead không thay timeout. Một call vẫn cần timeout, và rejection phải có metric. Khi queue đầy, trả lỗi có kiểm soát hoặc enqueue durable; không âm thầm drop công việc bắt buộc.

---

## 11. Graceful shutdown

Khi deploy phiên bản mới:

1. Đánh dấu instance không ready.
2. Load balancer ngừng gửi request mới.
3. Chờ request đang xử lý trong grace period.
4. Dừng nhận job mới.
5. Hoàn tất hoặc hủy có kiểm soát async task.
6. Đóng datasource, executor và client.

Cấu hình cơ bản:

~~~yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
~~~

Graceful shutdown không đảm bảo task kéo dài vô hạn sẽ hoàn tất. Async/job phải có checkpoint, idempotency key và cơ chế retry sau restart. Nếu dùng queue, ack message sau khi side effect thành công hoặc đã ghi trạng thái retry.

Đừng coi process signal là một transaction boundary. Một pod có thể bị kill sau grace period; dữ liệu bắt buộc phải được lưu durable trước khi ack.

---

## 12. Resilience cho endpoint Task API

Ví dụ luồng tạo task gọi notification:

~~~text
POST /tasks
  ├─ validate request
  ├─ transaction ghi task
  ├─ commit
  └─ outbox/event → notification worker
                    ├─ timeout 2s
                    ├─ retry tối đa 3 lần
                    ├─ circuit breaker
                    └─ dead-letter/manual review
~~~

Nguyên tắc:

- Không gọi email provider trong transaction ghi task.
- Không giữ DB transaction trong lúc chờ HTTP dependency.
- Ghi outbox event trong cùng transaction với task.
- Worker đọc outbox, dùng idempotency key `task-assigned:{taskId}:{version}`.
- Chỉ đánh dấu event `SENT` sau khi provider xác nhận.
- Lỗi transient chuyển sang retry với `next_attempt_at`.
- Lỗi permanent chuyển `FAILED` và alert.

Đây là cách nối Tuần 14 với Tuần 15: async giảm latency, còn outbox, timeout, retry và metric làm delivery có thể vận hành.

---

## 13. Kiểm tra resilience bằng failure injection

Đừng chỉ test khi PostgreSQL và email provider đều khỏe. Tạo các case:

- Provider trả 503 ba lần rồi thành công.
- Provider chậm hơn timeout.
- Provider mở connection nhưng không trả body.
- Redis unavailable.
- Database connection pool cạn.
- Executor queue đầy.
- Pod shutdown giữa lúc xử lý notification.
- Hai worker cùng xử lý một outbox event.

Mỗi case cần xác định:

1. Client nhận status/error gì?
2. Có retry không, bao nhiêu lần?
3. Có tạo duplicate side effect không?
4. Metric, log và trace nào xuất hiện?
5. Alert nào kích hoạt?
6. Dữ liệu có được retry hoặc đưa vào dead-letter không?

Test integration có thể dùng Testcontainers cho PostgreSQL/Redis và WireMock hoặc mock HTTP server cho provider. Dùng Awaitility cho async, không dùng `Thread.sleep` cố định.

---

## 14. Thiết kế dashboard và runbook

Dashboard tối thiểu:

### Golden signals

- Latency: p50, p95, p99 theo route.
- Traffic: requests/second và background jobs/second.
- Errors: HTTP 5xx, validation errors, dependency errors.
- Saturation: CPU, heap, DB pool, executor queue, Redis connections.

### Dependency panel

- HTTP call count/latency/error theo dependency.
- Circuit breaker state.
- Retry count và exhausted retry.
- Timeout count.
- Queue depth và age của oldest message.

### Runbook mẫu khi notification provider lỗi

1. Xem circuit breaker state và `notification_provider_errors_total`.
2. Mở trace một request lỗi để xác nhận dependency.
3. Kiểm tra queue/outbox oldest age.
4. Giảm traffic hoặc bật provider dự phòng nếu có.
5. Không tắt breaker để “thử lại” liên tục.
6. Sau khi provider hồi phục, theo dõi drain rate và duplicate rate.

Observability chỉ có giá trị khi người trực biết hành động tiếp theo.

---

## 15. Lỗi thường gặp

- Expose `env`, `beans`, `heapdump` hoặc `shutdown` ra Internet.
- Dùng database dependency trong liveness khiến pod restart dây chuyền.
- Health check không có timeout.
- Metric tag chứa `userId`, `taskId`, email hoặc exception message.
- Log access token, password, cookie hoặc request body nhạy cảm.
- Tin `X-Request-Id` từ client mà không validate.
- MDC không được clear sau request.
- Retry không có max attempts hoặc không có jitter.
- Retry POST có side effect nhưng không có idempotency key.
- Đặt timeout lớn hơn timeout của caller.
- Circuit breaker có fallback trả “success” dù chưa lưu công việc.
- Dùng một thread pool cho HTTP request, scheduler và notification.
- Không có graceful shutdown cho executor.
- Ack queue trước khi side effect thành công.
- Chỉ có log, không có metric/trace và alert.
- Alert không có runbook hoặc alert cho mọi exception.

---

## 16. Bài thực hành bắt buộc

1. Thêm Actuator và chỉ expose `health`, `info`, `prometheus`.
2. Tách readiness/liveness và kiểm tra response khi database lỗi.
3. Tạo metric cho task created, request latency, HTTP 5xx, login failure và background job.
4. Cấu hình Prometheus scrape và tạo dashboard golden signals.
5. Bật JSON logging với `request_id`, `trace_id`, route và duration.
6. Validate `X-Request-Id` và không log secret.
7. Thêm timeout cho HTTP client gọi notification provider.
8. Thêm retry tối đa 3 lần với exponential backoff và jitter.
9. Thêm circuit breaker và bulkhead cho provider.
10. Ghi outbox event cùng transaction tạo task.
11. Test provider chậm, 503, executor đầy và pod shutdown.
12. Viết runbook cho Redis outage, database outage và notification outage.

---

## 17. Câu hỏi tự kiểm tra

1. Liveness khác readiness ở quyết định vận hành nào?
2. Vì sao không đưa database vào liveness trong mọi trường hợp?
3. Counter, gauge và timer khác nhau thế nào?
4. Cardinality cao gây vấn đề gì cho Prometheus?
5. Correlation ID khác trace ID thế nào?
6. Vì sao phải validate `X-Request-Id`?
7. Timeout nên đặt ở những boundary nào?
8. Khi nào retry làm tình hình tệ hơn?
9. Vì sao backoff cần jitter?
10. Circuit breaker bảo vệ tài nguyên nào?
11. Fallback thế nào là nói dối người dùng?
12. Bulkhead khác thread pool chung ra sao?
13. Graceful shutdown xử lý queue message thế nào?
14. Vì sao outbox event cần idempotency key?
15. Alert tốt phải đi kèm thông tin gì?

---

## 18. Tiêu chí hoàn thành tuần 15

- [ ] Actuator chỉ expose endpoint cần thiết và có bảo vệ.
- [ ] Health, readiness, liveness có semantics rõ.
- [ ] Có metric cho request, task, login và background job.
- [ ] Metric không có high-cardinality tag.
- [ ] Prometheus scrape được endpoint quản trị.
- [ ] Log JSON có request/correlation ID và không lộ secret.
- [ ] Trace context đi qua HTTP client và async boundary phù hợp.
- [ ] External call có connect/read/overall timeout.
- [ ] Retry có giới hạn, jitter và chỉ áp dụng lỗi transient.
- [ ] Circuit breaker có fallback không đánh lừa.
- [ ] Bulkhead giới hạn tài nguyên dependency.
- [ ] Graceful shutdown có grace period và xử lý job đang chạy.
- [ ] Có test failure injection và duplicate prevention.
- [ ] Có dashboard, alert và runbook.

---

## 19. Gợi ý lịch học 7 ngày

| Ngày | Nội dung | Kết quả |
|---|---|---|
| 1 | Actuator, endpoint security, health | Probe an toàn |
| 2 | Micrometer, Prometheus, cardinality | Metrics và dashboard |
| 3 | Structured logging, MDC, correlation ID | Log tìm được một request |
| 4 | Trace, timeout và HTTP client | Biết dependency nào chậm |
| 5 | Retry, backoff, circuit breaker | Dependency lỗi không kéo sập API |
| 6 | Bulkhead, outbox và graceful shutdown | Job không mất/duplicate |
| 7 | Failure injection, alert và runbook | Có quy trình vận hành |

Tuần 16 theo roadmap chuyển sang Docker và triển khai Spring Boot: image, multi-stage build, network, volume, Compose, health check, environment variable và non-root container.

## 20. Tài liệu tham khảo

- Spring Boot Actuator endpoints: https://docs.spring.io/spring-boot/reference/actuator/endpoints.html
- Spring Boot observability: https://docs.spring.io/spring-boot/reference/actuator/observability.html
- Spring Boot monitoring: https://docs.spring.io/spring-boot/reference/actuator/monitoring.html
- Spring Boot graceful shutdown: https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html
- Micrometer concepts: https://docs.micrometer.io/micrometer/reference/concepts.html
- Prometheus metric types: https://prometheus.io/docs/concepts/metric_types/
- Resilience4j Spring Boot: https://resilience4j.readme.io/docs/getting-started-3
- OpenTelemetry Java: https://opentelemetry.io/docs/languages/java/
