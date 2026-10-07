# Tuần 3: Spring MVC & RESTful API

> Tài liệu tổng hợp kiến thức mục 3 trong lộ trình [spring-boot-roadmap.md](spring-boot-roadmap.md). Nội dung đã được đối chiếu với Spring Framework Reference (6.2) qua Context7 để đảm bảo chính xác về cơ chế xử lý validation exception và request mapping.

---

## 1. Controller & Request Mapping

### 1.1 `@RestController` = `@Controller` + `@ResponseBody`
- `@Controller`: đánh dấu class là nơi xử lý HTTP request, giá trị method trả về mặc định được hiểu là **tên view** (dùng cho ứng dụng trả HTML truyền thống).
- `@ResponseBody`: khiến giá trị trả về được ghi thẳng vào response body (qua `HttpMessageConverter`, thường là JSON) thay vì coi là tên view.
- `@RestController` gộp cả 2 lại — mặc định cho mọi method trong class đều trả JSON/XML thẳng ra response, đúng bản chất REST API.

### 1.2 Các annotation mapping request
```java
@RestController
@RequestMapping("/owners/{ownerId}") // base path chung cho cả class
public class PetController {

    @GetMapping("/pets/{petId}")     // = @RequestMapping(method = RequestMethod.GET, ...)
    public Pet findPet(@PathVariable Long ownerId, @PathVariable Long petId) {
        // ownerId, petId lấy từ URL path
        return petService.find(ownerId, petId);
    }

    @PostMapping("/pets")
    public Pet createPet(@PathVariable Long ownerId, @RequestBody PetRequest request) {
        // request body JSON được Jackson tự động convert thành PetRequest
        return petService.create(ownerId, request);
    }

    @GetMapping("/pets")
    public List<Pet> searchPets(@RequestParam("status") String status, // query param ?status=...
                                 @RequestHeader("X-Client-Id") String clientId) { // đọc HTTP header
        return petService.findByStatus(ownerId, status);
    }

    @PutMapping("/pets/{petId}")
    public Pet updatePet(@PathVariable Long petId, @RequestBody PetRequest request) { ... }

    @PatchMapping("/pets/{petId}")
    public Pet partialUpdate(@PathVariable Long petId, @RequestBody Map<String, Object> patch) { ... }

    @DeleteMapping("/pets/{petId}")
    public void deletePet(@PathVariable Long petId) { ... }
}
```
| Annotation | Ý nghĩa |
|---|---|
| `@RequestMapping` | Annotation gốc, có thể chỉ định `method =`, dùng ở cấp class để khai báo base path dùng chung |
| `@GetMapping`/`@PostMapping`/`@PutMapping`/`@PatchMapping`/`@DeleteMapping` | Rút gọn của `@RequestMapping` cho từng HTTP method cụ thể — nên dùng thay vì `@RequestMapping` trần để code rõ ràng hơn |
| `@RequestBody` | Bind **toàn bộ request body** (thường JSON) vào 1 object |
| `@RequestParam` | Bind **query parameter** (`?key=value`) hoặc form field vào tham số method |
| `@PathVariable` | Bind giá trị nằm trong **URL path** (`/pets/{petId}`) vào tham số method |
| `@RequestHeader` | Bind giá trị 1 HTTP header vào tham số method |

### 1.3 `ResponseEntity<T>` — kiểm soát tường minh status code, header, body
```java
@GetMapping("/{id}")
public ResponseEntity<Pet> getPet(@PathVariable Long id) {
    Pet pet = petService.find(id);
    if (pet == null) {
        return ResponseEntity.notFound().build(); // 404, không có body
    }
    return ResponseEntity.ok()
        .eTag(pet.version())
        .body(pet); // 200 + header ETag + body JSON
}

@PostMapping
public ResponseEntity<Pet> createPet(@RequestBody PetRequest request) {
    Pet created = petService.create(request);
    return ResponseEntity
        .created(URI.create("/pets/" + created.id())) // 201 Created + header Location
        .body(created);
}
```
Khác với việc chỉ trả thẳng `Pet` (Spring tự mặc định `200 OK`), `ResponseEntity<T>` cho phép chỉ định chính xác status code (`404`, `201`, `204`...), thêm header tùy ý — cần thiết khi API phải tuân thủ đúng REST convention.

