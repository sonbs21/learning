# Tuần 3 — Spring Core: IoC, Dependency Injection và Bean

> Stack đề xuất: Java 21, Maven, Spring Boot 4.x. Khi tạo project bằng Spring Initializr, hãy chọn bản Spring Boot stable mới nhất trong nhánh 4.x.

## 1. Mục tiêu bài học

Sau tuần này, bạn có thể:

- Giải thích được IoC, Dependency Injection, bean và `ApplicationContext`.
- Tạo dependency bằng constructor injection, không dùng field injection.
- Phân biệt vai trò của `@Component`, `@Service`, `@Repository` và `@Configuration`.
- Hiểu cách component scanning tìm và đăng ký bean.
- Biết khi nào dùng `@Bean` thay vì stereotype annotation.
- Hiểu các bean scope phổ biến và chọn scope phù hợp.
- Đọc cấu hình bằng `@ConfigurationProperties`.
- Dùng profile để thay đổi bean hoặc cấu hình theo môi trường.
- Giải thích starter dependency, auto-configuration và `@SpringBootApplication`.
- Xây được luồng `TaskController → TaskService → TaskRepository` với repository lưu trong bộ nhớ.

## 2. Bức tranh tổng thể

Không dùng Spring, một class thường tự tạo dependency của nó:

```java
public class TaskService {
    private final TaskRepository taskRepository = new InMemoryTaskRepository();
}
```

`TaskService` lúc này phụ thuộc chặt vào `InMemoryTaskRepository`. Việc đổi sang database hoặc viết unit test sẽ khó vì class tự quyết định dependency.

Với Spring, class chỉ khai báo thứ nó cần:

```java
@Service
public class TaskService {
    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }
}
```

Spring sẽ:

1. Tìm implementation của `TaskRepository`.
2. Tạo object tương ứng.
3. Tạo `TaskService` và truyền repository vào constructor.
4. Tạo controller và truyền service vào controller.
5. Quản lý các object này trong `ApplicationContext`.

Luồng dependency của ứng dụng:

```text
HTTP request
    ↓
TaskController
    ↓
TaskService
    ↓
TaskRepository
    ↓
In-memory storage
```

Dependency chỉ đi một chiều. Repository không được gọi ngược lên service, service không phụ thuộc controller.

---

## 3. IoC và Dependency Injection

### 3.1. Inversion of Control là gì?

**Inversion of Control (IoC)** nghĩa là quyền tạo và liên kết object được chuyển từ code nghiệp vụ sang framework/container.

Không có IoC:

```java
TaskRepository repository = new InMemoryTaskRepository();
TaskService service = new TaskService(repository);
TaskController controller = new TaskController(service);
```

Có IoC, Spring thực hiện phần khởi tạo trên dựa vào metadata như annotation và configuration.

IoC là nguyên lý tổng quát. **Dependency Injection (DI)** là cách Spring hiện thực IoC bằng việc truyền dependency vào object.

### 3.2. `@Autowired` là gì?

`@Autowired` là annotation của Spring dùng để đánh dấu một constructor, setter, field hoặc method cần được container tự động inject một Bean phù hợp.

Ví dụ:

```java
@Service
public class TaskService {

    private final TaskRepository taskRepository;

    @Autowired
    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }
}
```

Khi tạo `TaskService`, Spring sẽ:

```text
1. Nhìn vào dependency TaskRepository
2. Tìm Bean phù hợp trong ApplicationContext
3. Tạo hoặc lấy Bean đó
4. Truyền Bean vào constructor của TaskService
```

`@Autowired` không tự tạo một object tùy ý bằng `new`. Nó chỉ inject những object đã được Spring đăng ký làm Bean, ví dụ qua `@Component`, `@Service`, `@Repository`, `@Controller` hoặc `@Bean`.

Nếu class chỉ có một constructor, Spring tự hiểu đó là constructor injection nên không cần ghi `@Autowired`:

```java
@Service
public class TaskService {
    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }
}
```

Khi có nhiều Bean cùng kiểu, `@Autowired` cần thêm thông tin để chọn:

```java
@Autowired
public TaskService(@Qualifier("postgresTaskRepository") TaskRepository repository) {
    this.repository = repository;
}
```

Hoặc đánh dấu một Bean mặc định bằng `@Primary`. Nếu không có Bean phù hợp, Spring báo `NoSuchBeanDefinitionException`; nếu có nhiều candidate mà không chọn được, Spring báo `NoUniqueBeanDefinitionException`.

`@Autowired(required = false)` cho phép dependency không bắt buộc, nhưng nên cân nhắc dùng `Optional`, `ObjectProvider` hoặc thiết kế configuration rõ ràng thay vì làm dependency bắt buộc trở thành tùy chọn một cách âm thầm.

