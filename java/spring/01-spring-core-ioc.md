# Tuần 1: Spring Core & IoC

> Tài liệu tổng hợp kiến thức mục 1 trong lộ trình [spring-boot-roadmap.md](spring-boot-roadmap.md). Nội dung đã được đối chiếu với Spring Framework Reference (6.2) qua Context7 để đảm bảo chính xác về thứ tự lifecycle và cơ chế xử lý bên trong container.

---

## 1. Inversion of Control (IoC) & Dependency Injection (DI)

### 1.1 IoC là gì
Bình thường, 1 class tự `new` ra các object nó cần (dependency) → class đó bị **coupling chặt** với implementation cụ thể, khó test, khó thay đổi.

**Inversion of Control (IoC)** đảo ngược trách nhiệm đó: thay vì object tự tạo dependency, một **container** (Spring `ApplicationContext`) đứng ra tạo sẵn các object (gọi là **bean**), rồi "tiêm" (inject) chúng vào nơi cần dùng. Class không còn quyết định "dùng implementation nào" — nó chỉ khai báo "tôi cần 1 thứ có kiểu này", còn container quyết định đưa instance nào vào.

```java
// KHÔNG có IoC: OrderService tự new ra dependency → coupling chặt
class OrderService {
    private final PaymentGateway gateway = new StripeGateway(); // hard-code implementation
}

// CÓ IoC: Spring container tạo sẵn PaymentGateway rồi tiêm vào
class OrderService {
    private final PaymentGateway gateway; // chỉ biết interface, không biết implementation cụ thể

    public OrderService(PaymentGateway gateway) { // Spring tự inject vào đây
        this.gateway = gateway;
    }
}
```

**Dependency Injection (DI)** là kỹ thuật cụ thể để hiện thực IoC — "tiêm" dependency vào object thay vì để object tự tạo.

### 1.2 Ba cách inject

```java
class OrderService {
    private final PaymentGateway gateway;
    private NotificationService notificationService;
    @Autowired
    private InventoryService inventoryService; // field injection

    // Constructor injection — khuyến nghị
    public OrderService(PaymentGateway gateway) {
        this.gateway = gateway;
    }

    // Setter injection
    @Autowired
    public void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }
}
```

| Cách inject | Đặc điểm |
|---|---|
| **Constructor injection** | Dependency truyền qua constructor, field có thể khai báo `final`. Từ 1 constructor duy nhất, Spring tự inject mà **không cần** `@Autowired` (kể từ Spring 4.3+, nếu class chỉ có đúng 1 constructor) |
| **Setter injection** | Dependency inject qua method `set...()`, dùng khi dependency là optional hoặc cần thay đổi sau khi khởi tạo |
| **Field injection** | Inject thẳng vào field bằng `@Autowired`, ngắn gọn nhất nhưng **không khuyến nghị** dùng trong code production |

### 1.3 Vì sao ưu tiên Constructor Injection
- **Immutability**: field có thể khai báo `final`, đảm bảo dependency không đổi sau khi object được tạo.
- **Dễ test**: có thể `new OrderService(mockGateway)` trực tiếp trong unit test mà không cần khởi động Spring container hay dùng reflection để set field.
- **Phát hiện circular dependency sớm**: nếu A và B phụ thuộc lẫn nhau qua constructor, Spring ném lỗi ngay lúc khởi động ứng dụng (xem mục 5) — thay vì ẩn lỗi thiết kế.
- **Không cần reflection**: field injection cần Spring dùng reflection để set giá trị vào field private, phá vỡ encapsulation ở một mức độ nhất định và chậm hơn (dù không đáng kể).
- **Rõ ràng về dependency bắt buộc**: nhìn vào constructor là biết ngay class cần những gì để hoạt động, không phải đọc hết cả class tìm field có `@Autowired`.

### 1.4 `@Autowired`, `@Qualifier`, `@Primary`
Khi có **nhiều bean cùng type** (VD 2 class cùng implement `PaymentGateway`), Spring không biết chọn bean nào → ném `NoUniqueBeanDefinitionException`. Cách giải quyết:

```java
interface PaymentGateway {}

@Service
class StripeGateway implements PaymentGateway {}

@Service
@Primary // ưu tiên bean này khi không chỉ định rõ
class PaypalGateway implements PaymentGateway {}

class OrderService {
    private final PaymentGateway gateway;

    public OrderService(@Qualifier("stripeGateway") PaymentGateway gateway) { // chỉ định rõ bean nào
        this.gateway = gateway;
    }
}
```
- `@Primary`: đánh dấu 1 bean là lựa chọn **mặc định** khi có nhiều candidate.
- `@Qualifier("beanName")`: chỉ định **chính xác** bean nào cần inject, ưu tiên cao hơn `@Primary` khi cả 2 cùng tồn tại.
- Tên bean mặc định = tên class viết thường chữ cái đầu (`StripeGateway` → bean name `stripeGateway`), trừ khi đặt tên tường minh qua `@Service("customName")`.

---

## 2. Bean & Component Scanning

### 2.1 `@Component`, `@Service`, `@Repository`, `@Controller`
Cả 4 annotation đều là **stereotype annotation**, về bản chất đều được Spring nhận diện là 1 bean (đều "meta-annotated" bởi `@Component`). Khác nhau chủ yếu về **ngữ nghĩa** (để code dễ đọc, phân tầng rõ ràng) và 1 số hành vi bổ sung:

| Annotation | Dùng cho | Hành vi đặc biệt |
|---|---|---|
| `@Component` | Bean chung chung, không thuộc tầng cụ thể | Không có |
| `@Service` | Tầng business logic | Về mặt kỹ thuật giống `@Component`, chỉ khác ngữ nghĩa |
| `@Repository` | Tầng truy cập dữ liệu (DAO) | **Exception translation**: tự động convert exception đặc thù của driver/ORM (VD `SQLException`) thành `DataAccessException` (unchecked) của Spring, giúp tầng trên không phải phụ thuộc vào exception riêng của từng công nghệ persistence |
| `@Controller` | Tầng xử lý HTTP request (Spring MVC) | Được `DispatcherServlet` nhận diện để map request tới method xử lý |

### 2.2 `@Configuration` + `@Bean`
Dùng khi cần khai báo bean thủ công — thường gặp khi:
- Bean đến từ **thư viện bên ngoài** (không sửa được source để thêm `@Component`).
- Cần **cấu hình phức tạp** trước khi trả về instance (gọi builder, set nhiều property...).

```java
@Configuration
class AppConfig {
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(5))
            .build();
    }
}
```

### 2.3 `@ComponentScan`
Spring chỉ tạo bean cho những class nằm trong **phạm vi được quét**. `@SpringBootApplication` đã ngầm định `@ComponentScan` quét từ package chứa class `main` trở xuống (mọi package con) — đây là lý do class `main` của Spring Boot app nên đặt ở package gốc, không đặt sâu bên trong 1 package con cụ thể (nếu không class ở package ngang hàng sẽ không được quét).

---

## 3. Bean Scope

| Scope | Mô tả |
|---|---|
| `singleton` | **Mặc định**. Container tạo đúng 1 instance, dùng chung cho mọi nơi inject bean này trong toàn bộ `ApplicationContext` |
| `prototype` | Mỗi lần `getBean()` hoặc mỗi lần được inject vào 1 bean khác, Spring tạo **1 instance mới** |
| `request` | 1 instance riêng cho **mỗi HTTP request** (chỉ có ý nghĩa trong web application context) |
| `session` | 1 instance riêng cho **mỗi HTTP session** (chỉ có ý nghĩa trong web application context) |

```java
@Component
@Scope("prototype")
class ReportGenerator {
    // mỗi lần cần 1 bản báo cáo mới, Spring tạo instance riêng — tránh chia sẻ state giữa các lần dùng
}
```

