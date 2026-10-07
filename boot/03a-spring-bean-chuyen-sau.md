# Spring Bean chuyên sâu — từ Bean Definition đến Lifecycle

> Tài liệu bổ sung cho **Tuần 3: Spring Core**. Ví dụ dùng Java 21, Spring Boot 4.x và Spring Framework 7.x.

## 1. Mục tiêu

Sau bài này, bạn cần giải thích được:

- Spring bean là gì và khác object Java bình thường ở đâu.
- Bean definition, bean instance, bean name và dependency khác nhau thế nào.
- Các cách đăng ký bean: component scanning, `@Bean`, auto-configuration.
- Spring chọn dependency ra sao khi có một hoặc nhiều candidate.
- Singleton, prototype, request/session/application/websocket scope dùng khi nào.
- Bean lifecycle từ lúc tạo đến lúc hủy, bao gồm init/destroy callback.
- Vì sao singleton không tự động thread-safe.
- Proxy, `BeanPostProcessor`, `BeanFactoryPostProcessor` và `FactoryBean` dùng để làm gì.
- Cách debug lỗi bean và test wiring đúng mức.

---

## 2. Bean là gì?

**Spring bean là object được Spring IoC container tạo, cấu hình, liên kết dependency và quản lý lifecycle.**

```java
import org.springframework.stereotype.Service;

@Service
public class TaskService {
}
```

Khi component scanning tìm thấy `TaskService`, Spring tạo metadata cho class này, tạo object tương ứng và quản lý object đó trong `ApplicationContext`.

Bean vẫn là object Java bình thường. Điểm khác biệt là object đó do ai quản lý.

| Object tạo bằng `new` | Spring bean |
|---|---|
| Code của bạn tự tạo | Container tạo hoặc nhận từ factory method |
| Code của bạn tự truyền dependency | Container resolve và inject dependency |
| Code của bạn tự quản lý init/cleanup | Container gọi lifecycle callback phù hợp |
| Không tự động được proxy | Có thể được proxy để hỗ trợ transaction, cache, security, async |

Cùng một class có thể có object do bạn `new` và object do Spring tạo. Chỉ object thứ hai là Spring bean.

Thường nên là bean:

- Controller, service, repository.
- Adapter/client gọi hệ thống ngoài.
- Infrastructure dùng chung như `Clock`, encoder, mapper.
- Configuration object cần bind từ file cấu hình.

Thường không nên là bean:

- Request/response DTO.
- Entity, value object, domain object như `Task`, `Money`, `OrderLine`.
- Object tạm chỉ sống trong một method.
- Object được tạo theo từng dữ liệu nghiệp vụ.

Đừng gắn `@Component` lên mọi class. Class chỉ nên là bean khi container thật sự cần quản lý nó hoặc cung cấp nó như dependency dùng chung.

---

## 3. Bốn khái niệm dễ nhầm

### 3.1. Bean definition

Bean definition là metadata mô tả cách tạo và quản lý bean. Nó có thể chứa class, factory method, bean name, scope, dependency, lazy/eager setting, init/destroy method, qualifier, primary, profile và condition.

Annotation như `@Component`, `@Service`, `@Repository`, `@Configuration` và method `@Bean` đều là cách tạo bean definition.

### 3.2. Bean instance

Bean instance là object thật được tạo từ bean definition. Một singleton bean definition thường tạo một instance trong mỗi `ApplicationContext`; một prototype bean definition có thể tạo nhiều instance.

### 3.3. Bean name

Mỗi bean có ít nhất một tên trong container.

```java
@Service
public class TaskService {
}
```

Tên mặc định thường là `taskService`.

Với `@Bean`, tên method là tên bean mặc định:

```java
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfiguration {

    @Bean
    Clock applicationClock() {
        return Clock.systemUTC();
    }
}
```

Bean name mặc định là `applicationClock`. Có thể đặt tên rõ:

```java
@Bean("utcClock")
Clock applicationClock() {
    return Clock.systemUTC();
}
```

Trong đa số trường hợp, resolve theo type là đủ. Chỉ dùng name hoặc qualifier khi có nhiều candidate.

### 3.4. Dependency

Dependency là object mà một object khác cần để làm việc.

```java
@Service
public class TaskService {
    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }
}
```

`TaskRepository` là dependency của `TaskService`. Spring tìm bean phù hợp rồi truyền vào constructor.

---

## 4. IoC container và ApplicationContext

Spring có hai abstraction container quan trọng:

- `BeanFactory`: năng lực nền tảng để tạo, cấu hình và cung cấp bean.
- `ApplicationContext`: mở rộng `BeanFactory`, thêm event, resource loading, environment, message resolution và nhiều dịch vụ ứng dụng.

