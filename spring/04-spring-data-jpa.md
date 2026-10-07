# Tuần 4: Spring Data JPA

> Tài liệu tổng hợp kiến thức mục 4 trong lộ trình [spring-boot-roadmap.md](spring-boot-roadmap.md). Nội dung đã được đối chiếu với Spring Data JPA Reference và Spring Framework Reference (6.2) qua Context7 để đảm bảo chính xác về derived query method, `@EntityGraph`, và transaction rollback rule.

---

## 1. ORM cơ bản

### 1.1 Entity
```java
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String customerName;

    private BigDecimal totalAmount;
}
```

| Chiến lược `@GeneratedValue` | Cơ chế |
|---|---|
| `IDENTITY` | Dựa vào cột auto-increment của database (MySQL, PostgreSQL `SERIAL`). Đơn giản nhưng **không dùng được JDBC batch insert hiệu quả** vì Hibernate phải insert ngay để lấy id trả về |
| `SEQUENCE` | Dùng database sequence riêng (PostgreSQL, Oracle) — Hibernate có thể lấy trước 1 dải id (`allocationSize`) để tối ưu batch insert |
| `AUTO` | Để Hibernate/JPA provider tự chọn chiến lược phù hợp với database đang dùng |
| `TABLE` | Dùng 1 bảng riêng để mô phỏng sequence — hiếm dùng vì chậm hơn (cần thêm transaction phụ) |

### 1.2 Mapping quan hệ

```java
@Entity
class Customer {
    @Id @GeneratedValue
    private Long id;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Order> orders = new ArrayList<>();
}

@Entity
class Order {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id") // Order là phía giữ khóa ngoại (owning side)
    private Customer customer;

    @ManyToMany
    @JoinTable(
        name = "order_tag",
        joinColumns = @JoinColumn(name = "order_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<Tag> tags = new HashSet<>();

    @OneToOne(mappedBy = "order")
    private Invoice invoice;
}
```
- **`mappedBy`**: đánh dấu phía **không giữ khóa ngoại** (inverse side) — trỏ tới tên field ở phía owning side. Chỉ khai báo ở **1 phía duy nhất** của quan hệ 2 chiều.
- **`@JoinColumn`**: khai báo tên cột khóa ngoại, đặt ở phía **owning side** (phía thực sự lưu khóa ngoại trong database).
- Quan hệ `@ManyToMany` cần bảng trung gian (`@JoinTable`), khai báo `joinColumns` (trỏ tới entity hiện tại) và `inverseJoinColumns` (trỏ tới entity còn lại).

### 1.3 `@Embeddable`/`@Embedded` — Value Object
Dùng khi 1 nhóm field luôn đi cùng nhau về mặt ngữ nghĩa (VD địa chỉ), không cần bảng riêng:

```java
@Embeddable
public class Address {
    private String street;
    private String city;
    private String zipCode;
}

@Entity
class Customer {
    @Embedded
    private Address address; // các field của Address được "làm phẳng" thành cột trong bảng customer
}
```

---

## 2. Repository

### 2.1 `JpaRepository<T, ID>`
```java
public interface OrderRepository extends JpaRepository<Order, Long> {
    // Có sẵn: save(), findById(), findAll(), delete(), count()...
    // Có sẵn phân trang/sort: findAll(Pageable pageable), findAll(Sort sort)
}
```

### 2.2 Derived Query Method
Spring Data JPA tự sinh JPQL dựa trên **tên method**, không cần viết query thủ công:

```java
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByCustomerNameAndStatus(String customerName, OrderStatus status);
    boolean existsByCustomerEmail(String email);
    long countByStatus(OrderStatus status);
    List<Order> findByTotalAmountGreaterThanOrderByCreatedAtDesc(BigDecimal amount);
}
```
Spring Data JPA phân tích tên method (`findBy`, `existsBy`, `countBy` + tên field + toán tử như `GreaterThan`, `Containing`, `OrderBy`...) để tự dịch thành JPQL tương ứng — tiện cho các query đơn giản, nhưng tên method dễ trở nên dài dòng nếu điều kiện quá phức tạp.

### 2.3 `@Query` (JPQL) và Native Query
```java
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("select o from Order o where o.customerEmail = ?1")
    List<Order> findByEmail(String email);

    @Query(value = "SELECT * FROM orders WHERE customer_email = ?1", nativeQuery = true)
    List<Order> findByEmailNative(String email);
}
```
`@Query` phù hợp khi derived method name quá dài hoặc cần logic query phức tạp hơn (join, subquery...); `nativeQuery = true` dùng khi cần tận dụng cú pháp SQL đặc thù của database mà JPQL không hỗ trợ.

