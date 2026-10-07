# Tuần 8 — Relationship và transaction trong Spring Data JPA

> Stack: Java 21, Spring Boot 4.x, Spring Data JPA, Hibernate, PostgreSQL và Flyway. Bài này tiếp nối `TaskEntity`/`TaskRepository` ở tuần 7.

## 1. Mục tiêu bài học

Sau tuần này, bạn có thể:

- Mapping `@ManyToOne`, `@OneToMany` và hiểu owning side.
- Chọn `LAZY`/`EAGER` có chủ đích.
- Phân biệt cascade với quan hệ database và với `orphanRemoval`.
- Thiết kế chính sách xóa `Project` có nhiều `Task` và `Task` có nhiều `Comment`.
- Đặt transaction boundary ở service/use case bằng `@Transactional`.
- Hiểu dirty checking, rollback và các giới hạn của proxy transaction.
- Phát hiện N+1 và chọn fetch join hoặc `@EntityGraph` phù hợp.
- Dùng `@Version` để phát hiện cập nhật đồng thời mất dữ liệu.
- Viết integration test kiểm tra relationship, transaction, N+1 và locking.

---

## 2. Mô hình bài tập

Trong tuần 7, Task độc lập. Bây giờ mở rộng thành:

```text
Project 1 ─────── * Task 1 ─────── * Comment
```

Foreign key nằm ở bảng phía nhiều:

```text
projects(id, name, ...)
tasks(id, project_id, title, ..., version)
task_comments(id, task_id, body, ...)
```

| Quan hệ | Foreign key | Phía owning trong JPA |
|---|---|---|
| Project — Task | `tasks.project_id` | `TaskEntity.project` |
| Task — Comment | `task_comments.task_id` | `TaskCommentEntity.task` |

Quyết định nghiệp vụ dùng trong ví dụ:

- Không cho xóa Project khi vẫn còn Task; trả conflict và yêu cầu chuyển Task sang Project khác hoặc archive Project.
- Comment thuộc Task và không có vòng đời độc lập trong ứng dụng; xóa Task thì xóa Comment.
- Không dùng `ManyToMany` trực tiếp cho thành viên Project; nếu quan hệ có role, trạng thái hoặc thời gian tham gia, tạo entity liên kết riêng.

Chính sách xóa là quy tắc nghiệp vụ. Annotation cascade không nên tự quyết định thay cho sản phẩm.

---

## 3. Migration schema

Tạo migration mới, không sửa `V1__create_tasks.sql` đã dùng ở tuần 7:

```text
src/main/resources/db/migration/V2__add_projects_and_task_comments.sql
```

Ví dụ SQL:

```sql
CREATE TABLE projects (
    id          UUID PRIMARY KEY,
    name        VARCHAR(160) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0
);

ALTER TABLE tasks
    ADD COLUMN project_id UUID,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE tasks
    ADD CONSTRAINT fk_tasks_project
    FOREIGN KEY (project_id) REFERENCES projects(id)
    ON DELETE RESTRICT;

CREATE INDEX idx_tasks_project_id ON tasks(project_id);

CREATE TABLE task_comments (
    id          UUID PRIMARY KEY,
    task_id     UUID NOT NULL,
    body        VARCHAR(4000) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_task_comments_task
        FOREIGN KEY (task_id) REFERENCES tasks(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_task_comments_task_id_created_at
    ON task_comments(task_id, created_at, id);
```

`project_id` ở ví dụ ban đầu nullable để dữ liệu Task hiện có không làm migration thất bại. Nếu mọi Task bắt buộc phải có Project, hãy backfill Project cho row cũ rồi thêm `NOT NULL` bằng migration sau. Với bảng lớn, chia migration theo chiến lược expand/backfill/contract thay vì thêm constraint đột ngột.

Foreign key ở database là lớp đảm bảo referential integrity cuối cùng. JPA mapping không thay thế constraint database.

---

## 4. `@ManyToOne` và owning side