Ứng dụng Spring Boot thường khởi động container bằng:

```java
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TaskApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(TaskApiApplication.class, args);
    }
}
```

Có thể lấy bean thủ công bằng `context.getBean(TaskService.class)`, nhưng business code không nên gọi `getBean()` liên tục. Cách đó biến container thành Service Locator, che giấu dependency và làm test khó hơn. Hãy khai báo dependency qua constructor.

Mô hình khởi động đơn giản:

```text
đọc configuration
    -> tạo BeanDefinition
    -> chạy BeanFactoryPostProcessor
    -> đăng ký BeanPostProcessor
    -> tạo singleton không lazy
    -> resolve và inject dependency
    -> chạy lifecycle callbacks
    -> ApplicationContext sẵn sàng
```

Auto-configuration, condition và proxy có thể thêm nhiều bước nội bộ, nhưng mental model này đủ để debug phần lớn lỗi bean.

---

## 5. Các cách đăng ký bean

### 5.1. Component scanning

Spring tìm class có stereotype annotation trong package được scan.

```java
import org.springframework.stereotype.Component;

@Component
public class SlugGenerator {
}
```

| Annotation | Ý nghĩa chính |
|---|---|
| `@Component` | Component tổng quát |
| `@Service` | Business service hoặc orchestration |
| `@Repository` | Data access; có semantics liên quan persistence exception translation |
| `@Controller` | Spring MVC controller trả view |
| `@RestController` | Controller trả response body |
| `@Configuration` | Class khai báo bean bằng method `@Bean` |

`@SpringBootApplication` bao gồm component scanning từ package chứa application class trở xuống.

```text
com.example.taskapi
├── TaskApiApplication.java        <- điểm bắt đầu scan
├── task/TaskService.java          <- được scan
└── config/TimeConfiguration.java  <- được scan
```

Không đặt application class ở package quá sâu. Không dùng default package.

### 5.2. Java configuration với `@Bean`

Dùng `@Bean` khi class thuộc thư viện ngoài, cần tự điều khiển cách khởi tạo, cần chọn implementation theo configuration, hoặc cần khai báo init/destroy method.

```java
@Configuration
public class ClientConfiguration {

    @Bean
    ExternalTaskClient externalTaskClient(
            Clock applicationClock,
            TaskClientProperties properties
    ) {
        return new ExternalTaskClient(applicationClock, properties.baseUrl());
    }
}
```

Dependency của method `@Bean` nên khai báo bằng parameter. Cách này rõ ràng hơn việc gọi chéo giữa các method `@Bean`.

`@Configuration` có thể proxy method `@Bean` để giữ semantics singleton khi method này gọi method kia. Trong application code, nên biểu diễn dependency bằng parameter:

```java
@Bean
TaskService taskService(TaskPolicy taskPolicy) {
    return new TaskService(taskPolicy);
}
```

Cách này vẫn đúng khi dùng `@Configuration(proxyBeanMethods = false)`.

### 5.3. Auto-configuration

Spring Boot auto-configuration tạo bean dựa trên điều kiện như có class trong classpath, chưa có bean cùng loại, property được bật, hoặc app thuộc loại servlet/reactive.

```java
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotificationConfiguration {

    @Bean
    @ConditionalOnMissingBean(NotificationSender.class)
    NotificationSender consoleNotificationSender() {
        return new ConsoleNotificationSender();
    }
}
```

Nếu app đã cung cấp `NotificationSender`, default bean trên sẽ back off.

Trong Spring Boot 4, starter web MVC chính là:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webmvc</artifactId>
</dependency>
```

`spring-boot-starter-web` vẫn tồn tại để tương thích nhưng đã deprecated theo hướng `spring-boot-starter-webmvc`.

---

## 6. Dependency Injection và cách Spring chọn bean

### 6.0. `@Autowired` là gì?

`@Autowired` là annotation của Spring báo cho IoC container biết rằng một constructor, setter, field hoặc method cần nhận một Bean phù hợp từ `ApplicationContext`.

Nói ngắn gọn:

```text
@Autowired = Spring tự tìm Bean phù hợp và truyền vào dependency này
```

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

Spring không gọi `new TaskRepository()` một cách tùy ý. `TaskRepository` phải là Bean đã được đăng ký, chẳng hạn bằng `@Repository`, `@Component` hoặc method `@Bean`.

Nếu class chỉ có một constructor, Spring tự dùng constructor đó và không cần `@Autowired`:

```java
@Service
public class TaskService {
    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }
}
```

Khi có nhiều Bean cùng implement một interface, `@Autowired` cần kết hợp với `@Qualifier` hoặc `@Primary`. Nếu không có candidate, lỗi thường là `NoSuchBeanDefinitionException`; nếu có nhiều candidate mà không chọn được, lỗi là `NoUniqueBeanDefinitionException`.

`@Autowired` có thể đặt ở field, nhưng field injection che giấu dependency, làm unit test khó hơn và không cho field `final`. Vì vậy constructor injection là lựa chọn mặc định; annotation chỉ thật sự cần khi class có nhiều constructor hoặc muốn đánh dấu injection point cụ thể.

### 6.1. Constructor injection

```java
@Service
public class TaskService {
    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }
}
```

Nếu class chỉ có một constructor, không cần `@Autowired`.

Constructor injection nên là mặc định vì dependency bắt buộc hiện rõ trong API của class, field có thể là `final`, object không tồn tại ở trạng thái thiếu dependency, và unit test có thể truyền fake/mock trực tiếp.

Setter injection chỉ nên dùng cho dependency thật sự tùy chọn. Field injection không nên dùng vì che giấu dependency, không dùng được `final`, khiến test khó hơn và dễ làm class phình trách nhiệm.

### 6.2. Chỉ có một candidate

```java
public interface TaskRepository {
}

