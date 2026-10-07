# Tuần 6 — Error Handling và thiết kế API trong Spring Boot

> Stack đề xuất: Java 21, Spring Boot 4.x, Spring Framework 7.x. Bài này tiếp nối Task API ở tuần 5.

## 1. Mục tiêu bài học

Sau tuần này, bạn có thể:

- Phân loại lỗi client, lỗi business và lỗi hệ thống.
- Tạo custom exception không phụ thuộc HTTP.
- Xử lý lỗi tập trung bằng `@RestControllerAdvice` và `@ExceptionHandler`.
- Dùng `ProblemDetail` theo RFC 9457.
- Trả validation error theo từng field bằng extension `errors`.
- Xử lý cả `MethodArgumentNotValidException` và `HandlerMethodValidationException`.
- Xử lý JSON lỗi cú pháp, sai kiểu path/query và request không hợp lệ.
- Chọn đúng status `400`, `401`, `403`, `404`, `409`, `415`, `422` và `500`.
- Gắn request/correlation ID vào response và log.
- Log lỗi an toàn, không lộ stack trace, token hoặc dữ liệu nội bộ.
- Hiểu phạm vi của controller advice và những lỗi xảy ra ngoài MVC controller.
- Chọn chiến lược versioning nhất quán.
- Viết MockMvc tests cho error contract.

---

## 2. Vì sao cần error contract thống nhất?

Nếu mỗi controller tự trả lỗi một kiểu:

```json
{"error":"not found"}
```

```json
{"message":"Validation failed","code":400}
```

```json
{"success":false,"reason":"bad status"}
```

Client phải viết nhiều nhánh xử lý và thường bắt đầu parse chuỗi message. Monitoring cũng khó nhóm lỗi theo loại.

Một error contract tốt phải:

- Có HTTP status đúng.
- Có machine-readable problem type ổn định.
- Có thông tin đủ để client sửa request.
- Không lộ implementation detail.
- Có request ID để đối chiếu log.
- Nhất quán giữa các endpoint.
- Có khả năng mở rộng mà không phá client.

Luồng mong muốn:

```text
Controller / Service / Spring MVC
            ↓ throws
GlobalExceptionHandler
            ↓ maps
RFC 9457 ProblemDetail
            ↓
HTTP status + application/problem+json
```

---

## 3. Phân loại lỗi

### 3.1. Lỗi input/transport

Ví dụ:

- JSON sai cú pháp.
- UUID sai format.
- Query parameter không chuyển được sang enum.
- Thiếu request body.
- `title` blank.
- `size` lớn hơn giới hạn.

Thông thường trả `400 Bad Request`.

### 3.2. Resource không tồn tại

Ví dụ:

- Task ID hợp lệ về format nhưng không có trong hệ thống.

Trả `404 Not Found`.

### 3.3. Conflict với trạng thái hiện tại

Ví dụ:

- Task đã hoàn thành nhưng client cố sửa field bị khóa.
- Version client gửi đã cũ.
- Resource có cùng unique business key đã tồn tại.

Thường trả `409 Conflict`.

### 3.4. Request đúng cú pháp nhưng không xử lý được

`422 Unprocessable Content` có thể phù hợp với semantic validation không phải conflict trạng thái. Tuy nhiên, đừng dùng `400` và `422` ngẫu nhiên. Chọn policy, document và test nhất quán.

Trong bài này:

- Binding/Bean Validation: `400`.
- Resource state conflict: `409`.
- Not found: `404`.
- Lỗi ngoài dự kiến: `500`.

### 3.5. Lỗi authentication/authorization

- `401 Unauthorized`: chưa xác thực hoặc credential không hợp lệ.
- `403 Forbidden`: đã xác thực nhưng không có quyền.

Phần xử lý đầy đủ sẽ học ở module Spring Security. Không đổi `403` thành `404` một cách tùy tiện; nếu dùng resource hiding, phải là security policy có chủ đích.

### 3.6. Lỗi hệ thống

Ví dụ:

- Bug/null pointer.
- Database hoặc dependency ngoài lỗi bất ngờ.
- Serialization response thất bại.

Trả `500 Internal Server Error` với detail chung. Log server giữ context kỹ thuật, response client không chứa stack trace hay tên class nội bộ.

---

## 4. Bảng status code thực hành

| Tình huống | Status | Ghi chú |
|---|---:|---|
| Request hợp lệ, lấy dữ liệu thành công | `200` | Có response body |
| Tạo resource thành công | `201` | Nên có `Location` |
| Xóa thành công | `204` | Không có body |
| JSON/parameter/validation sai | `400` | Client sửa request |
| Chưa xác thực | `401` | Thường có `WWW-Authenticate` tùy cơ chế |
| Không có quyền | `403` | Không retry cùng quyền hiện tại |
| Resource không tồn tại | `404` | ID đúng format nhưng không có resource |
| HTTP method không hỗ trợ | `405` | Nên có `Allow` header |
| Xung đột trạng thái/unique | `409` | Có thể retry sau khi đọc state mới |
| Content type không hỗ trợ | `415` | Ví dụ gửi XML vào endpoint chỉ nhận JSON |
| Semantic input không xử lý được | `422` | Chỉ dùng nếu API có policy rõ |
| Rate limit | `429` | Có thể có `Retry-After` |
| Lỗi ngoài dự kiến | `500` | Không lộ nguyên nhân nội bộ |
| Dependency ngoài không sẵn sàng | `503` | Có thể có `Retry-After` |