`TaskEntity` giữ foreign key `project_id`, vì vậy đây là owning side. Thêm các field/method dưới đây vào `TaskEntity` từ tuần 7:

```java
import com.example.taskapi.project.ProjectEntity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.util.Objects;

@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "project_id")
private ProjectEntity project;

@Version
private long version;

public void assignTo(ProjectEntity project) {
    this.project = Objects.requireNonNull(project, "project must not be null");
}

public void removeFromProject() {
    this.project = null;
}

public ProjectEntity getProject() { return project; }
public long getVersion() { return version; }
```

Đây là phần field/method ghép vào class, không phải file Java độc lập. `@JoinColumn(name = "project_id")` đặt tên cột FK. Mặc định `@ManyToOne` là eager theo Jakarta Persistence, nên ví dụ chủ động chọn `LAZY` để Project chỉ được tải khi cần.

### 4.1. Owning side có ý nghĩa gì?

Owning side là phía mapping điều khiển giá trị foreign key. Khi gán Project cho Task, phải cập nhật `task.project`; chỉ thêm Task vào collection phía Project mà không đổi owning side thì FK có thể không đổi.

### 4.2. Giữ hai phía trong bộ nhớ đồng bộ

Nếu có collection ngược trên Project, helper method có thể cập nhật cả hai phía:

```java
public void assignTo(ProjectEntity newProject) {
    Objects.requireNonNull(newProject, "project must not be null");

    if (this.project == newProject) {
        return;
    }

    if (this.project != null) {
        this.project.removeTaskReference(this);
    }

    this.project = newProject;
    newProject.addTaskReference(this);
}
```

Collection helper trên Project chỉ quản lý object graph trong memory; owning field `TaskEntity.project` vẫn là thứ ghi FK. Hai helper này chỉ được gọi từ `TaskEntity.assignTo(...)`; application code gọi method đó, không gọi trực tiếp helper trên Project.

---

## 5. `@OneToMany` inverse side

```java
package com.example.taskapi.project;

import com.example.taskapi.task.TaskEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "projects")
@EntityListeners(AuditingEntityListener.class)
public class ProjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "project", fetch = FetchType.LAZY)
    private Set<TaskEntity> tasks = new LinkedHashSet<>();

    protected ProjectEntity() {
    }

    private ProjectEntity(String name) {
        this.name = requireName(name);
    }

    public static ProjectEntity create(String name) {
        return new ProjectEntity(name);
    }

    public void rename(String name) {
        this.name = requireName(name);
    }

    public void addTaskReference(TaskEntity task) {
        tasks.add(Objects.requireNonNull(task));
    }

    public void removeTaskReference(TaskEntity task) {
        tasks.remove(task);
    }

    public Set<TaskEntity> getTasks() {
        return Collections.unmodifiableSet(tasks);
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Long getVersion() { return version; }

    private static String requireName(String value) {
        String name = Objects.requireNonNull(value, "name must not be null").strip();
        if (name.isEmpty() || name.length() > 160) {
            throw new IllegalArgumentException("name must contain 1 to 160 characters");
        }
        return name;
    }
}
```

Trong ví dụ, `TaskEntity.project` là owning side; `ProjectEntity.tasks` là inverse side nhờ `mappedBy = "project"`. `mappedBy` nhận tên Java field, không phải tên cột `project_id`.

Một mapping chuẩn chỉ đặt `@JoinColumn` ở owning side. Nếu khai báo mapping độc lập ở cả hai phía, JPA có thể tạo join table hoặc mapping không như mong muốn.

Collection `Project.tasks` không bắt buộc. Nếu use case chỉ cần biết Project của một Task, chỉ giữ `TaskEntity.project` giúp model gọn và tránh collection lớn. Thêm inverse collection khi use case cần thao tác object graph hai chiều.

Project dùng UUID do JPA sinh. Nếu ứng dụng tự gán ID, hãy cẩn thận: Spring Data cần xác định entity mới để chọn `persist` hay `merge`; có thể dùng nullable `@Version` hoặc `Persistable` với `isNew()` thay vì giả định rằng UUID khác `null` luôn đồng nghĩa row đã tồn tại.

