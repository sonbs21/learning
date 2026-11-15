# 9.1.3. Sử dụng JdbcClient (Using JdbcClient)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.sql.jdbc-client>

`JdbcClient` của Spring được tự động cấu hình dựa trên sự hiện diện của một `NamedParameterJdbcTemplate`. Bạn cũng có thể inject nó trực tiếp vào các bean của riêng mình, như trong ví dụ sau:

**Java**

```java
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class MyBean {

    private final JdbcClient jdbcClient;

    public MyBean(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void doSomething() {
        this.jdbcClient ...
    }

}
```

**Kotlin**

```kotlin
import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

@Component
class MyBean(private val jdbcClient: JdbcClient) {

    fun doSomething() {
        jdbcClient.sql("delete from customer").update()
    }

}
```

Nếu bạn dựa vào auto-configuration để tạo `JdbcTemplate` bên dưới, mọi tùy chỉnh bằng các property `spring.jdbc.template.*` cũng sẽ được tính đến trong client.
