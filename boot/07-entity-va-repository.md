# Tuần 7 — Entity và Repository với Spring Data JPA

> Stack đề xuất: Java 21, Spring Boot 4.x, Spring Data JPA, Hibernate, PostgreSQL và Flyway. Bài này tiếp nối Task API của tuần 5–6 và thay repository in-memory bằng database thật.

## 1. Mục tiêu bài học

Sau tuần này, bạn có thể:

- Phân biệt entity, DTO, domain model và database row.
- Mapping một Java class bằng `@Entity`, `@Id` và `@GeneratedValue`.
- Giải thích bốn trạng thái chính của entity: transient, managed, detached và removed.
- Hiểu persistence context, dirty checking, flush và khác biệt giữa `persist`/`merge`.
- Tạo repository với `JpaRepository`.
- Viết derived query method, JPQL và native query đúng trường hợp.
- Tự động điền `createdAt`, `updatedAt` bằng JPA auditing.
- Phân trang và sắp xếp với `Page`, `Pageable`, `Sort`.
- Dùng DTO projection để chỉ lấy dữ liệu cần thiết.
- Quản lý schema PostgreSQL bằng Flyway thay vì `ddl-auto=update`.
- Test repository trên PostgreSQL thật bằng Testcontainers.

---

## 2. Bức tranh tổng thể

Sau khi thay repository in-memory, luồng chính là:

```text
HTTP request
    ↓
TaskController        request/response DTO
    ↓
TaskService           use case và business rule
    ↓
TaskRepository        abstraction của application
    ↓
JpaTaskRepositoryAdapter
    ↓
SpringDataTaskRepository extends JpaRepository
    ↓
Hibernate / JDBC
    ↓
PostgreSQL
```

Flyway chạy trước khi ứng dụng sử dụng schema:

```text
V1__create_tasks.sql → flyway_schema_history → schema PostgreSQL
```

Ba khái niệm thường bị trộn lẫn:

| Khái niệm | Trách nhiệm |
|---|---|
| Entity | Object được JPA quản lý và mapping với table |
| DTO | Dữ liệu đi qua boundary HTTP hoặc projection |
| Repository | Cổng truy cập dữ liệu, không chứa HTTP logic |

Không trả entity trực tiếp từ controller. Entity thay đổi theo database/persistence, còn DTO là API contract dành cho client.

---

## 3. Thêm dependency

Nếu project đã có Web và Validation, bổ sung vào `pom.xml`:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-flyway</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-database-postgresql</artifactId>
    </dependency>

    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-testcontainers</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>testcontainers-postgresql</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

Khi dùng Spring Boot parent/BOM, không tự ghi version cho các dependency do Boot quản lý. Điều này giữ các phiên bản tương thích với nhau.

---

## 4. PostgreSQL cho môi trường local

Ví dụ `compose.yml` chỉ dùng cho máy local:

```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: ${DB_NAME:-taskdb}
      POSTGRES_USER: ${DB_USERNAME:-taskapp}
      POSTGRES_PASSWORD: ${DB_PASSWORD:?set DB_PASSWORD before starting Compose}
    ports:
      - "5432:5432"
    volumes:
      - taskdb-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $${POSTGRES_USER} -d $${POSTGRES_DB}"]
      interval: 5s
      timeout: 3s
      retries: 10

volumes:
  taskdb-data:
```

Đặt password local trong environment rồi khởi động; không ghi giá trị thật vào file:

```bash
export DB_PASSWORD='<your-local-password>'
docker compose up -d postgres
```

Production phải lấy secret từ secret manager hoặc environment do platform cấp, không commit secret thật vào Git.

---

## 5. Cấu hình datasource, JPA và Flyway

`application.yml` không chứa fallback credential:

```yaml
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}

  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        format_sql: true

  flyway:
    enabled: true
    locations: classpath:db/migration
```

`application-local.yml` mới chứa giá trị tiện dụng cho container local:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/taskdb
    username: taskapp
    password: ${DB_PASSWORD}
