# Tuần 5 — Controller, DTO và Validation trong Spring Boot

> Stack đề xuất: Java 21, Spring Boot 4.x, Spring Framework 7.x, Maven. Bài này tiếp tục Task API in-memory của tuần 3–4.

## 1. Mục tiêu bài học

Sau tuần này, bạn có thể:

- Giải thích vai trò của controller trong Spring MVC.
- Dùng đúng `@RestController`, `@RequestMapping`, `@GetMapping`, `@PostMapping`, `@PatchMapping` và `@DeleteMapping`.
- Nhận dữ liệu qua `@RequestBody`, `@PathVariable` và `@RequestParam`.
- Trả status code, header và body bằng `ResponseEntity`.
- Tách request DTO, response DTO và model nội bộ.
- Không trả entity/model lưu trữ trực tiếp ra API.
- Validate request bằng Jakarta Bean Validation.
- Dùng `@Valid`, `@NotBlank`, `@Size`, `@Email` và constraint cho query parameter.
- Tạo custom validator cho validation nhiều field.
- Thiết kế PATCH có semantics rõ ràng.
- Hoàn thiện Task API: tạo, danh sách, chi tiết, cập nhật, xóa, pagination và lọc trạng thái.
- Kiểm thử controller và validation bằng MockMvc.

---

## 2. Kiến trúc của request HTTP

Một request đi qua các lớp:

```text
HTTP request
    ↓
DispatcherServlet
    ↓
TaskController
    ↓
TaskService
    ↓
TaskRepository
```

Khi trả response:

```text
TaskEntity/domain model
    ↓
TaskDtoMapper
    ↓
TaskResponse
    ↓
JSON + HTTP status + headers
```

Controller là adapter ở ranh giới HTTP. Controller chịu trách nhiệm:

- Đọc path, query, header và body.
- Yêu cầu validation input.
- Chuyển DTO thành input cho use case.
- Chuyển kết quả thành response DTO.
- Chọn HTTP status và header phù hợp.

Controller không nên:

- Chứa business rule.
- Truy cập repository trực tiếp.
- Tự quản lý transaction.
- Trả persistence entity trực tiếp.
- Nuốt exception hoặc trả stack trace.

---

## 3. Dependency cần có

Với Spring Boot 4.x:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

Trong Spring Initializr, `spring-boot-starter-webmvc` được hiển thị với tên **Spring Web**.

Web starter cung cấp Spring MVC, JSON message conversion và embedded server. Validation starter cung cấp Jakarta Bean Validation implementation.

Spring Boot 4 cũng có các starter test theo từng module, ví dụ `spring-boot-starter-webmvc-test`. Với project học nhỏ, `spring-boot-starter-test` thường đủ vì nó kéo JUnit Jupiter, AssertJ, Mockito và các module test phù hợp. Khi muốn dependency test thật tối giản theo web MVC, có thể dùng starter focused:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## 4. `@Controller` và `@RestController`

### 4.1. `@Controller`

`@Controller` thường dùng trong Spring MVC trả view:

```java
@Controller
public class PageController {

    @GetMapping("/tasks-page")
    public String tasksPage() {
        return "tasks";
    }
}
```

String `"tasks"` được hiểu là tên view.

### 4.2. `@RestController`

`@RestController` tương đương về ý nghĩa với:

```text
@Controller + @ResponseBody
```

Object trả về được HTTP message converter serialize thành JSON:

```java
@RestController
public class HealthMessageController {

    @GetMapping("/api/message")
    public Map<String, String> message() {
        return Map.of("message", "Task API is running");
    }
}
```

Response:

```json
{
  "message": "Task API is running"
}
```

---

## 5. Request mapping

### 5.1. Base path ở class

```java
@RestController
@RequestMapping("/api/tasks")
public class TaskController {
}
```

Mọi endpoint trong class bắt đầu bằng `/api/tasks`.

### 5.2. Dùng annotation theo HTTP method

```java
@GetMapping
@PostMapping
@PutMapping
@PatchMapping
@DeleteMapping
```

Không nên dùng `@RequestMapping` ở method mà không chỉ rõ HTTP method, vì endpoint có thể match nhiều method ngoài ý muốn.

### 5.3. Endpoint của Task API

| Method | Path | Chức năng | Thành công |
|---|---|---|---|
| `POST` | `/api/tasks` | Tạo task | `201 Created` |
| `GET` | `/api/tasks` | Danh sách/lọc/phân trang | `200 OK` |
| `GET` | `/api/tasks/{id}` | Chi tiết | `200 OK` hoặc `404` |
| `PATCH` | `/api/tasks/{id}` | Cập nhật một phần | `200 OK` hoặc `404` |
| `DELETE` | `/api/tasks/{id}` | Xóa | `204 No Content` hoặc `404` |

---

## 6. Ba nguồn input phổ biến

### 6.1. `@PathVariable`

Path variable xác định resource cụ thể:

```java
@GetMapping("/{id}")
public ResponseEntity<TaskResponse> findById(
        @PathVariable UUID id
) {
    // ...
}
```