### 3.3. Ba kiểu injection

Spring có thể inject qua constructor, setter hoặc field.

#### Định nghĩa ngắn gọn

| Kiểu injection | Định nghĩa | Nên dùng khi |
|---|---|---|
| Constructor injection | Spring truyền dependency vào constructor lúc tạo object | Dependency bắt buộc; đây là lựa chọn mặc định |
| Setter injection | Spring truyền dependency qua setter sau khi object được tạo | Dependency tùy chọn hoặc có thể thay đổi |
| Field injection | Spring ghi trực tiếp dependency vào field bằng reflection | Không khuyến nghị trong code ứng dụng |

Nói đơn giản:

```text
Constructor injection → dependency đi vào lúc khởi tạo
Setter injection      → dependency đi vào qua phương thức set
Field injection       → dependency đi thẳng vào field
```

#### Constructor injection — nên dùng mặc định

```java
@Service
public class TaskService {
    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }
}
```

Ưu điểm:

- Dependency bắt buộc được thể hiện rõ.
- Field có thể là `final`.
- Object không tồn tại ở trạng thái thiếu dependency.
- Có thể unit test bằng cách truyền fake/mock trực tiếp.
- Không phụ thuộc Spring khi khởi tạo class trong test.

Nếu class chỉ có một constructor, không cần ghi `@Autowired`.

#### Setter injection — chỉ dành cho dependency tùy chọn

Setter injection cho phép thay đổi dependency sau khi object đã được tạo. Điều này hiếm khi cần trong business service và làm object dễ rơi vào trạng thái chưa hoàn chỉnh.

#### Field injection — không dùng

```java
// Không nên làm
@Autowired
private TaskRepository taskRepository;
```

Field injection che giấu dependency, khó unit test, không dùng được `final` và thường khuyến khích class có quá nhiều trách nhiệm.

---

## 4. Bean và ApplicationContext

### 4.1. Bean là gì?

Bean là object được Spring tạo, cấu hình và quản lý vòng đời. Không phải mọi object trong ứng dụng đều cần là bean.

Nên là bean:

- Controller, service, repository.
- Adapter gọi dịch vụ bên ngoài.
- Object cấu hình hoặc infrastructure dùng chung.

Không nhất thiết là bean:

- Entity, DTO, request/response model.
- Value object được tạo theo dữ liệu của từng request.
- Object tạm thời chỉ dùng bên trong một method.

### 4.2. ApplicationContext là gì?

`ApplicationContext` là IoC container chính của Spring. Nó chứa bean definitions, tạo bean, resolve dependency, quản lý lifecycle và cung cấp các dịch vụ framework khác.

```java
ConfigurableApplicationContext context =
        SpringApplication.run(TaskApplication.class, args);

TaskService taskService = context.getBean(TaskService.class);
```

Đoạn trên giúp minh họa container, nhưng code nghiệp vụ không nên gọi `getBean()` liên tục. Hãy để Spring inject dependency qua constructor. Tự lấy bean từ context là Service Locator pattern và che giấu dependency.

### 4.3. Khi Spring tạo bean

Với singleton mặc định, quy trình đơn giản hóa là:

```text
Đọc cấu hình
  → tìm bean definitions
  → resolve dependency
  → gọi constructor
  → inject dependency
  → chạy callback khởi tạo
  → bean sẵn sàng sử dụng
  → chạy callback hủy khi context đóng
```

Không nên thực hiện network call hoặc logic nặng trong constructor. Constructor chỉ nên gán dependency và kiểm tra invariant đơn giản.

---

## 5. Component scanning và stereotype annotations

### 5.1. Component scanning

Khi application class đặt tại package gốc, Spring Boot mặc định scan package đó và toàn bộ package con.

```text
com.example.taskapp
├── TaskApplication.java       ← package gốc
├── task
│   ├── TaskController.java
│   ├── TaskService.java
│   └── InMemoryTaskRepository.java
└── config
    └── TaskProperties.java
```

Nếu đặt `TaskApplication` trong package quá sâu, các component nằm ngoài cây package sẽ không được tìm thấy.

Không nên dùng default package. Cũng không nên cấu hình `@ComponentScan` quá rộng nếu cấu trúc package đúng ngay từ đầu.

### 5.2. Các stereotype thường dùng

| Annotation | Vai trò |
|---|---|
| `@Component` | Bean tổng quát, không thuộc vai trò chuyên biệt nào |
| `@Service` | Business logic hoặc orchestration |
| `@Repository` | Data access; còn hỗ trợ chuyển đổi một số persistence exception |
| `@Controller` | MVC controller trả view |
| `@RestController` | REST controller; response body được serialize trực tiếp |
| `@Configuration` | Class khai báo bean bằng các method `@Bean` |

