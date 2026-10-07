# Roadmap học Java Spring Boot

## Lịch trình tổng quan (8 tuần)

| Tuần | Chủ đề chính | Trọng tâm |
|---|---|---|
| 1 | Spring Core & IoC | DI, Bean lifecycle, scope, Application Context |
| 2 | Spring Boot Fundamentals | Auto-configuration, starter, properties, profile |
| 3 | Spring MVC & RESTful API | Controller, request mapping, validation, exception handling |
| 4 | Spring Data JPA | Entity, Repository, JPQL, transaction |
| 5 | Spring Security cơ bản | Authentication, Authorization, filter chain |
| 6 | Security nâng cao | JWT, OAuth2/OIDC, Keycloak |
| 7 | Testing & Best Practices | Unit test, integration test, logging, exception design |
| 8 | Production-ready & Microservices | Actuator, caching, messaging, deployment |

**Gợi ý luyện tập:** mỗi tuần build một tính năng nhỏ áp dụng đúng chủ đề, ghép dần vào một project mẫu (ví dụ: hệ thống quản lý đơn hàng) để cuối lộ trình có một ứng dụng hoàn chỉnh production-ready.

---

## 1. Spring Core & IoC

### 1.1 Inversion of Control & Dependency Injection
- Khái niệm IoC: Spring container quản lý vòng đời object thay vì code tự `new`
- 3 cách inject: **Constructor injection** (khuyến nghị), Setter injection, Field injection
- Tại sao ưu tiên constructor injection: immutability, dễ test, phát hiện circular dependency sớm, không cần reflection
- `@Autowired`, `@Qualifier`, `@Primary` khi có nhiều bean cùng type

### 1.2 Bean & Component Scanning
- `@Component`, `@Service`, `@Repository`, `@Controller` — khác nhau về mặt ngữ nghĩa, `@Repository` có thêm exception translation
- `@Configuration` + `@Bean` — khai báo bean thủ công, dùng khi cần cấu hình phức tạp hoặc bean từ thư viện bên ngoài
- `@ComponentScan` — phạm vi quét, cách Spring Boot tự động quét từ package gốc

### 1.3 Bean Scope
| Scope | Mô tả |
|---|---|
| singleton | Mặc định, 1 instance dùng chung toàn container |
| prototype | Tạo instance mới mỗi lần inject/getBean |
| request | 1 instance/HTTP request (web) |
| session | 1 instance/HTTP session (web) |

### 1.4 Bean Lifecycle
- Các bước: Instantiate → Populate properties → `BeanNameAware`/`BeanFactoryAware` → `@PostConstruct`/`InitializingBean.afterPropertiesSet()` → sẵn sàng dùng → `@PreDestroy`/`DisposableBean.destroy()`
- `BeanPostProcessor` — can thiệp trước/sau khi bean khởi tạo
- `ApplicationContextAware`, `ApplicationListener`, `ApplicationEvent` (custom event + `@EventListener`)

### 1.5 Circular Dependency
- Nguyên nhân: A phụ thuộc B, B phụ thuộc A
- Với setter injection Spring có thể tự xử lý (dùng cache 3-level), với constructor injection thì fail ngay khi start-up
- Cách xử lý đúng: refactor thiết kế, tránh dùng `@Lazy` như một "cách chữa cháy"

---

## 2. Spring Boot Fundamentals

### 2.1 Auto-configuration
- `@SpringBootApplication` = `@Configuration` + `@EnableAutoConfiguration` + `@ComponentScan`
- Cơ chế: Spring Boot đọc `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` (Boot 3+) để nạp các auto-config class
- `@Conditional...`: `@ConditionalOnClass`, `@ConditionalOnMissingBean`, `@ConditionalOnProperty` — điều kiện để 1 bean được tạo tự động
- Cách debug auto-configuration: `--debug` flag, xem báo cáo "Positive/Negative matches"

### 2.2 Starter Dependencies
- Starter là tập hợp dependency đã được test tương thích (ví dụ `spring-boot-starter-web` kéo theo Tomcat, Jackson, Spring MVC)
- Tự viết custom starter: tách `autoconfigure` module + `starter` module, dùng khi muốn đóng gói thư viện dùng lại cho nhiều dự án nội bộ

### 2.3 Configuration Properties
- `application.properties` vs `application.yml`
- `@Value("${key}")` — inject giá trị đơn lẻ
- `@ConfigurationProperties(prefix = "app")` — bind cả nhóm property vào 1 class, hỗ trợ validation (`@Validated`)
- Thứ tự ưu tiên override: command-line args > env variable > application-{profile}.yml > application.yml > default

### 2.4 Profile
- `@Profile("dev")`, `spring.profiles.active`
- Tách cấu hình theo môi trường: `application-dev.yml`, `application-prod.yml`
- Kết hợp với `@ConditionalOnProfile` (thực chất dùng `@Profile`) để bật/tắt bean theo môi trường