Request:

```http
GET /api/tasks/47fc86b7-9de7-4a39-bdc0-d732576f0ed6
```

Spring chuyển chuỗi trong path thành `UUID`. Nếu format không hợp lệ, request bị từ chối trước khi vào method.

### 6.2. `@RequestParam`

Query parameter dùng cho filter, pagination, sorting hoặc option:

```java
@GetMapping
public TaskPageResponse findAll(
        @RequestParam(required = false) TaskStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
) {
    // ...
}
```

Request:

```http
GET /api/tasks?status=TODO&page=0&size=20
```

Query parameter không nên dùng để truyền object nghiệp vụ lớn.

### 6.3. `@RequestBody`

Request body chứa representation cần tạo/cập nhật:

```java
@PostMapping
public ResponseEntity<TaskResponse> create(
        @Valid @RequestBody CreateTaskRequest request
) {
    // ...
}
```

Spring dùng JSON converter để deserialize JSON thành `CreateTaskRequest`, sau đó validation chạy vì có `@Valid`.

Request cần header:

```http
Content-Type: application/json
```

JSON sai cú pháp hoặc sai kiểu dữ liệu là lỗi đọc message, không phải constraint violation thông thường. Cách chuẩn hóa error response sẽ học ở tuần 6.

---

## 7. DTO là gì?

DTO — Data Transfer Object — mô tả dữ liệu đi qua boundary.

Trong API nên tách:

```text
CreateTaskRequest  → input khi tạo
UpdateTaskRequest  → input khi PATCH
TaskResponse       → output công khai
TaskEntity         → model nội bộ/lưu trữ
```

### 7.1. Vì sao không dùng một class cho tất cả?

Input và output có quyền khác nhau:

- Client được gửi `title`, nhưng không tự đặt `id`.
- Client không tự đặt `createdAt` hoặc `updatedAt`.
- Response có thể chứa field tính toán không tồn tại trong storage.
- Validation khi tạo khác validation khi cập nhật.
- Internal model có thể thay đổi mà không muốn phá API contract.

Tách DTO giúp ngăn **over-posting/mass assignment**: client không thể gán field mà API không cho phép chỉ vì field tồn tại trong entity.

### 7.2. Vì sao không trả entity trực tiếp?

Trả entity trực tiếp có thể gây:

- Lộ field nội bộ hoặc nhạy cảm.
- API contract bị phụ thuộc database schema.
- Lazy-loading query ngoài ý muốn.
- Vòng lặp JSON với quan hệ hai chiều.
- Client gửi ngược field không được phép sửa.
- Khó version API.

Ngay cả khi tuần này repository còn in-memory, hãy tạo thói quen tách boundary từ đầu.

---

## 8. Model cho Task API

### 8.1. Task status

```java
package com.example.taskapi.task;

public enum TaskStatus {
    TODO,
    IN_PROGRESS,
    DONE
}
```

### 8.2. Model nội bộ

```java
package com.example.taskapi.task;

import java.time.Instant;
import java.util.UUID;

public record TaskEntity(
        UUID id,
        String title,
        String description,
        String assigneeEmail,
        TaskStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
```

Tên `TaskEntity` được dùng theo roadmap để phân biệt với DTO. Ở tuần 5 class này chưa cần annotation JPA; mapping database sẽ được thêm ở tuần 7.

`record` làm object bất biến. Cập nhật task nghĩa là tạo `TaskEntity` mới thay vì thay đổi field cũ.

---

## 9. Request DTO và Bean Validation

### 9.1. `CreateTaskRequest`

```java
package com.example.taskapi.task.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
        @NotBlank(message = "title must not be blank")
        @Size(max = 200, message = "title must not exceed 200 characters")
        String title,

        @Size(max = 2_000, message = "description must not exceed 2000 characters")
        String description,

        @Pattern(regexp = ".*\\S.*", message = "assigneeEmail must not be blank")
        @Email(message = "assigneeEmail must be a valid email address")
        @Size(max = 254, message = "assigneeEmail must not exceed 254 characters")
        String assigneeEmail
) {
}
```

### 9.2. Ý nghĩa constraint

#### `@NotNull`

Chỉ cấm `null`. Chuỗi `""` hoặc `"   "` vẫn hợp lệ.

#### `@NotEmpty`

Cấm `null` và chuỗi/collection rỗng, nhưng chuỗi toàn khoảng trắng vẫn có thể hợp lệ.

#### `@NotBlank`

Dành cho text bắt buộc; cấm `null`, rỗng và toàn khoảng trắng.

#### `@Size`

Giới hạn độ dài chuỗi, collection, map hoặc array. `null` thường được xem là hợp lệ; kết hợp `@NotNull`/`@NotBlank` nếu field bắt buộc.

#### `@Email`

Kiểm tra hình dạng email. `null` được chấp nhận, nên kết hợp `@NotBlank` nếu email bắt buộc.

Validation email không chứng minh mailbox tồn tại hoặc thuộc về người dùng. Việc xác minh email cần workflow riêng.