---

## 2. Validation

### 2.1 Bean Validation (Jakarta Validation)
```java
public class PetRequest {
    @NotBlank(message = "Tên không được để trống")
    private String name;

    @NotNull
    @Min(0) @Max(30)
    private Integer age;

    @Email
    private String ownerEmail;

    @Pattern(regexp = "^(DOG|CAT|BIRD)$", message = "Loại thú cưng không hợp lệ")
    private String type;

    @Size(min = 1, max = 5)
    private List<String> tags;
}
```

### 2.2 `@Valid` kích hoạt validate tự động
```java
@PostMapping("/pets")
public ResponseEntity<Pet> createPet(@Valid @RequestBody PetRequest request) {
    // nếu request vi phạm bất kỳ constraint nào ở trên, Spring ném MethodArgumentNotValidException
    // TRƯỚC KHI method body này được chạy — không cần tự if/else kiểm tra
    return ResponseEntity.ok(petService.create(request));
}
```
> **Phân biệt 2 exception khi validate lỗi**: `MethodArgumentNotValidException` được ném khi 1 object đơn (VD `@RequestBody`/`@ModelAttribute` có `@Valid`) vi phạm constraint. Từ Spring Framework 6.1, khi validate áp dụng cho **nhiều tham số method riêng lẻ** (VD `@RequestParam`/`@PathVariable` có gắn constraint annotation trực tiếp, kết hợp `@Validated` ở cấp class), Spring ném `HandlerMethodValidationException` thay vì `MethodArgumentNotValidException` — 2 exception này xử lý gần giống nhau nhưng khác nhau ở việc `HandlerMethodValidationException` gom lỗi theo **danh sách nhiều tham số** thay vì 1 object duy nhất.

### 2.3 Custom Validator
```java
@Target({ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = UniqueEmailValidator.class)
public @interface UniqueEmail {
    String message() default "Email đã tồn tại";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

class UniqueEmailValidator implements ConstraintValidator<UniqueEmail, String> {
    private final UserRepository userRepository; // có thể inject dependency vào validator

    UniqueEmailValidator(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean isValid(String email, ConstraintValidatorContext context) {
        return email == null || !userRepository.existsByEmail(email);
    }
}
```

### 2.4 Validation nhóm (Groups) — cùng 1 DTO, khác rule cho create/update
```java
interface OnCreate {}
interface OnUpdate {}

public class UserRequest {
    @Null(groups = OnCreate.class)      // lúc tạo mới, id phải null (chưa có)
    @NotNull(groups = OnUpdate.class)   // lúc update, bắt buộc phải có id
    private Long id;

    @NotBlank(groups = {OnCreate.class, OnUpdate.class})
    private String name;
}

@PostMapping
public User create(@Validated(OnCreate.class) @RequestBody UserRequest req) { ... }

@PutMapping("/{id}")
public User update(@Validated(OnUpdate.class) @RequestBody UserRequest req) { ... }
```

---

## 3. Exception Handling

### 3.1 `@ControllerAdvice` + `@ExceptionHandler` — xử lý lỗi tập trung
```java
@RestControllerAdvice // = @ControllerAdvice + @ResponseBody, áp dụng cho MỌI @RestController trong ứng dụng
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .toList();
        return ResponseEntity.badRequest().body(new ApiError(400, "Validation failed", errors, Instant.now(), req.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleMalformedJson(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return ResponseEntity.badRequest().body(new ApiError(400, "Request body không đúng định dạng JSON", null, Instant.now(), req.getRequestURI()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(EntityNotFoundException ex, HttpServletRequest req) {
        return ResponseEntity.status(404).body(new ApiError(404, ex.getMessage(), null, Instant.now(), req.getRequestURI()));
    }

    @ExceptionHandler(Exception.class) // fallback cuối cùng cho lỗi không lường trước
    public ResponseEntity<ApiError> handleUnknown(Exception ex, HttpServletRequest req) {
        // KHÔNG trả ex.getMessage()/stack trace trực tiếp ra client ở production — chỉ log server-side
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(500).body(new ApiError(500, "Internal server error", null, Instant.now(), req.getRequestURI()));
    }
}
```