`@Service`, `@Repository`, `@Controller` đều là stereotype chuyên biệt của `@Component`. Dùng annotation đúng nghĩa giúp kiến trúc dễ đọc và cho phép framework áp dụng behavior phù hợp.

### 5.3. Khi nào dùng `@Bean`?

Dùng component scanning cho class do mình sở hữu:

```java
@Service
public class TaskService {
}
```

Dùng `@Bean` khi cần tự điều khiển cách khởi tạo, đặc biệt với class từ thư viện bên ngoài:

```java
@Configuration
public class ClockConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
```

Spring đăng ký object trả về từ method `clock()` thành một bean kiểu `Clock`.

---

## 6. Bean scope

Bean scope là quy tắc quyết định:

1. Spring tạo bao nhiêu instance của một Bean.
2. Hai lần gọi `getBean()` có nhận cùng object hay object mới.
3. Bean tồn tại tới khi nào.
4. Context có chịu trách nhiệm hủy Bean hay không.

Ví dụ, scope không quyết định class có phải singleton theo nghĩa toàn hệ thống hay không. `singleton` của Spring nghĩa là **một instance trong một `ApplicationContext`**. Nếu ứng dụng chạy 3 instance/pod, sẽ có tối đa 3 singleton tương ứng.

| Scope | Ý nghĩa | Dùng khi |
|---|---|---|
| `singleton` | Một instance cho mỗi `ApplicationContext` | Mặc định cho service, repository, controller |
| `prototype` | Một instance mới mỗi lần container cung cấp bean | Object có trạng thái ngắn hạn, trường hợp hiếm |
| `request` | Một instance cho mỗi HTTP request | Dữ liệu thực sự gắn với request |
| `session` | Một instance cho mỗi HTTP session | Ứng dụng web stateful |
| `application` | Một instance cho mỗi `ServletContext` | Trạng thái cấp web application |
| `websocket` | Một instance cho mỗi WebSocket session | State gắn với một kết nối WebSocket |

### 6.1. `singleton` — mặc định

Spring tạo một instance khi context khởi động và tái sử dụng instance đó cho mọi consumer:

```java
@Service
public class TaskService {
    // Không ghi state riêng của request/user vào field ở đây.
}
```

```java
TaskService first = context.getBean(TaskService.class);
TaskService second = context.getBean(TaskService.class);

assert first == second; // cùng một instance trong context này
```

Đây là scope phù hợp cho service, repository, controller, mapper stateless và client dùng chung. Singleton bean có thể bị nhiều request xử lý đồng thời, nên field mutable như `currentUser`, `currentTask` hoặc `lastRequest` dễ gây race condition và rò dữ liệu giữa user.

Singleton không có nghĩa là thread-safe. Nếu bean phải giữ cache/counter mutable, cần thiết kế concurrency, lock hoặc cấu trúc dữ liệu thread-safe một cách có chủ đích.

### 6.2. `prototype` — instance mới theo yêu cầu

```java
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ImportJobContext {
    private final UUID jobId = UUID.randomUUID();

    public UUID jobId() {
        return jobId;
    }
}
```

Mỗi lần container thực sự cung cấp `ImportJobContext`, Spring tạo instance mới:

```java
ImportJobContext first = context.getBean(ImportJobContext.class);
ImportJobContext second = context.getBean(ImportJobContext.class);

assert first != second;
```

Prototype phù hợp cho object có state ngắn hạn, không nên dùng chung giữa các request. Tuy nhiên, Spring chỉ quản lý việc tạo prototype; sau khi trả object cho application, container **không tự gọi destruction callback** khi prototype không còn được dùng. Code sở hữu prototype phải có cleanup strategy nếu object giữ resource.

Một bẫy phổ biến:

```java
@Service
public class ImportService {
    private final ImportJobContext context;

    public ImportService(ImportJobContext context) {
        this.context = context;
    }
}
```

Nếu `ImportService` là singleton, `ImportJobContext` được inject một lần khi `ImportService` được tạo. Nó không tự thay đổi thành instance mới ở mỗi method call.

Muốn lấy prototype mới theo nhu cầu, dùng `ObjectProvider`:

```java
@Service
public class ImportService {
    private final ObjectProvider<ImportJobContext> contexts;

    public ImportService(ObjectProvider<ImportJobContext> contexts) {
        this.contexts = contexts;
    }

    public UUID startJob() {
        ImportJobContext context = contexts.getObject();
        return context.jobId();
    }
}
```