### 9.3. Optional field và chuỗi rỗng

`description` và `assigneeEmail` ở ví dụ trên là optional. Quy ước API nên yêu cầu client:

- Bỏ field hoặc gửi `null` nếu không có giá trị.
- Không dùng chuỗi trắng để biểu diễn “không có dữ liệu”.

Service có thể chuẩn hóa input thành object mới, nhưng không nên âm thầm biến mọi input sai thành đúng. Validation cần bắt các lỗi làm contract mơ hồ.

### 9.4. Nested validation

```java
public record CreateTaskRequest(
        @NotBlank String title,
        @Valid AssigneeRequest assignee
) {
}

public record AssigneeRequest(
        @NotBlank String name,
        @NotBlank @Email String email
) {
}
```

Không có `@Valid` trên nested object, constraint bên trong `AssigneeRequest` sẽ không được cascade validate.

### 9.5. Container element validation

```java
public record CreateTaskRequest(
        @NotBlank String title,
        @Size(max = 10)
        List<@NotBlank @Size(max = 30) String> labels
) {
}
```

Constraint áp dụng cả collection và từng phần tử.

---

## 10. DTO cho PATCH

### 10.1. PATCH không giống PUT

- `PUT` thường biểu diễn thay thế toàn bộ representation.
- `PATCH` biểu diễn cập nhật một phần.

Với PATCH, field không xuất hiện thường có nghĩa “giữ nguyên”.

### 10.2. DTO cơ bản

```java
package com.example.taskapi.task.api;

import com.example.taskapi.task.TaskStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@ValidTaskUpdate
public record UpdateTaskRequest(
        @Size(max = 200, message = "title must not exceed 200 characters")
        String title,

        @Size(max = 2_000, message = "description must not exceed 2000 characters")
        String description,

        @Pattern(regexp = ".*\\S.*", message = "assigneeEmail must not be blank")
        @Email(message = "assigneeEmail must be a valid email address")
        @Size(max = 254, message = "assigneeEmail must not exceed 254 characters")
        String assigneeEmail,

        TaskStatus status
) {
}
```

Trong bài này, quy ước:

- `null` nghĩa là field không được cập nhật.
- PATCH phải có ít nhất một field khác `null`.
- `title`, nếu xuất hiện, không được blank.
- API hiện tại chưa hỗ trợ “xóa description/assignee bằng explicit null”.

### 10.3. Giới hạn quan trọng của DTO thông thường

Sau khi deserialize vào record trên, hai JSON sau đều tạo `description == null`:

```json
{}
```

```json
{
  "description": null
}
```

Vì vậy Java nullable field đơn thuần không phân biệt:

- Field không được gửi.
- Field được gửi với giá trị `null` để yêu cầu xóa.

Nếu API cần phân biệt, phải chọn contract rõ ràng, ví dụ:

- JSON Merge Patch.
- JSON Patch.
- Wrapper biểu diễn trạng thái absent/present.
- Endpoint riêng cho thao tác clear.

Không tự suy đoán semantics vì có thể làm mất dữ liệu.

---

## 11. Custom validator

Validation chuẩn xử lý từng field. Custom class-level constraint phù hợp với quy tắc liên quan nhiều field của cùng DTO.

### 11.1. Khai báo annotation

```java
package com.example.taskapi.task.api;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Target({TYPE, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Constraint(validatedBy = ValidTaskUpdateValidator.class)
public @interface ValidTaskUpdate {
    String message() default "update must contain at least one valid field";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
```

### 11.2. Implement validator

```java
package com.example.taskapi.task.api;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public final class ValidTaskUpdateValidator
        implements ConstraintValidator<ValidTaskUpdate, UpdateTaskRequest> {

    @Override
    public boolean isValid(
            UpdateTaskRequest request,
            ConstraintValidatorContext context
    ) {
        if (request == null) {
            return true;
        }

        boolean containsUpdate = request.title() != null
                || request.description() != null
                || request.assigneeEmail() != null
                || request.status() != null;

        if (!containsUpdate) {
            return false;
        }

        return request.title() == null || !request.title().isBlank();
    }
}
```

`null` request được validator chấp nhận vì nullability là trách nhiệm của `@NotNull` hoặc của argument resolver. `@RequestBody` mặc định bắt buộc có body; body thiếu sẽ không tới constraint này.

### 11.3. Custom validator nên và không nên làm gì?

Nên:

- Kiểm tra quan hệ giữa các field trong cùng request.
- Kiểm tra format/domain-independent invariant.
- Chạy nhanh, xác định và không có side effect.

Không nên:

- Query database để kiểm tra task tồn tại.
- Gọi API bên ngoài.
- Thực hiện business transition.
- Thay đổi request.

“Task có tồn tại không?” và “TODO có được chuyển thẳng sang DONE không?” là business rule trong service/domain, không phải shape validation của DTO.

---

## 12. Response DTO

```java
package com.example.taskapi.task.api;

import com.example.taskapi.task.TaskStatus;

import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        String title,
        String description,
        String assigneeEmail,
        TaskStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
```

