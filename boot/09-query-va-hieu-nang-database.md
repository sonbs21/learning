# Tuần 9 — Query và hiệu năng database

> Stack: Java 21, Spring Boot 4.x, Spring Data JPA, Hibernate, PostgreSQL và Flyway. Bài này tiếp nối relationship, transaction và optimistic locking ở tuần 8.

## 1. Mục tiêu bài học

Sau tuần này, bạn có thể:

- Tạo dynamic query bằng Specification mà không nối chuỗi SQL từ input.
- Chọn giữa derived query, JPQL, native SQL và DTO projection.
- Đo query bằng EXPLAIN (ANALYZE, BUFFERS), query counter và metrics.
- Nhận diện N+1, count query đắt và fetch plan không phù hợp.
- Thiết kế index đơn/composite dựa trên workload thực tế.
- Dùng offset pagination và cursor/keyset pagination.
- Cấu hình HikariCP, query timeout và read-only transaction.
- Batch insert/update mà không làm persistence context phình lớn.
- Seed 100.000 Task và so sánh trước/sau khi thêm index.

---

## 2. Bài toán tìm kiếm Task

Endpoint cần tìm theo các filter tùy chọn:

Ví dụ dùng assigneeId theo bài tập roadmap. Nếu entity tuần 7 của bạn vẫn chỉ có assigneeEmail, hãy đổi filter/predicate/index sang assigneeEmail; đừng gọi root.get("assignee") khi entity không có field đó.

~~~text
GET /api/tasks/search
    ?keyword=payment
    &status=IN_PROGRESS
    &assigneeId=...
    &from=2026-01-01T00:00:00Z
    &to=2026-09-30T23:59:59Z
    &page=0&size=20&sort=createdAt,desc
~~~

Luồng nên là:

~~~text
request DTO → validated filter → Specification/query object
             → parameter binding → SQL + query plan
~~~

Hiệu năng cần được nhìn ở ba tầng:

| Tầng | Câu hỏi |
|---|---|
| Database | Query scan bao nhiêu row? Có dùng index không? |
| Connection pool | Có phải chờ connection không? Pool có cạn không? |
| Application | Có hydrate quá nhiều entity, query N+1 hoặc serialize thừa không? |

Không bắt đầu bằng việc tăng pool hoặc thêm index. Trước tiên cần biết query nào chậm và chậm ở tầng nào.

---

## 3. Chọn công cụ query

| Công cụ | Khi dùng | Cảnh báo |
|---|---|---|
| Derived method | Điều kiện đơn giản | Tên dài khó bảo trì |
| JPQL @Query | Query cố định, cần join/projection | Dùng tên entity/field Java |
| Native SQL | Cần PostgreSQL feature | Phụ thuộc dialect |
| Specification | Nhiều filter tùy chọn | Cần giữ predicate có cấu trúc |
| DTO projection | List chỉ cần vài cột | Không tự đảm bảo query nhanh |
| Query builder/Querydsl | Query động phức tạp | Thêm dependency và quy ước |

Một endpoint có thể kết hợp Specification để lọc, projection để chọn cột và Pageable để phân trang, nhưng cần chọn đúng kỹ thuật. `findAll(spec, pageable).map(...)` chỉ map entity sau khi query đã chạy; nếu muốn database chỉ select vài cột, dùng projection qua fluent query của `JpaSpecificationExecutor`, `@Query` constructor projection, hoặc custom repository.

---

## 4. Filter DTO và giới hạn đầu vào

~~~java
public record TaskSearchFilter(
        String keyword,
        TaskStatus status,
        UUID assigneeId,
        Instant from,
        Instant to,
        int page,
        int size,
        String sort,
        String direction
) {
    public TaskSearchFilter {
        keyword = keyword == null ? null : keyword.strip();
        if (keyword != null && keyword.isBlank()) {
            keyword = null;
        }
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("size must be between 1 and 100");
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
    }
}
~~~

Validation ở controller vẫn cần Bean Validation. Constructor filter/service bảo vệ caller khác như batch job hoặc message consumer.

Không đưa sort client trực tiếp vào persistence query. Map API field vào allowlist:

~~~java
private static final Map<String, String> SORT_FIELDS = Map.of(
        "createdAt", "createdAt",
        "updatedAt", "updatedAt",
        "title", "title",
        "status", "status"
);

