# Tuần 7: Testing & Best Practices

> Tài liệu tổng hợp kiến thức mục 7 trong lộ trình [spring-boot-roadmap.md](spring-boot-roadmap.md). Nội dung đã được đối chiếu với Spring Boot Reference (3.5) qua Context7 để đảm bảo chính xác — phát hiện quan trọng: **`@MockBean` đã bị deprecated từ Spring Boot 3.4** (dự kiến gỡ bỏ ở 4.0), thay thế bằng `@MockitoBean`. Bài học này dùng `@MockitoBean` làm chuẩn, có ghi chú về `@MockBean` cho các dự án còn dùng Boot cũ hơn.

---

## 1. Unit Test

### 1.1 JUnit 5 cơ bản
```java
class OrderCalculatorTest {

    private OrderCalculator calculator;

    @BeforeEach // chạy trước MỖI test method — dùng để khởi tạo state sạch, tránh test ảnh hưởng lẫn nhau
    void setUp() {
        calculator = new OrderCalculator();
    }

    @Test
    void calculatesTotalWithTax() {
        BigDecimal total = calculator.calculateTotal(new BigDecimal("100"), 0.1);
        assertThat(total).isEqualByComparingTo("110.00"); // dùng AssertJ, dễ đọc hơn assertEquals thuần JUnit
    }

    @ParameterizedTest
    @CsvSource({
        "100, 0.1, 110.00",
        "0, 0.1, 0.00",
        "100, 0, 100.00"
    })
    void calculatesTotalForVariousInputs(BigDecimal amount, double taxRate, BigDecimal expected) {
        assertThat(calculator.calculateTotal(amount, taxRate)).isEqualByComparingTo(expected);
    }
}
```
`@ParameterizedTest` giúp chạy **cùng 1 logic test với nhiều bộ input khác nhau** thay vì copy-paste nhiều method `@Test` gần giống nhau — nguồn dữ liệu có thể lấy từ `@CsvSource`, `@ValueSource`, `@MethodSource`...

### 1.2 Mockito — giả lập dependency
```java
@ExtendWith(MockitoExtension.class) // bật Mockito cho JUnit 5, KHÔNG load Spring context
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository; // object giả, tự tạo bởi Mockito

    @InjectMocks
    private OrderService orderService; // Mockito tự inject các @Mock ở trên vào constructor/field của class này

    @Test
    void placeOrder_savesOrderWithPendingStatus() {
        Order order = new Order("customer-1");
        when(orderRepository.save(any(Order.class))).thenReturn(order); // định nghĩa hành vi giả

        Order result = orderService.placeOrder(order);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, times(1)).save(order); // xác minh method thực sự được gọi đúng 1 lần
    }
}
```

### 1.3 `@MockitoBean` (Spring context) vs `@Mock` (Mockito thuần)
| | `@Mock` (Mockito thuần) | `@MockitoBean` (tích hợp Spring context) |
|---|---|---|
| Có load Spring `ApplicationContext` không | **Không** — chỉ tạo object giả bằng Mockito, không đụng tới Spring | **Có** — mock được đăng ký thẳng vào `ApplicationContext`, thay thế bean thật cùng type |
| Tốc độ | **Nhanh** — không tốn chi phí khởi động context | Chậm hơn — dù chỉ mock 1 bean, vẫn cần Spring khởi động context (trừ khi dùng cùng `@WebMvcTest`/`@DataJpaTest` đã giới hạn phạm vi) |
| Dùng khi | Test **unit thuần túy** 1 class, tự `new` object cần test, không cần Spring | Cần test 1 phần có tích hợp Spring (VD `@WebMvcTest` cho Controller) nhưng muốn **giả lập 1 bean cụ thể** (VD Service tầng dưới) mà Controller phụ thuộc vào |

> **Lưu ý quan trọng**: `@MockBean` (annotation cũ, cùng chức năng) đã bị **deprecated kể từ Spring Boot 3.4** và dự kiến gỡ bỏ ở Boot 4.0 — nên dùng `@MockitoBean` (từ package `org.springframework.test.context.bean.override.mockito`) cho code mới.

```java
@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvcTester mvc; // chỉ load layer web, không load toàn bộ context

    @MockitoBean // thay thế OrderService thật bằng mock, ngay trong Spring context giới hạn của @WebMvcTest
    private OrderService orderService;

    @Test
    void getOrder_returns200() {
        given(orderService.findById(1L)).willReturn(new Order("customer-1"));

        assertThat(mvc.get().uri("/orders/1"))
            .hasStatusOk();
    }
}
```

---