---

## 6. Cascade, `orphanRemoval` và chính sách xóa

| Cơ chế | Ý nghĩa |
|---|---|
| `CascadeType.PERSIST` | Persist cha thì propagate persist đến con |
| `CascadeType.REMOVE` | Remove cha thì propagate remove đến con |
| `orphanRemoval = true` | Bỏ child khỏi relationship thì child bị xóa khi flush |
| Foreign key `ON DELETE ...` | Quy tắc do database áp dụng khi xóa row |

Đừng bật `CascadeType.ALL` theo thói quen. Nó gồm cả remove và có thể khiến thao tác xóa lan qua object graph ngoài dự định.

### 6.1. Chính sách Project trong bài

Project không cascade delete xuống Task. FK dùng `ON DELETE RESTRICT`, và service kiểm tra trước để trả business error dễ hiểu:

```java
public interface TaskRepository {
    boolean existsByProjectId(UUID projectId);
}
```

```java
@Transactional
public void deleteProject(UUID projectId) {
    ProjectEntity project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ProjectNotFoundException(projectId));

    if (taskRepository.existsByProjectId(projectId)) {
        throw new ProjectHasTasksException(projectId);
    }

    projectRepository.delete(project);
}
```

Kiểm tra service giúp trả lỗi rõ ràng; FK vẫn bảo vệ nếu có race hoặc code khác bỏ qua service. Nếu hai request đồng thời cùng xóa/tạo Task, cần constraint và concurrency policy để giữ invariant.

### 6.2. Chính sách Comment thuộc Task

Comment không sống độc lập nên mapping có thể dùng `cascade = CascadeType.ALL, orphanRemoval = true` ở collection comments. Điều này có nghĩa bỏ Comment khỏi collection của Task có thể xóa row.

Thêm vào `TaskEntity`:

```java
@OneToMany(mappedBy = "task", fetch = FetchType.LAZY,
        cascade = CascadeType.ALL, orphanRemoval = true)
private List<TaskCommentEntity> comments = new ArrayList<>();

public void addComment(TaskCommentEntity comment) {
    comments.add(Objects.requireNonNull(comment));
    comment.attachTo(this);
}

public void removeComment(TaskCommentEntity comment) {
    if (comments.remove(comment)) {
        comment.detachFromTask();
    }
}
```

`TaskCommentEntity.task` là owning side, nên `addComment` phải gán field đó. Khi xóa orphan, helper đồng bộ collection và field của Comment; JPA lên lịch xóa orphan. Không dùng object Comment đó tiếp sau khi đã bỏ khỏi Task. Nếu Comment không được phép xóa tùy tiện, bỏ method này và chỉ xóa qua use case rõ ràng.

```java
import jakarta.persistence.EntityListeners;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "task_comments")
@EntityListeners(AuditingEntityListener.class)
public class TaskCommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private TaskEntity task;

    @Column(nullable = false, length = 4_000)
    private String body;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private Long version;

    protected TaskCommentEntity() {
    }

    public TaskCommentEntity(String body) {
        this.body = requireBody(body);
    }

    void attachTo(TaskEntity task) {
        this.task = Objects.requireNonNull(task);
    }

    void detachFromTask() {
        this.task = null;
    }

    private static String requireBody(String value) {
        String body = Objects.requireNonNull(value, "body must not be null").strip();
        if (body.isEmpty() || body.length() > 4_000) {
            throw new IllegalArgumentException("body must contain 1 to 4000 characters");
        }
        return body;
    }
}
```

Đây là trích đoạn entity. Thêm các import JPA (`Entity`, `Table`, `Id`, `GeneratedValue`, `GenerationType`, `ManyToOne`, `JoinColumn`, `FetchType`, `Column`, `Version`, `EntityListeners`), auditing (`CreatedDate`, `LastModifiedDate`, `AuditingEntityListener`), `Instant`, `Objects`, `UUID`, cùng `ArrayList`, `List`, `CascadeType`, `OneToMany` vào các class tương ứng. Auditing configuration `@EnableJpaAuditing` được tạo ở tuần 7.