### 2.5 Embedded Server & DevTools
- Tomcat (mặc định), Jetty, Undertow — cách đổi bằng exclude dependency
- Spring Boot DevTools: auto-restart, LiveReload — chỉ dùng khi dev, tự tắt khi build production

---

## 3. Spring MVC & RESTful API

### 3.1 Controller & Request Mapping
- `@RestController` = `@Controller` + `@ResponseBody`
- `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PutMapping`, `@PatchMapping`, `@DeleteMapping`
- `@RequestBody` (đọc JSON → object), `@RequestParam` (query param), `@PathVariable` (URL param), `@RequestHeader`
- `ResponseEntity<T>` — kiểm soát status code, header, body tường minh

### 3.2 Validation
- Bean Validation (Jakarta Validation): `@NotNull`, `@NotBlank`, `@Size`, `@Email`, `@Min/@Max`, `@Pattern`
- `@Valid` trên `@RequestBody` để kích hoạt validate tự động
- Custom validator: tạo annotation riêng + `ConstraintValidator`
- Validation nhóm (`@Validated` với groups) khi cùng 1 DTO nhưng khác rule cho create/update

### 3.3 Exception Handling
- `@ControllerAdvice` + `@ExceptionHandler` — xử lý lỗi tập trung toàn ứng dụng
- Thiết kế response lỗi chuẩn: mã lỗi, message, timestamp, path — nhất quán cho toàn bộ API
- `MethodArgumentNotValidException` (lỗi validate), `HttpMessageNotReadableException` (JSON sai định dạng)
- Phân biệt lỗi 4xx (client) và 5xx (server), không leak stack trace ra response production

### 3.4 DTO & Mapping
- Tại sao không trả trực tiếp Entity ra ngoài (lộ dữ liệu thừa, vòng lặp JSON khi có quan hệ 2 chiều)
- MapStruct hoặc ModelMapper để convert Entity ↔ DTO
- `@JsonIgnore`, `@JsonProperty`, `@JsonInclude(NON_NULL)`

### 3.5 API Documentation & Versioning
- OpenAPI/Swagger (springdoc-openapi) — tự sinh doc từ annotation
- Versioning: URL path (`/api/v1/...`), header, hoặc media type — ưu nhược điểm từng cách

---

## 4. Spring Data JPA

### 4.1 ORM cơ bản
- Entity: `@Entity`, `@Table`, `@Id`, `@GeneratedValue(strategy = ...)` (IDENTITY, SEQUENCE, AUTO)
- Mapping quan hệ: `@OneToMany`, `@ManyToOne`, `@ManyToMany`, `@OneToOne`, `mappedBy`, `@JoinColumn`
- `@Embeddable`/`@Embedded` cho value object

### 4.2 Repository
- `JpaRepository<T, ID>` — CRUD, phân trang, sort có sẵn
- Derived query method: `findByEmailAndStatus(...)`, `existsBy...`, `countBy...`
- `@Query` (JPQL) và Native Query (`nativeQuery = true`)
- Specification/Criteria API — build query động theo điều kiện

### 4.3 Transaction
- `@Transactional`: propagation (`REQUIRED`, `REQUIRES_NEW`, `NESTED`...), isolation level (`READ_COMMITTED`, `REPEATABLE_READ`...)
- Transaction rollback rule mặc định (chỉ rollback với unchecked exception trừ khi khai báo `rollbackFor`)
- Vấn đề self-invocation (gọi method `@Transactional` từ trong cùng class sẽ không hoạt động do AOP proxy)

### 4.4 Fetch Strategy & Hiệu năng
- Lazy vs Eager loading, `LazyInitializationException`
- N+1 query problem và cách khắc phục: `JOIN FETCH`, `@EntityGraph`, batch fetching
- Pagination: `Pageable`, `Page<T>`, `Slice<T>`

### 4.5 Auditing & Migration
- `@CreatedDate`, `@LastModifiedDate`, `@CreatedBy`, `@LastModifiedBy` + `@EnableJpaAuditing`
- Schema migration: Flyway hoặc Liquibase — quản lý version database, tránh dùng `ddl-auto=update` ở production

---

## 5. Spring Security cơ bản

### 5.1 Kiến trúc Filter Chain
- `SecurityFilterChain` — chuỗi filter xử lý request trước khi tới Controller
- Thứ tự filter quan trọng (ví dụ: `UsernamePasswordAuthenticationFilter` trước `FilterSecurityInterceptor`)

### 5.2 Authentication
- `AuthenticationManager`, `AuthenticationProvider`
- `UserDetailsService`, `UserDetails` — cách load user để xác thực
- `PasswordEncoder`: BCrypt, Argon2 (liên hệ phần cryptography đã học)