```

Chạy local với profile tường minh:

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

Ý nghĩa:

- `ddl-auto: validate`: Hibernate đối chiếu mapping với schema và fail fast nếu lệch; không tự sửa schema.
- `open-in-view: false`: không kéo persistence context ra toàn bộ request; service phải chủ động tải dữ liệu cần dùng.
- `flyway.locations`: nơi chứa migration, mặc định cũng là `classpath:db/migration`.
- Production phải cung cấp đủ `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`; thiếu biến thì ứng dụng fail fast.

Không dùng `ddl-auto: update` trong production. Hibernate có thể thay schema theo mapping nhưng không tạo ra lịch sử thay đổi có review, checksum và quy trình deploy rõ ràng.

Trong test, có thể bật SQL log tạm thời:

```yaml
logging:
  level:
    org.hibernate.SQL: debug
    org.hibernate.orm.jdbc.bind: trace
```

Không bật bind-value log ở production vì tham số SQL có thể chứa dữ liệu nhạy cảm và tạo lượng log lớn.

---

## 6. Migration đầu tiên với Flyway

Tạo file:

```text
src/main/resources/db/migration/V1__create_tasks.sql
```

Nội dung:

```sql
CREATE TABLE tasks (
    id              UUID PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    description     VARCHAR(2000),
    assignee_email  VARCHAR(254),
    status          VARCHAR(30) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,

    CONSTRAINT ck_tasks_status
        CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE'))
);

CREATE INDEX idx_tasks_status_created_at
    ON tasks (status, created_at DESC, id DESC);
```

Tên migration theo mẫu:

```text
V<version>__<description>.sql
V1__create_tasks.sql
V2__add_task_priority.sql
V3__index_tasks_by_assignee.sql
```

Flyway lưu migration đã chạy và checksum trong `flyway_schema_history`. Vì vậy:

- Không sửa migration đã chạy ở môi trường dùng chung.
- Muốn đổi schema, tạo migration phiên bản mới.
- Commit migration cùng code cần schema đó.
- Review SQL như review code.
- Backup và kiểm tra kế hoạch rollback/roll-forward trước thay đổi nguy hiểm.

`repair` chỉ sửa metadata/checksum trong schema history; nó không tự sửa schema bị sai. `baseline-on-migrate` cũng không phải nút “chữa lỗi”: cấu hình sai có thể baseline nhầm database. Chỉ dùng hai tính năng này sau khi xác minh database, migration history và có kế hoạch phục hồi.

---

## 7. Mapping `TaskEntity`

Ở tuần 5, `TaskEntity` là `record` bất biến cho repository in-memory. JPA entity phù hợp hơn với class có constructor không tham số và state được Hibernate quản lý.

```java
package com.example.taskapi.task;

import com.example.taskapi.task.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "tasks")
@EntityListeners(AuditingEntityListener.class)
public class TaskEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 2_000)
    private String description;

    @Column(name = "assignee_email", length = 254)
    private String assigneeEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TaskStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TaskEntity() {
        // JPA cần constructor không tham số.
    }

    private TaskEntity(
            String title,
            String description,
            String assigneeEmail,
            TaskStatus status
    ) {
        this.title = requireValidTitle(title);
        this.description = description;
        this.assigneeEmail = assigneeEmail;
        this.status = Objects.requireNonNull(status, "status must not be null");
    }

    public static TaskEntity create(
            String title,
            String description,
            String assigneeEmail
    ) {
        return new TaskEntity(
                title,
                description,
                assigneeEmail,
                TaskStatus.TODO
        );
    }

    public void updateDetails(
            String title,
            String description,
            String assigneeEmail
    ) {
        this.title = requireValidTitle(title);
        this.description = description;
        this.assigneeEmail = assigneeEmail;
    }

    public void changeStatus(TaskStatus newStatus) {
        this.status = Objects.requireNonNull(
                newStatus,
                "newStatus must not be null"
        );
    }

    private static String requireValidTitle(String value) {
        String title = Objects.requireNonNull(
                value,
                "title must not be null"
        ).strip();
        if (title.isEmpty() || title.length() > 200) {
            throw new IllegalArgumentException(
                    "title must contain 1 to 200 characters"
            );
        }
        return title;
    }

    public UUID getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getAssigneeEmail() { return assigneeEmail; }
    public TaskStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