HTTP status là protocol contract, không phải chỉ là một field trong JSON. Status trong body phải khớp status thực tế của HTTP response.

---

## 5. RFC 9457 Problem Details

RFC 9457 định nghĩa format chung cho lỗi HTTP với media type:

```http
Content-Type: application/problem+json
```

Ví dụ:

```json
{
  "type": "https://api.example.com/problems/task-not-found",
  "title": "Task not found",
  "status": 404,
  "detail": "Task 47fc86b7-9de7-4a39-bdc0-d732576f0ed6 does not exist.",
  "instance": "/api/tasks/47fc86b7-9de7-4a39-bdc0-d732576f0ed6",
  "timestamp": "2026-09-25T08:00:00Z",
  "requestId": "be26c094-69e1-4d54-8bc8-295ca647c61e"
}
```

### 5.1. Năm member chuẩn

#### `type`

URI reference xác định **loại vấn đề** và là identifier chính để client phân nhánh. Nếu dùng HTTP(S) URI, URI nên dẫn tới tài liệu cho problem type.

Không tạo `type` chứa task ID hoặc request ID:

```text
Đúng:  /problems/task-not-found
Sai:   /problems/task-not-found/47fc86b7-...
```

Nếu không có `type`, giá trị mặc định được hiểu là `about:blank`.

#### `title`

Tóm tắt ngắn, dễ đọc và ổn định cho problem type. Không nên thay đổi theo từng occurrence, ngoại trừ localization.

#### `status`

HTTP status dạng số. Trong Problem Details nó mang tính thông tin; status line thực tế vẫn là nguồn quyết định cho HTTP software. Hai giá trị phải khớp.

#### `detail`

Giải thích cho occurrence cụ thể, giúp client sửa vấn đề. Client không nên parse chuỗi `detail` để ra quyết định; hãy dùng `type`, status và extension có cấu trúc.

#### `instance`

URI reference nhận diện occurrence cụ thể, thường là request path. Nó không nhất thiết là URL có thể truy cập để lấy thêm dữ liệu.

### 5.2. Extension members

RFC cho phép thêm field:

```text
timestamp
requestId
errors
```

Client phải bỏ qua extension không biết. Extension nên có schema ổn định và không chứa dữ liệu nhạy cảm.

### 5.3. Validation problem

```json
{
  "type": "https://api.example.com/problems/validation-error",
  "title": "Request validation failed",
  "status": 400,
  "detail": "One or more request values are invalid.",
  "instance": "/api/tasks",
  "timestamp": "2026-09-25T08:00:00Z",
  "requestId": "be26c094-69e1-4d54-8bc8-295ca647c61e",
  "errors": [
    {
      "field": "title",
      "code": "NotBlank",
      "message": "title must not be blank"
    }
  ]
}
```

Không trả rejected value mặc định. Field có thể chứa password, token hoặc dữ liệu cá nhân.

---

## 6. `ProblemDetail` trong Spring Framework

Spring cung cấp:

- `ProblemDetail`: representation của RFC 9457.
- `ErrorResponse`: contract gồm status, headers và ProblemDetail body.
- `ErrorResponseException`: base exception tiện dụng implement `ErrorResponse`.
- `ResponseEntityExceptionHandler`: base class xử lý các exception chuẩn của Spring MVC.

Tạo ProblemDetail:

```java
ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.NOT_FOUND,
        "Task does not exist."
);

problem.setType(URI.create(
        "https://api.example.com/problems/task-not-found"
));
problem.setTitle("Task not found");
problem.setInstance(URI.create("/api/tasks/123"));
problem.setProperty("requestId", requestId);
```

Khi `ProblemDetail` được render, Spring ưu tiên `application/problem+json` cho JSON content negotiation. Status trong `ProblemDetail` quyết định HTTP status khi handler trả object này.

---

## 7. Custom exception không phụ thuộc HTTP

Application/domain layer không nên biết `HttpStatus` hoặc `ResponseEntity`.

### 7.1. Not found

```java
package com.example.taskapi.task;

import java.util.UUID;

public final class TaskNotFoundException extends RuntimeException {
    private final UUID taskId;

    public TaskNotFoundException(UUID taskId) {
        super("Task not found: " + taskId);
        this.taskId = taskId;
    }

    public UUID taskId() {
        return taskId;
    }
}
```

### 7.2. Conflict

```java
package com.example.taskapi.task;

import java.util.UUID;

public final class InvalidTaskTransitionException
        extends RuntimeException {
    private final UUID taskId;
    private final TaskStatus from;
    private final TaskStatus to;

    public InvalidTaskTransitionException(
            UUID taskId,
            TaskStatus from,
            TaskStatus to
    ) {
        super("Invalid task status transition");
        this.taskId = taskId;
        this.from = from;
        this.to = to;
    }

    public UUID taskId() {
        return taskId;
    }

    public TaskStatus from() {
        return from;
    }

    public TaskStatus to() {
        return to;
    }
}
```

Handler quyết định exception nào ánh xạ sang HTTP status nào. Sau này cùng use case có thể được gọi từ message consumer mà không kéo HTTP abstraction vào domain.

### 7.3. Service fail fast

```java
public TaskEntity findById(UUID id) {
    return taskRepository.findById(id)
            .orElseThrow(() -> new TaskNotFoundException(id));
}
```

Controller không cần lặp `Optional` mapping:

```java
@GetMapping("/{id}")
public TaskResponse findById(@PathVariable UUID id) {
    return mapper.toResponse(taskService.findById(id));
}
```

---

## 8. Request ID và correlation

Request ID giúp nối ba thứ:

```text
client error response ↔ application log ↔ downstream call/log
```

Nó không phải authentication token, không chứng minh danh tính và không được dùng để authorize.

### 8.1. Filter tạo request ID

```java
package com.example.taskapi.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class RequestIdFilter extends OncePerRequestFilter {
    public static final String HEADER_NAME = "X-Request-ID";
    public static final String ATTRIBUTE_NAME =
            RequestIdFilter.class.getName() + ".requestId";
    private static final Pattern SAFE_REQUEST_ID =
            Pattern.compile("[A-Za-z0-9._-]{1,64}");

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String requestId = resolveRequestId(request.getHeader(HEADER_NAME));

        request.setAttribute(ATTRIBUTE_NAME, requestId);
        response.setHeader(HEADER_NAME, requestId);
        MDC.put("requestId", requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
        }
    }

    private String resolveRequestId(String candidate) {
        if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }

        return UUID.randomUUID().toString();
    }
}
```

### 8.2. Vì sao phải validate header?

Không đưa header tùy ý của client thẳng vào log:

- Chuỗi quá dài có thể gây log amplification.
- Ký tự xuống dòng có thể gây log injection.
- Dữ liệu lạ làm hỏng format structured log.

Request ID do client gửi vẫn không phải trusted identity. Nếu hệ thống cần trace chống spoofing, gateway/server nên sinh trace ID riêng và chỉ lưu client ID như metadata.

### 8.3. Logging pattern

Local pattern ví dụ:

```yaml
logging:
  pattern:
    console: "%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level requestId=%X{requestId:-} %logger{36} - %msg%n"
```

Production structured logging nên do logging stack của hệ thống quy định.

---

## 9. Model cho field validation errors

```java
package com.example.taskapi.web.error;

public record ApiViolation(
        String field,
        String code,
        String message
) {
}
```

- `field`: path hoặc parameter name.
- `code`: machine-friendly constraint code như `NotBlank`, `Max`.
- `message`: human-readable message.

Client không nên dùng `message` làm machine identifier vì message có thể được localize.

---

## 10. Problem type registry

Không rải URI string khắp code:

```java
package com.example.taskapi.web.error;

import java.net.URI;

public final class ProblemTypes {
    private static final String BASE =
            "https://api.example.com/problems/";

    public static final URI VALIDATION_ERROR =
            URI.create(BASE + "validation-error");
    public static final URI MALFORMED_JSON =
            URI.create(BASE + "malformed-json");
    public static final URI INVALID_PARAMETER =
            URI.create(BASE + "invalid-parameter");
    public static final URI TASK_NOT_FOUND =
            URI.create(BASE + "task-not-found");
    public static final URI TASK_STATE_CONFLICT =
            URI.create(BASE + "task-state-conflict");
    public static final URI INTERNAL_ERROR =
            URI.create(BASE + "internal-error");

    private ProblemTypes() {
    }
}
```

Thay `api.example.com` bằng domain thật và publish tài liệu cho từng problem type. Type URI là API contract, không đổi tùy tiện theo wording của exception.

---

## 11. Global exception handler

### 11.1. Khung chính

```java
package com.example.taskapi.web.error;

import com.example.taskapi.task.InvalidTaskTransitionException;
import com.example.taskapi.task.TaskNotFoundException;
import com.example.taskapi.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@RestControllerAdvice
public class GlobalExceptionHandler
        extends ResponseEntityExceptionHandler {
    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    // Các handler nằm ở phần tiếp theo.
}
```

`ResponseEntityExceptionHandler` đã biết nhiều exception Spring MVC và là base phù hợp để chuẩn hóa chúng thành RFC 9457 body.

Khai báo `Clock` bean để timestamp test được:

```java
@Configuration
public class TimeConfiguration {

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }
}
```

### 11.2. Helper tạo ProblemDetail

Thêm vào `GlobalExceptionHandler`:

```java
private ProblemDetail problem(
        HttpStatus status,
        URI type,
        String title,
        String detail,
        HttpServletRequest request
) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setType(type);
    problem.setTitle(title);
    problem.setInstance(URI.create(request.getRequestURI()));
    problem.setProperty("timestamp", clock.instant());
    problem.setProperty("requestId", requestId(request));
    return problem;
}

private String requestId(HttpServletRequest request) {
    Object requestId = request.getAttribute(
            RequestIdFilter.ATTRIBUTE_NAME
    );
    return Objects.toString(requestId, "unavailable");
}

private HttpServletRequest servletRequest(WebRequest request) {
    return ((ServletWebRequest) request).getRequest();
}
```

Trong Spring MVC servlet application, `WebRequest` ở đây là `ServletWebRequest`. Nếu library handler cần tái sử dụng ngoài servlet stack, không cast cứng như application-specific code trên.

---

## 12. Xử lý custom exceptions

Thêm vào `GlobalExceptionHandler`:

```java
@ExceptionHandler(TaskNotFoundException.class)
public ProblemDetail handleTaskNotFound(
        TaskNotFoundException exception,
        HttpServletRequest request
) {
    return problem(
            HttpStatus.NOT_FOUND,
            ProblemTypes.TASK_NOT_FOUND,
            "Task not found",
            "Task " + exception.taskId() + " does not exist.",
            request
    );
}

@ExceptionHandler(InvalidTaskTransitionException.class)
public ProblemDetail handleInvalidTransition(
        InvalidTaskTransitionException exception,
        HttpServletRequest request
) {
    ProblemDetail problem = problem(
            HttpStatus.CONFLICT,
            ProblemTypes.TASK_STATE_CONFLICT,
            "Task state conflict",
            "The requested status transition is not allowed.",
            request
    );
    problem.setProperty("taskId", exception.taskId());
    problem.setProperty("from", exception.from());
    problem.setProperty("to", exception.to());
    return problem;
}
```

Không nhất thiết log mọi `404` với stack trace. Đây là outcome dự kiến. Metrics có thể đếm theo problem type; log `DEBUG` hoặc `INFO` tùy nhu cầu vận hành.

Không trả `exception.getMessage()` tự động. Exception message thường viết cho developer và có thể chứa implementation detail.

---

## 13. Xử lý validation của request body

`@Valid @RequestBody` thất bại thường tạo `MethodArgumentNotValidException`.

Override trong `GlobalExceptionHandler`:

```java
@Override
protected ResponseEntity<Object> handleMethodArgumentNotValid(
        MethodArgumentNotValidException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
) {
    List<ApiViolation> errors = Stream.concat(
            exception.getBindingResult()
                    .getFieldErrors()
                    .stream()
                    .map(this::toViolation),
            exception.getBindingResult()
                    .getGlobalErrors()
                    .stream()
                    .map(this::toViolation)
    ).toList();

    ProblemDetail problem = problem(
            HttpStatus.BAD_REQUEST,
            ProblemTypes.VALIDATION_ERROR,
            "Request validation failed",
            "One or more request fields are invalid.",
            servletRequest(request)
    );
    problem.setProperty("errors", errors);

    return handleExceptionInternal(
            exception,
            problem,
            headers,
            HttpStatus.BAD_REQUEST,
            request
    );
}

private ApiViolation toViolation(FieldError error) {
    return new ApiViolation(
            error.getField(),
            Objects.requireNonNullElse(error.getCode(), "Invalid"),
            Objects.requireNonNullElse(
                    error.getDefaultMessage(),
                    "Invalid value"
            )
    );
}

private ApiViolation toViolation(ObjectError error) {
    return new ApiViolation(
            "$",
            Objects.requireNonNullElse(error.getCode(), "Invalid"),
            Objects.requireNonNullElse(
                    error.getDefaultMessage(),
                    "Invalid request"
            )
    );
}
```

### Object-level errors

Custom class-level validator tạo `ObjectError`, không xuất hiện trong `getFieldErrors()`. Implementation trên dùng `Stream.concat` để tạo list mới gồm cả field errors và global errors. Ký hiệu `"$"` biểu diễn lỗi ở cấp toàn request thay vì một field cụ thể.

Không mutate list trả về từ framework.

---

## 14. Xử lý validation của query/path/header

Constraint đặt trực tiếp lên controller method parameter có thể tạo `HandlerMethodValidationException`:

```java
@RequestParam
@Min(0)
int page
```

Override:

```java
@Override
protected ResponseEntity<Object> handleHandlerMethodValidationException(
        HandlerMethodValidationException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
) {
    if (exception.isForReturnValue()) {
        HttpServletRequest servletRequest = servletRequest(request);
        log.error(
                "Controller return value validation failed requestId={} method={} path={}",
                requestId(servletRequest),
                servletRequest.getMethod(),
                servletRequest.getRequestURI(),
                exception
        );

        ProblemDetail problem = problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ProblemTypes.INTERNAL_ERROR,
                "Internal server error",
                "An unexpected error occurred. Contact support with the request ID.",
                servletRequest
        );

        return handleExceptionInternal(
                exception,
                problem,
                headers,
                HttpStatus.INTERNAL_SERVER_ERROR,
                request
        );
    }

    List<ApiViolation> errors = exception
            .getParameterValidationResults()
            .stream()
            .flatMap(result -> result.getResolvableErrors()
                    .stream()
                    .map(error -> new ApiViolation(
                            parameterName(result),
                            violationCode(error),
                            Objects.requireNonNullElse(
                                    error.getDefaultMessage(),
                                    "Invalid value"
                            )
                    )))
            .toList();

    ProblemDetail problem = problem(
            HttpStatus.BAD_REQUEST,
            ProblemTypes.VALIDATION_ERROR,
            "Request validation failed",
            "One or more request parameters are invalid.",
            servletRequest(request)
    );
    problem.setProperty("errors", errors);

    return handleExceptionInternal(
            exception,
            problem,
            headers,
            HttpStatus.BAD_REQUEST,
            request
    );
}

private String parameterName(ParameterValidationResult result) {
    return Objects.requireNonNullElse(
            result.getMethodParameter().getParameterName(),
            "parameter"
    );
}

private String violationCode(MessageSourceResolvable error) {
    String[] codes = error.getCodes();
    if (codes == null || codes.length == 0) {
        return "Invalid";
    }

    return codes[codes.length - 1];
}
```

Giữ compiler parameter metadata giúp tên như `page`/`size` có sẵn. Spring Boot Maven setup thông thường hỗ trợ, nhưng tests vẫn nên xác nhận error contract thực tế.

Ứng dụng có request body validation và parameter validation cần xử lý **cả hai exception**, không chỉ một.