Chỉ chọn chính sách này khi đúng nghiệp vụ. Nếu cần giữ lịch sử hoặc audit, dùng archive/soft delete thay vì xóa cứng.

---

## 7. `ManyToMany` và entity liên kết

`@ManyToMany` tạo join table nhưng thường che giấu entity nghiệp vụ. Membership có role và ngày tham gia nên được model thành:

```text
project_memberships(
  project_id,
  user_id,
  role,
  joined_at,
  version
)
```

Khi join table có thuộc tính riêng, tạo `ProjectMembershipEntity` với hai `@ManyToOne` và unique constraint `(project_id, user_id)`. Entity liên kết giúp đặt validation, audit và lifecycle rõ ràng.

---

## 8. Fetch type: `LAZY` và `EAGER`

- `LAZY`: association chỉ được tải khi truy cập, thường phù hợp mặc định cho relationship.
- `EAGER`: association phải có sẵn khi entity được trả về; provider có thể dùng join hoặc query bổ sung.

`EAGER` không đồng nghĩa luôn là một SQL JOIN. Với nhiều association eager, provider có thể chạy thêm query và vẫn tạo N+1.

Gợi ý ban đầu:

```java
@ManyToOne(fetch = FetchType.LAZY)
private ProjectEntity project;

@OneToMany(mappedBy = "project", fetch = FetchType.LAZY)
private Set<TaskEntity> tasks = new LinkedHashSet<>();
```

Chọn dữ liệu theo use case query, thay vì cấu hình global eager để chữa lỗi lazy loading. Nếu entity bị serialize trực tiếp, serializer có thể chạm lazy proxy sau transaction, gây exception hoặc truy vấn không mong muốn. Response DTO giúp API chỉ lấy dữ liệu đã chọn.

---

## 9. Transaction boundary với `@Transactional`

Đặt transaction quanh một use case hoàn chỉnh ở application/service layer:

```java
@Service
public class DefaultTaskService implements TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;

    public DefaultTaskService(
            TaskRepository taskRepository,
            ProjectRepository projectRepository
    ) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
    }

    @Transactional
    public TaskEntity createTask(UUID projectId, CreateTaskCommand command) {
        ProjectEntity project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));

        TaskEntity task = TaskEntity.create(
                command.title(),
                command.description(),
                command.assigneeEmail()
        );
        task.assignTo(project);

        return taskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public TaskDetailResponse getTask(UUID taskId) {
        TaskEntity task = taskRepository.findById(taskId)
                .orElseThrow(() -> new TaskNotFoundException(taskId));

        return TaskDetailResponse.from(task);
    }
}
```

Map sang DTO trong transaction nếu việc mapping cần chạm lazy relationship. Tốt hơn nữa, query đúng projection/fetch plan cho response.

### 9.1. Spring transaction thường dùng proxy

Các lưu ý:

- `@Transactional` thường được áp dụng qua Spring proxy; gọi method transactional từ method khác trong cùng instance (self-invocation) không đi qua proxy nên annotation bên trong có thể không được áp dụng.
- Method public trên Spring bean là lựa chọn dễ hiểu cho bài học; hỗ trợ method khác còn tùy proxy mode/framework.
- Không gọi `new DefaultTaskService(...)` nếu muốn Spring transaction hoạt động.
- Không giữ transaction mở khi chờ HTTP call, người dùng nhập liệu hoặc xử lý lâu.
- Transaction manager mặc định với JPA là transaction database; nó không tự làm atomic một lời gọi email, Kafka hoặc service bên ngoài.

### 9.2. Rollback

Mặc định Spring rollback với `RuntimeException` và `Error`; checked exception không tự rollback theo mặc định. Nếu use case ném checked exception và cần rollback, khai báo policy rõ, ví dụ `@Transactional(rollbackFor = SomeCheckedException.class)`.