### 2.4 Specification/Criteria API — build query động
Dùng khi điều kiện lọc **thay đổi tùy runtime** (VD form tìm kiếm có nhiều filter tùy chọn), không thể liệt kê hết bằng derived method:

```java
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {}

public class OrderSpecifications {
    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }
    public static Specification<Order> customerNameContains(String keyword) {
        return (root, query, cb) -> keyword == null ? null : cb.like(root.get("customerName"), "%" + keyword + "%");
    }
}

// Sử dụng: kết hợp động các điều kiện, bỏ qua điều kiện nào null
Specification<Order> spec = Specification
    .where(OrderSpecifications.hasStatus(status))
    .and(OrderSpecifications.customerNameContains(keyword));
List<Order> results = orderRepository.findAll(spec);
```

---

## 3. Transaction

### 3.1 `@Transactional` — Propagation
| Propagation | Ý nghĩa |
|---|---|
| `REQUIRED` (mặc định) | Dùng transaction hiện có nếu đang có, nếu chưa có thì tạo mới |
| `REQUIRES_NEW` | Luôn tạo transaction **mới**, tạm treo (suspend) transaction hiện tại nếu có — dùng khi muốn 1 đoạn logic commit/rollback **độc lập** với transaction bên ngoài (VD ghi log audit dù nghiệp vụ chính fail) |
| `NESTED` | Tạo 1 **savepoint** trong transaction hiện tại — nếu phần nested rollback, chỉ rollback về savepoint đó, không ảnh hưởng phần transaction cha đã chạy trước |
| `MANDATORY` | Bắt buộc phải **đã có sẵn** transaction đang chạy, nếu không sẽ ném exception — dùng để ép buộc method chỉ được gọi từ trong ngữ cảnh transaction |

### 3.2 Isolation Level
`READ_UNCOMMITTED`, `READ_COMMITTED`, `REPEATABLE_READ`, `SERIALIZABLE` — mức độ cô lập càng cao càng tránh được nhiều anomaly (dirty read, non-repeatable read, phantom read) nhưng đánh đổi hiệu năng do khóa nhiều hơn. `READ_COMMITTED` là mức phổ biến nhất, đủ dùng cho đa số ứng dụng.

### 3.3 Rollback Rule mặc định
Theo tài liệu chính thức của Spring Framework: **mặc định transaction chỉ tự rollback khi gặp unchecked exception** (`RuntimeException` và `Error`) — **checked exception thì không** tự động rollback.

```java
@Transactional
public void placeOrder(OrderRequest req) throws InvalidOrderException {
    orderRepository.save(new Order(req));
    if (someCondition) {
        throw new InvalidOrderException("Đơn hàng không hợp lệ"); // checked exception → KHÔNG rollback mặc định!
    }
}

@Transactional(rollbackFor = InvalidOrderException.class) // ép rollback cả với checked exception
public void placeOrderSafe(OrderRequest req) throws InvalidOrderException { ... }
```
> Đây là điểm gây bug rất phổ biến: dev tưởng "ném exception là tự rollback", nhưng nếu exception đó là **checked exception** (extends `Exception`, không extends `RuntimeException`) mà không khai báo `rollbackFor`, dữ liệu đã lưu trước đó **vẫn được commit** dù có exception xảy ra sau.

### 3.4 Vấn đề Self-invocation
```java
@Service
class OrderService {
    public void placeOrder(OrderRequest req) {
        // ...
        this.sendConfirmation(req); // gọi trực tiếp qua "this" — KHÔNG đi qua Spring proxy!
    }

    @Transactional
    public void sendConfirmation(OrderRequest req) { ... } // @Transactional bị BỎ QUA khi gọi kiểu này
}
```
`@Transactional` được Spring hiện thực bằng **AOP proxy** — khi gọi method từ **bên ngoài** class (qua bean đã được proxy), proxy sẽ chặn lời gọi để mở transaction trước khi giao cho method thật xử lý. Nhưng khi gọi `this.method()` **từ bên trong cùng class**, lời gọi đi thẳng tới object gốc, **không đi qua proxy** → annotation `@Transactional` không có tác dụng gì.

