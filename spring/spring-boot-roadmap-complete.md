# Roadmap học Java Spring Boot (Đầy đủ — theo spring.io/guides)

Roadmap tổng hợp toàn bộ 16 category từ danh sách bài học Spring Boot, sắp xếp theo thứ tự học hợp lý: nền tảng trước → lưu trữ & xử lý dữ liệu → bảo mật → chất lượng/tối ưu/vận hành → giao tiếp bất đồng bộ & xử lý dữ liệu lớn → kiến trúc thay thế → kiến trúc phân tán → triển khai → công cụ/bổ trợ.

## Thứ tự tổng quan các category

| STT | Category | Số bài |
|---|---|---|
| 1 | Web | 4 |
| 2 | REST APIs | 7 |
| 3 | Data Access | 11 |
| 4 | IO | 5 |
| 5 | Security | 7 |
| 6 | Testing | 2 |
| 7 | Documentation | 1 |
| 8 | Caching | 1 |
| 9 | Ops | 1 |
| 10 | Messaging | 7 |
| 11 | Batch Processing | 2 |
| 12 | Streaming | 1 |
| 13 | GraphQL | 1 |
| 14 | Microservices | 5 |
| 15 | Cloud Platform | 3 |
| 16 | Misc | 10 |

---

## 1. Web

| # | Bài học | Ghi chú |
|---|---|---|
| 2 | Serving Web Content with Spring MVC | Render trang web với Thymeleaf — DispatcherServlet, ViewResolver |
| 3 | Producing a SOAP web service | Tạo service trước khi học cách gọi nó |
| 4 | Consuming a SOAP web service | Dùng kiến thức WSDL ở bài 3 để tạo client |

## 2. REST APIs

| # | Bài học | Ghi chú |
|---|---|---|
| 1 | Building an Application with Spring Boot | Nền tảng nhất — cấu hình tối thiểu, auto-configuration, cách chạy 1 app |
| 2 | Building a RESTful Web Service | `@RestController`, JSON serialization, GET/POST cơ bản |
| 3 | Consuming a RESTful Web Service | Gọi REST service bằng `RestTemplate` |
| 4 | Building REST services with Spring (tutorial) | Hệ thống hóa REST cơ bản + mở rộng (error handling, HAL) |
| 5 | Building a Hypermedia-Driven RESTful Web Service (HATEOAS) | Thêm link điều hướng vào response |
| 6 | Accessing Neo4j Data with REST | Spring Data REST — datastore Neo4j |
| 7 | Accessing Data in Pivotal GemFire with REST | Spring Data REST — datastore GemFire |
| 8 | Building a Reactive RESTful Web Service | Mô hình reactive (WebFlux, `WebClient`) |

## 3. Data Access

| # | Bài học | Ghi chú |
|---|---|---|
| 1 | Accessing Relational Data using JDBC with Spring | Nền tảng thấp nhất — `JdbcTemplate` |
| 2 | Accessing Data with JPA | ORM — Spring Data JPA, Repository pattern |
| 3 | Managing Transactions | `@Transactional` |
| 4 | Accessing JPA Data with REST | Kết hợp JPA + Spring Data REST |
| 5 | Accessing data with MySQL | Áp dụng vào RDBMS cụ thể |
| 6 | Accessing data with R2DBC | Phiên bản reactive của JDBC |
| 7 | Accessing Data with MongoDB | NoSQL dạng document |
| 8 | Accessing MongoDB Data with REST | Expose MongoDB repository qua Spring Data REST |
| 9 | Accessing Data with Neo4j | NoSQL dạng graph |
| 10 | Accessing Data with Cassandra | NoSQL dạng wide-column |
| 11 | Accessing Data Reactively with Redis | Reactive + NoSQL key-value/cache |

## 4. IO

| # | Bài học | Ghi chú |
|---|---|---|
| 1 | Validating Form Input | Bean Validation cơ bản |
| 2 | Handling Form Submission | Vòng đời submit form (GET/POST) |
| 3 | Uploading Files | Multipart form, upload file |
| 4 | Scheduling Tasks | `@Scheduled` |
| 5 | Creating Asynchronous Methods | `@Async` |

## 5. Security

| # | Bài học | Thời lượng | Ghi chú |
|---|---|---|---|
| 1 | Securing a Web Application | 30m | `SecurityFilterChain`, form login, in-memory user |
| 2 | Enabling Cross Origin Requests for a RESTful Web Service (CORS) | 30m | Bảo mật khi frontend gọi REST API khác origin |
| 3 | Authenticating a User with LDAP | 30m | Nguồn xác thực khác ngoài in-memory/database |
| 4 | Vault Configuration | 30m | Quản lý secret bằng Vault |
| 5 | Accessing Vault | 30m | Spring Vault client trực tiếp |
| 6 | Spring Boot and OAuth2 | 3h | Social login, Single Sign-On |
| 7 | Spring Security and Angular | 3h | SPA + kiến trúc backend (API Gateway + OAuth2) |

## 6. Testing

| # | Bài học | Ghi chú |
|---|---|---|
| 1 | Testing the Web Layer | `@WebMvcTest`, `MockMvc` |
| 2 | Consumer Driven Contracts | Kiểm thử hợp đồng giữa các service (microservices) |

## 7. Documentation