@Repository
public class InMemoryTaskRepository implements TaskRepository {
}
```

Constructor cần `TaskRepository`; container thấy một bean phù hợp nên inject bean đó.

### 6.3. Nhiều candidate

Dùng `@Primary` khi có một default rõ ràng:

```java
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

@Repository
@Primary
public class PostgreSqlTaskRepository implements TaskRepository {
}
```

Dùng `@Qualifier` khi injection point cần một candidate cụ thể:

```java
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class TaskImportService {
    private final TaskRepository stagingRepository;

    public TaskImportService(
            @Qualifier("inMemoryTaskRepository") TaskRepository stagingRepository
    ) {
        this.stagingRepository = stagingRepository;
    }
}
```

Inject collection khi cần tất cả implementation:

```java
@Service
public class TaskValidationChain {
    private final List<TaskValidator> validators;

    public TaskValidationChain(List<TaskValidator> validators) {
        this.validators = List.copyOf(validators);
    }
}
```

Nếu thứ tự quan trọng, dùng `@Order` hoặc `Ordered`.

### 6.4. Optional dependency hoặc lấy theo nhu cầu

```java
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class OptionalAuditSupport {
    private final ObjectProvider<AuditLogger> auditLoggers;

    public OptionalAuditSupport(ObjectProvider<AuditLogger> auditLoggers) {
        this.auditLoggers = auditLoggers;
    }

    public void record(String message) {
        auditLoggers.ifAvailable(logger -> logger.log(message));
    }
}
```

Không biến mọi dependency thành optional chỉ để app khởi động được. Dependency cần cho correctness phải fail fast khi thiếu.

---

## 7. Bean scope

| Scope | Ý nghĩa | Dùng khi |
|---|---|---|
| `singleton` | Một instance cho mỗi Spring IoC container | Service, repository, controller stateless |
| `prototype` | Instance mới mỗi lần container cung cấp bean | Object stateful ngắn hạn, hiếm dùng |
| `request` | Một instance cho mỗi HTTP request | Request-specific context |
| `session` | Một instance cho mỗi HTTP session | Ứng dụng web stateful |
| `application` | Một instance cho mỗi `ServletContext` | Trạng thái cấp web application |
| `websocket` | Một instance cho mỗi WebSocket session | Dữ liệu gắn với WebSocket session |

### 7.1. Singleton

Singleton là mặc định. Spring singleton không có nghĩa là singleton toàn JVM; nó là một instance trong mỗi IoC container.

Singleton cũng không tự động thread-safe. Controller, service và repository singleton có thể được nhiều request thread gọi đồng thời. Vì vậy không lưu dữ liệu request/user trong field singleton.

Không nên:

```java
@Service
public class UnsafeTaskService {
    private String currentUserId;

    public void process(String userId) {
        this.currentUserId = userId;
    }
}
```

Nên giữ service stateless:

```java
@Service
public class TaskService {

    public void process(String userId) {
        String normalizedUserId = userId.trim();
    }
}
```

### 7.2. Prototype

```java
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ExportJob {
}
```

Mỗi lần container resolve `ExportJob`, một instance mới được tạo. Nhưng nếu prototype được inject trực tiếp vào singleton, dependency chỉ được resolve một lần khi singleton được tạo.

Muốn lấy instance mới theo nhu cầu:

```java
@Service
public class ExportService {
    private final ObjectProvider<ExportJob> exportJobs;

    public ExportService(ObjectProvider<ExportJob> exportJobs) {
        this.exportJobs = exportJobs;
    }

