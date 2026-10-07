# Roadmap học Java Spring Boot trong 20 tuần

Lộ trình này dành cho người đã biết Java Core, OOP, collections, exception, Maven và Git ở mức đủ dùng. Tài liệu chỉ tập trung vào Spring Framework, Spring Boot và hệ sinh thái Java backend. Thời lượng đề xuất là **12–15 giờ/tuần**, tương đương khoảng 5 tháng.

Tại thời điểm biên soạn, trang chính thức đang hiển thị Spring Boot 4.1.1. Spring Boot yêu cầu tối thiểu Java 17; để học, nên dùng Java 21 LTS, Maven và IntelliJ IDEA.

## Mục tiêu cuối lộ trình

Sau 20 tuần, bạn có thể tự xây dựng một backend production-ready với:

- REST API đúng chuẩn HTTP.
- PostgreSQL, Spring Data JPA và database migration.
- Đăng ký, đăng nhập và phân quyền bằng Spring Security.
- Validation và xử lý lỗi tập trung.
- Unit test, integration test và API test.
- Redis cache và background job.
- Logging, metrics và health check.
- Docker, CI/CD và deployment.
- Kiến trúc modular monolith dễ bảo trì.
- Một project portfolio hoàn chỉnh có thể trình bày khi phỏng vấn.

## Stack khuyến nghị

| Thành phần | Lựa chọn |
|---|---|
| Ngôn ngữ | Java 21 |
| Framework | Spring Boot 4.x |
| Build tool | Maven |
| Database | PostgreSQL |
| ORM | Spring Data JPA + Hibernate |
| Migration | Flyway |
| Security | Spring Security |
| Testing | JUnit 5, Mockito, MockMvc, Testcontainers |
| Cache | Redis |
| API documentation | OpenAPI/Swagger |
| Container | Docker, Docker Compose |
| Monitoring | Actuator, Micrometer, Prometheus |
| CI/CD | GitHub Actions |

Nguyên tắc thứ tự học: Spring MVC trước WebFlux, modular monolith trước microservices, PostgreSQL trước MongoDB, authentication cơ bản trước JWT nâng cao.

---

# Giai đoạn 1 — Spring Web và nền tảng backend

## Tuần 1: HTTP và REST API trong Spring

### Học

- Client–server.
- HTTP request và response.
- GET, POST, PUT, PATCH, DELETE.
- Status code 200, 201, 204, 400, 401, 403, 404, 409, 500.
- Header, query parameter, path variable, request body.
- JSON, REST resource và idempotency.
- Pagination, filtering, sorting, CORS.

### Bài tập

Thiết kế Task Management API:

~~~text
POST   /api/tasks
GET    /api/tasks
GET    /api/tasks/{id}
PATCH  /api/tasks/{id}
DELETE /api/tasks/{id}
GET    /api/tasks?status=TODO&page=0&size=20&sort=createdAt,desc
~~~

Với mỗi endpoint, ghi rõ request, response, status code, validation và các trường hợp lỗi.

## Tuần 2: SQL và PostgreSQL cho ứng dụng Spring

### Học

- Table, row, column.
- Primary key, foreign key, unique constraint.
- SELECT, INSERT, UPDATE, DELETE.
- JOIN, GROUP BY và aggregate.
- Index.
- Transaction và ACID.
- Quan hệ one-to-one, one-to-many, many-to-many.
- Chuẩn hóa dữ liệu.
- N+1 query.

### Bài tập

Thiết kế database cho cửa hàng gồm users, products, categories, orders, order_items và payments. Viết query doanh thu theo tháng, top sản phẩm, đơn hàng của khách hàng và khách hàng chi tiêu cao nhất.

### Tiêu chí hoàn thành

- Tự thiết kế được quan hệ giữa các bảng.
- Biết khi nào cần index.
- Biết dùng EXPLAIN để xem query plan.

---

# Giai đoạn 2 — Spring Core và Spring Boot căn bản

## Tuần 3: Spring Core

### Học

- IoC và Dependency Injection.
- Bean và Application Context.
- Constructor injection.
- Component scanning.
- Component, Service, Repository, Configuration.
- Bean scope.
- Configuration properties.
- Profile.
- Auto-configuration.
- Starter dependency.
- SpringBootApplication.

### Thực hành

Tạo project bằng Spring Initializr với Spring Web, Validation và Actuator. Xây luồng:

~~~text
TaskController → TaskService → TaskRepository
~~~

Ban đầu repository có thể lưu trong Map, chưa cần database.

### Quy tắc