### 3.2 Thiết kế response lỗi chuẩn
```java
public record ApiError(
    int status,
    String message,
    List<String> errors, // null nếu không có chi tiết field-level
    Instant timestamp,
    String path
) {}
```
Giữ **nhất quán 1 cấu trúc** cho mọi lỗi trong toàn hệ thống — client chỉ cần parse 1 format duy nhất, không phải xử lý từng dạng response lỗi khác nhau tùy endpoint.

### 3.3 Phân biệt lỗi 4xx và 5xx
| Nhóm | Ý nghĩa | Ví dụ |
|---|---|---|
| **4xx** | Lỗi do **client** (request sai) | `400` validate fail/JSON sai, `401` chưa xác thực, `403` không đủ quyền, `404` không tìm thấy resource, `409` xung đột (VD trùng email) |
| **5xx** | Lỗi do **server** | `500` lỗi không lường trước, `503` service tạm thời không sẵn sàng |

**Nguyên tắc quan trọng**: không bao giờ để stack trace hoặc chi tiết exception nội bộ (tên class, câu SQL, đường dẫn file...) lọt ra response ở môi trường production — vừa lộ thông tin nhạy cảm về kiến trúc hệ thống, vừa không thân thiện với client. Chỉ log chi tiết ở server, trả về client 1 message chung chung + mã lỗi để dễ tra cứu.

---

## 4. DTO & Mapping

### 4.1 Vì sao không trả trực tiếp Entity ra ngoài API
- **Lộ dữ liệu thừa**: Entity thường có field nội bộ (password hash, audit field, foreign key kỹ thuật...) không nên public ra client.
- **Vòng lặp JSON (infinite recursion)**: khi Entity có quan hệ 2 chiều (`@OneToMany`/`@ManyToOne` cả 2 phía), serialize trực tiếp Entity dễ gây stack overflow do Jackson lặp vô hạn A→B→A→B...
- **Coupling chặt giữa API contract và schema database**: đổi cấu trúc bảng sẽ vô tình làm vỡ API public, trong khi đúng ra 2 thứ này nên độc lập với nhau.

### 4.2 Convert Entity ↔ DTO
```java
// MapStruct — sinh code convert lúc compile-time, hiệu năng tốt (không dùng reflection runtime)
@Mapper(componentModel = "spring")
public interface PetMapper {
    PetResponse toResponse(Pet entity);
    Pet toEntity(PetRequest request);
}
```
So với ModelMapper (dùng reflection runtime, convention-based, dễ setup nhưng chậm hơn và lỗi khó phát hiện lúc compile), MapStruct sinh code Java thật lúc build, nên lỗi mapping (VD field không khớp tên) được phát hiện ngay khi compile thay vì chỉ lộ ra lúc chạy.

### 4.3 Kiểm soát JSON output với Jackson
```java
public class UserResponse {
    private String name;

    @JsonIgnore // không bao giờ xuất hiện trong JSON
    private String passwordHash;

    @JsonProperty("email_address") // đổi tên field khi serialize
    private String email;
}

@JsonInclude(JsonInclude.Include.NON_NULL) // field null sẽ bị loại khỏi JSON output, không in ra "field": null
public class SearchResult { ... }
```

---

## 5. API Documentation & Versioning

### 5.1 OpenAPI/Swagger tự sinh doc
Thêm dependency `springdoc-openapi-starter-webmvc-ui`, Spring Boot tự quét toàn bộ `@RestController` để sinh ra tài liệu OpenAPI (mặc định tại `/v3/api-docs`) và giao diện Swagger UI (`/swagger-ui.html`) — không cần viết doc thủ công tách rời code:

```java
@Operation(summary = "Tìm thú cưng theo id")
@ApiResponse(responseCode = "200", description = "Tìm thấy")
@ApiResponse(responseCode = "404", description = "Không tồn tại")
@GetMapping("/{id}")
public Pet findPet(@PathVariable Long id) { ... }
```

### 5.2 Versioning — 3 cách phổ biến
| Cách | Ví dụ | Ưu điểm | Nhược điểm |
|---|---|---|---|
| **URL path** | `/api/v1/pets`, `/api/v2/pets` | Rõ ràng, dễ test bằng browser/curl, dễ cache theo URL | "Xấu" theo quan điểm REST thuần túy (URL lẽ ra chỉ nên định danh resource, không nên chứa version) |
| **Header tùy chỉnh** | `X-API-Version: 2` | URL sạch, không đổi khi version đổi | Khó test nhanh trên browser, dễ bị quên khi gọi API |
| **Media type (Accept header)** | `Accept: application/vnd.myapp.v2+json` | Đúng chuẩn REST/HATEOAS nhất | Phức tạp nhất để implement và document, ít công cụ hỗ trợ trực quan |

Thực tế đa số hệ thống chọn **URL path versioning** vì đơn giản, dễ hiểu cho cả team backend lẫn frontend, dù không "thuần REST" nhất.

---

## Câu hỏi tự kiểm tra
1. `@RestController` khác `@Controller` ở điểm nào? Nếu dùng `@Controller` thường mà quên thêm `@ResponseBody`, điều gì sẽ xảy ra khi client gọi API?
2. `@RequestParam`, `@PathVariable`, `@RequestBody` lấy dữ liệu từ đâu trong HTTP request? Cho 1 ví dụ URL cụ thể minh họa rõ sự khác biệt.
3. Vì sao nên dùng `ResponseEntity<T>` thay vì trả thẳng object, đặc biệt trong trường hợp nào bắt buộc phải dùng?
4. `MethodArgumentNotValidException` và `HandlerMethodValidationException` khác nhau ở điểm nào? Trường hợp nào mỗi loại được ném ra?
5. Tại sao không nên trả trực tiếp Entity JPA ra response API? Nêu 2 vấn đề cụ thể có thể xảy ra.
6. So sánh 3 cách versioning API — nếu bạn thiết kế 1 API công khai (public) cho bên thứ 3 dùng, bạn chọn cách nào và vì sao?

## Bài tập luyện tập
- Xây dựng `PetController` đầy đủ CRUD (Create/Read/Update/Delete) dùng đúng annotation mapping tương ứng từng thao tác, trả về `ResponseEntity` với status code chuẩn REST (`201` khi tạo, `204` khi xóa, `404` khi không tìm thấy).
- Thêm Bean Validation đầy đủ cho `PetRequest` (ít nhất 4 constraint khác nhau), viết `@RestControllerAdvice` bắt `MethodArgumentNotValidException` trả về response lỗi chuẩn hóa liệt kê từng field lỗi.
- Viết 1 custom validator (`@UniqueEmail` hoặc tương tự) có inject dependency (VD Repository) để kiểm tra logic nghiệp vụ, không chỉ format đơn thuần như `@Email`.
- Thiết kế DTO `PetResponse` tách biệt hoàn toàn khỏi Entity `Pet`, dùng MapStruct để convert, thử thêm quan hệ `@OneToMany` 2 chiều vào Entity để tự chứng minh nếu serialize thẳng Entity sẽ bị lỗi vòng lặp JSON, còn qua DTO thì không.
- Tích hợp `springdoc-openapi`, mở `/swagger-ui.html`, thêm `@Operation`/`@ApiResponse` cho ít nhất 3 endpoint, so sánh doc tự sinh trước/sau khi thêm annotation.