Các lựa chọn khác là `Provider<T>` hoặc `@Lookup`, nhưng `ObjectProvider` thường rõ ràng và dễ test hơn.

### 6.3. `request` — một instance cho mỗi HTTP request

```java
@Component
@RequestScope
public class RequestMetadata {
    private final String requestId = UUID.randomUUID().toString();

    public String requestId() {
        return requestId;
    }
}
```

Request A và request B nhận hai instance khác nhau:

```text
Request A → RequestMetadata(requestId=A)
Request B → RequestMetadata(requestId=B)
```

Dùng request scope cho correlation metadata, request-level context hoặc dữ liệu chỉ tồn tại trong một request. Không dùng nó để giữ business state cần tồn tại sau request hoặc để thay thế database/session store.

Request scope chỉ hoạt động khi có HTTP request context. Gọi Bean này trong startup runner, scheduled job hoặc async thread không có request sẽ gây lỗi hoặc không có context phù hợp.

### 6.4. `session` — một instance cho mỗi HTTP session

```java
@Component
@SessionScope
public class ShoppingCart {
    private final Map<UUID, Integer> quantities = new HashMap<>();

    public void add(UUID productId, int quantity) {
        quantities.merge(productId, quantity, Integer::sum);
    }
}
```

Mọi request thuộc cùng HTTP session dùng cùng `ShoppingCart`; session khác dùng cart khác. Scope này phù hợp ứng dụng web stateful, nhưng cần cân nhắc:

- Session replication khi chạy nhiều instance.
- Sticky session hoặc external session store.
- Giới hạn kích thước và dữ liệu nhạy cảm.
- Timeout và logout/invalid session.

Với REST API stateless dùng JWT, `session` thường không phải nơi lưu business state. Dữ liệu quan trọng nên nằm ở database hoặc store chuyên dụng.

### 6.5. `application` — một instance cho mỗi `ServletContext`

```java
@Component
@ApplicationScope
public class ApplicationStatistics {
    private final AtomicLong requests = new AtomicLong();

    public long incrementAndGet() {
        return requests.incrementAndGet();
    }
}
```

`application` scope gắn với web application/`ServletContext`, không phải toàn bộ JVM và không phải toàn cluster. Khi chạy nhiều pod, mỗi pod có một instance riêng. Không dùng scope này làm distributed counter hoặc coordination mechanism; dùng database/Redis/metrics phù hợp.

### 6.6. `websocket` — một instance cho mỗi WebSocket session

WebSocket scope giữ state trong vòng đời một WebSocket session. Nó chỉ phù hợp khi ứng dụng bật WebSocket và có lifecycle tương ứng:

```java
@Component
@Scope(scopeName = "websocket", proxyMode = ScopedProxyMode.TARGET_CLASS)
public class WebSocketConversation {
    private final UUID conversationId = UUID.randomUUID();

    public UUID conversationId() {
        return conversationId;
    }
}
```

Không dùng WebSocket scope cho HTTP request thông thường. Nếu app không dùng WebSocket, scope này không cần thiết.

### 6.7. Inject scope ngắn vào singleton

Một singleton tồn tại lâu hơn request/session. Inject trực tiếp scope ngắn có thể khiến Spring giữ sai instance hoặc không biết target hiện tại. Dùng scoped proxy:

```java
@Component
@RequestScope(proxyMode = ScopedProxyMode.TARGET_CLASS)
public class RequestMetadata {
    private final String requestId = UUID.randomUUID().toString();

    public String requestId() {
        return requestId;
    }
}
```

Singleton nhận proxy ổn định; mỗi request, proxy chuyển lời gọi tới target `RequestMetadata` tương ứng request đó. Proxy không làm request scope dùng được trong background thread không có request context.

Thay vì proxy, có thể inject `ObjectProvider<RequestMetadata>` và lấy target tại thời điểm cần. Cách này thường làm dependency động rõ ràng hơn.

### 6.8. Bảng chọn scope nhanh

| Câu hỏi | Scope thường phù hợp |
|---|---|
| Bean stateless, dùng chung cho mọi request? | `singleton` |
| Mỗi lần xử lý cần object state mới? | `prototype` hoặc factory/provider |
| Dữ liệu chỉ sống trong một HTTP request? | `request` |
| Dữ liệu gắn với HTTP session? | `session` |
| State gắn với cả web application trong một instance? | `application` |
| State gắn với WebSocket connection? | `websocket` |

Đừng chọn scope chỉ vì annotation tồn tại. Hãy xác định owner của state, số instance khi scale ngang, thread-safety, cleanup và cách test trước.

Điểm cần nhớ:

- Singleton của Spring không đồng nghĩa singleton toàn JVM; nó là singleton trong một context.
- Singleton bean được nhiều thread sử dụng đồng thời, vì vậy service nên stateless.
- Không lưu dữ liệu riêng của user/request trong field của singleton controller hoặc service.
- Prototype bean được inject trực tiếp vào singleton vẫn chỉ được resolve lúc singleton được tạo; muốn lấy instance mới theo nhu cầu cần cơ chế provider phù hợp.
- Web scope (`request`, `session`, `application`, `websocket`) cần web-aware `ApplicationContext` và đúng lifecycle; không phải scope nào cũng dùng được trong command line hoặc scheduled application.

---

## 7. Starter dependency, auto-configuration và `@SpringBootApplication`

### 7.1. Starter dependency

Starter là tập dependency được đóng gói theo một use case. Ví dụ:

- `spring-boot-starter-webmvc`: Spring MVC, JSON, embedded web server và các dependency web cần thiết. Trong Spring Initializr, dependency này được hiển thị với tên **Spring Web**. Starter cũ `spring-boot-starter-web` đã deprecated từ Spring Boot 4.
- `spring-boot-starter-validation`: Jakarta Bean Validation.
- `spring-boot-starter-actuator`: health, metrics và các management endpoints.
- `spring-boot-starter-test`: JUnit, Spring Test, AssertJ, Mockito và các công cụ test phổ biến.

Starter giúp các dependency tương thích phiên bản với nhau thông qua dependency management của Spring Boot.

### 7.2. Auto-configuration

Auto-configuration cấu hình ứng dụng dựa trên:

- Dependency có trong classpath.
- Bean mà developer đã hoặc chưa khai báo.
- Giá trị configuration properties.
- Loại ứng dụng, ví dụ servlet web application.

Ví dụ, khi có web starter, Spring Boot có thể cấu hình MVC và embedded server. Nếu developer tự định nghĩa một bean thuộc loại mà auto-configuration đang chờ, cấu hình mặc định thường sẽ **back off** để dùng bean của developer.

Auto-configuration không phải phép thuật. Đó là các configuration class có điều kiện, thường dựa trên những điều kiện kiểu “có class này”, “chưa có bean kia” hoặc “property này được bật”.

Khi cần xem lý do một auto-configuration được hoặc không được áp dụng, chạy ứng dụng với debug:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--debug
```

### 7.3. `@SpringBootApplication`

Annotation này kết hợp ba khả năng chính:

```text
@SpringBootConfiguration
        +
@EnableAutoConfiguration
        +
@ComponentScan
```

Application class tối thiểu:

```java
@SpringBootApplication
public class TaskApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskApplication.class, args);
    }
}
```

---

## 8. Thực hành: tạo Task API in-memory

### 8.1. Khởi tạo project

Truy cập Spring Initializr và chọn:

- Project: Maven.
- Language: Java.
- Spring Boot: bản stable mới nhất thuộc 4.x.
- Group: `com.example`.
- Artifact: `task-api`.
- Packaging: Jar.
- Java: 21.
- Dependencies: Spring Web, Validation, Spring Boot Actuator.

Thêm test starter nếu project được tạo không có sẵn dependency test:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

### 8.2. Cấu trúc project

```text
src
├── main
│   ├── java/com/example/taskapi
│   │   ├── TaskApiApplication.java
│   │   ├── config/TaskProperties.java
│   │   └── task
│   │       ├── CreateTaskRequest.java
│   │       ├── InMemoryTaskRepository.java
│   │       ├── Task.java
│   │       ├── TaskController.java
│   │       ├── TaskRepository.java
│   │       ├── TaskService.java
│   │       └── TaskStatus.java
│   └── resources
│       ├── application.yml
│       └── application-local.yml
└── test
    └── java/com/example/taskapi
        ├── TaskApiApplicationTests.java
        └── task/TaskServiceTest.java
```

### 8.3. Application class

```java
package com.example.taskapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TaskApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskApiApplication.class, args);
    }
}
```

`@ConfigurationPropertiesScan` tìm các class có `@ConfigurationProperties` trong package hiện tại và package con.

### 8.4. Domain model bất biến

```java
package com.example.taskapi.task;

public enum TaskStatus {
    TODO,
    DONE
}
```

```java
package com.example.taskapi.task;

import java.util.UUID;

public record Task(UUID id, String title, TaskStatus status) {
}
```

`record` phù hợp vì `Task` là một value-like data carrier bất biến. Khi muốn thay đổi task, hãy tạo một `Task` mới thay vì thay đổi field cũ.

Request model:

```java
package com.example.taskapi.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTaskRequest(
        @NotBlank
        @Size(max = 200)
        String title
) {
}
```

Validation sẽ được học sâu hơn ở tuần 5; tại đây nó minh họa dependency `spring-boot-starter-validation`.

### 8.5. Repository abstraction

```java
package com.example.taskapi.task;