> **Lưu ý dễ mắc lỗi**: inject 1 bean `prototype` vào 1 bean `singleton` bằng constructor/field injection thông thường sẽ khiến bean `singleton` chỉ giữ **đúng 1 instance prototype được tạo lúc khởi tạo** (vì singleton chỉ được tạo 1 lần) — mất ý nghĩa "mỗi lần dùng 1 instance mới". Muốn đúng hành vi phải dùng `ObjectProvider<T>`/`ObjectFactory<T>` hoặc `@Lookup` method để lấy instance mới mỗi lần cần.

---

## 4. Bean Lifecycle

### 4.1 Thứ tự đầy đủ khi 1 bean được khởi tạo

```
1. Instantiate (gọi constructor)
2. Populate properties (Dependency Injection — set field/setter)
3. Aware interfaces (nếu bean implement):
   BeanNameAware → BeanClassLoaderAware → BeanFactoryAware → ApplicationContextAware ...
4. BeanPostProcessor.postProcessBeforeInitialization()
5. Initialization callback (thứ tự khi có nhiều cơ chế cùng lúc):
   @PostConstruct  →  InitializingBean.afterPropertiesSet()  →  custom init-method
6. BeanPostProcessor.postProcessAfterInitialization()
7. Bean sẵn sàng sử dụng
   ...
8. Khi container đóng — Destruction callback (thứ tự tương tự lúc init):
   @PreDestroy  →  DisposableBean.destroy()  →  custom destroy-method
```
> Thứ tự init (`@PostConstruct` → `InitializingBean` → custom init-method) và thứ tự destroy (`@PreDestroy` → `DisposableBean` → custom destroy-method) đã được xác nhận trực tiếp từ Spring Framework Reference — đây là điểm nhiều tài liệu không chính thức ghi sai thứ tự.

### 4.2 Ví dụ minh hoạ đầy đủ

```java
@Component
class CacheWarmer implements InitializingBean, DisposableBean,
        BeanNameAware, ApplicationContextAware {

    private ApplicationContext context;

    @Override
    public void setBeanName(String name) {
        System.out.println("1. BeanNameAware: " + name);
    }

    @Override
    public void setApplicationContext(ApplicationContext context) {
        System.out.println("2. ApplicationContextAware");
        this.context = context;
    }

    @PostConstruct
    public void postConstruct() {
        System.out.println("3. @PostConstruct");
    }

    @Override
    public void afterPropertiesSet() {
        System.out.println("4. InitializingBean.afterPropertiesSet()");
    }

    @PreDestroy
    public void preDestroy() {
        System.out.println("5. @PreDestroy");
    }

    @Override
    public void destroy() {
        System.out.println("6. DisposableBean.destroy()");
    }
}
```

### 4.3 `BeanPostProcessor`
Can thiệp vào **mọi bean** trong container, trước và sau bước initialization — thường dùng để xây dựng cơ chế xuyên suốt (cross-cutting), ví dụ tự động wrap bean bằng proxy (chính là cách `@Transactional`, `@Async`, AOP hoạt động bên dưới).

```java
@Component
class LoggingBeanPostProcessor implements BeanPostProcessor {
    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        System.out.println("Before init: " + beanName);
        return bean; // có thể trả về 1 object khác (VD proxy) thay vì bean gốc
    }
}
```

### 4.4 Custom Event: `ApplicationEvent` + `@EventListener`
Cho phép các bean giao tiếp với nhau mà không cần phụ thuộc trực tiếp (loose coupling) — publisher không cần biết ai đang lắng nghe.

```java
class OrderPlacedEvent extends ApplicationEvent {
    public OrderPlacedEvent(Object source) { super(source); }
}

@Service
class OrderService {
    private final ApplicationEventPublisher publisher;

    OrderService(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    void placeOrder() {
        // ... xử lý logic đặt hàng
        publisher.publishEvent(new OrderPlacedEvent(this));
    }
}

@Component
class EmailNotifier {
    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        System.out.println("Gửi email xác nhận đơn hàng");
    }
}
```

---

## 5. Circular Dependency

**Nguyên nhân**: `A` phụ thuộc `B`, `B` phụ thuộc `A` (trực tiếp hoặc qua 1 chuỗi bean trung gian).