### 5.3 Authorization
- `@PreAuthorize`, `@PostAuthorize`, `@Secured` — method-level security
- Role vs Authority — phân biệt và cách thiết kế phân quyền hợp lý
- `HttpSecurity` config: `.authorizeHttpRequests()`, permitAll/hasRole/hasAuthority

### 5.4 Session-based vs Stateless
- Session-based: dùng cho web truyền thống, server lưu session
- Stateless (JWT): phù hợp REST API, scale ngang dễ hơn

### 5.5 CORS & CSRF
- Cấu hình CORS đúng cho REST API (allowedOrigins, allowedMethods)
- CSRF: khi nào cần bật/tắt (thường tắt với API stateless dùng JWT, giữ với session-based)

---

## 6. Security nâng cao

### 6.1 JWT
- Cấu trúc: header.payload.signature
- Access token vs Refresh token — vòng đời và cách renew
- Ký và verify: HMAC (HS256) vs RSA (RS256)
- Lưu trữ token phía client: cookie HttpOnly vs localStorage — trade-off bảo mật (XSS/CSRF)

### 6.2 OAuth2 & OIDC
- Các flow chính: Authorization Code, Client Credentials, (Implicit/Password — không khuyến nghị dùng nữa)
- OIDC bổ sung ID Token (chứa thông tin định danh user) trên nền OAuth2
- Resource Server vs Authorization Server vs Client

### 6.3 Keycloak Integration
- Cấu hình Realm, Client, Role mapping
- Spring Boot làm Resource Server xác thực token từ Keycloak
- Cấu hình `spring-boot-starter-oauth2-resource-server`

### 6.4 Cryptography liên quan
- AES-GCM (mã hóa đối xứng, có authentication tag)
- RSA (mã hóa bất đối xứng, dùng cho chữ ký JWT RS256)
- BCrypt, Argon2 (băm mật khẩu, có salt tự động, chống brute-force)

---

## 7. Testing & Best Practices

### 7.1 Unit Test
- JUnit 5: `@Test`, `@BeforeEach`, `@ParameterizedTest`
- Mockito: `@Mock`, `@InjectMocks`, `when().thenReturn()`, `verify()`
- `@MockBean` (context Spring) vs `@Mock` (thuần Mockito, không load context — nhanh hơn)

### 7.2 Integration Test
- `@SpringBootTest` — load toàn bộ context
- Test slice: `@WebMvcTest` (chỉ layer web), `@DataJpaTest` (chỉ layer JPA, dùng in-memory DB)
- TestContainers — test với database thật (Postgres, MySQL) trong container, độ tin cậy cao hơn H2

### 7.3 Logging
- SLF4J + Logback, cấu hình log level theo profile
- Structured logging, tránh log thông tin nhạy cảm (password, token)

### 7.4 Cấu trúc code & Best Practice
- Layered architecture: Controller → Service → Repository, tách rõ trách nhiệm
- Package theo layer vs package theo feature — ưu nhược điểm
- Chuẩn hóa response API và exception xuyên suốt hệ thống (đã đề cập ở mục 3.3)

---

## 8. Production-ready & Microservices

### 8.1 Actuator
- Health check (`/actuator/health`), metrics (`/actuator/metrics`)
- Custom endpoint, bảo mật actuator endpoint (không để public)

### 8.2 Caching
- `@EnableCaching`, `@Cacheable`, `@CacheEvict`, `@CachePut`
- Tích hợp Redis làm cache store phân tán

### 8.3 Messaging
- Kafka: producer/consumer cơ bản, `@KafkaListener`
- RabbitMQ: exchange, queue, binding cơ bản
- Khi nào chọn message queue thay vì gọi API trực tiếp (giảm coupling, xử lý bất đồng bộ)

### 8.4 Resilience
- Circuit breaker (Resilience4j) — tránh cascading failure
- Retry, timeout, rate limiter

### 8.5 Microservices Ecosystem (nếu mở rộng)
- API Gateway (Spring Cloud Gateway)
- Service Discovery (Eureka)
- Config Server tập trung (Spring Cloud Config)

### 8.6 Deployment
- Dockerize Spring Boot app (multi-stage build, tối ưu image size)
- CI/CD cơ bản: build → test → package → deploy

---

## Thứ tự ưu tiên bổ sung nội dung
Dựa trên phần bạn đã có sẵn khá kỹ (Spring Core, JWT/OAuth2/Keycloak, cryptography), thứ tự nên bổ sung để hoàn thiện series:
1. **Spring Data JPA** (mục 4) — mảng lớn còn thiếu, hay bị hỏi khi phỏng vấn
2. **Testing** (mục 7) — điểm yếu phổ biến của lập trình viên backend
3. **Production-ready/Microservices** (mục 8) — nâng giá trị khóa học lên mức thực chiến