import java.util.List;

public interface TaskRepository {
    Task save(Task task);

    List<Task> findAll();

    long count();
}
```

Business layer phụ thuộc interface, không phụ thuộc cách lưu Map hay database.

Implementation in-memory:

```java
package com.example.taskapi.task;

import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class InMemoryTaskRepository implements TaskRepository {
    private Map<UUID, Task> tasks = Map.of();

    @Override
    public synchronized Task save(Task task) {
        Map<UUID, Task> updatedTasks = new LinkedHashMap<>(tasks);
        updatedTasks.put(task.id(), task);
        tasks = Collections.unmodifiableMap(updatedTasks);
        return task;
    }

    @Override
    public synchronized List<Task> findAll() {
        return List.copyOf(tasks.values());
    }

    @Override
    public synchronized long count() {
        return tasks.size();
    }
}
```

Repository tạo snapshot mới khi lưu thay vì để caller sửa trực tiếp collection nội bộ. `synchronized` giữ thao tác đọc–ghi nhất quán cho bài thực hành. Đây chỉ là storage phục vụ học tập; ứng dụng thực tế sẽ thay bằng database ở tuần 7.

### 8.6. Type-safe configuration với `@ConfigurationProperties`

```java
package com.example.taskapi.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.task")
public record TaskProperties(
        @Min(1)
        @Max(10_000)
        int maxItems
) {
}
```

`application.yml`:

```yaml
spring:
  application:
    name: task-api

app:
  task:
    max-items: 100

management:
  endpoints:
    web:
      exposure:
        include: health,info
```

Ưu điểm so với đọc từng giá trị bằng `@Value`:

- Gom cấu hình liên quan vào một object có kiểu rõ ràng.
- Hỗ trợ relaxed binding: `max-items` ánh xạ tới `maxItems`.
- Có thể validate ngay lúc khởi động.
- Dễ tìm kiếm, tái sử dụng và test.

Không đặt password, token hoặc signing key trực tiếp trong file được commit. Các giá trị nhạy cảm phải đến từ environment variable hoặc secret manager.

### 8.7. Service chứa business logic

```java
package com.example.taskapi.task;

import com.example.taskapi.config.TaskProperties;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class TaskService {
    private final TaskRepository taskRepository;
    private final TaskProperties taskProperties;

    public TaskService(
            TaskRepository taskRepository,
            TaskProperties taskProperties
    ) {
        this.taskRepository = taskRepository;
        this.taskProperties = taskProperties;
    }

    public synchronized Task create(String title) {
        if (taskRepository.count() >= taskProperties.maxItems()) {
            throw new IllegalStateException("Task limit reached");
        }

        Task task = new Task(UUID.randomUUID(), title.trim(), TaskStatus.TODO);
        return taskRepository.save(task);
    }

    public List<Task> findAll() {
        return taskRepository.findAll();
    }
}
```

Business rule “không vượt quá số task tối đa” nằm trong service, không nằm trong controller. Cách chuyển exception thành HTTP response thân thiện sẽ được học ở tuần 6.

`create()` được đồng bộ để thao tác kiểm tra giới hạn và lưu task là một khối nguyên tử trong ứng dụng in-memory chạy một instance. Đây chỉ là giải pháp học tập. Khi chuyển sang database, invariant đồng thời phải được bảo vệ bằng transaction, constraint hoặc cơ chế locking phù hợp; `synchronized` không bảo vệ được nhiều instance ứng dụng.

### 8.8. Controller chỉ xử lý HTTP

```java
package com.example.taskapi.task;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Task create(@Valid @RequestBody CreateTaskRequest request) {
        return taskService.create(request.title());
    }

    @GetMapping
    public List<Task> findAll() {
        return taskService.findAll();
    }
}
```

Controller chỉ:

- Nhận và validate HTTP input.
- Gọi use case trong service.
- Trả HTTP output.

Controller không tự tạo repository, không lưu task vào field và không chứa business rule.

---

## 9. Profile và cấu hình theo môi trường

Profile cho phép chỉ kích hoạt một nhóm bean hoặc một file cấu hình trong môi trường phù hợp.

Tạo `application-local.yml`:

```yaml
app:
  task:
    max-items: 20

logging:
  level:
    com.example.taskapi: DEBUG