```

### 7.1. Những lựa chọn quan trọng

#### Field access

`@Id` đặt trên field nên JPA dùng field access. Hibernate đọc/ghi field trực tiếp; annotation mapping nên đặt nhất quán trên field, không trộn tùy tiện với getter.

#### Constructor `protected`

JPA cần constructor không tham số `public` hoặc `protected`. Giữ `protected` để application không tạo entity thiếu invariant.

#### Không có setter công khai cho mọi field

Các method có ý nghĩa như `changeStatus` thể hiện intent tốt hơn `setStatus`. Validation HTTP bảo vệ boundary nhưng không đủ vì batch job hoặc message consumer cũng có thể gọi service. Entity/service vẫn phải bảo vệ invariant như title bắt buộc và status không được `null`; database constraint là lớp phòng vệ cuối cùng.

#### Enum dùng `STRING`

`EnumType.STRING` lưu `TODO`, `DONE` thay vì ordinal `0`, `1`. Thứ tự enum có thể đổi; ordinal làm dữ liệu cũ bị hiểu sai.

#### UUID

`GenerationType.UUID` để JPA provider sinh UUID. Migration vì thế chỉ cần column `UUID PRIMARY KEY`, không cần default function của PostgreSQL.

### 7.2. Entity không hoàn toàn immutable

JPA/Hibernate cần quản lý state, proxy và dirty checking. Do đó đừng ép entity thành `record` chỉ vì muốn bất biến. Cách thực tế là:

- Giữ field `private`.
- Không public setter đại trà.
- Chỉ thay đổi qua method có ý nghĩa.
- Dùng immutable record cho request/response DTO và command.
- Không truyền entity ra khỏi boundary không cần thiết.

### 7.3. `equals()` và `hashCode()`

Generated ID chưa có trước khi persist, còn Hibernate có thể dùng proxy. Một implementation dựa hoàn toàn vào mọi mutable field rất dễ làm hỏng `HashSet`/`HashMap` sau khi entity thay đổi.

Với bài này, không cần override. Khi thật sự cần so sánh entity:

- Chọn business key bất biến nếu có.
- Hoặc so sánh generated ID chỉ khi ID khác `null`, đồng thời test với proxy.
- Không dùng Lombok `@Data` bừa bãi cho entity, nhất là khi sau này có quan hệ hai chiều.

---

## 8. Entity lifecycle và persistence context

Một entity thường đi qua các trạng thái:

```text
new TaskEntity(...)
        │
        │ persist/save entity mới
        ▼
     MANAGED ───────── dirty checking ────────┐
        │                                      │
        │ clear/close/detach                   │ flush/commit
        ▼                                      ▼
     DETACHED                              SQL UPDATE
        │
        │ merge/save entity cũ
        ▼
     MANAGED COPY