Response DTO không có validation annotation vì nó không phải input không tin cậy từ client. Correctness của response đến từ business/application code và tests.

### Pagination response

```java
package com.example.taskapi.task.api;

import java.util.List;

public record TaskPageResponse(
        List<TaskResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public TaskPageResponse {
        items = List.copyOf(items);
    }
}
```

API không trả trực tiếp type pagination của persistence framework. Contract HTTP không nên bị khóa vào JPA/Spring Data trước khi module database được thêm.

---

## 13. Mapper giữa model và DTO

```java
package com.example.taskapi.task.api;

import com.example.taskapi.task.TaskEntity;
import org.springframework.stereotype.Component;

@Component
public class TaskDtoMapper {

    public TaskResponse toResponse(TaskEntity task) {
        return new TaskResponse(
                task.id(),
                task.title(),
                task.description(),
                task.assigneeEmail(),
                task.status(),
                task.createdAt(),
                task.updatedAt()
        );
    }
}
```

Mapper chỉ chuyển dữ liệu, không chứa business rule hoặc gọi repository.

Với mapping đơn giản có thể dùng code thủ công. Khi project lớn có thể cân nhắc mapping library, nhưng không nên thêm dependency chỉ để tránh vài dòng rõ ràng.

---

## 14. Input cho service

HTTP DTO thuộc API layer. Service nên nhận command/use-case input không phụ thuộc annotation HTTP:

```java
package com.example.taskapi.task;

public record CreateTaskCommand(
        String title,
        String description,
        String assigneeEmail
) {
}
```

```java
package com.example.taskapi.task;

public record UpdateTaskCommand(
        String title,
        String description,
        String assigneeEmail,
        TaskStatus status
) {
}
```

Controller tạo command mới từ DTO:

```java
CreateTaskCommand command = new CreateTaskCommand(
        request.title(),
        request.description(),
        request.assigneeEmail()
);
```

Với ứng dụng nhỏ, service nhận request DTO có thể ít code hơn nhưng làm application layer phụ thuộc API contract. Tách command tạo boundary rõ và thuận lợi khi sau này cùng use case được gọi từ message consumer hoặc batch job.

---

## 15. Service contract

```java
package com.example.taskapi.task;

import java.util.Optional;
import java.util.UUID;

public interface TaskService {
    TaskEntity create(CreateTaskCommand command);

    TaskSlice findAll(TaskStatus status, int page, int size);

    Optional<TaskEntity> findById(UUID id);

    Optional<TaskEntity> update(UUID id, UpdateTaskCommand command);

    boolean delete(UUID id);
}
```

Pagination model nội bộ:

```java
package com.example.taskapi.task;

import java.util.List;

public record TaskSlice(
        List<TaskEntity> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public TaskSlice {
        items = List.copyOf(items);
    }
}
```

Ở tuần 6, not-found và business error sẽ được biểu diễn bằng exception/problem details thống nhất. Trong tuần này `Optional`/boolean giúp controller trả `404` mà chưa cần error handling tập trung.

---

## 16. Controller hoàn chỉnh

```java
package com.example.taskapi.task.api;

import com.example.taskapi.task.CreateTaskCommand;
import com.example.taskapi.task.TaskEntity;
import com.example.taskapi.task.TaskService;
import com.example.taskapi.task.TaskSlice;
import com.example.taskapi.task.TaskStatus;
import com.example.taskapi.task.UpdateTaskCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService taskService;
    private final TaskDtoMapper mapper;

    public TaskController(TaskService taskService, TaskDtoMapper mapper) {
        this.taskService = taskService;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(
            @Valid @RequestBody CreateTaskRequest request
    ) {
        CreateTaskCommand command = new CreateTaskCommand(
                request.title(),
                request.description(),
                request.assigneeEmail()
        );

        TaskEntity created = taskService.create(command);
        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity
                .created(location)
                .body(mapper.toResponse(created));
    }

    @GetMapping
    public TaskPageResponse findAll(
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(defaultValue = "0")
            @Min(value = 0, message = "page must be at least 0")
            int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "size must be at least 1")
            @Max(value = 100, message = "size must not exceed 100")
            int size
    ) {
        TaskSlice result = taskService.findAll(status, page, size);
        List<TaskResponse> items = result.items().stream()
                .map(mapper::toResponse)
                .toList();

        return new TaskPageResponse(
                items,
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> findById(
            @PathVariable UUID id
    ) {
        return taskService.findById(id)
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TaskResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTaskRequest request
    ) {
        UpdateTaskCommand command = new UpdateTaskCommand(
                request.title(),
                request.description(),
                request.assigneeEmail(),
                request.status()
        );

        return taskService.update(id, command)
                .map(mapper::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        if (!taskService.delete(id)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }
}
```

### Controller trên giữ đúng trách nhiệm

- HTTP input nằm trong annotation/DTO.
- Business work được giao cho service.
- Mapping output qua mapper.
- `201` kèm `Location` khi tạo.
- `404` khi resource không tồn tại.
- `204` không có response body khi xóa thành công.