    public void export() {
        ExportJob job = exportJobs.getObject();
    }
}
```

### 7.3. Request scope và scoped proxy

```java
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component
@RequestScope
public class RequestMetadata {
    private final String requestId = UUID.randomUUID().toString();

    public String requestId() {
        return requestId;
    }
}
```

Khi bean scope ngắn như `request` hoặc `session` được inject vào singleton, Spring thường cần scoped proxy. Singleton giữ proxy ổn định; proxy tìm target thật tương ứng với request/session hiện tại.

---

## 8. Eager và lazy initialization

Singleton bean mặc định thường được tạo khi `ApplicationContext` khởi động. Điều này giúp phát hiện sớm thiếu dependency, circular dependency, configuration sai và lỗi trong initialization callback.

Lazy bean chỉ được tạo khi cần:

```java
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class ExpensiveReportEngine {
}
```

Spring Boot có thể bật lazy toàn ứng dụng:

```yaml
spring:
  main:
    lazy-initialization: true
```

### 8.1. Eager hoạt động như thế nào?

Với singleton bean, Spring thường tạo instance trong lúc `ApplicationContext` khởi động:

```text
ApplicationContext start
        ↓
Đọc BeanDefinition
        ↓
Tạo singleton eager
        ↓
Inject dependency
        ↓
Chạy lifecycle callback
        ↓
Context sẵn sàng nhận request
```

Ví dụ bean này được tạo ngay khi ứng dụng start:

```java
@Service
public class TaskService {

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }
}
```

Nếu `TaskRepository` không tồn tại hoặc constructor ném exception, ứng dụng thường fail ngay khi startup. Đây là đặc tính **fail fast**, hữu ích cho dependency bắt buộc.

### 8.2. Lazy hoạt động như thế nào?

Với lazy bean, Spring đăng ký `BeanDefinition` nhưng chưa tạo instance:

```text
ApplicationContext start
        ↓
Đăng ký BeanDefinition
        ↓
Chưa tạo bean lazy

Consumer gọi bean lần đầu
        ↓
Spring tạo instance
        ↓
Inject dependency và chạy lifecycle
        ↓
Trả bean cho consumer
```

```java
@Component
@Lazy
public class ExpensiveReportEngine {

    public ExpensiveReportEngine() {
        // Ví dụ: khởi tạo thư viện nặng hoặc client ít dùng.
    }
}
```

Nếu một singleton eager inject `ExpensiveReportEngine`, tùy cách proxy và điểm đặt `@Lazy`, Spring có thể tạo proxy để trì hoãn target thật. Vì vậy hãy kiểm tra behavior bằng test thay vì suy đoán từ annotation.

### 8.3. So sánh Eager và Lazy

| Tiêu chí | Eager | Lazy |
|---|---|---|
| Thời điểm tạo | Khi context khởi động | Khi được yêu cầu lần đầu |
| Phát hiện lỗi | Sớm, lúc startup | Muộn, thường ở runtime |
| Startup | Có thể lâu hơn | Có thể nhanh hơn |
| Request đầu tiên | Ổn định hơn | Có thể chịu chi phí khởi tạo |
| Tài nguyên | Tạo ngay cả khi chưa dùng | Chỉ tạo khi cần |
| Phù hợp | Dependency bắt buộc | Bean nặng hoặc ít dùng |

Ví dụ, `TaskRepository`, `TaskService` và security infrastructure thường nên eager để cấu hình sai làm ứng dụng fail sớm. Một report engine, client gọi hệ thống hiếm dùng hoặc integration tùy chọn có thể cân nhắc lazy.

### 8.4. Ưu điểm và rủi ro của lazy

Lazy có thể:

- Giảm thời gian startup.
- Giảm số object và connection được tạo ở môi trường không dùng feature đó.
- Có lợi cho ứng dụng có nhiều integration tùy chọn hoặc serverless cold start.

Nhưng lazy cũng có rủi ro:

- Lỗi thiếu dependency xuất hiện ở request đầu tiên.
- Request đầu tiên bị chậm do phải khởi tạo bean.
- Một lỗi ở integration hiếm dùng có thể không xuất hiện trong smoke test startup.
- Nhiều request đầu tiên đồng thời có thể tranh chi phí khởi tạo hoặc tạo tải đột biến lên dependency.
- Startup xanh không còn đồng nghĩa mọi bean quan trọng đã được kiểm tra.

Nếu bật lazy toàn ứng dụng, nên có smoke test chủ động gọi các endpoint/use case quan trọng để buộc bean được tạo trước khi đưa instance vào traffic.

### 8.5. `@Lazy` không phải thuốc chữa circular dependency

Ví dụ vòng phụ thuộc:

```text
TaskService → NotificationService → TaskService
```

Thêm `@Lazy` có thể trì hoãn một phía và làm context khởi động được, nhưng vòng dependency vẫn tồn tại. Khi chạy thật, code vẫn khó test, khó hiểu và dễ tạo side effect ngoài ý muốn.

Hướng refactor tốt hơn:

```text
TaskApplicationService
    ├── TaskService
    └── NotificationService