MANAGED ── remove/delete ──▶ REMOVED ── flush ──▶ SQL DELETE
```

### 8.1. Transient/new

Object vừa được tạo bằng `TaskEntity.create(...)`, chưa thuộc persistence context và chưa đại diện cho row đã được lưu.

### 8.2. Managed/persistent

Entity được load hoặc persist trong persistence context hiện tại. Hibernate theo dõi thay đổi của nó. Trong transaction:

```java
TaskEntity task = repository.findById(id).orElseThrow(...);
task.changeStatus(TaskStatus.DONE);
```

Nếu `task` vẫn managed, Hibernate phát hiện state thay đổi và sinh `UPDATE` khi flush; không bắt buộc gọi `save()` lần nữa.

### 8.3. Detached

Entity từng managed nhưng persistence context đã đóng, bị `clear()` hoặc `detach()`. Sửa detached entity không được dirty checking tự động ghi xuống database.

### 8.4. Removed

Entity đã được đánh dấu xóa; SQL `DELETE` thường được gửi khi flush.

### 8.5. Persistence context là gì?

Persistence context là vùng Hibernate theo dõi entity trong một unit of work. Nó có:

- Identity guarantee: cùng một row thường ánh xạ về cùng object instance trong context.
- First-level cache: tìm lại entity có thể không query database lần nữa.
- Dirty checking: so state hiện tại với snapshot để tạo `UPDATE`.
- Write-behind: gom thay đổi và đẩy SQL ở thời điểm flush.

Persistence context không phải cache dùng chung cho toàn ứng dụng. Nó thường gắn với transaction/request boundary và không thay thế Redis hoặc second-level cache.

### 8.6. `flush()` không phải `commit()`

- `flush`: đồng bộ thay đổi trong persistence context thành SQL với database connection.
- `commit`: hoàn tất transaction để thay đổi trở nên bền vững theo isolation/ACID.

SQL có thể chạy khi flush nhưng transaction vẫn rollback sau đó. Hibernate cũng có thể flush trước một query để kết quả query phản ánh thay đổi đang chờ.

### 8.7. `save()` dùng `persist` hay `merge`?

Spring Data JPA xác định entity mới rồi thường:

- Gọi `EntityManager.persist()` cho entity mới.
- Gọi `EntityManager.merge()` cho entity được xem là đã tồn tại.

`merge()` trả về managed instance; object truyền vào không tự biến thành managed. Vì vậy luôn dùng giá trị trả về:

```java
TaskEntity saved = repository.save(task);
```

Đừng coi `save()` là “chạy INSERT ngay”. SQL thường được gửi lúc flush/commit; `saveAndFlush()` chỉ nên dùng khi thực sự cần database kiểm tra hoặc dữ liệu phải thấy ngay trong cùng flow.

---

## 9. Lifecycle callback và auditing

JPA có callback như:

```text
@PrePersist   @PostPersist
@PreUpdate    @PostUpdate
@PreRemove    @PostRemove
@PostLoad
```

Callback phù hợp với việc nhỏ, đồng bộ và gắn chặt với persistence, ví dụ chuẩn hóa metadata. Không gọi HTTP, gửi email, query phức tạp hoặc điều phối business workflow trong callback vì thời điểm flush có thể khó đoán và lỗi sẽ làm transaction thất bại.

### 9.1. Bật auditing

```java
package com.example.taskapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing
public class JpaAuditingConfiguration {
}
```

`@CreatedDate` và `@LastModifiedDate` trong entity sẽ được `AuditingEntityListener` điền. Chỉ auditing thời gian thì không cần `AuditorAware`. Khi thêm `@CreatedBy`/`@LastModifiedBy`, mới cần cung cấp `AuditorAware<UserId>` để lấy người dùng hiện tại.

Sử dụng `Instant` cho thời điểm tuyệt đối. API có thể serialize dạng UTC ISO-8601, còn UI tự đổi sang múi giờ người dùng.

---

## 10. Tạo `JpaRepository`

```java
package com.example.taskapi.task.persistence;

import com.example.taskapi.task.TaskEntity;
import com.example.taskapi.task.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SpringDataTaskRepository
        extends JpaRepository<TaskEntity, UUID> {

    Page<TaskEntity> findByStatus(TaskStatus status, Pageable pageable);

    List<TaskEntity> findTop10ByStatusOrderByCreatedAtDesc(TaskStatus status);

    boolean existsByTitleIgnoreCase(String title);
}
```

`JpaRepository<TaskEntity, UUID>` cung cấp sẵn các thao tác như:

```text
save, saveAll, findById, existsById,
findAll, count, delete, deleteById,
flush, saveAndFlush
```

Không cần viết implementation cho interface này; Spring Data tạo proxy bean khi khởi động.

---

## 11. Derived query method

Spring Data phân tích tên method để tạo query:

```java
Page<TaskEntity> findByStatus(TaskStatus status, Pageable pageable);

List<TaskEntity> findByAssigneeEmailIgnoreCase(String email);

List<TaskEntity> findTop10ByStatusOrderByCreatedAtDesc(TaskStatus status);

boolean existsByTitleIgnoreCase(String title);
```

Tên method gồm các phần:

```text
find + Top10 + By + Status + OrderBy + CreatedAt + Desc
```

Ưu điểm:

- Ngắn, type-safe theo tên property.
- Phù hợp query đơn giản.
- Refactor property thường được IDE hỗ trợ.

Nhược điểm:

- Tên dài và khó đọc khi có nhiều điều kiện.
- Logic optional filter phức tạp nhanh chóng trở nên rối.
- Tên đúng cú pháp chưa chắc query hiệu quả.

Nếu tên bắt đầu giống một câu văn dài, chuyển sang `@Query`, Specification/Querydsl hoặc query object phù hợp.

---

## 12. JPQL với `@Query`

JPQL query theo **tên entity và Java field**, không theo table/column:

```java
@Query("""
        select t
        from TaskEntity t
        where (:status is null or t.status = :status)
          and (
              :keyword is null
              or lower(t.title) like lower(concat('%', :keyword, '%'))
          )
        """)
Page<TaskEntity> search(
        @Param("status") TaskStatus status,
        @Param("keyword") String keyword,
        Pageable pageable
);
```

Import cần thiết:

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
```