---

## 17. Validation cho method parameter

Constraint có thể đặt trực tiếp lên `@RequestParam` và `@PathVariable`:

```java
@RequestParam
@Min(0)
int page
```

Trong Spring MVC hiện đại, method validation cho controller được tích hợp khi Bean Validation có trên classpath. Không thêm class-level `@Validated` vào controller chỉ theo thói quen; nó có thể chuyển sang cơ chế AOP method validation khác với xử lý tích hợp của MVC.

Hai nhóm lỗi cần phân biệt:

- Validation object `@Valid @RequestBody`.
- Constraint trực tiếp trên method parameter/return value.

Các nhóm này có thể tạo exception khác nhau. Tuần 6 sẽ gom chúng về một error format thống nhất.

---

## 18. `ResponseEntity`

`ResponseEntity<T>` đại diện cho:

```text
status + headers + body
```

Ví dụ:

```java
return ResponseEntity.ok(response);                     // 200
return ResponseEntity.created(location).body(response); // 201 + Location
return ResponseEntity.noContent().build();              // 204
return ResponseEntity.notFound().build();                // 404
```

### Khi nào không cần `ResponseEntity`?

Nếu endpoint luôn trả `200 OK` và không cần header tùy chỉnh:

```java
@GetMapping
public TaskPageResponse findAll(...) {
    return response;
}
```

Spring serialize object và trả `200`.

Có thể dùng `@ResponseStatus` cho status cố định, nhưng `ResponseEntity` rõ hơn khi response thay đổi theo kết quả.

### `204 No Content` không có body

Không trả JSON với status `204`:

```java
// Không nên
return ResponseEntity.status(HttpStatus.NO_CONTENT)
        .body(Map.of("deleted", true));
```

Theo semantics HTTP, response `204` không có content.

---

## 19. Pagination và filtering

Endpoint:

```http
GET /api/tasks?status=TODO&page=0&size=20
```

Quy ước:

- `page` bắt đầu từ `0`.
- `size` từ `1` đến `100`.
- `status` là optional.
- Có thứ tự ổn định, ví dụ `createdAt DESC, id DESC`.

Không có thứ tự ổn định, item có thể nhảy giữa các page khi dữ liệu thay đổi.

Response:

```json
{
  "items": [
    {
      "id": "47fc86b7-9de7-4a39-bdc0-d732576f0ed6",
      "title": "Learn validation",
      "description": null,
      "assigneeEmail": "student@example.com",
      "status": "TODO",
      "createdAt": "2026-09-25T08:00:00Z",
      "updatedAt": "2026-09-25T08:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

### Tính total pages

Với số không âm:

```java
int totalPages = totalElements == 0
        ? 0
        : (int) ((totalElements + size - 1) / size);
```

Nếu dữ liệu rất lớn, tránh overflow bằng phép chia/remainder hoặc utility an toàn. Khi chuyển sang Spring Data JPA, `Page` xử lý metadata nội bộ nhưng API vẫn nên map sang response contract của mình.

### Offset pagination có giới hạn

`page/size` dễ dùng nhưng page sâu có thể chậm và không ổn định khi dữ liệu thay đổi liên tục. Cursor pagination sẽ được cân nhắc ở tuần query/performance.

---

## 20. Validation không thay thế business rule

Ba lớp bảo vệ khác nhau:

### API validation

```text
title không blank
email đúng format
size không quá 100
```

### Business validation

```text
chỉ owner được sửa task
DONE không được quay về TODO
project đã đóng không nhận task mới
```

### Persistence constraint

```text
NOT NULL
UNIQUE
FOREIGN KEY
CHECK constraint
```

Không chỉ validate ở frontend. Client có thể bỏ qua UI và gọi API trực tiếp.

Không chỉ dựa vào DTO validation cho invariant dữ liệu. Concurrent request vẫn có thể vượt qua check application; database constraint/transaction cần bảo vệ invariant phù hợp ở tuần sau.

---

## 21. Input normalization

Validation và normalization là hai việc khác nhau.

Input:

```json
{
  "title": "  Learn Spring MVC  "
}
```

Service có thể tạo object mới đã chuẩn hóa:

```java
String normalizedTitle = command.title().trim();