```

Hoặc phát `TaskAssignedEvent` sau commit để notification lắng nghe. Dùng `@Lazy` chỉ khi trì hoãn khởi tạo là chủ đích kiến trúc, không phải để che lỗi thiết kế.

### 8.6. `@Lazy` trên injection point

Có thể trì hoãn dependency cụ thể:

```java
@Service
public class ReportService {

    private final ExpensiveReportEngine engine;

    public ReportService(@Lazy ExpensiveReportEngine engine) {
        this.engine = engine;
    }
}
```

Trong trường hợp này, `ReportService` nhận một proxy lazy và target engine chỉ được tạo khi được gọi. Proxy có thể ảnh hưởng đến:

- Kiểu class khi debug.
- Việc dùng `final`/reflection đặc biệt.
- AOP và self-invocation.
- Exception xuất hiện ở thời điểm muộn hơn.

Ưu tiên đặt `@Lazy` ở bean hoặc configuration có chủ đích, ghi rõ lý do và viết test cho lần gọi đầu tiên.

### 8.7. Quy tắc chọn

1. Mặc định dùng eager cho dependency bắt buộc.
2. Đo startup trước khi bật lazy toàn cục.
3. Chỉ lazy bean thật sự nặng, ít dùng hoặc tùy chọn.
4. Đặt timeout cho client/resource được lazy khởi tạo.
5. Smoke test endpoint quan trọng nếu dùng lazy.
6. Không dùng lazy để xử lý circular dependency lâu dài.
7. Ghi freshness/initialization behavior trong tài liệu vận hành.

Đừng dùng lazy để che lỗi thiết kế. Lazy có thể giảm startup, nhưng lỗi cấu hình sẽ xuất hiện muộn hơn, thường ở request đầu tiên.

---

## 9. Bean lifecycle

Luồng tổng quát:

```text
1. Đọc BeanDefinition
2. Chọn constructor hoặc factory method
3. Tạo bean instance
4. Inject dependency và populate properties
5. Gọi Aware callbacks
6. BeanPostProcessor: before initialization
7. Chạy initialization callbacks
8. BeanPostProcessor: after initialization
9. Bean sẵn sàng sử dụng
10. ApplicationContext đóng hoặc scope kết thúc
11. Chạy destruction callbacks
```

`BeanPostProcessor` có thể trả về object khác object ban đầu, thường là proxy. Vì vậy consumer nhận được có thể là proxy bọc quanh target bean.

### 9.1. Constructor

Constructor nên dùng để gán dependency và kiểm tra invariant đơn giản. Không nên làm I/O nặng, start thread, gọi network hoặc dựa vào AOP/proxy của chính bean đang tạo.

### 9.2. Aware callbacks

Bean có thể implement các interface như `BeanNameAware`, `BeanFactoryAware`, `ApplicationContextAware`, `EnvironmentAware`.

```java
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.stereotype.Component;

@Component
public class BeanIdentityLogger implements BeanNameAware {
    private String beanName;

    @Override
    public void setBeanName(String beanName) {
        this.beanName = beanName;
    }
}
```

Chỉ dùng khi class thật sự cần hạ tầng container. Business service phụ thuộc `ApplicationContext` thường là dấu hiệu Service Locator.

### 9.3. Initialization callbacks

```java
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class TaskCache {

    @PostConstruct
    void initialize() {
        // Dependency đã được inject.
    }
}
```

Nếu cùng tồn tại với các method khác nhau, thứ tự initialization là:

```text
@PostConstruct
    -> InitializingBean.afterPropertiesSet()
    -> custom init method
```

Custom init method:

```java
@Bean(initMethod = "start")
TaskSchedulerClient taskSchedulerClient() {
    return new TaskSchedulerClient();
}
```

Ưu tiên `@PostConstruct` hoặc init method rõ ràng. `InitializingBean` làm class phụ thuộc Spring API.

### 9.4. Destruction callbacks

```java
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class TaskEventWorker {

    @PreDestroy
    void shutdown() {
        // Dừng worker, flush buffer, giải phóng tài nguyên.
    }
}
```

Nếu cùng tồn tại với các method khác nhau, thứ tự destruction là:

```text
@PreDestroy
    -> DisposableBean.destroy()
    -> custom destroy method