Giá trị được bind bằng parameter, không nối chuỗi input vào query. Parameter binding vừa đúng kiểu vừa tránh SQL/JPQL injection.

Với nhiều optional filter, dạng `:value is null or ...` dễ viết nhưng có thể tạo plan kém. Hãy đo bằng SQL log và `EXPLAIN ANALYZE`; khi bộ lọc tăng, cân nhắc query động có cấu trúc.

---

## 13. Native query

Native query dùng SQL của database, phù hợp khi cần tính năng PostgreSQL hoặc query tối ưu mà JPQL không diễn đạt tốt:

```java
@Query(
        value = """
                select *
                from tasks t
                where t.title ilike concat('%', :keyword, '%')
                order by t.created_at desc, t.id desc
                """,
        countQuery = """
                select count(*)
                from tasks t
                where t.title ilike concat('%', :keyword, '%')
                """,
        nativeQuery = true
)
Page<TaskEntity> searchTitleNative(
        @Param("keyword") String keyword,
        Pageable pageable
);
```

Khác JPQL, native query dùng `tasks`, `created_at` là tên thật trong database. Khi trả `Page`, cung cấp `countQuery` rõ ràng cho query phức tạp để Spring Data tính tổng số phần tử.

Query trên sở hữu thứ tự cố định `created_at, id`; caller chỉ truyền `PageRequest.of(page, size)` không kèm `Sort`. Nếu API cho client chọn sort, hãy dùng một query path khác, bỏ `ORDER BY` cố định và chỉ tạo `Sort` từ allowlist đã kiểm tra. Không để hai nguồn cùng quyết định thứ tự.

Chỉ dùng native query khi có lý do đo được:

- Cần `ILIKE`, CTE, window function, JSONB hoặc PostgreSQL-specific operator.
- Cần kiểm soát SQL/plan chính xác.
- JPQL tạo SQL không phù hợp.

Đổi lại, query bị gắn với database và refactor mapping khó hơn. Mọi giá trị từ client vẫn phải đi qua parameter binding.

---

## 14. `Page`, `Pageable` và `Sort`

Tạo pageable trong application layer:

```java
Pageable pageable = PageRequest.of(
        page,
        size,
        Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("id")
        )
);

Page<TaskEntity> result = status == null
        ? repository.findAll(pageable)
        : repository.findByStatus(status, pageable);
```

Import:

```java
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
```

### 14.1. `Page` và `Slice`

| Kiểu | Nội dung | Count query |
|---|---|---|
| `Page<T>` | Data + tổng phần tử + tổng trang | Thường có |
| `Slice<T>` | Data + có trang kế tiếp hay không | Không cần tổng đầy đủ |
| `List<T>` | Chỉ danh sách | Không có metadata |

Nếu UI không cần tổng trang, `Slice` tránh một count query có thể tốn kém.

### 14.2. Sort phải có allowlist

Không chuyển thẳng field tùy ý của client vào persistence query. Map API field vào property được phép:

```java
private static final Map<String, String> ALLOWED_SORTS = Map.of(
        "createdAt", "createdAt",
        "updatedAt", "updatedAt",
        "title", "title",
        "status", "status"
);

String property = Optional.ofNullable(ALLOWED_SORTS.get(requestedSort))
        .orElseThrow(() -> new InvalidSortException(requestedSort));
```

Giới hạn `size`, ví dụ `1..100`, để một request không đọc cả bảng. Thêm secondary sort ổn định như `id` để row có cùng `createdAt` không nhảy giữa các trang.

Offset pagination phù hợp giai đoạn đầu. Với bảng lớn và người dùng duyệt sâu, nghiên cứu keyset/cursor pagination ở phần nâng cao.

---

## 15. DTO projection

Projection giúp chỉ lấy column cần cho use case danh sách, thay vì hydrate toàn bộ entity.

### 15.1. Interface projection

```java
public interface TaskSummaryView {
    UUID getId();
    String getTitle();
    TaskStatus getStatus();
    Instant getCreatedAt();
}
```

```java
Page<TaskSummaryView> findProjectedByStatus(
        TaskStatus status,
        Pageable pageable
);
```

Tên getter phải khớp property entity hoặc alias query.

### 15.2. Record/class projection bằng JPQL

```java
package com.example.taskapi.task.persistence;

import com.example.taskapi.task.TaskStatus;

import java.time.Instant;
import java.util.UUID;

public record TaskSummary(
        UUID id,
        String title,
        TaskStatus status,
        Instant createdAt
) {
}
```