TaskEntity task = new TaskEntity(
        id,
        normalizedTitle,
        command.description(),
        command.assigneeEmail(),
        TaskStatus.TODO,
        now,
        now
);
```

Không sửa DTO hiện có. Records giúp giữ input bất biến.

Không normalize một cách làm thay đổi ý nghĩa dữ liệu mà client không biết. Ví dụ email internationalization, Unicode và case sensitivity cần policy rõ ràng.

---

## 22. JSON contract và security

### 22.1. Chỉ expose field cần thiết

Response không chứa:

- Internal note.
- Audit actor nhạy cảm.
- Secret/token.
- Stack trace.
- Database implementation detail.

### 22.2. Giới hạn kích thước

`@Size` bảo vệ từng field, nhưng server/reverse proxy cũng cần giới hạn request body. Không chấp nhận payload không giới hạn rồi mới validate sau khi đã đọc hết vào memory.

### 22.3. Unknown JSON fields

Chọn policy rõ:

- Reject unknown fields để bắt typo và contract sai sớm.
- Ignore unknown fields để hỗ trợ forward compatibility.

Không có lựa chọn đúng cho mọi API. Nếu chọn strict mode, document và test behavior. Không dùng unknown field để thực hiện mass assignment.

### 22.4. Content type

Client gửi JSON cần:

```http
Content-Type: application/json
Accept: application/json
```

Có thể khai báo `consumes`/`produces` khi API cần contract content negotiation rõ ràng:

```java
@PostMapping(
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
)
```

Không thêm nếu chỉ lặp default mà project không cần kiểm soát chặt.

---

## 23. Kiểm thử validation độc lập

```java
package com.example.taskapi.task.api;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CreateTaskRequestValidationTest {
    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void rejectsBlankTitleAndInvalidEmail() {
        CreateTaskRequest request = new CreateTaskRequest(
                "   ",
                "description",
                "not-an-email"
        );

        Set<ConstraintViolation<CreateTaskRequest>> violations =
                validator.validate(request);

        assertThat(violations)
                .extracting(violation ->
                        violation.getPropertyPath().toString()
                )
                .containsExactlyInAnyOrder("title", "assigneeEmail");
    }
}
```

Test constraint theo behavior/property path, không phụ thuộc quá chặt vào message mặc định của provider.

### Test custom validator

```java
@Test
void rejectsEmptyPatch() {
    UpdateTaskRequest request =
            new UpdateTaskRequest(null, null, null, null);

    assertThat(validator.validate(request))
            .anyMatch(violation ->
                    violation.getMessage().contains("at least one")
            );
}
```

---

## 24. Kiểm thử controller bằng MockMvc

MockMvc gửi request qua Spring MVC infrastructure mà không cần mở port thật.

```java
package com.example.taskapi.task.api;

import com.example.taskapi.task.TaskEntity;
import com.example.taskapi.task.TaskService;
import com.example.taskapi.task.TaskStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TaskControllerTest {
    private TaskService taskService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        TaskController controller = new TaskController(
                taskService,
                new TaskDtoMapper()
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
    }

    @Test
    void createsTask() throws Exception {
        UUID id = UUID.fromString(
                "47fc86b7-9de7-4a39-bdc0-d732576f0ed6"
        );
        Instant now = Instant.parse("2026-09-25T08:00:00Z");
        TaskEntity created = new TaskEntity(
                id,
                "Learn validation",
                null,
                "student@example.com",
                TaskStatus.TODO,
                now,
                now
        );
        when(taskService.create(any())).thenReturn(created);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Learn validation",
                                  "assigneeEmail": "student@example.com"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "http://localhost/api/tasks/" + id
                ))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("TODO"));
    }

    @Test
    void rejectsBlankTitle() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskService);
    }
}
```

Test này xác nhận:

- JSON được bind vào DTO.
- Validation chạy trước service.
- Status `201` và `Location` đúng.
- Response JSON đúng contract.

### Standalone và context-based MockMvc

`standaloneSetup`:

- Nhanh.
- Tập trung một controller.
- Phải đăng ký thủ công advice/converter tùy chỉnh nếu có.
- Có thể bỏ sót khác biệt configuration thật.

MockMvc từ application context:

- Gần wiring thật hơn.
- Kiểm tra controller, converter, validation và advice đã đăng ký.
- Chậm hơn và phạm vi rộng hơn.

Nên có test tập trung cho controller và một số integration tests cho HTTP flow quan trọng.

### `@WebMvcTest` trong Spring Boot 4

Khi muốn test gần Spring MVC configuration thật hơn nhưng vẫn không load toàn bộ app, dùng `@WebMvcTest`:

```java
package com.example.taskapi.task.api;