Không catch exception rồi nuốt trong transaction. Nếu đã bắt để bổ sung context, thường cần ném lại runtime exception để transaction interceptor nhận biết lỗi.

### 9.3. `readOnly = true`

Đây là hint tối ưu cho transaction đọc, không phải cơ chế authorization hay bảo đảm tuyệt đối rằng database không thể ghi. Không thay `@Transactional` ở use case có read-modify-write bằng `readOnly = true`.

---

## 10. Dirty checking và cập nhật entity

Entity được load trong transaction là managed. Thay đổi field qua method domain sẽ được dirty checking phát hiện:

```java
@Transactional
public void renameProject(UUID projectId, String newName) {
    ProjectEntity project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ProjectNotFoundException(projectId));

    project.rename(newName);
    // Không bắt buộc gọi save(project); Hibernate flush UPDATE khi cần.
}
```

Đây không phải lý do để bỏ transaction. Ngoài transaction entity có thể detached; thay đổi sẽ không tự persist. `save()` vẫn hữu ích cho insert hoặc khi dùng repository abstraction, nhưng với managed entity update trong transaction, dirty checking làm phần lớn công việc.

SQL có thể gửi ở flush/commit. Constraint violation vì vậy có thể xảy ra cuối method hoặc tại commit, không nhất thiết đúng dòng gọi `save()`.

---

## 11. N+1 query

Giả sử lấy 20 Project rồi đọc `project.getTasks()` cho từng Project. SQL có thể là:

```text
1 query lấy danh sách project
20 query lấy tasks của từng project
------------------------------
21 query tổng cộng
```

Đây là N+1. Dataset nhỏ vẫn hoạt động nên lỗi dễ lọt qua unit test.

### 11.1. Đo trước khi sửa

Bật SQL logging trong local/test:

```yaml
logging:
  level:
    org.hibernate.SQL: debug
    org.hibernate.orm.jdbc.bind: trace
```

Bind parameter log hữu ích khi debug nhưng có thể lộ PII; không bật rộng trong production. Với test, dùng Hibernate statistics hoặc datasource proxy để assert số query cho use case trọng yếu.

### 11.2. Fetch join

```java
public interface SpringDataTaskRepository extends JpaRepository<TaskEntity, UUID> {

    @Query("""
            select t
            from TaskEntity t
            left join fetch t.project
            where t.id = :id
            """)
    Optional<TaskEntity> findTaskWithProjectById(@Param("id") UUID id);
}
```

Fetch join lấy Task và Project trong một query cho use case cần cả hai. Dùng `join fetch` cho to-one thường an toàn hơn collection to-many.

#### Fetch collection và pagination

Không tùy tiện kết hợp collection `join fetch` với `Pageable`: kết quả SQL bị nhân dòng theo số child, phân trang có thể sai hoặc Hibernate phải phân trang trong memory. Với danh sách lớn:

1. Phân trang ID/root trước.
2. Query thêm associations cho các ID trong trang, hoặc dùng batch fetching/entity graph phù hợp.
3. Đảm bảo thứ tự của page được giữ lại khi map kết quả.

Không bật cấu hình cho phép pagination in-memory như cách chữa mặc định; nó có thể tải quá nhiều dữ liệu.

`@EntityGraph` cũng không phải cách chữa pagination cho to-many collection: Hibernate vẫn có thể gặp duplicate rows hoặc pagination in-memory. Với collection, phân trang root trước rồi tải children riêng, hoặc dùng batch fetching/projection.

### 11.3. `@EntityGraph`

```java
import org.springframework.data.jpa.repository.EntityGraph;

public interface SpringDataTaskRepository extends JpaRepository<TaskEntity, UUID> {

    @EntityGraph(attributePaths = "project")
    Optional<TaskEntity> findTaskWithProjectById(UUID id);
}
```

Entity graph khai báo association cần load cho repository method mà không hard-code JPQL fetch join. Dùng `attributePaths` cho graph nhỏ, hoặc named entity graph khi tái sử dụng. Kiểm tra SQL sinh ra để xác nhận provider thực sự tạo fetch plan phù hợp.

