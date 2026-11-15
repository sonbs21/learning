# 18.5.2. Dùng Jersey cùng với một web framework khác (Use Jersey Alongside Another Web Framework)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.jersey.alongside-another-web-framework>

Để dùng Jersey cùng với một web framework khác, chẳng hạn như Spring MVC, nó nên được cấu hình sao cho cho phép framework kia xử lý các request mà nó không thể xử lý. Trước tiên, cấu hình Jersey dùng một filter thay vì một servlet bằng cách cấu hình application property `spring.jersey.type` với giá trị `filter`. Thứ hai, cấu hình `ResourceConfig` của bạn để chuyển tiếp các request mà lẽ ra sẽ dẫn đến lỗi 404, như trong ví dụ sau.

```java
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletProperties;

import org.springframework.stereotype.Component;

@Component
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() {
        register(Endpoint.class);
        property(ServletProperties.FILTER_FORWARD_ON_404, true);
    }

}
```