import com.example.taskapi.task.TaskEntity;
import com.example.taskapi.task.TaskService;
import com.example.taskapi.task.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskController.class)
class TaskControllerWebMvcTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaskService taskService;

    @MockitoBean
    private TaskDtoMapper mapper;

    @Test
    void rejectsBlankTitleBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "   "
                                }
                                """))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(taskService);
    }

    @Test
    void createsTask() throws Exception {
        UUID id = UUID.fromString(
                "47fc86b7-9de7-4a39-bdc0-d732576f0ed6"
        );
        Instant now = Instant.parse("2026-09-25T08:00:00Z");
        TaskEntity created = new TaskEntity(
                id,
                "Learn validation",
                null,
                "student@example.com",
                TaskStatus.TODO,
                now,
                now
        );
        TaskResponse response = new TaskResponse(
                id,
                "Learn validation",
                null,
                "student@example.com",
                TaskStatus.TODO,
                now,
                now
        );

        when(taskService.create(any())).thenReturn(created);
        when(mapper.toResponse(created)).thenReturn(response);

        mockMvc.perform(post("/api/tasks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Learn validation",
                                  "assigneeEmail": "student@example.com"
                                }
                                """))
                .andExpect(status().isCreated());
    }
}
```

Trong Boot 4, package của `@WebMvcTest` là `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`. Khi cần mock bean phụ thuộc của controller, dùng `@MockitoBean` từ Spring Test thay cho thói quen cũ `@MockBean`.

---

## 25. Test case tối thiểu cho Task API

### POST `/api/tasks`

- Request hợp lệ trả `201`.
- Có `Location` header.
- Title null/rỗng/blank trả `400`.
- Title quá 200 ký tự trả `400`.
- Email sai trả `400`.
- JSON malformed trả `400`.

### GET `/api/tasks`

- Default `page=0`, `size=20`.
- Filter status hợp lệ.
- Status không thuộc enum trả `400`.
- Page âm trả `400`.
- Size bằng 0 hoặc lớn hơn 100 trả `400`.
- Response có pagination metadata.

### GET `/api/tasks/{id}`

- ID tồn tại trả `200`.
- ID không tồn tại trả `404`.
- UUID sai format trả `400`.

### PATCH `/api/tasks/{id}`

- Cập nhật một field thành công.
- Body rỗng `{}` trả `400`.
- Title blank trả `400`.
- Status sai trả `400`.
- Task không tồn tại trả `404`.

### DELETE `/api/tasks/{id}`

- Xóa thành công trả `204` và không có body.
- Task không tồn tại trả `404`.

---

## 26. Chạy thử bằng curl

### Tạo task

```bash
curl -i \
  -X POST http://localhost:8080/api/tasks \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "Learn Spring MVC",
    "description": "Controller, DTO and validation",
    "assigneeEmail": "student@example.com"
  }'
```

### Danh sách có filter

```bash
curl -i \
  'http://localhost:8080/api/tasks?status=TODO&page=0&size=20'
```

### Chi tiết

```bash
curl -i \
  http://localhost:8080/api/tasks/47fc86b7-9de7-4a39-bdc0-d732576f0ed6
```

### Cập nhật

```bash
curl -i \
  -X PATCH \
  http://localhost:8080/api/tasks/47fc86b7-9de7-4a39-bdc0-d732576f0ed6 \
  -H 'Content-Type: application/json' \
  -d '{"status":"IN_PROGRESS"}'
```

### Xóa

```bash
curl -i \
  -X DELETE \
  http://localhost:8080/api/tasks/47fc86b7-9de7-4a39-bdc0-d732576f0ed6
