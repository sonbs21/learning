# 9.1.2. Sử dụng JdbcTemplate (Using JdbcTemplate)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.sql.jdbc-template>

Các class `JdbcTemplate` và `NamedParameterJdbcTemplate` của Spring được tự động cấu hình, và bạn có thể `@Autowire` chúng trực tiếp vào các bean của riêng mình, như trong ví dụ sau:

**Java**

```java
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    private final JdbcTemplate jdbcTemplate;

    public MyBean(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void doSomething() {
        this.jdbcTemplate ...
    }

}
```

**Kotlin**

```kotlin
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component
class MyBean(private val jdbcTemplate: JdbcTemplate) {

    fun doSomething() {
        jdbcTemplate.execute("delete from customer")
    }

}
```

Bạn có thể tùy chỉnh một số property của template bằng các property `spring.jdbc.template.*`, như trong ví dụ sau:

**Properties**

```properties
spring.jdbc.template.max-rows=500
```

**Yaml**

```yaml
spring:
  jdbc:
    template:
      max-rows: 500
```

> **LƯU Ý (NOTE):** `NamedParameterJdbcTemplate` tái sử dụng cùng một instance `JdbcTemplate` ở phía sau. Nếu có nhiều hơn một `JdbcTemplate` được định nghĩa và không có ứng viên chính (primary) nào, `NamedParameterJdbcTemplate` sẽ không được tự động cấu hình.