```

Với class ngoài:

```java
@Bean(destroyMethod = "close")
ExternalClient externalClient() {
    return new ExternalClient();
}
```

`@PreDestroy` không phải bảo đảm tuyệt đối. Callback có thể không chạy nếu process bị kill cưỡng bức, JVM crash hoặc máy mất điện.

### 9.5. Prototype là ngoại lệ quan trọng

Spring tạo prototype bean, inject dependency và gọi initialization callbacks. Nhưng sau khi giao object cho client, container không quản lý đầy đủ phần destruction.

Vì vậy `@PreDestroy` của prototype thường không được container tự gọi. Bên sử dụng phải tự cleanup hoặc chọn thiết kế khác. Đừng dùng prototype để giữ connection, file handle hoặc tài nguyên đắt đỏ nếu chưa có ownership/cleanup strategy rõ ràng.

### 9.6. Ví dụ quan sát lifecycle

```java
package com.example.taskapi.lifecycle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.stereotype.Component;

@Component
public class LifecycleProbe implements BeanNameAware {
    private String beanName;

    public LifecycleProbe() {
        System.out.println("1. constructor");
    }

    @Override
    public void setBeanName(String beanName) {
        this.beanName = beanName;
        System.out.println("2. BeanNameAware: " + beanName);
    }

    @PostConstruct
    void initialize() {
        System.out.println("3. @PostConstruct: " + beanName);
    }

    @PreDestroy
    void destroy() {
        System.out.println("4. @PreDestroy: " + beanName);
    }
}
```

Chạy rồi dừng app bình thường để quan sát log. Sau đó thử đổi thành prototype và so sánh destruction callback.

---

## 10. Extension point và proxy

### 10.1. BeanFactoryPostProcessor

`BeanFactoryPostProcessor` làm việc với **bean definitions trước khi phần lớn bean instance được tạo**. Nó có thể đọc, sửa metadata hoặc đăng ký thêm definition. Không dùng nó như business service.

### 10.2. BeanPostProcessor

`BeanPostProcessor` làm việc với **bean instances trong quá trình initialization**.

```java
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class BeanTracingPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(
            Object bean,
            String beanName
    ) throws BeansException {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(
            Object bean,
            String beanName
    ) throws BeansException {
        return bean;
    }
}
```

Post-processor phải trả về bean gốc hoặc object thay thế có chủ đích. Nhiều tính năng Spring như annotation lifecycle, AOP proxy, transaction, cache và async được xây quanh cơ chế tương tự.

### 10.3. Proxy và self-invocation

Spring có thể trả về proxy thay vì target object gốc. Điều này thường xảy ra với transaction, cache, security, async hoặc scoped proxy.

Hệ quả:

- Không dùng `bean.getClass()` để quyết định business logic.
- Nên inject theo interface hoặc abstraction khi hợp lý.
- Method gọi từ object khác đi qua proxy.
- Method gọi từ chính object đó sang method khác của nó là self-invocation và có thể không đi qua proxy.
- Annotation như `@Transactional`, `@Async`, `@Cacheable` không nên được kỳ vọng hoạt động khi gọi từ constructor hoặc `@PostConstruct` của chính bean.

---

## 11. FactoryBean không phải BeanFactory

Hai tên này dễ nhầm:

- `BeanFactory`: container tạo và cung cấp bean.
- `FactoryBean<T>`: một bean đặc biệt có nhiệm vụ tạo ra object khác.

```java
import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

@Component("taskClient")
public class TaskClientFactoryBean implements FactoryBean<TaskClient> {

    @Override
    public TaskClient getObject() {
        return new TaskClient("https://tasks.example");
    }

    @Override
    public Class<?> getObjectType() {
        return TaskClient.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }
}
```

Khi lấy bean:

```java
context.getBean("taskClient");   // TaskClient product
context.getBean("&taskClient");  // TaskClientFactoryBean
```

Với application code thông thường, method `@Bean` thường đơn giản và dễ hiểu hơn `FactoryBean`.

---

## 12. Circular dependency

Ví dụ:

```text
TaskService
    -> NotificationService
    -> TaskService
```

Với constructor injection, container không thể tạo object đầu tiên vì mỗi object đều cần object còn lại trước.

Đừng chữa bằng field injection hoặc `@Lazy` một cách máy móc. Circular dependency thường nói rằng hai class chia trách nhiệm sai, thiếu abstraction trung gian, orchestration nằm sai layer hoặc hai use case đang bị trộn vào nhau.

Một hướng refactor:

```text
TaskApplicationService
    -> TaskService
    -> NotificationService
