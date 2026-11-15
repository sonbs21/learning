# 13.3.4. Tùy chỉnh địa chỉ của management server (Customizing the Management Server Address)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.monitoring.customizing-management-server-address>

Bạn có thể tùy chỉnh địa chỉ mà các management endpoint khả dụng bằng cách đặt property `management.server.address`. Làm như vậy có thể hữu ích nếu bạn chỉ muốn lắng nghe trên một mạng nội bộ hoặc mạng dành cho vận hành (ops), hoặc chỉ lắng nghe các kết nối từ `localhost`.

> **LƯU Ý (NOTE):** Bạn chỉ có thể lắng nghe trên một địa chỉ khác khi cổng khác với cổng server chính.

Ví dụ `application.properties` sau không cho phép các kết nối management từ xa:

**Properties**

```properties
management.server.port=8081
management.server.address=127.0.0.1
```

**Yaml**

```yaml
management:
  server:
    port: 8081
    address: "127.0.0.1"
```
