# 11.3. Quartz Scheduler

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#io.quartz>

Spring Boot cung cấp một số tiện ích để làm việc với [Quartz scheduler](https://www.quartz-scheduler.org/), bao gồm "Starter" `spring-boot-starter-quartz`. Nếu có Quartz, một `Scheduler` sẽ được tự động cấu hình (thông qua abstraction `SchedulerFactoryBean`).

Các bean thuộc các kiểu sau được tự động nhận diện và gắn với `Scheduler`:

- `JobDetail`: định nghĩa một Job cụ thể. Các instance `JobDetail` có thể được xây dựng bằng API `JobBuilder`.
- `Calendar`.
- `Trigger`: định nghĩa khi nào một job cụ thể được kích hoạt.

Theo mặc định, một `JobStore` in-memory được sử dụng. Tuy nhiên, có thể cấu hình một kho lưu dựa trên JDBC nếu có bean `DataSource` trong ứng dụng và nếu property `spring.quartz.job-store-type` được cấu hình tương ứng, như trong ví dụ sau:

**Properties**

```properties
spring.quartz.job-store-type=jdbc
```

**Yaml**

```yaml
spring:
  quartz:
    job-store-type: "jdbc"
```

Khi dùng kho lưu JDBC, schema có thể được khởi tạo khi khởi động, như trong ví dụ sau:

**Properties**

```properties
spring.quartz.jdbc.initialize-schema=always
```

**Yaml**

```yaml
spring:
  quartz:
    jdbc:
      initialize-schema: "always"
```

> **CẢNH BÁO (WARNING):** Theo mặc định, database được phát hiện và khởi tạo bằng các script tiêu chuẩn đi kèm thư viện Quartz. Các script này xóa các bảng hiện có, xóa tất cả các trigger mỗi lần khởi động lại. Cũng có thể cung cấp một script tùy chỉnh bằng cách đặt property `spring.quartz.jdbc.schema`.

Để Quartz dùng một `DataSource` khác với `DataSource` chính của ứng dụng, hãy khai báo một bean `DataSource`, đánh annotation `@QuartzDataSource` lên phương thức `@Bean` của nó. Làm như vậy đảm bảo `DataSource` dành riêng cho Quartz được dùng bởi cả `SchedulerFactoryBean` và cho việc khởi tạo schema. Tương tự, để Quartz dùng một `TransactionManager` khác với `TransactionManager` chính của ứng dụng, hãy khai báo một bean `TransactionManager`, đánh annotation `@QuartzTransactionManager` lên phương thức `@Bean` của nó.

Theo mặc định, các job được tạo bởi cấu hình sẽ không ghi đè các job đã được đăng ký mà đã được đọc từ một kho lưu job bền vững. Để cho phép ghi đè các định nghĩa job hiện có, hãy đặt property `spring.quartz.overwrite-existing-jobs`.

Cấu hình Quartz Scheduler có thể được tùy chỉnh bằng các property `spring.quartz` và các bean `SchedulerFactoryBeanCustomizer`, cho phép tùy chỉnh `SchedulerFactoryBean` bằng code. Các configuration property Quartz nâng cao có thể được tùy chỉnh bằng `spring.quartz.properties.*`.

> **LƯU Ý (NOTE):** Đặc biệt, một bean `Executor` không được gắn với scheduler vì Quartz cung cấp cách cấu hình scheduler thông qua `spring.quartz.properties`. Nếu bạn cần tùy chỉnh task executor, hãy cân nhắc hiện thực `SchedulerFactoryBeanCustomizer`.

Các job có thể định nghĩa setter để inject các property của data map. Các bean thông thường cũng có thể được inject theo cách tương tự, như trong ví dụ sau:

**Java**

```java
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import org.springframework.scheduling.quartz.QuartzJobBean;

public class MySampleJob extends QuartzJobBean {

    // fields ...

    private MyService myService;

    private String name;

    // Inject "MyService" bean
    public void setMyService(MyService myService) {
        this.myService = myService;
    }

    // Inject the "name" job data property
    public void setName(String name) {
        this.name = name;
    }

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        this.myService.someMethod(context.getFireTime(), this.name);
    }

}
```

**Kotlin**

```kotlin
import org.quartz.JobExecutionContext
import org.springframework.scheduling.quartz.QuartzJobBean

class MySampleJob : QuartzJobBean() {

    // fields ...

    private var myService: MyService? = null

    private var name: String? = null

    // Inject "MyService" bean
    fun setMyService(myService: MyService?) {
        this.myService = myService
    }

    // Inject the "name" job data property
    fun setName(name: String?) {
        this.name = name
    }

    override fun executeInternal(context: JobExecutionContext) {
        myService!!.someMethod(context.fireTime, name)
    }

}
```