### 11.4. Batch fetching

`@BatchSize` hoặc cấu hình batch fetch có thể gom nhiều lazy loads thành `IN (...)` queries. Đây là tối ưu hữu ích cho một số screen, nhưng không thay thế việc chọn fetch plan theo use case.

---

## 12. `@Version` và optimistic locking

Thêm field version vào entity và cột `version` vào database:

```java
@Version
private long version;
```

Khi Hibernate update, SQL về khái niệm có dạng:

```sql
UPDATE tasks
SET status = ?, version = version + 1
WHERE id = ? AND version = ?;
```

Nếu request A và B cùng đọc `version = 3`, A update thành version 4 trước. Update của B với điều kiện `version = 3` chạm 0 row, provider báo optimistic lock failure và transaction rollback.

### 12.1. Vì sao optimistic locking?

Phù hợp khi xung đột hiếm: request không giữ database lock trong lúc người dùng đọc/sửa lâu, nhưng hệ thống phát hiện stale write thay vì âm thầm ghi đè.

Dùng `jakarta.persistence.Version`, không dùng annotation cùng tên từ package khác.

### 12.2. Bề mặt API

Với web API, có thể đưa version vào response DTO và yêu cầu client gửi lại khi cập nhật, hoặc dùng HTTP `ETag`/`If-Match`. Nếu phiên bản client cũ, trả `409 Conflict` hoặc `412 Precondition Failed` theo contract đã chọn.

`@Version` tự nó chỉ phát hiện khi persistence context đang giữ phiên bản cũ đến lúc ghi. API stateless thường tải entity mới cho mỗi request, nên phải so sánh version client gửi với entity vừa tải trước mutation; sau đó `@Version` vẫn bảo vệ race xảy ra giữa lần đọc và lần ghi:

```java
public record ChangeTaskStatusCommand(TaskStatus status, long expectedVersion) {
}

@Transactional
public TaskEntity changeTaskStatus(UUID taskId, ChangeTaskStatusCommand command) {
    TaskEntity task = taskRepository.findById(taskId)
            .orElseThrow(() -> new TaskNotFoundException(taskId));

    if (task.getVersion() != command.expectedVersion()) {
        throw new StaleTaskVersionException(taskId);
    }

    task.changeStatus(command.status());
    return task;
}
```

Nếu version không khớp, không mutate entity và trả lỗi conflict/precondition theo API contract. Nếu hai request vượt qua phép so sánh cùng lúc, database predicate theo `@Version` vẫn khiến một transaction thất bại.

Không expose version persistence một cách vô tình; quyết định đó thuộc API contract. Bắt `OptimisticLockingFailureException` ở error layer tuần 6 để trả lỗi conflict ổn định, không trả stack trace.

### 12.3. Optimistic và pessimistic locking

- Optimistic: không khóa row suốt quá trình đọc; kiểm tra version lúc ghi. Tốt khi conflict thấp.
- Pessimistic: database giữ lock theo query/transaction; phù hợp khi cần tuần tự hóa nhưng tăng contention/deadlock.

Bắt đầu với optimistic locking cho CRUD thông thường; chỉ dùng pessimistic lock sau khi xác định invariant và đo được vấn đề.

---

## 13. DTO cho relationship

Không serialize entity graph trực tiếp. Tạo DTO phẳng theo use case:

```java
public record TaskDetailResponse(
        UUID id,
        String title,
        TaskStatus status,
        UUID projectId,
        String projectName,
        long version
) {
    public static TaskDetailResponse from(TaskEntity task) {
        ProjectEntity project = task.getProject();
        return new TaskDetailResponse(
                task.getId(),
                task.getTitle(),
                task.getStatus(),
                project == null ? null : project.getId(),
                project == null ? null : project.getName(),
                task.getVersion()
        );
    }
}
```