```java
@Service
class ServiceA {
    ServiceA(ServiceB b) {} // cần B để tạo A
}

@Service
class ServiceB {
    ServiceB(ServiceA a) {} // cần A để tạo B → circular!
}
```

- **Với constructor injection**: Spring phát hiện ngay lúc khởi động ứng dụng và ném `BeanCurrentlyInCreationException` — vì để tạo xong `A` cần có `B` hoàn chỉnh, nhưng để tạo `B` lại cần `A` hoàn chỉnh, không có điểm bắt đầu hợp lệ.
- **Với setter/field injection**: Spring có thể xử lý được nhờ cơ chế **3-level cache** — tạo `A` bằng constructor rỗng trước (chưa gọi setter), đưa "early reference" của `A` vào cache, dùng nó để tạo xong `B`, rồi quay lại set `B` vào `A` qua setter. Vì vậy ứng dụng vẫn chạy được, nhưng đây là dấu hiệu thiết kế có vấn đề, không phải giải pháp nên tận dụng.

**Cách xử lý đúng**: refactor lại thiết kế — thường là tách phần logic gây phụ thuộc chéo ra 1 class thứ 3 mà cả `A` và `B` cùng phụ thuộc vào (loại bỏ cạnh vòng lặp trong dependency graph). **Tránh dùng `@Lazy`** như một cách "chữa cháy" (trì hoãn việc resolve bean tới khi thực sự cần dùng) — nó che giấu vấn đề thiết kế thay vì giải quyết tận gốc, và có thể gây lỗi khó debug khi bean thực sự được truy cập lần đầu ở 1 thời điểm không ngờ tới.

---

## Câu hỏi tự kiểm tra
1. Vì sao constructor injection giúp phát hiện circular dependency sớm hơn setter injection? Giải thích cơ chế "3-level cache" giúp setter injection "sống sót" qua circular dependency.
2. `@Component`, `@Service`, `@Repository` khác nhau như thế nào về mặt kỹ thuật (không chỉ về tên gọi)? Vì sao `@Repository` lại có hành vi exception translation mà 2 cái kia không có?
3. Khi nào nên dùng `@Configuration` + `@Bean` thay vì `@Component`? Cho 1 ví dụ cụ thể không thể dùng `@Component`.
4. Điều gì xảy ra khi inject 1 bean `prototype` vào 1 bean `singleton` bằng constructor injection thông thường? Cách khắc phục đúng là gì?
5. Liệt kê đúng thứ tự các bước khi 1 bean được khởi tạo, từ lúc constructor chạy tới lúc sẵn sàng sử dụng.
6. `BeanPostProcessor` khác gì so với `@PostConstruct`? Tại sao các cơ chế AOP (như `@Transactional`) lại cần dùng `BeanPostProcessor` thay vì chỉ dùng annotation thông thường?

## Bài tập luyện tập
- Viết 2 implementation cho 1 interface `NotificationService` (`EmailNotificationService`, `SmsNotificationService`), dùng `@Primary` cho 1 cái và `@Qualifier` để inject cái còn lại vào 1 class khác — chứng minh cả 2 cơ chế hoạt động đúng.
- Viết 1 bean implement đủ `BeanNameAware`, `InitializingBean`, `DisposableBean` kèm `@PostConstruct`/`@PreDestroy` — in log ra console để tự kiểm chứng đúng thứ tự lifecycle đã học ở mục 4.1.
- Tạo tình huống circular dependency bằng constructor injection giữa 2 `@Service`, chạy thử để thấy `BeanCurrentlyInCreationException`, sau đó refactor bằng cách tách 1 class thứ 3 chứa phần logic dùng chung để loại bỏ vòng lặp phụ thuộc (không dùng `@Lazy`).
- Viết 1 `ApplicationEvent` tùy chỉnh (VD `UserRegisteredEvent`) và ít nhất 2 `@EventListener` khác nhau cùng lắng nghe (VD gửi email chào mừng + ghi log audit) — minh chứng 1 publisher có thể có nhiều subscriber độc lập mà không cần biết về nhau.