private static Sort toSort(String field, String direction) {
    String property = Optional.ofNullable(SORT_FIELDS.get(field))
            .orElseThrow(() -> new InvalidSortException(field));

    Sort.Direction order = switch (direction == null ? "desc" : direction.toLowerCase(Locale.ROOT)) {
        case "asc" -> Sort.Direction.ASC;
        case "desc" -> Sort.Direction.DESC;
        default -> throw new InvalidSortException(direction);
    };

    return Sort.by(order, property)
            .and(Sort.by(Sort.Direction.DESC, "id"));
}
~~~

Secondary sort id làm thứ tự ổn định khi nhiều row có cùng createdAt.

---

## 5. Dynamic query với Specification

### 5.1. Repository

~~~java
public interface TaskRepository extends
        JpaRepository<TaskEntity, UUID>,
        JpaSpecificationExecutor<TaskEntity> {
}
~~~

Import JpaSpecificationExecutor từ org.springframework.data.jpa.repository.

### 5.2. Predicate nhỏ, ghép được

~~~java
public final class TaskSpecifications {

    private TaskSpecifications() {
    }

    public static Specification<TaskEntity> hasStatus(TaskStatus status) {
        return status == null
                ? null
                : (root, query, cb) ->
                        cb.equal(root.get("status"), status);
    }

    public static Specification<TaskEntity> hasAssignee(UUID assigneeId) {
        return assigneeId == null
                ? null
                : (root, query, cb) ->
                        cb.equal(root.get("assigneeId"), assigneeId);
    }

    public static Specification<TaskEntity> createdFrom(Instant from) {
        return from == null
                ? null
                : (root, query, cb) ->
                        cb.greaterThanOrEqualTo(root.get("createdAt"), from);
    }

    public static Specification<TaskEntity> createdUntil(Instant to) {
        return to == null
                ? null
                : (root, query, cb) ->
                        cb.lessThanOrEqualTo(root.get("createdAt"), to);
    }

    public static Specification<TaskEntity> titleContains(String keyword) {
        return keyword == null || keyword.isBlank()
                ? null
                : (root, query, cb) -> cb.like(
                        cb.lower(root.get("title")),
                        "%" + keyword.toLowerCase(Locale.ROOT) + "%"
                );
    }
}
~~~

Ghép Specification tùy version Spring Data:

~~~java
Specification<TaskEntity> specification = Specification.allOf(
        TaskSpecifications.hasStatus(filter.status()),
        TaskSpecifications.hasAssignee(filter.assigneeId()),
        TaskSpecifications.createdFrom(filter.from()),
        TaskSpecifications.createdUntil(filter.to()),
        TaskSpecifications.titleContains(filter.keyword())
);

Pageable pageable = PageRequest.of(
        filter.page(),
        filter.size(),
        toSort(filter.sort(), filter.direction())
);

Page<TaskEntity> page = taskRepository.findAll(specification, pageable);
~~~

Nếu BOM đang dùng chưa có Specification.allOf, dùng Specification.where(null) rồi and từng predicate. Chọn một style theo version, không trộn snippet của nhiều version.

Nếu muốn projection ngay ở query khi dùng Specification, dùng fluent query cho projection đơn giản:

~~~java
Page<TaskSummaryView> page = taskRepository.findBy(
        specification,
        query -> query.as(TaskSummaryView.class).page(pageable)
);
~~~

Projection fluent phù hợp nhất với field top-level của `TaskEntity`. Nếu DTO cần field từ join như `projectId` hoặc aggregate phức tạp, viết `@Query` constructor projection hoặc custom repository để kiểm soát select/count query rõ ràng.

### 5.3. Join filter khác fetch join

~~~java
public static Specification<TaskEntity> hasProjectName(String name) {
    return (root, query, cb) -> {
        Join<TaskEntity, ProjectEntity> project = root.join("project");
        return cb.equal(project.get("name"), name);
    };
}
~~~

Predicate join để lọc không đồng nghĩa fetch join. Nếu cần trả Project, dùng projection hoặc entity graph riêng. Đừng thêm fetch join vô điều kiện vào Specification có Page: count query có thể lỗi hoặc plan xấu.

### 5.4. Search text

LIKE '%keyword%' thường không dùng B-tree index. Với PostgreSQL và workload lớn, có thể dùng trigram:

~~~sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_tasks_title_trgm
    ON tasks USING gin (title gin_trgm_ops);
~~~

Chỉ thêm extension/index sau khi đo. Giới hạn keyword và page size để tránh query quá rộng.

---

## 6. DTO projection

List API thường không cần toàn bộ entity và relationship:

~~~java
public record TaskListItem(
        UUID id,
        String title,
        TaskStatus status,
        UUID projectId,
        Instant createdAt
) {
}
~~~

JPQL constructor projection:

~~~java
@Query("""
        select new com.example.taskapi.task.TaskListItem(
            t.id, t.title, t.status, p.id, t.createdAt
        )
        from TaskEntity t
        left join t.project p
        where (:status is null or t.status = :status)
        """)
Page<TaskListItem> findList(
        @Param("status") TaskStatus status,
        Pageable pageable
);
~~~

Projection giảm hydration và serialization nhưng vẫn cần index và predicate tốt. Tránh trả Object[] rồi cast thủ công. Với Page, kiểm tra cả data query và count query; count trên join có thể cần count(distinct t.id).

Interface projection phù hợp list đơn giản:

~~~java
public interface TaskSummaryView {
    UUID getId();
    String getTitle();
    TaskStatus getStatus();
    Instant getCreatedAt();
}
~~~

---

## 7. Đo query trước khi tối ưu

### 7.1. SQL log

~~~yaml
logging:
  level:
    org.hibernate.SQL: debug
    org.hibernate.orm.jdbc.bind: trace
~~~

Log giúp thấy số query, tham số và N+1. Nó không phải benchmark chính xác vì console logging làm thay đổi timing. Bind values chỉ bật local/test vì có thể lộ PII.

### 7.2. EXPLAIN

~~~sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT id, title, status, created_at
FROM tasks
WHERE status = 'IN_PROGRESS'
  AND created_at >= TIMESTAMPTZ '2026-01-01T00:00:00Z'
ORDER BY created_at DESC, id DESC
LIMIT 20;
~~~

Đọc các điểm chính:

- Seq Scan: đọc toàn bảng; chưa chắc sai nếu bảng nhỏ hoặc trả phần lớn row.
- Index Scan/Bitmap Index Scan: có dùng index, nhưng vẫn xem số row thực tế.
- actual time, rows, loops: chi phí thật của từng node.
- Buffers: đọc cache hay disk.
- Estimate lệch xa actual: statistics cũ hoặc predicate khó ước lượng.

EXPLAIN ANALYZE thực thi query. Với UPDATE/DELETE, chỉ chạy trên dữ liệu test hoặc transaction rollback có chủ đích.

### 7.3. Application metrics

Theo dõi p50/p95/p99 latency, database execution time, connection acquire time, số query/request, rows đọc/trả, Hikari active/idle/pending và timeout rate.

Query database 20 ms nhưng request chậm 2 giây vì chờ connection hoặc N+1 vẫn là vấn đề.

---

## 8. Index đơn và composite index

Index tăng tốc read nhưng làm INSERT/UPDATE, vacuum và disk tốn hơn. Không tạo index cho mọi column.

~~~sql
CREATE INDEX idx_tasks_assignee_id ON tasks(assignee_id);
CREATE INDEX idx_tasks_status ON tasks(status);
~~~

Với workload:

~~~sql
WHERE status = ?
  AND assignee_id = ?
ORDER BY created_at DESC, id DESC
~~~

có thể thử:

~~~sql
CREATE INDEX idx_tasks_status_assignee_created_id
    ON tasks(status, assignee_id, created_at DESC, id DESC);
~~~

Thứ tự column dựa trên predicate, cardinality, sort và workload. Hiểu leftmost prefix: index (status, assignee_id, created_at) không tự nhiên tối ưu cho query chỉ lọc assignee_id.

Không tạo một index khổng lồ cho mọi filter optional. Đo query phổ biến trước.

Các trường hợp cần chú ý:

- %keyword% cần trigram/full-text strategy.
- lower(title) có thể cần functional index.
- Cast/expression có thể khiến index không được dùng.
- Query trả phần lớn bảng có thể hợp lý với sequential scan.

Tạo index bằng Flyway migration, không tạo lén khi application startup.

---

## 9. Offset và cursor pagination

### 9.1. Offset

~~~sql
SELECT id, title, created_at
FROM tasks
WHERE status = 'TODO'
ORDER BY created_at DESC, id DESC
LIMIT 20 OFFSET 10000;
~~~

Offset dễ làm API và hỗ trợ page number/tổng trang, nhưng database phải đi qua row bị bỏ qua. Dữ liệu chèn/xóa giữa hai request có thể làm row trùng hoặc bị bỏ.

### 9.2. Cursor/keyset