```

Chạy với profile `local`:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Hoặc dùng environment variable:

```bash
SPRING_PROFILES_ACTIVE=local ./mvnw spring-boot:run
```

`application.yml` chứa giá trị chung. `application-local.yml` chỉ override phần dành cho local.

### Dùng `@Profile` cho bean

```java
@Repository
@Profile("local")
public class LocalTaskRepository implements TaskRepository {
    // ...
}
```

Nếu thêm bean trên, cần đồng thời giới hạn repository mặc định để hai implementation không cùng active:

```java
@Repository
@Profile("!local")
public class InMemoryTaskRepository implements TaskRepository {
    // ...
}
```

Chỉ dùng `@Profile` khi thực sự cần thay implementation theo môi trường. Nếu chỉ khác giá trị như URL, timeout hoặc giới hạn, ưu tiên configuration properties thay vì tạo nhiều bean gần giống nhau.

Không hardcode profile active trong artifact production. Hãy để môi trường deploy quyết định profile.

---

## 10. Kiểm thử dependency graph và business logic

### 10.1. Unit test service không khởi động Spring

```java
package com.example.taskapi.task;

import com.example.taskapi.config.TaskProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskServiceTest {

    @Test
    void createsTaskWithTodoStatus() {
        TaskRepository repository = new InMemoryTaskRepository();
        TaskService service = new TaskService(repository, new TaskProperties(10));

        Task created = service.create("Learn Spring Core");

        assertThat(created.id()).isNotNull();
        assertThat(created.title()).isEqualTo("Learn Spring Core");
        assertThat(created.status()).isEqualTo(TaskStatus.TODO);
        assertThat(repository.findAll()).containsExactly(created);
    }
}
```

Constructor injection cho phép test class mà không cần `@SpringBootTest` và không cần reflection.

Viết thêm test cho giới hạn:

```java
@Test
void rejectsNewTaskWhenLimitIsReached() {
    TaskRepository repository = new InMemoryTaskRepository();
    TaskService service = new TaskService(repository, new TaskProperties(1));
    service.create("First task");

    assertThatThrownBy(() -> service.create("Second task"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Task limit reached");
}
```

Thêm static import:

```java
import static org.assertj.core.api.Assertions.assertThatThrownBy;
```

### 10.2. Integration test kiểm tra ApplicationContext

```java
package com.example.taskapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TaskApiApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

Test này xác nhận Spring có thể tạo toàn bộ dependency graph và bind configuration thành công. Nó không thay thế unit test cho business rule.

### 10.3. Chạy test

```bash
./mvnw test
```

Mục tiêu của project là tất cả test pass và phần code tự viết đạt tối thiểu 80% coverage. Không nên viết test chỉ để tăng con số; hãy bao phủ happy path, validation và business rule quan trọng.

---

## 11. Chạy và kiểm tra ứng dụng

Khởi động:

```bash
./mvnw spring-boot:run
```

Tạo task:

```bash
curl -i \
  -X POST http://localhost:8080/api/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title":"Learn Spring Core"}'
```

Kết quả mong đợi:

```http
HTTP/1.1 201 Created
Content-Type: application/json

{"id":"...","title":"Learn Spring Core","status":"TODO"}
```

Lấy danh sách:

```bash
curl -i http://localhost:8080/api/tasks
```

Kiểm tra health:

```bash
curl -i http://localhost:8080/actuator/health
```

Không expose toàn bộ Actuator endpoints ra Internet. Một số endpoint có thể tiết lộ cấu hình hoặc cấu trúc nội bộ; production cần Spring Security và exposure policy rõ ràng.

---

## 12. Các lỗi thường gặp

### “No qualifying bean of type …”

Kiểm tra:

- Implementation đã có `@Repository`, `@Service`, `@Component` hoặc được tạo bằng `@Bean` chưa?
- Component có nằm dưới package của application class không?
- Có đúng một implementation phù hợp không?
- Profile chứa bean có đang active không?

### Có nhiều bean cùng interface

Nếu có hai implementation `TaskRepository`, Spring không biết chọn bean nào. Giải pháp:

- Giữ một implementation nếu chưa cần nhiều hơn.
- Dùng `@Primary` cho lựa chọn mặc định.
- Dùng `@Qualifier` khi dependency thực sự cần một implementation cụ thể.
- Dùng profile nếu mỗi môi trường chỉ cần một implementation.

Không thêm `@Qualifier` khắp nơi để che một thiết kế mơ hồ.

### Circular dependency

```text
TaskService → NotificationService → TaskService
```

Đây thường là tín hiệu trách nhiệm bị chia sai. Hãy tách orchestration hoặc domain operation phù hợp, không chữa bằng field injection hoặc lazy injection một cách máy móc.

### Đưa business logic vào controller

Controller lớn, khó test và không tái sử dụng được. Business rule phải nằm trong service hoặc domain layer.

### Lưu request state trong singleton bean

Controller/service mặc định là singleton và được nhiều request dùng đồng thời. Field có thể gây race condition hoặc rò dữ liệu giữa user.

---

## 13. Bài tập thực hành

### Bài 1 — Hoàn thiện API

Thêm các use case:

- Tìm task theo ID.
- Đánh dấu task là `DONE` bằng cách tạo một `Task` mới.
- Xóa task.
- Trả lỗi rõ ràng nếu task không tồn tại.

Giữ đúng dependency direction và không đặt business logic vào controller.

### Bài 2 — Đổi implementation bằng profile

Tạo `LoggingTaskRepository` chỉ dành cho profile `debug-repository`. Implementation này delegate đến repository in-memory và log mỗi thao tác.

Yêu cầu:

- Không sửa `TaskService`.
- Không gọi `new LoggingTaskRepository(...)` trong service.
- Không để đồng thời hai bean `TaskRepository` gây ambiguity.

### Bài 3 — Mở rộng configuration properties

Thêm cấu hình:

```yaml
app:
  task:
    max-items: 100
    default-status: TODO
    title-max-length: 200
```

Validate các giá trị ngay khi khởi động. Viết test cho binding và validation.

### Bài 4 — Quan sát context

Chạy ứng dụng với `--debug`, đọc condition evaluation report và trả lời:

1. Auto-configuration nào tạo web server?
2. Auto-configuration nào liên quan đến Spring MVC?
3. Điều kiện nào khiến một auto-configuration không được áp dụng?
4. Những bean nào do code của bạn tạo, bean nào do Spring Boot tạo?

---

## 14. Câu hỏi tự kiểm tra

1. IoC khác DI như thế nào?
2. Bean khác object được tạo bằng `new` ở điểm nào?
3. Vì sao constructor injection tốt hơn field injection?
4. `ApplicationContext` chịu trách nhiệm gì?
5. Vì sao application class nên đặt ở package gốc?
6. Khi nào dùng `@Bean` thay vì `@Component`?
7. Singleton bean có an toàn đa luồng mặc định không?
8. `@SpringBootApplication` kết hợp những khả năng nào?
9. Starter dependency khác auto-configuration như thế nào?
10. Khi nào dùng profile, khi nào chỉ cần configuration properties?
11. Vì sao controller không nên chứa business logic?
12. Nếu có hai bean cùng implement một interface, Spring xử lý ra sao?

---

## 15. Tiêu chí hoàn thành tuần 3

- [ ] Tự giải thích được IoC, DI, bean và `ApplicationContext`.
- [ ] Không sử dụng field injection.
- [ ] Tạo đúng dependency graph `Controller → Service → Repository`.
- [ ] Application class nằm ở package gốc và component scanning hoạt động.
- [ ] Phân biệt đúng `@Component`, `@Service`, `@Repository`, `@Configuration` và `@Bean`.
- [ ] Không lưu request/user state trong singleton bean.
- [ ] Bind và validate được `TaskProperties`.
- [ ] Chạy được profile `local`.
- [ ] Giải thích được starter, auto-configuration và `@SpringBootApplication`.
- [ ] Unit test business rule và integration test context đều pass.
- [ ] Tạo/lấy task qua HTTP và kiểm tra được `/actuator/health`.
- [ ] Code tự viết đạt tối thiểu 80% test coverage.

## 16. Tóm tắt

Spring Core giúp tách việc **class cần gì** khỏi việc **dependency được tạo như thế nào**. Class khai báo dependency qua constructor, còn `ApplicationContext` tạo và liên kết các bean. Spring Boot bổ sung starter, auto-configuration và `@SpringBootApplication` để giảm cấu hình lặp lại.

Kiến trúc cần ghi nhớ:

```text
TaskController → TaskService → TaskRepository
```

- Controller chịu trách nhiệm HTTP.
- Service chịu trách nhiệm business logic.
- Repository chịu trách nhiệm lưu và truy xuất dữ liệu.
- Configuration đi qua object có kiểu rõ ràng.
- Dependency đi một chiều và được inject qua constructor.

Nắm chắc những nguyên tắc này trước khi học configuration/lifecycle ở tuần 4 và xây REST API hoàn chỉnh ở các tuần tiếp theo.

## Tài liệu chính thức

- [Spring Beans and Dependency Injection](https://docs.spring.io/spring-boot/reference/using/spring-beans-and-dependency-injection.html)
- [Using `@SpringBootApplication`](https://docs.spring.io/spring-boot/reference/using/using-the-springbootapplication-annotation.html)
- [Externalized Configuration](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html)
- [Spring Framework — Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)