```

Application service orchestration gọi hai service độc lập, thay vì để chúng phụ thuộc vòng tròn.

---

## 13. Lỗi bean thường gặp

### 13.1. `NoSuchBeanDefinitionException`

Nguyên nhân hay gặp: class chưa có stereotype annotation, method tạo object chưa có `@Bean`, class nằm ngoài component scan, profile/condition không active hoặc dependency type không khớp.

### 13.2. `NoUniqueBeanDefinitionException`

Có nhiều bean phù hợp nhưng injection point cần một bean. Hãy kiểm tra theo thứ tự:

1. Có thật sự cần nhiều implementation không?
2. Có default rõ ràng để dùng `@Primary` không?
3. Injection point có cần candidate cụ thể để dùng `@Qualifier` không?
4. Có cần inject collection tất cả implementation không?
5. Các implementation có nên tách theo profile/condition không?

### 13.3. Lifecycle callback không chạy

Kiểm tra:

- Object có thật sự là Spring bean không, hay được tạo bằng `new`.
- Import đúng `jakarta.annotation.PostConstruct` và `jakarta.annotation.PreDestroy` chưa.
- Context có shutdown bình thường không.
- Bean có scope prototype không.
- Method có cấu hình sai hoặc không thể gọi không.

### 13.4. Annotation framework không có tác dụng

Nguyên nhân thường gặp: object không phải bean, method bị self-invoke nên không đi qua proxy, bean bị inject theo target không phải proxy do cấu hình sai, hoặc annotation đặt ở layer không được framework xử lý.

### 13.5. Startup chậm

Đừng vội bật lazy toàn cục. Hãy kiểm tra constructor hoặc `@PostConstruct` có làm I/O nặng không, component scan có quá rộng không, bean nào tạo client/resource quá sớm không, migration hoặc external dependency có chậm không.

---

## 14. Debug bean trong Spring Boot

Bật condition evaluation report:

```bash
./mvnw spring-boot:run -Dspring-boot.run.arguments=--debug
```

Liệt kê bean ở local:

```java
import java.util.Arrays;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class BeanInventory implements ApplicationRunner {
    private final ApplicationContext context;

    public BeanInventory(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void run(ApplicationArguments args) {
        Arrays.stream(context.getBeanDefinitionNames())
                .sorted()
                .forEach(System.out::println);
    }
}
```

Actuator có `beans` endpoint, nhưng không expose công khai. Endpoint này có thể tiết lộ cấu trúc ứng dụng. Production cần authentication, authorization và exposure policy chặt chẽ.

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,beans
```

---

## 15. Kiểm thử bean đúng mức

### 15.1. Unit test không cần Spring

Nếu class dùng constructor injection, ưu tiên test trực tiếp:

```java
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class TaskServiceTest {

    @Test
    void createsTask() {
        TaskRepository repository = new InMemoryTaskRepository();
        TaskService service = new TaskService(repository);

        Task task = service.create("Learn Spring Bean");

        assertThat(task.title()).isEqualTo("Learn Spring Bean");
    }
}
```

Test này nhanh và tập trung vào business behavior.

### 15.2. Test wiring bằng `ApplicationContextRunner`

Khi cần test bean registration, condition hoặc configuration:

```java
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class TaskConfigurationTest {
    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TaskConfiguration.class);

    @Test
    void registersTaskService() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(TaskService.class);
            assertThat(context).hasSingleBean(TaskRepository.class);
        });
    }
}
```

Đây là test wiring/configuration, không thay thế unit test business logic.

### 15.3. Integration test toàn context

```java
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class TaskApiApplicationTests {

    @Test
    void contextLoads() {
    }
}
```

Test này xác nhận dependency graph tổng thể khởi động được, nhưng không chứng minh từng use case đúng.

---

## 16. Quy tắc thiết kế bean

1. Ưu tiên constructor injection.
2. Dependency bắt buộc phải fail fast khi thiếu.
3. Controller, service và repository singleton nên stateless.
4. Không đặt dữ liệu riêng của request trong field singleton.
5. Dùng stereotype đúng vai trò.
6. Dùng `@Bean` cho third-party class hoặc custom construction.
7. Phụ thuộc abstraction khi có nhiều implementation hoặc cần thay thế trong test.
8. Không inject `ApplicationContext` vào business service nếu constructor injection giải quyết được.
9. Không dùng lazy để che lỗi thiết kế.
10. Không tạo circular dependency.
11. Lifecycle callback nên ngắn, rõ lỗi và không âm thầm nuốt exception.
12. Tài nguyên phải có ownership và cleanup strategy rõ ràng.
13. Không giả định singleton đồng nghĩa thread-safe.
14. Không expose Actuator bean/config endpoint công khai.
15. Test business logic không khởi động context khi không cần.

---

## 17. Bài tập thực hành

### Bài 1 — Nhận diện bean

Với các class `TaskController`, `TaskService`, `TaskRepository`, `Task`, `CreateTaskRequest`, `TaskProperties`, `ExternalTaskClient`, `Money`, hãy quyết định class nào nên là bean, đăng ký bằng cách nào và vì sao.

### Bài 2 — Nhiều implementation

Tạo `EmailNotificationSender` và `ConsoleNotificationSender`. Làm ba phiên bản: chọn default bằng `@Primary`, chọn cụ thể bằng `@Qualifier`, inject `List<NotificationSender>` để gửi qua tất cả channel. Viết test chứng minh bean được chọn đúng.

### Bài 3 — Quan sát lifecycle

Tạo `LifecycleProbe` có constructor log, `BeanNameAware`, `@PostConstruct`, `@PreDestroy`. Chạy và dừng app bình thường, ghi lại thứ tự. Sau đó đổi bean thành prototype và quan sát khác biệt ở destruction.

### Bài 4 — Scope và thread safety

Tạo singleton service có counter mutable, gửi nhiều request đồng thời và quan sát kết quả sai. Sau đó giải thích race condition và refactor về thiết kế stateless nếu có thể.

### Bài 5 — Refactor circular dependency

Tạo ví dụ `TaskService <-> NotificationService`, xác nhận context không khởi động được, rồi refactor bằng application service orchestration mà không dùng `@Lazy`.

### Bài 6 — Bean của thư viện ngoài

Tạo `Clock` bean bằng `@Bean`, inject vào service và dùng `Clock.fixed(...)` trong unit test để kết quả thời gian có tính xác định.

---

## 18. Câu hỏi tự kiểm tra

1. Bean khác object thường ở điểm nào?
2. Bean definition khác bean instance ra sao?
3. Bean name mặc định được tạo như thế nào?
4. Khi nào dùng `@Component`, khi nào dùng `@Bean`?
5. Vì sao constructor injection là lựa chọn mặc định?
6. Spring làm gì khi có hai bean cùng implement một interface?
7. `@Primary` khác `@Qualifier` thế nào?
8. Inject `List<SomeInterface>` có ý nghĩa gì?
9. Singleton bean có thread-safe không?
10. Vì sao prototype inject trực tiếp vào singleton không tạo instance mới ở mỗi method call?
11. Scoped proxy giải quyết vấn đề gì?
12. Lazy initialization có lợi và hại gì?
13. Thứ tự ba initialization callback là gì?
14. Thứ tự ba destruction callback là gì?
15. Vì sao `@PreDestroy` của prototype không được container tự gọi?
16. `BeanPostProcessor` khác `BeanFactoryPostProcessor` ra sao?
17. `FactoryBean` khác `BeanFactory` ra sao?
18. Vì sao self-invocation có thể bỏ qua proxy?
19. Circular dependency phản ánh vấn đề thiết kế nào?
20. Khi nào cần khởi động `ApplicationContext` trong test?

---

## 19. Tóm tắt

Một Spring bean là object nằm dưới sự quản lý của IoC container. Container không chỉ gọi constructor mà còn:

```text
đọc BeanDefinition
    -> tạo instance
    -> inject dependency
    -> chạy lifecycle callbacks
    -> có thể tạo proxy
    -> cung cấp bean cho consumer
    -> hủy bean khi scope/context kết thúc
```

Những ý quan trọng nhất:

- Bean vẫn là object Java, nhưng được container quản lý.
- Constructor injection làm dependency rõ ràng và test dễ.
- Singleton là mặc định nhưng không tự động thread-safe.
- Scope ngắn inject vào scope dài cần provider hoặc proxy phù hợp.
- Lifecycle callback dùng để quản lý tài nguyên, không phải nơi chứa workflow nặng.
- Business code nên phụ thuộc hành vi và abstraction, không phụ thuộc container internals.
- Dependency graph khó hiểu thường là vấn đề thiết kế, không phải thiếu annotation.

## Tài liệu chính thức

- [Spring IoC Container and Beans](https://docs.spring.io/spring-framework/reference/core/beans.html)
- [Classpath Scanning and Managed Components](https://docs.spring.io/spring-framework/reference/core/beans/classpath-scanning.html)
- [Java-based Container Configuration](https://docs.spring.io/spring-framework/reference/core/beans/java.html)
- [Bean Scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html)
- [Lifecycle Callbacks](https://docs.spring.io/spring-framework/reference/core/beans/factory-nature.html)
- [Container Extension Points](https://docs.spring.io/spring-framework/reference/core/beans/factory-extension.html)
- [Spring Boot `@SpringBootApplication`](https://docs.spring.io/spring-boot/reference/using/using-the-springbootapplication-annotation.html)