Cursor chứa vị trí cuối trang (createdAt, id):

~~~sql
SELECT id, title, created_at
FROM tasks
WHERE status = :status
  AND (
      created_at < :lastCreatedAt
      OR (created_at = :lastCreatedAt AND id < :lastId)
  )
ORDER BY created_at DESC, id DESC
LIMIT :limit;
~~~

Index:

~~~sql
CREATE INDEX idx_tasks_status_created_id
    ON tasks(status, created_at DESC, id DESC);
~~~

Cursor ổn định hơn với bảng lớn và dữ liệu thay đổi, nhưng không nhảy trực tiếp tới page 5000. Cursor nên là opaque token chứa filter/sort/version và được ký hoặc mã hóa nếu không muốn client sửa giá trị. Validate cursor cùng filter hiện tại.

Ví dụ trên dùng SQL PostgreSQL. Nếu `id` là UUID ngẫu nhiên, nó vẫn có thể làm tie-breaker ổn định trong PostgreSQL, nhưng thứ tự này phụ thuộc database/dialect. Với JPQL portable hoặc API cần cursor dễ dự đoán hơn, cân nhắc dùng khóa tăng dần, ULID/time-ordered UUID, hoặc custom native query được test rõ ràng.

~~~json
{
  "items": [],
  "nextCursor": "opaque-token"
}
~~~

---

## 10. HikariCP

Spring Boot dùng HikariCP khi dependency có mặt. Bắt đầu với cấu hình nhỏ rồi đo:

~~~yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 2000
      validation-timeout: 1000
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: task-api-pool
~~~

- maximum-pool-size: connection tối đa.
- connection-timeout: chờ lấy connection bao lâu.
- max-lifetime: tuổi tối đa, nên ngắn hơn giới hạn database/proxy.
- idle-timeout: thời gian connection idle có thể bị thu hồi.

Pool lớn không đồng nghĩa nhanh hơn. Tổng pool của tất cả instance phải phù hợp giới hạn PostgreSQL. Pool quá lớn gây contention và làm DB quá tải.

Phân biệt:

~~~text
pool connection-timeout → chờ lấy connection
query timeout           → query đã chạy quá lâu
transaction timeout     → use case giữ transaction quá lâu
~~~

Leak detection chỉ bật tạm khi điều tra. Không đặt pool size theo công thức cứng; đo CPU, DB limit, query concurrency và pending connections.

---

## 11. Batch insert/update

### 11.1. Hibernate JDBC batching

~~~yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50
        order_inserts: true
        order_updates: true
~~~

Batch size 50 chỉ là điểm bắt đầu. ID strategy phụ thuộc IDENTITY có thể hạn chế insert batching; UUID/sequence thường thuận lợi hơn.

### 11.2. Flush/clear theo chunk

~~~java
@Transactional
public void importTasks(List<CreateTaskCommand> commands) {
    for (int i = 0; i < commands.size(); i++) {
        entityManager.persist(TaskEntity.createFrom(commands.get(i)));

        if ((i + 1) % 50 == 0) {
            entityManager.flush();
            entityManager.clear();
        }
    }
}
~~~

Không clear sẽ giữ hàng trăm nghìn entity và làm dirty checking tốn memory. Sau clear, entity cũ detached; không dùng tiếp reference nếu chưa reload.

### 11.3. Bulk update

~~~java
@Modifying(clearAutomatically = true, flushAutomatically = true)
@Query("""
        update TaskEntity t
        set t.status = :newStatus
        where t.project.id = :projectId
          and t.status = :oldStatus
        """)
int moveTasks(
        @Param("projectId") UUID projectId,
        @Param("oldStatus") TaskStatus oldStatus,
        @Param("newStatus") TaskStatus newStatus
);
~~~

Method chứa modifying query phải chạy trong transaction. Đặt @Transactional ở service use case, nơi cũng kiểm tra authorization và business invariant, thay vì mở transaction trong controller.

Bulk JPQL bypass entity callback, domain method, managed state và có thể không cập nhật Version giống flow entity thông thường. Flush/clear đúng thời điểm và cập nhật version/audit có chủ đích.

Khi import dữ liệu phẳng không cần entity lifecycle, JdbcTemplate.batchUpdate hoặc PostgreSQL COPY thường hiệu quả hơn JPA. Đổi lại phải tự đảm bảo validation, mapping và transaction.

---

## 12. Query timeout