- Ưu tiên constructor injection.
- Không dùng field injection.
- Controller không chứa business logic.
- Dependency chỉ đi theo một chiều.

## Tuần 4: Configuration và vòng đời ứng dụng

### Học

- application.yml.
- Environment variable.
- ConfigurationProperties.
- Profile local, test, production.
- Logging với SLF4J.
- DevTools.
- Embedded server.
- Đóng gói executable JAR.

### Thực hành

Tạo application-local.yml, application-test.yml và application-prod.yml. Không lưu password hoặc signing key vào Git; đọc chúng từ environment variable.

---

# Giai đoạn 3 — REST API hoàn chỉnh

## Tuần 5: Controller, DTO và validation

### Học

- RestController, RequestMapping.
- GetMapping, PostMapping.
- RequestBody, PathVariable, RequestParam.
- ResponseEntity.
- Request DTO và response DTO.
- Bean Validation.
- Valid, NotBlank, Size, Email.
- Custom validator.

### Thực hành

Hoàn thiện Task API với tạo, xem danh sách, xem chi tiết, cập nhật một phần, xóa, pagination và tìm theo trạng thái.

Tách riêng:

~~~text
CreateTaskRequest
UpdateTaskRequest
TaskResponse
TaskEntity
~~~

Không trả JPA entity trực tiếp ra API.

## Tuần 6: Error handling và thiết kế API

### Học

- Custom exception.
- RestControllerAdvice.
- ExceptionHandler.
- RFC 9457 Problem Details.
- Validation error theo từng field.
- Request/correlation ID.
- API versioning.
- Logging lỗi an toàn.

Response lỗi nên có type, title, status, detail, instance và timestamp. Client không được nhận stack trace hoặc thông tin nội bộ.

---

# Giai đoạn 4 — Spring Data JPA

## Tuần 7: Entity và Repository

### Học

- Entity, Id, GeneratedValue.
- Entity lifecycle.
- JpaRepository.
- Derived query method.
- JPQL và native query.
- Auditing với createdAt, updatedAt.
- Page, Pageable, Sort.
- DTO projection.

### Thực hành

Thay in-memory repository bằng PostgreSQL. Dùng Flyway để tạo schema với các migration có version, thay vì phụ thuộc vào ddl-auto: update trong production.

## Tuần 8: Relationship và transaction

### Học

- OneToMany, ManyToOne, ManyToMany.
- Lazy và eager loading.
- Cascade.
- Owning side.
- Transaction boundary.
- Transactional.
- Dirty checking.
- Optimistic locking với Version.
- N+1 query.
- Fetch join và entity graph.

### Bài tập

- Project chứa nhiều task.
- Task có nhiều comment.
- Xây quy tắc rõ ràng khi xóa project.
- Xử lý hai request cập nhật đồng thời.
- Đo số SQL query phát sinh cho một endpoint.

### Cần tránh

- Mặc định dùng EAGER.
- Cascade mọi thứ bằng CascadeType.ALL.
- Mở transaction trong controller.
- Gọi API bên ngoài trong transaction dài.
- Serialize entity hai chiều trực tiếp thành JSON.

## Tuần 9: Query và hiệu năng database

### Học

- Specification và dynamic query.
- Projection.
- Batch insert/update.
- HikariCP.
- Index đơn và composite index.
- Offset pagination và cursor pagination.
- Query timeout.
- Read-only transaction.

### Bài tập

Tạo endpoint tìm kiếm task theo keyword, status, assigneeId, from và to. Seed ít nhất 100.000 records; đo trước và sau khi thêm index; kiểm tra N+1 query.

---

# Giai đoạn 5 — Spring Security

## Tuần 10: Authentication và authorization

### Học

- Authentication và authorization.
- Security filter chain.
- SecurityContext.
- Password hashing với BCrypt hoặc Argon2.
- Role và permission.
- Method security.
- Session và stateless authentication.
- Phân biệt 401 và 403.
- CSRF.

### Thực hành

~~~text
POST /api/auth/register
POST /api/auth/login
GET  /api/users/me
~~~

Role gồm USER, MANAGER và ADMIN. User chỉ sửa task của mình; Manager quản lý task trong project; Admin quản lý user.

## Tuần 11: JWT và bảo mật API

### Học

- Access token và refresh token.
- Token expiration.
- Key rotation.
- Token revocation.
- OAuth 2.0 và OpenID Connect.
- CORS, rate limiting.
- OWASP Top 10.
- Secret management.

### Yêu cầu

