# Tuần 8: Production-ready & Microservices

> Tài liệu tổng hợp kiến thức mục 8 trong lộ trình [spring-boot-roadmap.md](spring-boot-roadmap.md) — bài cuối cùng của series 8 tuần. Nội dung đã được đối chiếu với Spring Boot Reference (3.5), Spring Framework Reference (6.2), Resilience4j, và Spring for Apache Kafka qua Context7 để đảm bảo chính xác về cấu hình bảo mật Actuator, ngữ nghĩa cache annotation, và thứ tự aspect của Resilience4j.

---

## 1. Actuator

### 1.1 Health check & Metrics
`spring-boot-starter-actuator` cung cấp sẵn các endpoint giám sát ứng dụng qua HTTP:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health, metrics, info # liệt kê rõ endpoint muốn public, KHÔNG dùng "*" tùy tiện
  endpoint:
    health:
      show-details: when-authorized # chỉ user đã xác thực mới thấy chi tiết (VD trạng thái từng DB/service phụ thuộc)
```
- `/actuator/health`: tổng hợp trạng thái ứng dụng (UP/DOWN) và các thành phần phụ thuộc (database, disk space, custom health indicator...).
- `/actuator/metrics`: số liệu runtime (JVM memory, request count, response time...) qua Micrometer — thường được scrape bởi Prometheus/Grafana.

> **Quan trọng**: theo Spring Boot Reference, **mặc định chỉ endpoint `health` được public qua HTTP** vì lý do bảo mật — các endpoint khác (`env`, `beans`, `configprops`...) có thể lộ thông tin nhạy cảm (biến môi trường, cấu trúc nội bộ) nên phải **chủ động khai báo** qua `management.endpoints.web.exposure.include`.

### 1.2 Custom Endpoint
```java
@Component
@Endpoint(id = "features")
public class FeatureFlagsEndpoint {
    @ReadOperation
    public Map<String, Boolean> features() {
        return Map.of("newCheckout", true, "betaSearch", false);
    }
}
```

### 1.3 Bảo mật Actuator Endpoint — không để public
```java
@Bean
public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
    http.securityMatcher(EndpointRequest.toAnyEndpoint()) // chỉ áp dụng chain này cho mọi endpoint /actuator/**
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(EndpointRequest.to("health")).permitAll() // health cho phép public (dùng cho load balancer check)
            .anyRequest().hasRole("ENDPOINT_ADMIN") // các endpoint còn lại yêu cầu role riêng
        )
        .httpBasic(Customizer.withDefaults());
    return http.build();
}
```
`EndpointRequest.toAnyEndpoint()`/`EndpointRequest.to(...)` là matcher chuyên dụng của Spring Boot, tự động khớp đúng path của actuator dù có đổi `management.endpoints.web.base-path` — **không nên** để toàn bộ actuator endpoint public không xác thực trong môi trường production, kể cả khi nghĩ rằng "chỉ giám sát nội bộ".

---

## 2. Caching

### 2.1 `@EnableCaching`, `@Cacheable`, `@CacheEvict`, `@CachePut`
```java
@Configuration
@EnableCaching // bật cache abstraction của Spring
public class CacheConfig {}

@Service
public class ProductService {

    @Cacheable(cacheNames = "products", key = "#id")
    public Product findById(Long id) {
        // Chỉ CHẠY method này nếu cache CHƯA có kết quả cho key tương ứng
        // Lần gọi sau với cùng id sẽ lấy thẳng từ cache, KHÔNG chạy lại method
        return productRepository.findById(id).orElseThrow();
    }

    @CachePut(cacheNames = "products", key = "#product.id")
    public Product update(Product product) {
        // LUÔN chạy method (khác @Cacheable), nhưng kết quả trả về sẽ ghi đè vào cache
        // Dùng khi vừa muốn cập nhật dữ liệu thật, vừa muốn cache luôn phản ánh giá trị mới nhất
        return productRepository.save(product);
    }

    @CacheEvict(cacheNames = "products", key = "#id")
    public void delete(Long id) {
        // Xóa entry khỏi cache sau khi method chạy xong
        productRepository.deleteById(id);
    }