```java
@Query("""
        select new com.example.taskapi.task.persistence.TaskSummary(
            t.id, t.title, t.status, t.createdAt
        )
        from TaskEntity t
        where t.status = :status
        """)
Page<TaskSummary> findSummariesByStatus(
        @Param("status") TaskStatus status,
        Pageable pageable
);
```

Ưu tiên projection có type thay vì `Object[]`. Tuy nhiên, projection không tự động đảm bảo nhanh: query, index, số row và execution plan vẫn quyết định hiệu năng.

Projection persistence cũng chưa nhất thiết là public response DTO. API layer có thể map nó sang response contract để tránh rò rỉ package/persistence choice.

---

## 16. Giữ abstraction khi thay repository in-memory

Nếu service tuần trước phụ thuộc interface riêng, giữ interface đó thay vì cho service phụ thuộc trực tiếp `JpaRepository`:

```java
package com.example.taskapi.task;

import java.util.Optional;
import java.util.UUID;

public interface TaskRepository {
    TaskEntity save(TaskEntity task);
    Optional<TaskEntity> findById(UUID id);
    TaskSlice findAll(TaskStatus status, int page, int size);
    void delete(TaskEntity task);
}
```

Adapter triển khai bằng Spring Data:

```java
package com.example.taskapi.task.persistence;

import com.example.taskapi.task.TaskEntity;
import com.example.taskapi.task.TaskRepository;
import com.example.taskapi.task.TaskSlice;
import com.example.taskapi.task.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class JpaTaskRepositoryAdapter implements TaskRepository {

    private final SpringDataTaskRepository repository;

    public JpaTaskRepositoryAdapter(SpringDataTaskRepository repository) {
        this.repository = repository;
    }

    @Override
    public TaskEntity save(TaskEntity task) {
        return repository.save(task);
    }

    @Override
    public Optional<TaskEntity> findById(UUID id) {
        return repository.findById(id);
    }

    @Override
    public TaskSlice findAll(TaskStatus status, int page, int size) {
        var pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))
        );
        Page<TaskEntity> result = status == null
                ? repository.findAll(pageable)
                : repository.findByStatus(status, pageable);

        return new TaskSlice(
                result.getContent(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages()
        );
    }

    @Override
    public void delete(TaskEntity task) {
        repository.delete(task);
    }
}
```

> Lưu ý khi ghép với code tuần 5: khi `TaskEntity` chuyển từ record sang class, mapper đổi `task.id()` thành `task.getId()`, tương tự với các field khác.

Lợi ích của adapter:

- Service không phụ thuộc Spring Data API.
- Có thể thay persistence implementation mà không sửa use case.
- `Page` của framework không tràn lên HTTP contract.

Ứng dụng CRUD nhỏ có thể dùng `JpaRepository` trực tiếp trong service để giảm code. Nhưng cần đưa ra lựa chọn có chủ đích, không để boundary hình thành ngẫu nhiên.

---

## 17. Transaction và dirty checking trong service

Transaction sẽ được học sâu ở tuần 8. Ở tuần này cần nắm boundary tối thiểu:

```java
@Service
public class DefaultTaskService implements TaskService {

    private final TaskRepository taskRepository;

    public DefaultTaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public TaskEntity updateStatus(UUID id, TaskStatus newStatus) {
        TaskEntity task = taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException(id));

        task.changeStatus(newStatus);
        return task;
    }
}
```

Entity load trong transaction vẫn managed nên dirty checking tạo `UPDATE` lúc flush/commit. Transaction nên bao trọn use case, không đặt ở controller. Query đơn giản do Spring Data cung cấp đã có transaction semantics, nhưng nhiều bước read–validate–write phải có một application transaction rõ ràng.

Không dựa vào Open Session in View để truy cập lazy data trong controller/serializer. Tuần 8 sẽ xử lý relationship, lazy loading, N+1 và transaction boundary kỹ hơn.

---

## 18. Test repository với PostgreSQL thật

H2 không giống hoàn toàn PostgreSQL về type, SQL dialect, constraint và native feature như `ILIKE`. Testcontainers giúp test trên PostgreSQL tạm thời.

### 18.1. Cấu hình container cho test

```java
package com.example.taskapi.testconfig;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

@TestConfiguration(proxyBeanMethods = false)
public class ContainersConfiguration {

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17-alpine");
    }
}
```