`HandlerMethodValidationException` còn có thể biểu diễn validation thất bại trên **return value**. Đó là lỗi phía server và Spring dùng `500`; handler trên tách riêng nhánh này, log để điều tra và không trả field details nội bộ cho client.

---

## 15. JSON lỗi cú pháp hoặc sai kiểu

JSON malformed, enum sai hoặc field không convert được có thể tạo `HttpMessageNotReadableException`.

Thêm import:

```java
import org.springframework.http.converter.HttpMessageNotReadableException;
```

Override:

```java
@Override
protected ResponseEntity<Object> handleHttpMessageNotReadable(
        HttpMessageNotReadableException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
) {
    ProblemDetail problem = problem(
            HttpStatus.BAD_REQUEST,
            ProblemTypes.MALFORMED_JSON,
            "Malformed JSON request",
            "The request body is missing, malformed, or contains an invalid value.",
            servletRequest(request)
    );

    return handleExceptionInternal(
            exception,
            problem,
            headers,
            HttpStatus.BAD_REQUEST,
            request
    );
}
```

Không trả Jackson parser message/cause trực tiếp. Nó có thể chứa payload fragment, class name hoặc chi tiết implementation.

Nếu client cần biết chính xác field JSON sai, xây mapping an toàn có kiểm thử; không serialize toàn bộ exception chain.

---

## 16. Sai kiểu path/query parameter

Ví dụ:

```http
GET /api/tasks/not-a-uuid
GET /api/tasks?status=UNKNOWN
```

Override `handleTypeMismatch`:

```java
import org.springframework.beans.TypeMismatchException;

@Override
protected ResponseEntity<Object> handleTypeMismatch(
        TypeMismatchException exception,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
) {
    String parameter = exception instanceof MethodArgumentTypeMismatchException mismatch
            ? mismatch.getName()
            : "parameter";

    ProblemDetail problem = problem(
            HttpStatus.BAD_REQUEST,
            ProblemTypes.INVALID_PARAMETER,
            "Invalid request parameter",
            "Parameter '" + parameter + "' has an invalid value.",
            servletRequest(request)
    );
    problem.setProperty(
            "errors",
            List.of(new ApiViolation(
                    parameter,
                    "TypeMismatch",
                    "Value cannot be converted to the required type"
            ))
    );

    return handleExceptionInternal(
            exception,
            problem,
            headers,
            HttpStatus.BAD_REQUEST,
            request
    );
}
```

Không echo raw invalid value nếu parameter có thể nhạy cảm hoặc rất dài.

---

## 17. Fallback cho lỗi ngoài dự kiến

```java
@ExceptionHandler(Exception.class)
public ProblemDetail handleUnexpectedException(
        Exception exception,
        HttpServletRequest request
) {
    String requestId = requestId(request);

    log.error(
            "Unexpected request failure requestId={} method={} path={}",
            requestId,
            request.getMethod(),
            request.getRequestURI(),
            exception
    );

    return problem(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ProblemTypes.INTERNAL_ERROR,
            "Internal server error",
            "An unexpected error occurred. Contact support with the request ID.",
            request
    );
}
```

Response không chứa:

- `exception.getMessage()`.
- Stack trace.
- SQL.
- Hostname/path nội bộ.
- Tên class/package.
- Credential hoặc request body.

Catch-all handler là hàng rào cuối, không phải cách thay thế handling cụ thể. Metrics/alert phải giúp đội ngũ biết `500` đang xảy ra.

---

## 18. Chuẩn hóa các exception MVC còn lại

`ResponseEntityExceptionHandler` còn xử lý nhiều lỗi như:

- HTTP method không hỗ trợ.
- Media type không hỗ trợ/không acceptable.
- Thiếu request parameter.
- Không tìm thấy static resource/handler theo cấu hình.
- Async timeout.
- Upload vượt giới hạn.
- Response serialization failure.

Base class tạo `ProblemDetail`, nhưng để mọi response có `timestamp`, `requestId` và `instance`, có thể override điểm tạo response:

```java
@Override
protected ResponseEntity<Object> createResponseEntity(
        Object body,
        HttpHeaders headers,
        HttpStatusCode status,
        WebRequest request
) {
    if (body instanceof ProblemDetail problem) {
        HttpServletRequest servletRequest = servletRequest(request);

        if (problem.getInstance() == null) {
            problem.setInstance(URI.create(
                    servletRequest.getRequestURI()
            ));
        }

        problem.setProperty("timestamp", clock.instant());
        problem.setProperty(
                "requestId",
                requestId(servletRequest)
        );
    }

    return super.createResponseEntity(
            body,
            headers,
            status,
            request
    );
}
```

Custom handler trong bài đã thêm extension từ helper `problem()`. Gán cùng key cùng giá trị ở `createResponseEntity` là idempotent; có thể refactor thành một đường enrich duy nhất khi hoàn thiện code.

Lưu ý: chữ ký nullability có thể được IDE hiển thị với annotation của Spring. Không thay đổi contract override chỉ để bỏ warning; dùng đúng chữ ký theo version dependency của project.

---

## 19. Error handling không chỉ nằm trong controller advice

`@RestControllerAdvice` xử lý exception trong luồng Spring MVC controller. Nó không tự động bao phủ mọi nơi:

```text
Servlet Filter trước DispatcherServlet
Spring Security filter chain
Servlet container error
Async/background thread
Scheduled job
Message consumer
Application startup
```