    @CacheEvict(cacheNames = "products", allEntries = true)
    public void clearAllCache() {
        // Xóa TOÀN BỘ entry trong cache "products", không cần biết key cụ thể
    }
}
```
`@Cacheable` hỗ trợ thêm `condition` (đánh giá **trước** khi gọi method, quyết định có dùng cache không) và `unless` (đánh giá **sau** khi có kết quả trả về, dùng để loại trừ 1 số kết quả không nên cache):
```java
@Cacheable(cacheNames = "book", condition = "#name.length() < 32", unless = "#result.hardback")
public Book findBook(String name) { ... }
```

### 2.2 Tích hợp Redis làm cache store phân tán
```yaml
spring:
  cache:
    type: redis
  data:
    redis:
      host: localhost
      port: 6379
```
Mặc định (không cấu hình `spring.cache.type`), Spring Boot dùng cache **in-memory** (`ConcurrentHashMap`) — chỉ hoạt động đúng khi ứng dụng chạy **1 instance duy nhất**. Khi scale ngang nhiều instance, cache in-memory của mỗi instance **không đồng bộ** với nhau (instance A cache 1 giá trị, instance B không biết) → cần **Redis** làm cache store tập trung, dùng chung cho mọi instance, đảm bảo dữ liệu cache nhất quán across toàn hệ thống.

---

## 3. Messaging

### 3.1 Kafka — Producer/Consumer cơ bản
```java
@Configuration
class KafkaTopicConfig {
    @Bean
    public NewTopic orderTopic() {
        return TopicBuilder.name("order-events").partitions(3).replicas(1).build();
    }
}

@Service
class OrderEventProducer {
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    OrderEventProducer(KafkaTemplate<String, OrderEvent> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    void publish(OrderEvent event) {
        kafkaTemplate.send("order-events", event.orderId(), event); // key = orderId đảm bảo cùng order luôn vào cùng partition
    }
}

@Component
class OrderEventConsumer {
    @KafkaListener(topics = "order-events", groupId = "notification-service")
    public void onOrderEvent(OrderEvent event) {
        System.out.println("Nhận sự kiện đơn hàng: " + event);
    }
}
```
`groupId` xác định **consumer group** — nhiều instance cùng `groupId` sẽ **chia nhau** đọc các partition của topic (mỗi message chỉ được 1 instance trong group xử lý), trong khi 2 `groupId` khác nhau đều nhận được **toàn bộ** message độc lập với nhau (phù hợp khi nhiều service khác nhau cùng cần biết về 1 sự kiện).

### 3.2 RabbitMQ — cơ bản
```java
@Configuration
class RabbitConfig {
    @Bean
    Queue orderQueue() { return new Queue("order.queue", true); } // durable = true, sống sót qua restart broker

    @Bean
    DirectExchange orderExchange() { return new DirectExchange("order.exchange"); }

    @Bean
    Binding binding(Queue orderQueue, DirectExchange orderExchange) {
        return BindingBuilder.bind(orderQueue).to(orderExchange).with("order.created");
    }
}
```
- **Exchange**: nơi message được gửi tới đầu tiên, quyết định **routing** message tới queue nào (dựa trên loại exchange: `Direct`, `Topic`, `Fanout`...).
- **Queue**: nơi message thực sự được lưu trữ chờ consumer xử lý.
- **Binding**: quy tắc liên kết exchange với queue (VD dựa trên `routing key`).

### 3.3 Khi nào chọn Message Queue thay vì gọi API trực tiếp
| Gọi API trực tiếp (REST/gRPC) | Message Queue (Kafka/RabbitMQ) |
|---|---|
| **Đồng bộ** — caller phải chờ response | **Bất đồng bộ** — caller gửi xong là xong, không cần chờ xử lý xong |
| Caller và callee **coupling chặt** — callee down thì caller cũng fail theo | **Giảm coupling** — producer không cần biết consumer là ai, tồn tại hay không tại thời điểm gửi |
| Không tự nhiên hỗ trợ "phát 1 sự kiện cho nhiều bên cùng nhận" | Tự nhiên hỗ trợ **1-nhiều** (1 sự kiện, nhiều consumer group độc lập cùng xử lý) |
| Phù hợp khi cần kết quả **ngay lập tức** để trả về cho user | Phù hợp cho tác vụ **có thể xử lý sau** (gửi email, ghi log audit, đồng bộ dữ liệu giữa service) |

---

## 4. Resilience

### 4.1 Circuit Breaker (Resilience4j) — tránh cascading failure
```java
@Service
class PaymentClient {