**Cách khắc phục phổ biến**: tách method cần `@Transactional` sang 1 bean/class khác rồi inject vào để gọi qua proxy, hoặc tự inject chính `ApplicationContext`/`AopContext.currentProxy()` (cần bật `exposeProxy`) để lấy proxy hiện tại — cách tách class riêng thường được khuyến nghị vì rõ ràng, dễ maintain hơn.

---

## 4. Fetch Strategy & Hiệu năng

### 4.1 Lazy vs Eager
```java
@ManyToOne(fetch = FetchType.LAZY)  // mặc định của @OneToMany/@ManyToMany là LAZY
private Customer customer;

@ManyToOne(fetch = FetchType.EAGER) // mặc định của @ManyToOne/@OneToOne là EAGER
private Category category;
```
- **`LAZY`**: chỉ query dữ liệu liên quan khi thực sự truy cập field đó (`order.getCustomer().getName()`) — nếu truy cập **sau khi** Hibernate Session/transaction đã đóng, ném `LazyInitializationException`.
- **`EAGER`**: luôn query kèm theo ngay khi load entity chính, kể cả khi không dùng đến — dễ gây thừa dữ liệu không cần thiết.
- **Best practice**: mặc định dùng `LAZY` cho hầu hết quan hệ, chỉ `EAGER` khi chắc chắn luôn cần dữ liệu đó.

### 4.2 N+1 Query Problem
Xảy ra khi query danh sách N entity, sau đó **với mỗi entity** lại tự động chạy thêm 1 query riêng để lấy quan hệ LAZY (VD load N `Order`, mỗi `Order` lại query `Customer` riêng → tổng 1 + N query thay vì 1 query duy nhất).

**Cách khắc phục:**
```java
// Cách 1: JOIN FETCH trong JPQL — ép query 1 lần duy nhất kèm quan hệ cần dùng
@Query("select o from Order o join fetch o.customer where o.status = ?1")
List<Order> findByStatusWithCustomer(OrderStatus status);

// Cách 2: @EntityGraph — khai báo attribute cần fetch kèm ngay trên method repository
public interface OrderRepository extends JpaRepository<Order, Long> {
    @EntityGraph(attributePaths = {"customer", "items"})
    List<Order> findByStatus(OrderStatus status);
}

// Cách 3: @NamedEntityGraph định nghĩa sẵn trên Entity, tái sử dụng ở nhiều query
@Entity
@NamedEntityGraph(name = "Order.detail", attributeNodes = @NamedAttributeNode("customer"))
class Order { ... }

public interface OrderRepository extends JpaRepository<Order, Long> {
    @EntityGraph(value = "Order.detail", type = EntityGraphType.LOAD)
    Optional<Order> findById(Long id);
}
```
`@EntityGraph` có 2 loại `type`: `FETCH` (chỉ fetch đúng những attribute liệt kê, các quan hệ khác về `LAZY`) và `LOAD` (giữ nguyên fetch type mặc định đã khai báo trên Entity cho các attribute không liệt kê). Ngoài ra còn có **batch fetching** (`@BatchSize` hoặc `hibernate.default_batch_fetch_size`) — gom nhiều lần fetch LAZY riêng lẻ thành ít query hơn (dùng `WHERE id IN (...)`) thay vì fetch tất cả trong 1 query như `JOIN FETCH`.

### 4.3 Pagination
```java
public interface OrderRepository extends JpaRepository<Order, Long> {
    Page<Order> findByStatus(OrderStatus status, Pageable pageable); // có tổng số phần tử/trang
    Slice<Order> findByCustomerName(String name, Pageable pageable); // không cần query COUNT, chỉ biết "còn trang sau hay không"
}

Pageable pageable = PageRequest.of(0, 20, Sort.by("createdAt").descending());
Page<Order> page = orderRepository.findByStatus(OrderStatus.PENDING, pageable);
```
`Page<T>` chạy thêm 1 query `COUNT` để biết tổng số phần tử/trang (phục vụ UI phân trang đầy đủ số), còn `Slice<T>` chỉ cần biết có trang tiếp theo hay không (dùng cho kiểu "load more") — nhẹ hơn vì không cần query đếm.

---

## 5. Auditing & Migration