| # | Bài học | Ghi chú |
|---|---|---|
| 1 | Creating API Documentation with Restdocs | Sinh tài liệu API từ test case — học sau Testing |

## 8. Caching

| # | Bài học | Ghi chú |
|---|---|---|
| 1 | Caching Data with Spring | `@Cacheable`, `@CacheEvict` |

## 9. Ops

| # | Bài học | Ghi chú |
|---|---|---|
| 1 | Building a RESTful Web Service with Spring Boot Actuator | Health check, metrics |

## 10. Messaging

| # | Bài học | Ghi chú |
|---|---|---|
| 1 | Integrating Data (Spring Integration) | Khái niệm tổng quát — Enterprise Integration Pattern |
| 2 | Messaging with JMS | Message broker cổ điển, publish/subscribe cơ bản |
| 3 | Messaging with RabbitMQ | AMQP protocol, broker phổ biến |
| 4 | Messaging with Redis | Redis làm message broker |
| 5 | Using WebSocket to build an interactive web application (STOMP) | Giao tiếp real-time browser-server |
| 6 | Messaging with Google Cloud Pub/Sub | Messaging trên cloud managed service |
| 7 | Spring Cloud Stream | Lớp trừu tượng thống nhất trên Kafka/RabbitMQ |

## 11. Batch Processing

| # | Bài học | Ghi chú |
|---|---|---|
| 1 | Creating a Batch Service | Spring Batch: Job, Step, Chunk |
| 2 | Spring Cloud Task | Task ngắn hạn trong hệ microservices |

## 12. Streaming

| # | Bài học | Ghi chú |
|---|---|---|
| 1 | Spring Cloud Data Flow | Điều phối pipeline streaming/batch — sau Messaging & Batch Processing |

## 13. GraphQL

| # | Bài học | Ghi chú |
|---|---|---|
| 1 | Building a GraphQL service | Schema, Query/Mutation, Resolver |

## 14. Microservices

| # | Bài học | Ghi chú |
|---|---|---|
| 1 | Service Registration and Discovery (Eureka) | Nền tảng — các service đăng ký/tìm nhau |
| 2 | Centralized Configuration | Quản lý cấu hình tập trung |
| 3 | Client-Side Load-Balancing with Spring Cloud LoadBalancer | Dựa trên Service Discovery |
| 4 | Building a Gateway | Điểm vào duy nhất, định tuyến |
| 5 | Spring Cloud Circuit Breaker Guide | Resilience pattern |

## 15. Cloud Platform

| # | Bài học | Thời lượng | Ghi chú |
|---|---|---|---|
| 1 | Spring Boot Kubernetes | 30m | Thực hành deploy cơ bản |
| 2 | Spring on Kubernetes (topical) | 1h | Đào sâu lý thuyết |
| 3 | Deploying a Spring Boot app to Azure | 30m | Nền tảng cloud khác để so sánh |

## 16. Misc

| # | Bài học | Ghi chú |
|---|---|---|
| 1 | Working a Getting Started guide with STS | Thiết lập IDE |
| 2 | Working a Getting Started guide with IntelliJ IDEA | Thiết lập IDE |
| 3 | Building a Guide with VS Code | Thiết lập IDE |
| 4 | Spring Boot with Docker | Containerize — cầu nối sang Cloud Platform |
| 5 | Creating a Multi Module Project | Cấu trúc project lớn |
| 6 | Creating CRUD UI with Vaadin | UI trực tiếp trên Spring Data JPA |
| 7 | Building web applications with Spring Boot and Kotlin | Giới thiệu Kotlin trong Spring |
| 8 | Spring Boot with Kotlin Coroutines and RSocket | Kotlin coroutine + reactive protocol RSocket |
| 9 | Observability with Spring (Tanzu Observability) | Giám sát nâng cao, mở rộng từ Ops |
| 10 | Observing GraphQL in action | GraphQL + Observability |

---

## Gợi ý khi lên slide đào tạo

- **Web:** phần SOAP có thể xếp "optional/tham khảo" nếu học viên chỉ cần REST API cho microservices.
- **REST APIs:** gộp Neo4j + GemFire REST vì cùng khái niệm Spring Data REST. Reactive REST nên tách riêng, làm cầu nối sang Streaming.
- **Data Access:** gộp nhóm NoSQL (MongoDB, Neo4j, Cassandra, Redis) thành 1 module riêng sau phần quan hệ (JDBC/JPA/MySQL).
- **IO:** gộp 3 bài đầu (Validating Form Input, Handling Form Submission, Uploading Files) thành 1 buổi "Xử lý dữ liệu form & file".
- **Security:** gộp Vault Configuration + Accessing Vault thành 1 buổi.
- **Messaging:** đây là category dài nhất sau Data Access — nên tách thành 2 buổi: (1) khái niệm + JMS/RabbitMQ/Redis, (2) WebSocket + Cloud Pub/Sub + Spring Cloud Stream.
- **Microservices:** 5 bài này nên dạy liền mạch trong cùng 1 module vì phụ thuộc chặt vào nhau (Discovery → Config → Load Balancer → Gateway → Circuit Breaker).
- **Misc:** 3 bài IDE (STS/IntelliJ/VS Code) chỉ nên nhắc qua ở buổi đầu tiên của khóa học, không cần dạy như nội dung chính; Docker nên dạy sớm hơn (trước Cloud Platform) vì là tiền đề để deploy.