## 2. Integration Test

### 2.1 `@SpringBootTest` — load toàn bộ context
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderIntegrationTest {
    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createOrder_endToEnd() {
        ResponseEntity<Order> response = restTemplate.postForEntity("/orders", new OrderRequest(...), Order.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
```
Dùng khi cần kiểm chứng **toàn bộ luồng thực tế** (Controller → Service → Repository → Database) hoạt động đúng cùng nhau — chi phí chạy chậm hơn nhiều so với unit test vì phải khởi động toàn bộ Spring context.

### 2.2 Test Slice — chỉ load 1 phần context cần thiết
| Annotation | Chỉ load |
|---|---|
| `@WebMvcTest(OrderController.class)` | Layer web (Controller, filter, `@ControllerAdvice`...) — **không** load Service/Repository thật, cần `@MockitoBean` cho dependency |
| `@DataJpaTest` | Layer JPA (Entity, Repository) — tự động cấu hình **in-memory database** (H2) thay database thật, mỗi test tự rollback transaction sau khi chạy xong |

```java
@DataJpaTest
class OrderRepositoryTest {
    @Autowired
    private TestEntityManager entityManager; // tiện ích để persist dữ liệu test trực tiếp, bỏ qua tầng Service

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void findByCustomerName_returnsMatchingOrders() {
        entityManager.persist(new Order("Alice"));
        List<Order> results = orderRepository.findByCustomerName("Alice");
        assertThat(results).hasSize(1);
    }
}
```

### 2.3 TestContainers — test với database thật
```java
@Testcontainers
@SpringBootTest
class OrderRepositoryContainerTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Test
    void repositoryWorksAgainstRealPostgres() { ... }
}
```
So với H2 (in-memory, giả lập cú pháp SQL của database thật), TestContainers chạy **đúng engine database thật** (Postgres/MySQL) trong Docker container — phát hiện được các vấn đề đặc thù của database thật mà H2 không mô phỏng chính xác (VD hành vi kiểu dữ liệu, function riêng, ràng buộc constraint), đổi lại chi phí chạy chậm hơn và cần Docker sẵn có trong môi trường CI.

---

## 3. Logging

### 3.1 SLF4J + Logback
Spring Boot mặc định dùng **SLF4J** làm facade logging (API chung) và **Logback** làm implementation thực thi phía sau:
```java
@Slf4j // Lombok tự sinh field `log`, tương đương viết tay: private static final Logger log = LoggerFactory.getLogger(OrderService.class);
@Service
public class OrderService {
    public void placeOrder(Order order) {
        log.info("Placing order for customer={}", order.getCustomerName()); // dùng placeholder {}, KHÔNG string concatenation
        // ...
    }
}
```

### 3.2 Cấu hình log level theo profile
```yaml
# application-dev.yml
logging:
  level:
    root: INFO
    com.myapp: DEBUG # chi tiết hơn ở dev để dễ debug

# application-prod.yml
logging:
  level:
    root: WARN
    com.myapp: INFO # gọn hơn ở production, tránh log quá nhiều gây tốn chi phí lưu trữ/I-O
```

### 3.3 Structured Logging & tránh log thông tin nhạy cảm
- **Structured logging** (log dạng JSON có field rõ ràng thay vì text tự do) giúp các hệ thống tổng hợp log (ELK, Grafana Loki...) dễ dàng parse, filter, alert theo field cụ thể thay vì phải regex chuỗi text.
- **Nguyên tắc bắt buộc**: **không bao giờ** log password, token, số thẻ tín dụng, hay bất kỳ PII (Personally Identifiable Information) nhạy cảm nào ra log — kể cả ở mức `DEBUG`, vì log thường được lưu trữ lâu dài và nhiều người có quyền truy cập hơn database. Cần rà soát kỹ các dòng log tự động in ra toàn bộ object (VD `log.debug("Request: {}", request)` nếu `request` chứa field password).

---

## 4. Cấu trúc code & Best Practice

### 4.1 Layered Architecture
```
Controller  →  Service  →  Repository
(nhận HTTP)   (business logic)  (truy cập dữ liệu)
```
- **Controller**: chỉ xử lý HTTP concern (parse request, validate, trả response) — **không** chứa business logic.
- **Service**: chứa toàn bộ business logic, orchestrate giữa nhiều Repository nếu cần, nơi đặt `@Transactional`.
- **Repository**: chỉ chịu trách nhiệm truy cập dữ liệu, không chứa business logic.

Tách rõ trách nhiệm giúp: test dễ hơn (mock từng layer độc lập), thay đổi công nghệ ở 1 layer không ảnh hưởng layer khác (VD đổi ORM không ảnh hưởng Controller).

### 4.2 Package theo Layer vs theo Feature
```
# Package theo Layer (truyền thống)          # Package theo Feature (khuyến nghị cho hệ thống lớn)
com.myapp.controller                          com.myapp.order
  OrderController, UserController               OrderController, OrderService, OrderRepository
com.myapp.service                             com.myapp.user
  OrderService, UserService                      UserController, UserService, UserRepository
com.myapp.repository
  OrderRepository, UserRepository
```
| | Theo Layer | Theo Feature |
|---|---|---|
| Ưu điểm | Dễ hiểu ngay từ đầu, quen thuộc | **Cao cohesion** — mọi thứ liên quan tới 1 nghiệp vụ nằm gần nhau, dễ tìm, dễ xóa cả 1 feature khi không cần nữa |
| Nhược điểm | Khi hệ thống lớn, phải nhảy qua lại nhiều package để hiểu trọn 1 luồng nghiệp vụ | Cần kỷ luật đặt tên/tổ chức nhất quán, mới tiếp cận có thể hơi lạ nếu quen kiểu layer |
| Phù hợp | Project nhỏ, ít module | Project vừa/lớn, nhiều team làm việc song song trên các feature khác nhau |

### 4.3 Chuẩn hóa response & exception xuyên suốt hệ thống
Đã trình bày chi tiết ở [03-spring-mvc-restful-api.md](03-spring-mvc-restful-api.md) mục 3 — nhắc lại nguyên tắc cốt lõi: **1 cấu trúc `ApiError` duy nhất** dùng cho mọi lỗi trong toàn hệ thống, xử lý tập trung qua `@RestControllerAdvice`, không để mỗi Controller tự xử lý exception theo cách khác nhau.

---

## Câu hỏi tự kiểm tra
1. `@Mock` và `@MockitoBean` khác nhau cơ bản ở điểm nào? Vì sao unit test thuần túy nên ưu tiên `@Mock` thay vì `@MockitoBean` dù cả 2 đều "mock được"?
2. Vì sao `@MockBean` bị deprecated? Nếu đang maintain 1 project cũ dùng Spring Boot 3.3 trở xuống, có cần vội migrate sang `@MockitoBean` ngay không?
3. `@WebMvcTest` và `@DataJpaTest` khác nhau ở việc load phần nào của Spring context? Vì sao dùng test slice thay vì `@SpringBootTest` cho mọi test lại giúp suite chạy nhanh hơn đáng kể?
4. TestContainers giải quyết vấn đề gì mà H2 in-memory không giải quyết được? Đánh đổi gì khi chọn TestContainers thay vì H2?
5. Vì sao Controller không nên chứa business logic? Nếu vi phạm nguyên tắc này, điều gì xảy ra khi cần viết unit test cho business logic đó?
6. So sánh package theo layer và package theo feature — với 1 project có 20+ feature nghiệp vụ khác nhau, cách nào sẽ dễ maintain hơn về lâu dài? Vì sao?
7. Nêu 1 ví dụ cụ thể về dòng log tưởng như vô hại nhưng vô tình làm lộ thông tin nhạy cảm (không phải ví dụ log trực tiếp `password`).

## Bài tập luyện tập
- Viết unit test cho 1 class `OrderCalculator` (không phụ thuộc Spring) dùng JUnit 5 + `@ParameterizedTest` với ít nhất 5 bộ dữ liệu khác nhau, đạt coverage 100% cho class đó.
- Viết unit test cho `OrderService` dùng Mockito thuần (`@Mock`/`@InjectMocks`, không có Spring context) — đo thời gian chạy, so sánh với 1 bản viết lại dùng `@SpringBootTest` cho cùng logic để thấy chênh lệch tốc độ rõ rệt.
- Viết test cho `OrderController` dùng `@WebMvcTest` kết hợp `@MockitoBean` cho `OrderService` — kiểm chứng đúng status code và response body mà không cần database thật.
- Viết test cho `OrderRepository` dùng `@DataJpaTest` với `TestEntityManager`, sau đó viết lại đúng test đó dùng TestContainers với Postgres thật — so sánh sự khác biệt (nếu có) khi chạy 1 native query có cú pháp đặc thù của Postgres mà H2 không hỗ trợ đúng.
- Refactor 1 project mẫu nhỏ đang tổ chức package theo layer sang package theo feature, đo lường (bằng cảm nhận cá nhân) mức độ dễ tìm code liên quan tới 1 nghiệp vụ cụ thể trước và sau khi refactor.