Trong code production, tránh static mapper phụ thuộc getter lazy ngoài transaction. Có thể map trong service transaction hoặc dùng JPQL DTO projection. DTO giúp tránh vòng lặp JSON kiểu Project → Tasks → Project và kiểm soát dữ liệu trả về.

---

## 14. Test relationship, transaction và locking

### 14.1. Integration test trên PostgreSQL

Dùng cấu hình Testcontainers từ tuần 7 và `@DataJpaTest`. Kiểm tra tối thiểu:

- Task lưu FK `project_id` đúng.
- Load Task không truy vấn Project cho đến khi association thực sự cần (nếu provider/query path cho phép lazy proxy).
- FK ngăn xóa Project còn Task.
- Cascade/orphan removal chỉ xóa Comment khi đúng policy.
- Version tăng khi entity được cập nhật.
- Hai transaction dùng cùng version thì một lần ghi thất bại.
- Query fetch plan trả đủ association với số query kỳ vọng.

### 14.2. Kiểm tra optimistic lock

Trong test, lấy cùng row ở hai persistence context/transaction độc lập:

```text
Tx A đọc version 0
Tx B đọc version 0
Tx A sửa và commit → version 1
Tx B sửa và commit → optimistic lock exception
```

Không dùng cùng một persistence context cho cả hai lần đọc; nếu cùng context thì bạn chưa mô phỏng cạnh tranh thật.

### 14.3. Test số query

Test N+1 nên:

1. Tạo nhiều Project/Task.
2. `flush()` rồi `clear()` để tránh first-level cache che query.
3. Reset statistics.
4. Gọi đúng repository/service method.
5. Đọc kết quả như controller/mapper sẽ đọc.
6. Assert số query hoặc số prepared statement.

Test query count có thể nhạy với version/provider; giới hạn nó cho đường đi quan trọng và kiểm tra hành vi thay vì khóa mọi test vào từng câu SQL.

---

## 15. Lỗi thường gặp

### 15.1. Chỉ cập nhật inverse collection

Thêm Task vào `project.tasks` nhưng không set `task.project` có thể không ghi FK. Cập nhật owning side, tốt nhất qua helper method đồng bộ hai phía.

### 15.2. Cascade ALL ở mọi quan hệ

Có thể xóa nhầm dữ liệu dùng chung. Chọn cascade theo ownership và vòng đời nghiệp vụ.

### 15.3. Dùng `EAGER` để né `LazyInitializationException`

Eager có thể tăng tải dữ liệu và tạo N+1. Chọn fetch plan theo query/use case, map DTO trong transaction.

### 15.4. Để controller/serializer chạm lazy association

Có thể query ngoài ý muốn hoặc lỗi khi persistence context đóng. Controller chỉ nhận DTO/application response đã hoàn chỉnh.

### 15.5. `@Transactional` private hoặc self-invocation rồi mong proxy chạy

Spring proxy thường chỉ chặn lời gọi đi qua bean proxy. Thiết kế transaction boundary ở public application service method được gọi từ bean khác.

### 15.6. Giữ transaction quá lâu

Không gọi HTTP service hoặc đợi thao tác người dùng khi đang giữ DB transaction. Làm tăng lock duration và giảm throughput.

### 15.7. Paginate collection fetch join

Có thể nhân row, sai kết quả hoặc fetch toàn bộ vào memory. Phân trang root trước, rồi batch-fetch children.

### 15.8. Không xử lý optimistic lock conflict

Version annotation phát hiện stale update, nhưng application vẫn phải chuyển exception thành response contract và hướng dẫn client reload/merge.

---

## 16. Bài thực hành bắt buộc

### Phần A — Relationship

1. Thêm `ProjectEntity` và `ProjectRepository`.
2. Thêm `TaskEntity.project` với `@ManyToOne(fetch = LAZY)`.
3. Quyết định có cần `ProjectEntity.tasks` hay chỉ giữ một phía.
4. Tạo Comment entity và chọn cascade/orphan policy.
5. Viết migration V2, giữ lại V1 đã áp dụng.