```

---

## 27. Lỗi thường gặp

### Quên `@Valid`

```java
public ResponseEntity<?> create(
        @RequestBody CreateTaskRequest request
)
```

Constraint trong DTO không tự chạy cho request body nếu thiếu trigger validation phù hợp.

### Dùng entity làm request body

Client có thể gửi `id`, timestamps hoặc field nội bộ. Tách request DTO chỉ chứa field được phép.

### Trả entity trực tiếp

API contract dính với internal model và dễ lộ dữ liệu. Map sang response DTO.

### Business logic trong controller

Controller kiểm tra transition, ownership hoặc query repository sẽ lớn và khó test. Chuyển business rule xuống service/domain.

### Dùng `@NotNull` cho text bắt buộc

`@NotNull` vẫn chấp nhận `"   "`. Dùng `@NotBlank`.

### Cho phép page size không giới hạn

Client có thể yêu cầu hàng triệu record, gây memory/latency issue. Đặt maximum rõ ràng.

### PATCH không rõ null semantics

Không phân biệt absent/null nhưng vẫn cho client “clear field” dẫn đến hành vi mơ hồ. Chọn contract cụ thể.

### Custom validator gọi database

Validation trở nên chậm, có side effect và race condition. Tồn tại/uniqueness/business rule thuộc service và persistence boundary.

### Catch `Exception` trong controller

```java
try {
    // ...
} catch (Exception exception) {
    return ResponseEntity.badRequest().build();
}
```

Cách này biến lỗi server thành lỗi client, nuốt context và làm monitoring sai. Tuần 6 sẽ dùng exception handler tập trung.

---

## 28. Bài tập thực hành

### Bài 1 — Hoàn thiện CRUD

Implement toàn bộ service/repository để controller trong bài chạy được. Repository in-memory phải:

- Tạo snapshot mới khi cập nhật.
- Có thứ tự danh sách ổn định.
- Không trả collection mutable nội bộ.
- An toàn với concurrent access trong phạm vi bài học.

### Bài 2 — Validation matrix

Lập bảng cho mỗi field:

```text
field | required? | null | empty | blank | max length | format
```

Viết test cho từng boundary, không chỉ happy path.

### Bài 3 — Custom constraint

Mở rộng update DTO:

- Nếu status là `DONE`, `completionNote` phải có nội dung.
- Nếu status khác `DONE`, `completionNote` phải null.

Chỉ kiểm tra consistency trong DTO. Quy tắc transition dựa trên trạng thái hiện tại vẫn nằm trong service.

### Bài 4 — Pagination

Seed 105 task và kiểm tra:

- `page=0,size=20` trả 20 item.
- `page=5,size=20` trả 5 item.
- `totalElements=105`.
- `totalPages=6`.
- Page vượt cuối trả list rỗng, không phải lỗi server.

### Bài 5 — Contract test

Viết MockMvc tests xác nhận:

- JSON field name.
- Status code.
- `Location` header.
- Content type.
- Không có field nội bộ trong response.

### Bài 6 — Thiết kế clear field

Viết tài liệu ngắn so sánh ba cách cho client xóa `assigneeEmail`:

1. Explicit `null` trong JSON Merge Patch.
2. Endpoint `DELETE /api/tasks/{id}/assignee`.
3. Wrapper absent/present trong PATCH DTO.

Chọn một cách và giải thích trade-off.

---

## 29. Câu hỏi tự kiểm tra

1. `@RestController` khác `@Controller` thế nào?
2. Vì sao dùng annotation riêng cho từng HTTP method?
3. Khi nào dùng path variable, query parameter và request body?
4. DTO giải quyết vấn đề gì?
5. Vì sao request DTO và response DTO nên tách nhau?
6. Vì sao không trả entity trực tiếp?
7. `@NotNull`, `@NotEmpty` và `@NotBlank` khác nhau thế nào?
8. `@Size` có làm `null` invalid không?
9. Vì sao nested object cần `@Valid`?
10. Custom validator phù hợp với loại quy tắc nào?
11. Vì sao custom validator không nên query database?
12. `ResponseEntity` chứa những thành phần nào?
13. Khi tạo resource, vì sao nên trả `Location`?
14. `204 No Content` có được chứa body không?
15. PUT khác PATCH ở đâu?
16. Vì sao nullable DTO không phân biệt absent và explicit null?
17. Tại sao page size cần giới hạn?
18. API validation khác business validation và database constraint thế nào?
19. MockMvc standalone có ưu/nhược điểm gì?
20. Vì sao validation vẫn phải chạy ở server dù frontend đã validate?

---

## 30. Tiêu chí hoàn thành tuần 5

- [ ] Tạo đủ năm endpoint cho Task API.
- [ ] Controller chỉ xử lý HTTP concerns.
- [ ] Service không phụ thuộc annotation/controller HTTP.
- [ ] Có `CreateTaskRequest`, `UpdateTaskRequest`, `TaskResponse` và `TaskEntity` riêng.
- [ ] Không trả entity trực tiếp.
- [ ] Tạo resource trả `201` và `Location`.
- [ ] Xóa thành công trả `204` không có body.
- [ ] Dùng đúng `@RequestBody`, `@PathVariable` và `@RequestParam`.
- [ ] Request body được kích hoạt validation bằng `@Valid`.
- [ ] Text bắt buộc dùng `@NotBlank`.
- [ ] Length, email và pagination có constraint.
- [ ] Custom validator không có I/O hoặc side effect.
- [ ] PATCH có null/absent semantics được document.
- [ ] Pagination có maximum size và thứ tự ổn định.
- [ ] Validation tests và MockMvc tests đều pass.
- [ ] Critical HTTP flows có test thành công và thất bại.
- [ ] Code tự viết đạt tối thiểu 80% test coverage.

---

## 31. Tóm tắt

Controller là HTTP adapter, không phải nơi chứa business logic:

```text
HTTP input
  → request DTO
  → validation
  → command/use case
  → service
  → internal model
  → response DTO
  → HTTP response
```

Các nguyên tắc cốt lõi:

- Request DTO chỉ chứa field client được phép gửi.
- Response DTO là API contract công khai.
- Internal entity/model không đi trực tiếp qua HTTP boundary.
- Bean Validation kiểm tra shape và format; service kiểm tra business rule.
- `ResponseEntity` dùng khi cần kiểm soát status/header/body.
- PATCH phải có semantics cho absent/null rõ ràng.
- Pagination luôn có default, maximum và stable ordering.
- Test validation độc lập và test HTTP contract bằng MockMvc.

Tuần 6 sẽ tiếp tục bằng custom exception, `@RestControllerAdvice`, validation errors theo field và RFC 9457 Problem Details để mọi lỗi của API có cùng một contract.

## Tài liệu chính thức

- [Spring MVC Annotated Controllers](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller.html)
- [Request Mapping](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html)
- [Method Arguments](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/arguments.html)
- [Return Values](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-methods/return-types.html)
- [Validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)
- [Java Bean Validation](https://docs.spring.io/spring-framework/reference/core/validation/beanvalidation.html)
- [MockMvc](https://docs.spring.io/spring-framework/reference/testing/mockmvc.html)
- [Spring Boot Web](https://docs.spring.io/spring-boot/4.0/reference/web/index.html)
- [Spring Boot Testing](https://docs.spring.io/spring-boot/4.0/reference/testing/index.html)
- [Testing Spring Boot Applications](https://docs.spring.io/spring-boot/4.0/reference/testing/spring-boot-applications.html)