### 5.1 Auditing tự động
```java
@EnableJpaAuditing // bật ở class @Configuration
public class JpaConfig {}

@Entity
@EntityListeners(AuditingEntityListener.class)
class Order {
    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @CreatedBy
    private String createdBy; // cần khai báo bean AuditorAware<String> để Spring biết "ai" đang thao tác

    @LastModifiedBy
    private String updatedBy;
}
```

### 5.2 Schema Migration — Flyway hoặc Liquibase
Quản lý version của schema database qua các file migration có đánh số thứ tự (VD `V1__create_orders_table.sql`, `V2__add_status_column.sql`), Spring Boot tự động chạy các migration chưa áp dụng khi khởi động ứng dụng.

**Tại sao tránh `spring.jpa.hibernate.ddl-auto=update` ở production:**
- Hibernate tự đoán và thay đổi schema dựa trên Entity — không kiểm soát được chính xác lệnh SQL nào sẽ chạy, dễ gây mất dữ liệu (VD đổi tên field → Hibernate hiểu là xóa cột cũ + thêm cột mới thay vì rename).
- Không có lịch sử version rõ ràng, không rollback được khi cần.
- Nhiều instance ứng dụng chạy đồng thời có thể tranh chấp khi cùng cố gắng tự động sửa schema.

`ddl-auto=update` chỉ nên dùng ở môi trường **dev/test** để tiện nghiệm nhanh; production luôn nên `ddl-auto=validate` (chỉ kiểm tra khớp, không tự sửa) hoặc `none`, và để Flyway/Liquibase quản lý toàn bộ thay đổi schema tường minh qua migration script.

---

## Câu hỏi tự kiểm tra
1. Khác nhau giữa `GenerationType.IDENTITY` và `SEQUENCE`? Vì sao `IDENTITY` gây khó khăn cho việc tối ưu batch insert?
2. `mappedBy` được khai báo ở phía nào của quan hệ 2 chiều (owning side hay inverse side)? Nếu khai báo nhầm cả 2 phía đều có `mappedBy`, điều gì xảy ra?
3. Khi nào nên dùng derived query method, khi nào nên chuyển sang `@Query`, và khi nào cần dùng `Specification`? Cho 1 tình huống cụ thể cho mỗi trường hợp.
4. Giải thích chính xác rollback rule mặc định của `@Transactional` — tại sao ném 1 checked exception mà không khai báo `rollbackFor` lại là một bug tiềm ẩn nguy hiểm?
5. Vấn đề self-invocation với `@Transactional` là gì? Vì sao gọi `this.method()` trong cùng class lại khiến annotation mất tác dụng?
6. N+1 query problem xảy ra trong tình huống nào? So sánh `JOIN FETCH`, `@EntityGraph`, và batch fetching — khi nào nên dùng cách nào?
7. Vì sao không nên dùng `ddl-auto=update` ở production? Flyway/Liquibase giải quyết vấn đề đó như thế nào?

## Bài tập luyện tập
- Thiết kế 3 Entity `Customer`–`Order`–`OrderItem` với quan hệ `@OneToMany`/`@ManyToOne` 2 chiều đầy đủ, xác định đúng owning side/inverse side, viết `Repository` cho mỗi Entity.
- Viết ít nhất 3 derived query method (kết hợp `And`, so sánh số, `OrderBy`) và 1 `@Query` JPQL tương đương cùng logic — so sánh độ dài/dễ đọc giữa 2 cách.
- Tạo tình huống N+1 thực tế: load danh sách `Order` rồi truy cập `order.getCustomer().getName()` trong vòng lặp, bật log SQL (`spring.jpa.show-sql=true`) để đếm số query thực tế chạy ra. Sau đó áp dụng `@EntityGraph` và chứng minh số query giảm xuống còn 1.
- Viết 1 method `@Transactional` cố tình ném checked exception mà không khai báo `rollbackFor`, kiểm tra dữ liệu vẫn được commit dù có exception — sau đó sửa lại bằng `rollbackFor` và kiểm chứng rollback đúng.
- Tạo tình huống self-invocation lỗi thật (gọi `this.method()` có `@Transactional` từ method khác cùng class), chứng minh transaction không hoạt động (VD method đó ném exception nhưng dữ liệu đã lưu trước đó vẫn bị commit), sau đó sửa bằng cách tách sang class/bean riêng.
- Cấu hình Flyway, viết 2 file migration (`V1__create_tables.sql`, `V2__add_index.sql`), chạy ứng dụng và kiểm tra bảng `flyway_schema_history` để xác nhận migration đã áp dụng đúng thứ tự.
