# 18.5.1. Bảo mật các endpoint Jersey bằng Spring Security (Secure Jersey endpoints with Spring Security)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.jersey.spring-security>

Spring Security có thể được dùng để bảo mật một ứng dụng web dựa trên Jersey theo cách gần giống như cách nó được dùng để bảo mật một ứng dụng web dựa trên Spring MVC. Tuy nhiên, nếu bạn muốn dùng method-level security của Spring Security với Jersey, bạn phải cấu hình Jersey dùng `setStatus(int)` thay vì `sendError(int)`. Điều này ngăn Jersey commit response trước khi Spring Security có cơ hội báo cáo lỗi xác thực hoặc phân quyền cho client.

Property `jersey.config.server.response.setStatusOverSendError` phải được đặt thành `true` trên bean `ResourceConfig` của ứng dụng, như trong ví dụ sau:

```java
import java.util.Collections;

import org.glassfish.jersey.server.ResourceConfig;

import org.springframework.stereotype.Component;

@Component
public class JerseySetStatusOverSendErrorConfig extends ResourceConfig {

    public JerseySetStatusOverSendErrorConfig() {
        register(Endpoint.class);
        setProperties(Collections.singletonMap("jersey.config.server.response.setStatusOverSendError", true));
    }

}
```