Ví dụ Spring Security dùng:

- `AuthenticationEntryPoint` cho `401`.
- `AccessDeniedHandler` cho `403`.

Hai component đó cần tạo cùng error contract nếu API yêu cầu nhất quán.

Background job không có HTTP response; nó cần retry/dead-letter/metrics/logging phù hợp thay vì `ProblemDetail`.

---

## 20. Logging lỗi an toàn

### 20.1. Log một lần, đúng layer

Không log cùng exception ở repository, service, controller và advice. Điều này tạo bốn stack trace cho một failure.

Layer biến exception thành outcome cuối cùng thường là nơi log với context phù hợp.

### 20.2. Level gợi ý

| Lỗi | Level thường dùng |
|---|---|
| Validation `400` | Không log hoặc `DEBUG` |
| Not found `404` | Không log/`DEBUG`, tùy nghiệp vụ |
| Conflict `409` | `INFO` hoặc metrics nếu đáng chú ý |
| Dấu hiệu abuse/security | `WARN` với rate limiting/redaction |
| Unexpected `500` | `ERROR` kèm stack trace và request ID |

Không biến mọi lỗi client thành `ERROR`; alert sẽ mất tín hiệu.

### 20.3. Context nên log

- Request ID/trace ID.
- HTTP method.
- Normalized route hoặc path an toàn.
- Problem type.
- User/account ID đã được policy cho phép và không phải secret.
- Dependency operation và timeout.

### 20.4. Không log

- Authorization header.
- Password.
- Access/refresh token.
- API key.
- Cookie/session secret.
- Toàn bộ request body mặc định.
- Query string chứa dữ liệu nhạy cảm.
- Rejected value chưa redact.

---

## 21. Thiết kế detail và message

Detail nên giúp client sửa vấn đề:

```text
Tốt:  "Parameter 'size' must be between 1 and 100."
Kém:  "ConstraintViolationException occurred."
Nguy hiểm: "SQLState 23505 on table internal_task_v2..."
```

Nhưng client không nên parse detail. Dữ liệu machine-readable nằm ở:

```text
type
status
errors[].field
errors[].code
```

Nếu hỗ trợ localization:

- `title`/`detail`/`message` có thể đổi ngôn ngữ.
- `type` và `code` phải ổn định.
- Response cần `Content-Language` khi phù hợp.

---

## 22. API versioning

Version API khi có thay đổi breaking, không phải mỗi lần release code.

### 22.1. URI versioning

```text
/api/v1/tasks
/api/v2/tasks
```

Ưu điểm:

- Dễ nhìn, route và cache rõ.
- Client/debug dễ dùng.

Nhược điểm:

- Version đi vào URL.
- Dễ duplicate controller nếu không tách application logic.

### 22.2. Header/media type versioning

Ví dụ custom media type:

```http
Accept: application/vnd.example.task-v2+json
```

URL sạch hơn nhưng khó thử bằng browser/curl, routing và documentation phức tạp hơn.

### 22.3. Query parameter versioning

```text
/api/tasks?version=2
```

Đơn giản nhưng thường kém rõ về cache, routing và resource identity; ít được ưu tiên cho public API.

### 22.4. Khuyến nghị cho project học tập

Chọn URI major version:

```java
@RequestMapping("/api/v1/tasks")
```

Sau đó:

- Không tạo `v2` cho thay đổi additive tương thích.
- Document deprecation.
- Đặt thời hạn migration.
- Đo client usage trước khi xóa version cũ.
- Giữ problem type ổn định hoặc version nó khi semantics thực sự breaking.

Nếu project nội bộ nhỏ chưa có client, `/api/tasks` vẫn hợp lệ. Điều quan trọng là policy có chủ đích và nhất quán.

---

## 23. Thiết kế success và error response

Error response dùng RFC 9457 làm top-level document, không bọc vào một lớp `{"success":false,"error":...}` khác vì sẽ làm mất khả năng dùng chuẩn trực tiếp.

Success response có thể:

- Trả resource trực tiếp cho endpoint đơn.
- Trả object gồm `items` và pagination metadata cho collection.
- Dùng envelope `data/meta` nếu toàn tổ chức đã có standard.

Ví dụ collection:

```json
{
  "items": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0
}
```

Đừng bọc response chỉ để thêm `success: true`; HTTP status đã biểu diễn thành công/thất bại. Nếu tổ chức yêu cầu envelope, áp dụng nhất quán và document cách RFC 9457 được tích hợp.

---

## 24. Không để lộ lỗi mặc định ngoài ý muốn

Spring Boot có error endpoint/fallback cho lỗi chưa được advice xử lý. Cấu hình production không nên include stack trace/message nội bộ:

```yaml
server:
  error:
    include-message: never
    include-binding-errors: never
    include-stacktrace: never
    include-exception: false
```

Đây là defense-in-depth, không thay thế `@RestControllerAdvice` và tests.

Không bật stack trace qua query parameter trong production.

---

## 25. Kiểm thử ProblemDetail bằng MockMvc

### 25.1. Setup

```java
class TaskErrorHandlingTest {
    private static final Instant FIXED_TIME =
            Instant.parse("2026-09-25T08:00:00Z");

    private TaskService taskService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        taskService = mock(TaskService.class);
        Clock clock = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);
        GlobalExceptionHandler advice =
                new GlobalExceptionHandler(clock);

        TaskController controller = new TaskController(
                taskService,
                new TaskDtoMapper()
        );

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(advice)
                .addFilters(new RequestIdFilter())
                .build();
    }
}
```