~~~java
@QueryHints(
        value = @QueryHint(
                name = "jakarta.persistence.query.timeout",
                value = "1000"
        ),
        forCounting = false
)
@Query("""
        select t
        from TaskEntity t
        where t.status = :status
        """)
List<TaskEntity> findActiveFast(
        @Param("status") TaskStatus status,
        Pageable pageable
);
~~~

Giá trị chuẩn JPA thường tính bằng milliseconds, nhưng provider/JDBC/database có thể hỗ trợ khác nhau. Test timeout trên PostgreSQL thật.

Hikari connection-timeout không thay query timeout. Query đã lấy connection nhưng chạy lâu vẫn cần timeout riêng. Khi timeout, rollback transaction, log query identity/trace ID an toàn và map thành response lỗi phù hợp.

---

## 13. Read-only transaction

~~~java
@Transactional(readOnly = true)
public Page<TaskSummaryView> search(TaskSearchFilter filter) {
    Specification<TaskEntity> specification = buildSpecification(filter);

    return taskRepository.findBy(
            specification,
            query -> query.as(TaskSummaryView.class).page(toPageable(filter))
    );
}
~~~

Nếu DTO cần join field hoặc constructor phức tạp, thay phần repository bằng `@Query` projection/custom repository như mục 6. Đừng nhầm `Page<Entity>.map(DTO::from)` với database projection: cách đó vẫn tải entity trước rồi mới map trong Java.

readOnly là hint cho transaction/provider và có thể giảm một phần dirty checking. Nó không phải security boundary và không bảo đảm tuyệt đối rằng SQL write không thể xảy ra.

Không sửa managed entity trong read-only method rồi kỳ vọng update được lưu. Tách use case đọc và ghi. Với projection, readOnly vẫn thể hiện intent và transaction boundary.

---

## 14. N+1 và fetch plan

Một list 20 Task rồi gọi task.getProject().getName() có thể tạo 1 query Task + 20 query Project.

Quy trình kiểm tra:

1. flush và clear trước use case.
2. Reset Hibernate statistics/query counter.
3. Gọi service như endpoint thật.
4. Map sang DTO để chạm đúng field cần trả.
5. Assert query count và latency.

Lựa chọn:

- DTO projection cho list.
- EntityGraph(attributePaths = "project") cho to-one cần tải.
- JPQL left join fetch cho detail query.
- Batch fetching khi nhiều lazy to-one được truy cập có chủ đích.

Collection to-many cùng pagination cần page root trước rồi tải children riêng. Entity graph không tự giải quyết duplicate row hoặc in-memory pagination.

---

## 15. Seed và benchmark 100.000 records

Không gọi repository.save từng record rồi giữ tất cả entity. Dùng JdbcTemplate.batchUpdate, PostgreSQL COPY, hoặc batch JPA theo chunk.

Baseline phải ghi cùng dataset, query và page size:

~~~text
p50 / p95 / p99 latency
rows returned / rows scanned
execution plan
buffer hits / reads
SQL count per request
connection pending time
~~~

Warm-up trước khi lấy mẫu vì database cache lạnh và JVM JIT làm lần đầu không đại diện. Sau index: tạo Flyway migration, refresh statistics khi cần, chạy cùng workload, so sánh plan/p95/p99 và kiểm tra chi phí write/dung lượng index.

Một index làm read nhanh hơn có thể làm write và vacuum đắt hơn.

---

## 16. Test query và hiệu năng

Dùng PostgreSQL Testcontainers từ tuần 7. H2 không đủ để kết luận về ILIKE, execution plan, index hoặc PostgreSQL operator.

Test tối thiểu:

- Mỗi filter Specification đúng.
- Nhiều filter ghép AND đúng.
- Null/blank filter không làm query sai.
- Sort và secondary id ổn định.
- Projection không tải association thừa.
- Bulk update trả đúng số row và clear persistence context.
- Query timeout được driver/provider xử lý.
- Query counter phát hiện N+1.

Test correctness và benchmark là hai việc khác nhau. Benchmark phải ghi database version, dataset, pool size, concurrency, warm-up, số sample và p50/p95/p99.

---

## 17. Lỗi thường gặp

- Nối chuỗi filter vào SQL: dễ tạo injection; dùng Specification/parameter binding.
- Tạo quá nhiều index: tăng write cost, vacuum cost và disk.
- Dùng Page ở mọi endpoint: Page có count query; nếu không cần tổng, dùng Slice hoặc cursor.
- Offset sâu trên bảng lớn: dùng keyset khi UX không cần nhảy page.
- Bulk update rồi đọc entity cũ: persistence context có stale state; flush/clear.
- Tăng Hikari pool để chữa query chậm: pool không sửa query plan tệ.
- Bật bind log production: có thể lộ PII/secret.
- Benchmark bằng dữ liệu quá nhỏ: seed gần workload thực tế.