`@ServiceConnection` để Spring Boot tự lấy JDBC URL, username và password từ container, không hardcode port ngẫu nhiên.

### 18.2. Repository integration test

```java
package com.example.taskapi.task.persistence;

import com.example.taskapi.task.TaskEntity;
import com.example.taskapi.task.TaskStatus;
import com.example.taskapi.config.JpaAuditingConfiguration;
import com.example.taskapi.testconfig.ContainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({ContainersConfiguration.class, JpaAuditingConfiguration.class})
class SpringDataTaskRepositoryTest {

    @Autowired
    private SpringDataTaskRepository repository;

    @Test
    void savesAndFindsTaskByStatus() {
        TaskEntity task = TaskEntity.create(
                "Learn Spring Data JPA",
                "Replace the in-memory repository",
                "learner@example.com"
        );

        TaskEntity saved = repository.saveAndFlush(task);
        var page = repository.findByStatus(
                TaskStatus.TODO,
                PageRequest.of(0, 20)
        );

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(page.getContent())
                .extracting(TaskEntity::getId)
                .contains(saved.getId());
    }
}
```

`@DataJpaTest` tập trung vào JPA slice và rollback sau mỗi test. Trong cấu hình trên, `@ServiceConnection` phải cung cấp datasource PostgreSQL từ container và Flyway phải tạo schema trước test. Kiểm tra log để chắc chắn JDBC URL là PostgreSQL container; nếu Boot báo thay datasource hoặc tìm embedded database, cấu hình test database replacement theo phiên bản Boot đang dùng thay vì vô tình chạy H2. Để test chạy cần Docker-compatible runtime.

Nên có test cho:

- V1 migration chạy thành công trên database trống.
- Mapping schema khớp khi `ddl-auto=validate`.
- UUID và audit timestamp được sinh.
- Derived query lọc đúng status.
- JPQL/native query bind input đúng.
- Pagination, sort ổn định và count đúng.
- Constraint database từ chối dữ liệu không hợp lệ.

Đừng chỉ test method repository trả kết quả “happy path”; migration và mapping mismatch mới là lỗi thường xuất hiện khi deploy.

---

## 19. Lỗi thường gặp

### 19.1. Trả entity trực tiếp ra API

Hậu quả: API phụ thuộc schema, dễ serialize lazy relation, lộ field nội bộ và khó version. Luôn map sang response DTO.

### 19.2. Dùng `ddl-auto=update` như migration tool

Không có migration history được review, khó biết production đã thay gì và khó phối hợp nhiều version ứng dụng. Dùng Flyway; Hibernate chỉ `validate`.

### 19.3. Sửa migration đã chạy

Flyway phát hiện checksum khác và validation fail. Tạo migration mới; không “chữa” bằng `repair` khi chưa hiểu trạng thái thật.

### 19.4. Nghĩ `save()` luôn chạy SQL ngay

JPA có write-behind. SQL thường xuất hiện ở flush/commit; behavior còn tùy ID strategy và query tiếp theo.

### 19.5. Sửa detached entity rồi chờ dirty checking

Detached entity không còn được persistence context theo dõi. Thực hiện thay đổi trong transaction trên managed entity hoặc merge có chủ đích.

### 19.6. Dùng `EnumType.ORDINAL`

Chèn hoặc đổi thứ tự enum làm dữ liệu cũ mang nghĩa khác. Dùng `STRING` và constraint/migration rõ ràng.

### 19.7. Cho client sort tùy ý

Field không tồn tại gây lỗi; expression nguy hiểm hoặc query đắt có thể bị lạm dụng. Dùng allowlist và giới hạn page size.

### 19.8. Viết native query cho mọi thứ

Làm code phụ thuộc dialect và mapping thủ công. Bắt đầu bằng derived query/JPQL; chỉ dùng native khi có nhu cầu PostgreSQL hoặc performance đã xác minh.

### 19.9. Dùng `CascadeType.ALL` mặc định

Cascade và relationship thuộc tuần 8. Không thêm cascade để “hết lỗi” khi chưa hiểu ownership và vòng đời dữ liệu; đặc biệt delete cascade có thể xóa ngoài ý muốn.

---

## 20. Bài thực hành bắt buộc

### Phần A — Database và migration