- Access token sống ngắn.
- Refresh token được lưu hoặc hash an toàn.
- Không log token và password.
- Không hardcode signing key.
- Validate issuer, audience, expiry và chữ ký.
- Rate limit endpoint login.
- Có cơ chế vô hiệu hóa refresh token.

---

# Giai đoạn 6 — Testing chuyên sâu trong Spring Boot

## Tuần 12: Unit test và test slice

### Học

- Unit test service.
- Mockito.
- WebMvcTest.
- MockMvc.
- DataJpaTest.
- Test validation.
- Test authorization.
- Test fixture và test naming.

### Test case tối thiểu

- Thành công.
- Input không hợp lệ.
- Resource không tồn tại.
- Resource bị trùng.
- User chưa đăng nhập.
- User không đủ quyền.
- Lỗi repository.
- Tranh chấp cập nhật đồng thời nếu có.

## Tuần 13: Integration test và Testcontainers

### Học

- SpringBootTest.
- Test với server thật.
- Testcontainers PostgreSQL và Redis.
- Database cleanup.
- API integration test.
- Coverage.

### Mục tiêu

- Integration test chạy trên PostgreSQL thật trong container.
- Test migration Flyway.
- Không dùng H2 để giả lập PostgreSQL cho integration test.
- Business logic coverage từ 80% trở lên.
- Chạy toàn bộ test bằng một lệnh:

~~~bash
./mvnw verify
~~~

---

# Giai đoạn 7 — Production readiness

## Tuần 14: Cache, async và scheduling

### Học

- Spring Cache.
- Cacheable và CacheEvict.
- Redis.
- Cache-aside.
- TTL và cache invalidation.
- Async.
- Thread pool.
- Scheduled.
- Idempotency.
- Retry và exponential backoff.

### Thực hành

- Cache dữ liệu project.
- Xóa hoặc cập nhật cache khi project thay đổi.
- Chạy job đóng task quá hạn.
- Gửi notification bất đồng bộ.
- Chống chạy trùng scheduled job khi có nhiều instance.

## Tuần 15: Observability và resilience

### Học

- Spring Boot Actuator.
- Health, readiness và liveness.
- Micrometer.
- Prometheus.
- Structured logging.
- Trace ID và correlation ID.
- Timeout, retry và circuit breaker.
- Graceful shutdown.

### Thực hành

Expose có kiểm soát health, info và prometheus endpoint. Tạo metrics cho số task được tạo, thời gian xử lý request, tỉ lệ lỗi, login thất bại và background job.

Không public toàn bộ Actuator endpoint ra Internet.

---

# Giai đoạn 8 — Docker và triển khai Spring Boot

## Tuần 16: Docker

### Học

- Image và container.
- Multi-stage Dockerfile.
- Docker network.
- Volume.
- Docker Compose.
- Health check.
- Environment variable.
- Non-root container.

### Thực hành

Chạy Spring Boot, PostgreSQL, Redis, Prometheus và Grafana bằng:

~~~bash
docker compose up
~~~

## Tuần 17: CI/CD và deployment

### Học

- CI pipeline.
- Build, test, lint, security scan.
- Container registry.
- Dev, staging, production.
- Database migration khi deploy.
- Rolling deployment.
- Rollback.
- Backup và restore.

### Pipeline gợi ý

~~~text
Checkout → Compile → Unit tests → Integration tests
→ Coverage → Security scan → Build image
→ Deploy staging → Smoke test → Deploy production
~~~

---

# Giai đoạn 9 — Kiến trúc Spring Boot nâng cao

## Tuần 18: Modular monolith

Tổ chức package theo feature:

~~~text
com.example.taskmanager
├── auth
├── user
├── project
├── task
├── notification
└── shared
~~~

Mỗi feature có thể gồm api, application, domain và infrastructure.

### Học

- Layered architecture.
- Package-by-feature.
- Hexagonal architecture.
- Domain model.
- Dependency inversion.
- Domain event.
- Transactional outbox.
- Module boundary.

Không áp dụng kiến trúc phức tạp nếu project chỉ là CRUD nhỏ.

## Tuần 19: Messaging và distributed systems

Chọn Kafka hoặc RabbitMQ để học trước, không cần học cả hai ngay.

### Học

- Producer và consumer.
- At-least-once delivery.
- Idempotent consumer.
- Dead-letter queue.
- Ordering.
- Retry.
- Outbox pattern.
- Eventual consistency.
- Saga ở mức khái niệm.

### Thực hành

Khi task được giao cho user, phát TaskAssigned event để notification consumer gửi email hoặc in-app notification. Thử xử lý event gửi trùng, consumer lỗi, timeout và service restart.

