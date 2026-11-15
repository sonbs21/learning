# 18.8.15. Cấu hình một component được JPA sử dụng (Configure a Component that is Used by JPA)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.data-access.configure-a-component-that-is-used-by-jpa>

Nếu bạn muốn cấu hình một component mà JPA sử dụng, thì bạn cần đảm bảo rằng component đó được khởi tạo trước JPA. Khi component được auto-configure, Spring Boot sẽ lo việc này cho bạn. Ví dụ, khi Flyway được auto-configure, Hibernate được cấu hình để phụ thuộc vào Flyway để Flyway có cơ hội khởi tạo cơ sở dữ liệu trước khi Hibernate cố gắng sử dụng nó.

Nếu bạn tự cấu hình một component, bạn có thể dùng một subclass của `EntityManagerFactoryDependsOnPostProcessor` như một cách tiện lợi để thiết lập các dependency cần thiết. Ví dụ, nếu bạn dùng Hibernate Search với Elasticsearch làm index manager, mọi bean `EntityManagerFactory` phải được cấu hình để phụ thuộc vào bean `elasticsearchClient`, như trong ví dụ sau:

**Java**

```java
import jakarta.persistence.EntityManagerFactory;

import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor;
import org.springframework.stereotype.Component;

/**
 * {@link EntityManagerFactoryDependsOnPostProcessor} that ensures that
 * {@link EntityManagerFactory} beans depend on the {@code elasticsearchClient} bean.
 */
@Component
public class ElasticsearchEntityManagerFactoryDependsOnPostProcessor
        extends EntityManagerFactoryDependsOnPostProcessor {

    public ElasticsearchEntityManagerFactoryDependsOnPostProcessor() {
        super("elasticsearchClient");
    }

}
```

**Kotlin**

```kotlin
import org.springframework.boot.autoconfigure.orm.jpa.EntityManagerFactoryDependsOnPostProcessor
import org.springframework.stereotype.Component

@Component
class ElasticsearchEntityManagerFactoryDependsOnPostProcessor :
    EntityManagerFactoryDependsOnPostProcessor("elasticsearchClient")
```
