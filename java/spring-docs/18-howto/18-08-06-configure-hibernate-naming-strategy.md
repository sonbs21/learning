# 18.8.6. Cấu hình naming strategy của Hibernate (Configure Hibernate Naming Strategy)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-access.configure-hibernate-naming-strategy>

Hibernate dùng [hai naming strategy khác nhau](https://docs.jboss.org/hibernate/orm/6.4/userguide/html_single/Hibernate_User_Guide.html#naming) để ánh xạ các tên từ object model sang các tên tương ứng trong cơ sở dữ liệu. Tên class đầy đủ (fully qualified) của các implementation physical strategy và implicit strategy có thể được cấu hình bằng cách đặt lần lượt các property `spring.jpa.hibernate.naming.physical-strategy` và `spring.jpa.hibernate.naming.implicit-strategy`. Ngoài ra, nếu có các bean `ImplicitNamingStrategy` hoặc `PhysicalNamingStrategy` trong application context, Hibernate sẽ được tự động cấu hình để dùng chúng.

Theo mặc định, Spring Boot cấu hình physical naming strategy với `CamelCaseToUnderscoresNamingStrategy`. Khi dùng strategy này, tất cả các dấu chấm được thay bằng dấu gạch dưới và kiểu viết camel case cũng được thay bằng dấu gạch dưới. Ngoài ra, theo mặc định, tất cả tên bảng được sinh ra ở dạng chữ thường. Ví dụ, một entity `TelephoneNumber` được ánh xạ tới bảng `telephone_number`. Nếu schema của bạn yêu cầu các định danh có chữ hoa chữ thường lẫn lộn, hãy định nghĩa một bean `CamelCaseToUnderscoresNamingStrategy` tùy chỉnh, như trong ví dụ sau:

**Java**

```java
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MyHibernateConfiguration {

    @Bean
    public CamelCaseToUnderscoresNamingStrategy caseSensitivePhysicalNamingStrategy() {
        return new CamelCaseToUnderscoresNamingStrategy() {

            @Override
            protected boolean isCaseInsensitive(JdbcEnvironment jdbcEnvironment) {
                return false;
            }

        };
    }

}
```

**Kotlin**

```kotlin
import org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
class MyHibernateConfiguration {

    @Bean
    fun caseSensitivePhysicalNamingStrategy(): CamelCaseToUnderscoresNamingStrategy {
        return object : CamelCaseToUnderscoresNamingStrategy() {
            override fun isCaseInsensitive(jdbcEnvironment: JdbcEnvironment): Boolean {
                return false
            }
        }
    }

}
```

Nếu bạn muốn dùng mặc định của Hibernate thay thế, hãy đặt property sau:

```properties
spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
```

Ngoài ra, bạn có thể cấu hình bean sau:

**Java**

```java
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class MyHibernateConfiguration {

    @Bean
    PhysicalNamingStrategyStandardImpl caseSensitivePhysicalNamingStrategy() {
        return new PhysicalNamingStrategyStandardImpl();
    }

}
```

**Kotlin**

```kotlin
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration(proxyBeanMethods = false)
internal class MyHibernateConfiguration {

    @Bean
    fun caseSensitivePhysicalNamingStrategy(): PhysicalNamingStrategyStandardImpl {
        return PhysicalNamingStrategyStandardImpl()
    }

}
```

Xem [`HibernateJpaAutoConfiguration`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/orm/jpa/HibernateJpaAutoConfiguration.java) và [`JpaBaseConfiguration`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/orm/jpa/JpaBaseConfiguration.java) để biết thêm chi tiết.