### Phần B — Chính sách xóa

1. Xóa Project có Task phải trả conflict.
2. Database FK phải ngăn xóa Project bất kể application check.
3. Task bị xóa thì Comment xử lý theo chính sách đã document.
4. Thêm test cho từng trường hợp.

### Phần C — Transaction và đồng thời

1. Đặt transaction quanh create Task + assign Project.
2. Cập nhật Task managed entity để quan sát dirty checking.
3. Thêm `@Version` cho Task.
4. Viết test stale update từ hai persistence context.
5. Chuyển optimistic lock failure thành problem response theo contract tuần 6.

### Phần D — Query performance

1. Tạo endpoint detail cần Task + Project.
2. Đo SQL ban đầu.
3. Dùng fetch join hoặc `@EntityGraph`.
4. Tạo endpoint list Project có số Task hoặc Task summary.
5. Tránh collection fetch join cùng pagination.
6. So sánh số query trước/sau bằng test hoặc Hibernate statistics.

### Phần E — Many-to-many nâng cao

Thiết kế membership Project–User có role và `joinedAt` bằng entity liên kết; thêm unique constraint cho cặp project/user.

---

## 17. Câu hỏi tự kiểm tra

1. Owning side là gì và FK của Project–Task nằm ở đâu?
2. `mappedBy` trỏ đến tên nào?
3. Vì sao chỉ thêm phần tử vào inverse collection có thể không ghi FK?
4. Cascade khác `ON DELETE CASCADE` thế nào?
5. Khi nào `orphanRemoval = true` hợp lý?
6. `EAGER` có đảm bảo một SQL JOIN không?
7. Vì sao N+1 thường không lộ trong dữ liệu nhỏ?
8. Khi nào fetch join phù hợp và vì sao cẩn thận với pagination collection?
9. `@EntityGraph` giải quyết vấn đề gì?
10. Transaction boundary nên nằm ở đâu?
11. Self-invocation ảnh hưởng `@Transactional` thế nào?
12. `readOnly = true` có phải cơ chế cấm ghi không?
13. Dirty checking hoạt động với managed hay detached entity?
14. `@Version` phát hiện lost update bằng cách nào?
15. Khi stale update xảy ra, API nên trả lỗi gì theo contract của mình?

---

## 18. Tiêu chí hoàn thành tuần 8

- [ ] Có Project–Task relationship và hiểu Task là owning side.
- [ ] Mapping to-one và collection được khai báo LAZY có chủ đích.
- [ ] Không cập nhật inverse collection một mình.
- [ ] Chính sách delete Project/Task/Comment được thể hiện cả service lẫn DB constraint.
- [ ] Không dùng cascade toàn bộ theo mặc định.
- [ ] Transaction bao quanh use case nhiều bước.
- [ ] Giải thích được proxy, self-invocation và rollback rule.
- [ ] Có `@Version` và test stale update.
- [ ] Đo và xử lý được ít nhất một N+1.
- [ ] Không dùng collection fetch join trực tiếp với pagination.
- [ ] API tiếp tục trả DTO, không trả entity graph.
- [ ] Migration mới chạy trên PostgreSQL Testcontainers.

---

## 19. Gợi ý lịch học 7 ngày

| Ngày | Nội dung | Kết quả |
|---|---|---|
| 1 | SQL FK, relationship và owning side | Vẽ được schema và phía ghi FK |
| 2 | ManyToOne/OneToMany | Gán Project cho Task đúng |
| 3 | Cascade, orphan removal và delete policy | Test được lifecycle child |
| 4 | Transaction, rollback, dirty checking | Use case nhiều bước atomic |
| 5 | LAZY, N+1, fetch join, entity graph | So sánh SQL trước/sau |
| 6 | `@Version` và concurrent update | Stale write bị phát hiện |
| 7 | Integration tests, ôn tập | Hoàn thiện checklist tuần |

Tuần 9 theo roadmap chuyển sang testing: unit test, slice test và integration test cho toàn bộ API.