1. Chạy PostgreSQL local bằng Docker Compose.
2. Thêm JPA, PostgreSQL và Flyway dependency.
3. Tạo `V1__create_tasks.sql`.
4. Cấu hình Hibernate `ddl-auto=validate`.
5. Khởi động ứng dụng hai lần và kiểm tra Flyway không chạy lại V1.

### Phần B — Entity và repository

1. Chuyển `TaskEntity` từ record sang JPA entity.
2. Dùng UUID, `EnumType.STRING`, auditing timestamp.
3. Tạo `SpringDataTaskRepository`.
4. Viết derived query lọc theo status.
5. Viết JPQL search theo status và keyword.
6. Viết một native query dùng PostgreSQL `ILIKE`.

### Phần C — Pagination và projection

1. Giới hạn `size` tối đa 100.
2. Chỉ cho sort theo `createdAt`, `updatedAt`, `title`, `status`.
3. Thêm `id` làm secondary sort.
4. Tạo projection cho màn hình danh sách chỉ gồm `id`, `title`, `status`, `createdAt`.
5. So sánh SQL của entity query và projection query.

### Phần D — Thay in-memory adapter

1. Giữ `TaskRepository` interface của application.
2. Xóa hoặc chỉ giữ in-memory implementation trong test nếu còn hữu ích.
3. Tạo `JpaTaskRepositoryAdapter`.
4. Đảm bảo controller và response contract tuần 5–6 không đổi.
5. Test lại create, list, detail, update và delete API.

### Phần E — Integration test

1. Chạy PostgreSQL bằng Testcontainers.
2. Test Flyway trên database mới.
3. Test create + auditing.
4. Test filter + pagination + sort.
5. Test database constraint.

---

## 21. Câu hỏi tự kiểm tra

1. Entity khác response DTO ở điểm nào?
2. Vì sao JPA entity thường không nên là `record`?
3. Bốn trạng thái chính của entity là gì?
4. Dirty checking chỉ hoạt động khi nào?
5. `flush` khác `commit` thế nào?
6. Tại sao phải dùng object trả về từ `save()`?
7. Derived query, JPQL và native query phù hợp với trường hợp nào?
8. Vì sao `Page` thường đắt hơn `Slice`?
9. Vì sao sort cần allowlist và secondary key?
10. DTO projection giúp gì và không giải quyết được gì?
11. Vì sao không sửa migration đã chạy?
12. `ddl-auto=validate` và Flyway phối hợp thế nào?
13. Tại sao test native PostgreSQL query bằng H2 có thể tạo false confidence?
14. Khi nào cần `AuditorAware`?

---

## 22. Tiêu chí hoàn thành tuần 7

- [ ] Task API lưu dữ liệu trong PostgreSQL, không còn phụ thuộc `Map`.
- [ ] Schema được tạo bằng Flyway versioned migration.
- [ ] Production không dùng `ddl-auto=update`.
- [ ] Entity có UUID, enum string, `createdAt` và `updatedAt`.
- [ ] Giải thích được transient, managed, detached và removed.
- [ ] Giải thích được dirty checking, flush và commit.
- [ ] Có derived query, JPQL và một native query có lý do rõ ràng.
- [ ] Pagination giới hạn kích thước và sort theo allowlist ổn định.
- [ ] Có ít nhất một DTO projection.
- [ ] Controller không trả JPA entity.
- [ ] Repository integration test chạy trên PostgreSQL bằng Testcontainers.
- [ ] Không có secret production trong source code hoặc Git.

---

## 23. Gợi ý lịch học 7 ngày

| Ngày | Nội dung | Kết quả |
|---|---|---|
| 1 | PostgreSQL, dependency, datasource | App kết nối database local |
| 2 | Flyway và V1 migration | Schema có history rõ ràng |
| 3 | Entity mapping và auditing | Lưu được Task có UUID/timestamp |
| 4 | Lifecycle, persistence context, dirty checking | Giải thích được SQL sinh khi nào |
| 5 | Derived query, JPQL, native query | Search/filter hoạt động |
| 6 | Page, Sort, projection, adapter | API list ổn định và tách boundary |
| 7 | Testcontainers, sửa lỗi, ôn tập | Integration tests chạy qua |

Kết thúc tuần 7, Task API đã có persistence layer thật. Tuần 8 sẽ mở rộng sang relationship, transaction boundary, lazy loading, optimistic locking và N+1 query.