---

## 18. Bài thực hành bắt buộc

1. Tạo endpoint tìm Task theo keyword, status, assigneeId, from, to.
2. Implement bằng Specification, parameter binding và sort allowlist.
3. Trả DTO projection cho list.
4. Seed ít nhất 100.000 records bằng batch/JDBC.
5. Đo baseline bằng EXPLAIN (ANALYZE, BUFFERS) và p95.
6. Thêm index composite dựa trên query workload.
7. Đo lại sau index, ghi plan trước/sau vào tài liệu project.
8. Viết endpoint cursor pagination với (createdAt, id).
9. Viết query count test để phát hiện N+1.
10. Cấu hình Hikari pool nhỏ và quan sát pending connections.
11. Thử bulk update theo Project, xác minh clear persistence context và version policy.
12. Đặt query timeout và kiểm tra error mapping.

---

## 19. Câu hỏi tự kiểm tra

1. Khi nào dùng Specification thay vì derived query?
2. Vì sao LIKE '%x%' thường không dùng B-tree index?
3. Composite index (a,b,c) phục vụ tốt những prefix nào?
4. Vì sao thêm index có thể làm write chậm?
5. Page khác Slice về count query thế nào?
6. Khi nào cursor pagination phù hợp hơn offset?
7. Vì sao cursor cần secondary key?
8. Hikari connection-timeout khác query timeout thế nào?
9. Bulk JPQL update bỏ qua lifecycle nào?
10. Vì sao phải flush/clear khi batch lớn?
11. readOnly=true có cấm mọi write không?
12. Làm sao chứng minh endpoint bị N+1?
13. Vì sao projection không tự đảm bảo query nhanh?
14. Vì sao không dùng H2 để kết luận hiệu năng PostgreSQL?
15. Cần ghi thông tin nào để benchmark lặp lại?

---

## 20. Tiêu chí hoàn thành tuần 9

- [ ] Endpoint search dùng dynamic query có parameter binding.
- [ ] Có DTO projection cho list.
- [ ] Có index được chọn từ workload và EXPLAIN.
- [ ] Hiểu leftmost prefix của composite index.
- [ ] Có offset pagination và biết giới hạn.
- [ ] Có cursor/keyset pagination với cursor opaque.
- [ ] Cấu hình HikariCP và đo được connection wait.
- [ ] Batch insert/update có chunk, flush và clear.
- [ ] Hiểu bulk update không chạy domain callback như entity update.
- [ ] Query timeout được test trên PostgreSQL.
- [ ] Search read path dùng read-only đúng giới hạn.
- [ ] Có query counter test cho N+1.
- [ ] Seed và benchmark ít nhất 100.000 records.
- [ ] Ghi plan và p95 trước/sau index.

---

## 21. Gợi ý lịch học 7 ngày

| Ngày | Nội dung | Kết quả |
|---|---|---|
| 1 | EXPLAIN và đo latency | Có baseline |
| 2 | Specification và filter DTO | Search nhiều điều kiện |
| 3 | Projection, count query, N+1 | List chỉ tải dữ liệu cần |
| 4 | Index đơn/composite | Migration index có lý do |
| 5 | Offset/cursor pagination | Chọn pagination theo workload |
| 6 | Hikari, timeout, batching | Kiểm soát pool và import |
| 7 | Seed 100k, benchmark | Báo cáo trước/sau tối ưu |

---

## 22. Tài liệu tham khảo

- Spring Data JPA Specifications: https://docs.spring.io/spring-data/jpa/reference/jpa/specifications.html
- Spring Data JPA Projections: https://docs.spring.io/spring-data/jpa/reference/repositories/projections.html
- Spring Data JPA Query Methods: https://docs.spring.io/spring-data/jpa/reference/jpa/query-methods.html
- PostgreSQL EXPLAIN: https://www.postgresql.org/docs/current/sql-explain.html
- PostgreSQL Indexes: https://www.postgresql.org/docs/current/indexes.html
- PostgreSQL pg_trgm: https://www.postgresql.org/docs/current/pgtrgm.html

Tuần 10 theo roadmap chuyển sang Spring Security: authentication, authorization, password hashing, role/permission và security filter chain.