Imports chính:

```java
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
```

### 25.2. Test not found

```java
@Test
void returnsProblemDetailWhenTaskDoesNotExist() throws Exception {
    UUID id = UUID.fromString(
            "47fc86b7-9de7-4a39-bdc0-d732576f0ed6"
    );
    when(taskService.findById(id))
            .thenThrow(new TaskNotFoundException(id));

    mockMvc.perform(get("/api/tasks/{id}", id)
                    .header("X-Request-ID", "test-request-123"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
            ))
            .andExpect(jsonPath("$.type").value(
                    "https://api.example.com/problems/task-not-found"
            ))
            .andExpect(jsonPath("$.title").value("Task not found"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.instance").value(
                    "/api/tasks/" + id
            ))
            .andExpect(jsonPath("$.requestId").value(
                    "test-request-123"
            ))
            .andExpect(header().string(
                    "X-Request-ID",
                    "test-request-123"
            ));
}
```

Nếu tuần 6 đã đổi route thành `/api/v1/tasks`, cập nhật test và controller cùng lúc; không để docs/test dùng hai route ngầm khác nhau.

### 25.3. Test field validation

```java
@Test
void returnsFieldErrorsForInvalidCreateRequest() throws Exception {
    mockMvc.perform(post("/api/tasks")
                    .header("X-Request-ID", "validation-123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {
                              "title": "   ",
                              "assigneeEmail": "not-an-email"
                            }
                            """))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentTypeCompatibleWith(
                    MediaType.APPLICATION_PROBLEM_JSON
            ))
            .andExpect(jsonPath("$.type").value(
                    "https://api.example.com/problems/validation-error"
            ))
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors[?(@.field == 'title')]")
                    .exists())
            .andExpect(jsonPath(
                    "$.errors[?(@.field == 'assigneeEmail')]"
            ).exists());

    verifyNoInteractions(taskService);
}
```

Không assert thứ tự field errors nếu contract không cam kết order.

### 25.4. Test malformed JSON

```java
@Test
void returnsSafeProblemForMalformedJson() throws Exception {
    mockMvc.perform(post("/api/tasks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.type").value(
                    "https://api.example.com/problems/malformed-json"
            ))
            .andExpect(jsonPath("$.detail").value(
                    "The request body is missing, malformed, or contains an invalid value."
            ))
            .andExpect(jsonPath("$.trace").doesNotExist())
            .andExpect(jsonPath("$.exception").doesNotExist());
}
```

### 25.5. Integration tests cần thêm

- Query parameter method validation.
- UUID/enum type mismatch.
- `405 Method Not Allowed` và `Allow` header.
- `415 Unsupported Media Type`.
- Conflict `409`.
- Unexpected `500` không lộ message.
- Unknown route error contract.
- Security `401/403` sau khi thêm Spring Security.
- Request ID invalid/too long được thay bằng ID mới.

---

## 26. Contract tests và compatibility

Error format là public contract. Khi thay đổi:

- Không rename/remove field mà không đánh giá client.
- Giữ `type` URI ổn định.
- Có thể thêm extension mới vì client phải bỏ qua field không biết.
- Test status header và body cùng nhau.
- Publish example trong OpenAPI/API docs.
- Dùng consumer/contract test nếu nhiều client độc lập.

Không để error contract chỉ tồn tại trong Java handler; client cần tài liệu machine/human-readable.

---

## 27. Lỗi thường gặp

### Trả `200 OK` cho lỗi

```json
{"success":false,"error":"not found"}
```

Cache, proxy, monitoring và client HTTP không hiểu đây là lỗi. Dùng status đúng.

### Trả mọi lỗi thành `400`

Bug server, not found và conflict có semantics khác nhau. Mapping sai làm client retry/sửa request sai hướng.

### Trả exception message trực tiếp

Message có thể chứa SQL, URL nội bộ hoặc dữ liệu nhạy cảm. Tạo detail an toàn theo problem type.

### Chỉ xử lý `MethodArgumentNotValidException`

Constraint trên query/path có thể tạo `HandlerMethodValidationException`; kết quả là format không nhất quán nếu bỏ sót.

### Echo rejected value

Field lỗi có thể là password/token. Chỉ trả field, code và message an toàn.

### Catch exception trong từng controller

Code lặp, mapping khác nhau và dễ nuốt lỗi. Dùng advice tập trung.

### Log stack trace cho mọi 4xx

Tạo noise và tăng chi phí. Dùng metrics/problem type cho lỗi dự kiến; stack trace dành cho lỗi cần điều tra.

### Dùng request ID làm security identity

Request ID có thể do client đề xuất và không chứng minh danh tính. Authentication context mới dùng cho authorization/audit identity.

### Dùng timestamp làm identifier

Timestamp có thể trùng và không thay request ID. Nó chỉ cho biết thời điểm.

### Cho rằng advice bắt mọi exception

Filter, security, async job và startup có error mechanisms riêng.

---

## 28. Bài tập thực hành

### Bài 1 — Hoàn thiện problem types

Tạo và document:

```text
validation-error
malformed-json
invalid-parameter
task-not-found
task-state-conflict
internal-error
```

Mỗi type có title, status mặc định, khi nào dùng và client action.

### Bài 2 — Validation errors đầy đủ

Gộp field errors và global errors thành list bất biến. Viết tests cho:

- `@NotBlank`.
- `@Email`.
- Custom class-level validator.
- Page/size method validation.

### Bài 3 — Request ID

Test các trường hợp:

- Không có header: server tạo UUID.
- Header hợp lệ: response echo lại.
- Header chứa newline/ký tự lạ: bị thay.
- Header dài hơn 64: bị thay.
- MDC được cleanup sau request.

### Bài 4 — Safe 500

Cho service throw exception có message chứa chuỗi giả lập secret. Xác nhận:

- Response trả `500`.
- Body không có secret/message/stack trace.
- Log có request ID và stack trace.

Không dùng secret thật trong test.

### Bài 5 — Conflict

Cấm transition `DONE → TODO`. Service throw `InvalidTaskTransitionException`; API trả `409` cùng `from`/`to` extensions. Không để controller tự kiểm tra transition.

### Bài 6 — Error contract ngoài MVC

Sau khi học Security, làm cho `401`/`403` dùng cùng ProblemDetail fields. So sánh vì sao controller advice không xử lý trực tiếp lỗi ở security filter chain.

### Bài 7 — Versioning decision

Viết một ADR ngắn chọn URI versioning hoặc media type versioning. Nêu:

- Client hiện có.
- Loại breaking change.
- Deprecation window.
- Routing/documentation/testing cost.

Không tạo file ADR top-level nếu project chưa có thư mục/quy ước docs; đặt nội dung trong bài tập hoặc hỏi maintainer trước.

---

## 29. Câu hỏi tự kiểm tra

1. Vì sao error response cần contract thống nhất?
2. `400`, `404`, `409` và `500` khác nhau thế nào?
3. `401` khác `403` ra sao?
4. Năm member chuẩn của RFC 9457 là gì?
5. `type` khác `instance` thế nào?
6. Vì sao client không nên parse `detail`?
7. Extension member dùng để làm gì?
8. `ProblemDetail` và `ErrorResponse` khác nhau ở mức khái niệm nào?
9. Vì sao domain exception không nên chứa `HttpStatus`?
10. `@RestControllerAdvice` giải quyết vấn đề gì?
11. Vì sao nên extend `ResponseEntityExceptionHandler`?
12. `MethodArgumentNotValidException` khác `HandlerMethodValidationException` ra sao?
13. Vì sao không trả rejected value mặc định?
14. JSON malformed được xử lý khác Bean Validation thế nào?
15. Request ID giúp gì và không được dùng cho việc gì?
16. Vì sao phải cleanup MDC?
17. Khi nào log stack trace?
18. Advice có xử lý lỗi Spring Security filter không?
19. Khi nào nên tạo API version mới?
20. Vì sao error contract cần contract test?

---

## 30. Tiêu chí hoàn thành tuần 6

- [ ] Có custom exceptions cho not-found và conflict.
- [ ] Domain/application exception không phụ thuộc HTTP class.
- [ ] Có một `@RestControllerAdvice` trung tâm.
- [ ] Error body dùng RFC 9457 `ProblemDetail`.
- [ ] HTTP status thực tế khớp `status` trong body.
- [ ] `type`, `title`, `detail` và `instance` có semantics đúng.
- [ ] Có `timestamp` và `requestId` extensions.
- [ ] Validation body trả errors theo field.
- [ ] Validation query/path có cùng format.
- [ ] Custom class-level errors không bị bỏ sót.
- [ ] JSON malformed và type mismatch trả detail an toàn.
- [ ] Unexpected exception trả `500` không lộ nội bộ.
- [ ] Không echo password/token/rejected value.
- [ ] Request ID được validate và cleanup khỏi MDC.
- [ ] Expected 4xx không tạo log noise/stack trace không cần thiết.
- [ ] Security/filter errors được nhận diện là boundary riêng.
- [ ] API versioning policy được chọn và document.
- [ ] MockMvc tests kiểm tra status, content type, body và headers.
- [ ] Critical error flows đạt tối thiểu 80% test coverage.

---

## 31. Tóm tắt

Luồng xử lý lỗi cần ghi nhớ:

```text
invalid input / domain conflict / unexpected failure
                    ↓
                 exception
                    ↓
      GlobalExceptionHandler maps safely
                    ↓
HTTP status + application/problem+json + requestId
```

Các nguyên tắc cốt lõi:

- Status code mang semantics protocol; đừng trả mọi thứ bằng `200` hoặc `400`.
- Dùng RFC 9457 thay vì tự phát minh nhiều error format.
- `type` và structured extensions phục vụ máy; `detail` phục vụ người đọc.
- Không cho domain exception phụ thuộc HTTP.
- Xử lý cả body validation và method parameter validation.
- Không trả exception message, stack trace hoặc rejected value nhạy cảm.
- Request ID nối response với log nhưng không phải security identity.
- Log lỗi một lần, đúng level, với context đã redact.
- Controller advice không bao phủ filter/security/background/startup.
- Error contract cần documentation và tests như success contract.

## Tài liệu chính thức

- [RFC 9457 — Problem Details for HTTP APIs](https://www.rfc-editor.org/rfc/rfc9457.html)
- [Spring MVC Error Responses](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html)
- [`@ExceptionHandler`](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-exceptionhandler.html)
- [Spring MVC Validation](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-validation.html)
- [`ProblemDetail` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/http/ProblemDetail.html)
- [`ResponseEntityExceptionHandler` Javadoc](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/ResponseEntityExceptionHandler.html)