    @CircuitBreaker(name = "paymentService", fallbackMethod = "paymentFallback")
    public PaymentResult charge(PaymentRequest request) {
        return restTemplate.postForObject("http://payment-service/charge", request, PaymentResult.class);
    }

    // fallback PHẢI cùng chữ ký tham số + thêm 1 tham số Exception ở cuối
    private PaymentResult paymentFallback(PaymentRequest request, Exception ex) {
        return PaymentResult.failed("Payment service tạm thời không khả dụng, vui lòng thử lại sau");
    }
}
```
**Nguyên lý Circuit Breaker**: theo dõi tỷ lệ lỗi của các lời gọi tới 1 service phụ thuộc — nếu vượt ngưỡng, circuit chuyển sang trạng thái **OPEN** (từ chối gọi ngay lập tức, không tốn thời gian chờ timeout thật), sau 1 khoảng thời gian chuyển sang **HALF_OPEN** để thử lại 1 số request, nếu thành công thì đóng lại (**CLOSED**). Cơ chế này ngăn 1 service bị lỗi/chậm kéo theo **cascading failure** — request dồn ứ chờ timeout ở toàn bộ hệ thống phía trên.

```yaml
resilience4j:
  circuitbreaker:
    instances:
      paymentService:
        failure-rate-threshold: 50 # OPEN khi tỷ lệ lỗi >= 50%
        wait-duration-in-open-state: 10s
        sliding-window-size: 10
```

### 4.2 Retry, Timeout, Rate Limiter
```java
@Retry(name = "paymentService", fallbackMethod = "paymentFallback")
@TimeLimiter(name = "paymentService")
public CompletableFuture<PaymentResult> chargeAsync(PaymentRequest request) { ... }

@RateLimiter(name = "publicApi")
public ApiResponse callPublicApi() { ... } // giới hạn số lượng request/giây, chống lạm dụng hoặc chống bị đối tác chặn do gọi quá nhiều
```
> **Lưu ý thứ tự aspect quan trọng**: khi dùng chung `@CircuitBreaker` + `@Retry` trên cùng method, cần cấu hình đúng thứ tự — `CircuitBreaker` nên **bọc ngoài** `Retry` (`circuitBreakerAspectOrder` nhỏ hơn `retryAspectOrder`), để circuit breaker đếm đúng số lần **toàn bộ chuỗi retry thất bại** là 1 lần gọi lỗi, thay vì đếm nhầm mỗi lần retry riêng lẻ là 1 lỗi độc lập (dễ khiến circuit OPEN sai thời điểm).
```properties
resilience4j.circuitbreaker.circuitBreakerAspectOrder=1
resilience4j.retry.retryAspectOrder=2
```

---

## 5. Microservices Ecosystem (nếu mở rộng)

| Thành phần | Vai trò |
|---|---|
| **API Gateway** (Spring Cloud Gateway) | 1 điểm vào duy nhất cho mọi request từ client — route request tới đúng microservice, tập trung xử lý cross-cutting concern (auth, rate limiting, logging) mà không cần lặp lại ở từng service |
| **Service Discovery** (Eureka) | Mỗi service khi khởi động **tự đăng ký** địa chỉ (IP:port) của mình vào Eureka Server; service khác muốn gọi thì **tra cứu** qua tên service thay vì hardcode địa chỉ — cần thiết vì trong môi trường cloud/container, địa chỉ IP của mỗi instance thay đổi liên tục |
| **Config Server** (Spring Cloud Config) | Quản lý cấu hình **tập trung** cho toàn bộ microservices tại 1 nơi (thường backed bởi Git repo) — thay đổi cấu hình không cần build lại từng service, hỗ trợ refresh cấu hình runtime |

> Đây là các thành phần **mở rộng** — chỉ cần thiết khi hệ thống thực sự tách thành nhiều service độc lập. Với ứng dụng monolith hoặc mới có 2-3 service, việc thêm ngay toàn bộ hệ sinh thái này thường là **over-engineering**, nên cân nhắc kỹ trước khi áp dụng.

---

## 6. Deployment

### 6.1 Dockerize Spring Boot — Multi-stage Build
```dockerfile
# Stage 1: build ứng dụng, cần đầy đủ JDK + Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline # cache dependency riêng, tận dụng Docker layer cache khi code đổi nhưng dependency không đổi
COPY src ./src
RUN mvn package -DskipTests

# Stage 2: chỉ cần JRE để CHẠY ứng dụng, không cần Maven/JDK đầy đủ
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```
**Tại sao multi-stage build tối ưu image size**: stage build cần toàn bộ JDK + Maven + source code + dependency cache (nặng, có thể vài trăm MB tới hơn 1GB) — nhưng khi **chạy** ứng dụng thì chỉ cần JRE + file jar đã build sẵn. Multi-stage build cho phép "vứt bỏ" toàn bộ môi trường build ở stage 1, chỉ copy đúng file jar kết quả sang image cuối cùng (stage 2) — image production nhỏ gọn hơn đáng kể, giảm bề mặt tấn công (ít công cụ/thư viện không cần thiết) và tải image nhanh hơn khi deploy.

### 6.2 CI/CD cơ bản
```
build → test → package → deploy
```
- **build**: compile source code, resolve dependency.
- **test**: chạy unit test + integration test, **fail pipeline ngay** nếu có test fail (không cho phép deploy code chưa qua kiểm chứng).
- **package**: đóng gói thành artifact (jar/Docker image), thường gắn version/tag rõ ràng để truy vết được.
- **deploy**: đưa artifact lên môi trường đích (staging/production), thường qua các bước trung gian (deploy staging → smoke test → deploy production) thay vì deploy thẳng lên production ngay.

---

## Câu hỏi tự kiểm tra
1. Vì sao Spring Boot mặc định chỉ public endpoint `health` mà không public toàn bộ actuator endpoint? Nếu vô tình để `management.endpoints.web.exposure.include: "*"` không bảo vệ ở production, rủi ro cụ thể là gì?
2. `@Cacheable` và `@CachePut` khác nhau ở điểm nào về việc method có được thực thi hay không? Cho 1 tình huống chỉ `@CachePut` mới giải quyết đúng.
3. Vì sao cache in-memory mặc định của Spring Boot không hoạt động đúng khi ứng dụng scale ngang nhiều instance? Redis giải quyết vấn đề đó như thế nào?
4. `groupId` trong `@KafkaListener` ảnh hưởng thế nào tới việc message được phân phối tới consumer? Nếu 3 instance cùng service dùng cùng 1 `groupId`, điều gì xảy ra khi có 1 message mới tới?
5. Circuit Breaker giải quyết vấn đề "cascading failure" như thế nào? Giải thích 3 trạng thái CLOSED/OPEN/HALF_OPEN.
6. Vì sao thứ tự aspect giữa `@CircuitBreaker` và `@Retry` lại quan trọng? Điều gì xảy ra nếu cấu hình sai thứ tự (Retry bọc ngoài CircuitBreaker)?
7. Multi-stage Docker build giảm kích thước image bằng cách nào? Nếu build 1 image Spring Boot chỉ dùng 1 stage duy nhất (không tách build/runtime), hậu quả cụ thể là gì?

## Bài tập luyện tập
- Bật Actuator cho 1 project mẫu, cấu hình chỉ public `health` không xác thực, các endpoint còn lại yêu cầu role `ENDPOINT_ADMIN` qua `EndpointRequest.toAnyEndpoint()`.
- Viết 1 `ProductService` với `@Cacheable`/`@CachePut`/`@CacheEvict` đầy đủ, dùng cache in-memory mặc định trước, sau đó chuyển sang Redis — viết test đo số lần method thực sự được gọi (qua log hoặc mock) để chứng minh cache hoạt động đúng.
- Dựng Kafka bằng Docker, viết 1 Producer publish sự kiện `OrderCreated`, 2 Consumer với `groupId` khác nhau cùng lắng nghe — chứng minh cả 2 đều nhận được message độc lập; sau đó thêm 1 Consumer thứ 3 **cùng `groupId`** với Consumer thứ nhất, chứng minh 2 Consumer trong cùng group chia nhau xử lý thay vì cả 2 đều nhận.
- Áp dụng `@CircuitBreaker` + `fallbackMethod` cho 1 lời gọi API giả lập hay lỗi (throw exception ngẫu nhiên), quan sát log để thấy circuit chuyển từ CLOSED sang OPEN khi tỷ lệ lỗi vượt ngưỡng, rồi tự chuyển sang HALF_OPEN sau `wait-duration-in-open-state`.
- Viết `Dockerfile` multi-stage cho 1 project Spring Boot mẫu, build image, so sánh kích thước với 1 bản build single-stage (copy toàn bộ source + Maven vào image runtime) để thấy chênh lệch thực tế bằng `docker images`.