Chỉ học microservices sau khi đã triển khai tốt modular monolith.

---

# Tuần 20 — Capstone project

## Đề tài

Chọn một trong hai:

- E-commerce backend.
- Project Management System.

## Module nên có

- Authentication.
- User và role.
- Product/project.
- Order/task.
- Payment hoặc workflow.
- Notification.
- Search.
- Audit log.
- Admin API.

## Yêu cầu kỹ thuật

- Spring Boot 4.x và Java 21.
- PostgreSQL và Flyway.
- Redis cache.
- Spring Security.
- Access/refresh token.
- Validation.
- Global exception handling.
- Pagination, filtering và sorting.
- OpenAPI documentation.
- Unit và integration test.
- Testcontainers.
- Docker Compose.
- Actuator và metrics.
- CI pipeline.
- README hướng dẫn chạy.
- ERD và sơ đồ kiến trúc.
- Deployment public.
- Business logic coverage từ 80% trở lên.

## Definition of Done

- Clone project về chạy được theo README.
- Một command chạy được toàn bộ test.
- Không có secret trong repository.
- Migration chạy được trên database trống.
- API trả lỗi nhất quán.
- Phân quyền được kiểm tra ở backend.
- Có log để điều tra lỗi.
- Có health check.
- Có demo account hoặc Postman collection.
- Có ít nhất một luồng end-to-end hoàn chỉnh.

---

# Lịch học mỗi tuần

Với 12–15 giờ/tuần:

| Buổi | Nội dung |
|---|---|
| Buổi 1 — 2 giờ | Học khái niệm và ghi chú |
| Buổi 2 — 2 giờ | Code theo tài liệu |
| Buổi 3 — 2 giờ | Tự code lại không nhìn hướng dẫn |
| Buổi 4 — 3 giờ | Phát triển project |
| Buổi 5 — 2 giờ | Viết test và sửa lỗi |
| Cuối tuần — 2 giờ | Ôn tập, refactor, viết README |

Tỉ lệ thời gian:

~~~text
20% đọc/xem
60% tự code
20% test, debug và ôn tập
~~~

Sau mỗi chủ đề:

1. Giải thích lại khái niệm bằng lời của mình.
2. Viết một ví dụ nhỏ.
3. Áp dụng vào project.
4. Viết test.
5. Tự tạo một bug rồi debug.
6. Commit với nội dung rõ ràng.

---

# Ba project nên có trong portfolio

## Project 1 — Task API

Thực hiện trong Tuần 5–9: CRUD, PostgreSQL, validation, error handling, pagination và unit test.

## Project 2 — Secure Project Management

Thực hiện trong Tuần 10–15: authentication, role/permission, JWT, Redis, background job, integration test và monitoring.

## Project 3 — Production-grade application

Thực hiện trong Tuần 16–20: Docker, CI/CD, messaging, modular architecture, deployment, observability và security documentation.

Ba project có độ sâu và được deploy tốt có giá trị hơn nhiều project CRUD sơ sài.

---

# Checklist trước khi xin việc

- Dependency Injection hoạt động như thế nào?
- Component, Service và Repository khác nhau gì?
- Spring Boot auto-configuration là gì?
- Transactional hoạt động qua proxy ra sao?
- Lazy loading và N+1 query là gì?
- Vì sao không nên trả entity trực tiếp?
- 401 khác 403 thế nào?
- JWT có điểm yếu gì?
- Unit test khác integration test thế nào?
- WebMvcTest khác SpringBootTest thế nào?
- Khi nào dùng cache?
- Cache invalidation xử lý ra sao?
- Retry có thể gây lỗi gì?
- Làm thế nào chống xử lý message hai lần?
- Một request chậm thì điều tra từ đâu?
- Database migration và rollback thế nào?
- Deploy không downtime bằng cách nào?

---

# Nguồn học chính thức

- [Spring Boot Reference](https://docs.spring.io/spring-boot/reference/)
- [Spring Guides](https://spring.io/guides)
- [Building REST services](https://spring.io/guides/tutorials/rest/)
- [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
- [Spring Security Reference](https://docs.spring.io/spring-security/reference/)
- [Spring Initializr](https://start.spring.io/)
- [Spring Boot system requirements](https://docs.spring.io/spring-boot/system-requirements.html)

Nguyên tắc quan trọng nhất: mỗi kiến thức phải đi qua bốn bước — **hiểu, code, test, áp dụng vào project**. Chỉ xem video mà không tự xây project sẽ tạo cảm giác hiểu Spring Boot nhưng khó giải quyết vấn đề thực tế.
