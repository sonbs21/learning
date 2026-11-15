# 10.1.1. Hỗ trợ ActiveMQ "Classic" (ActiveMQ "Classic" Support)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.jms.activemq>

Khi [ActiveMQ "Classic"](https://activemq.apache.org/components/classic) có trên classpath, Spring Boot có thể cấu hình một `ConnectionFactory`.

> **LƯU Ý (NOTE):** Nếu bạn dùng `spring-boot-starter-activemq`, các dependency cần thiết để kết nối tới một instance ActiveMQ "Classic" được cung cấp, cũng như hạ tầng Spring để tích hợp với JMS.

Cấu hình ActiveMQ "Classic" được kiểm soát bởi các configuration property bên ngoài trong `spring.activemq.*`. Theo mặc định, ActiveMQ "Classic" được tự động cấu hình để dùng [TCP transport](https://activemq.apache.org/tcp-transport-reference), mặc định kết nối tới `tcp://localhost:61616`. Ví dụ sau cho thấy cách thay đổi URL broker mặc định:

**Properties**

```properties
spring.activemq.broker-url=tcp://192.168.1.210:9876
spring.activemq.user=admin
spring.activemq.password=secret
```

**Yaml**

```yaml
spring:
  activemq:
    broker-url: "tcp://192.168.1.210:9876"
    user: "admin"
    password: "secret"
```

Theo mặc định, một `CachingConnectionFactory` bọc `ConnectionFactory` gốc với các thiết lập hợp lý mà bạn có thể kiểm soát bằng các configuration property bên ngoài trong `spring.jms.*`:

**Properties**

```properties
spring.jms.cache.session-cache-size=5
```

**Yaml**

```yaml
spring:
  jms:
    cache:
      session-cache-size: 5
```

Nếu bạn muốn dùng pooling gốc (native pooling), bạn có thể làm vậy bằng cách thêm dependency tới `org.messaginghub:pooled-jms` và cấu hình `JmsPoolConnectionFactory` tương ứng, như trong ví dụ sau:

**Properties**

```properties
spring.activemq.pool.enabled=true
spring.activemq.pool.max-connections=50
```

**Yaml**

```yaml
spring:
  activemq:
    pool:
      enabled: true
      max-connections: 50
```

> **MẸO (TIP):** Xem [`ActiveMQProperties`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/jms/activemq/ActiveMQProperties.java) để biết thêm các tùy chọn được hỗ trợ. Bạn cũng có thể đăng ký một số lượng tùy ý các bean hiện thực `ActiveMQConnectionFactoryCustomizer` để tùy chỉnh nâng cao hơn.

Theo mặc định, ActiveMQ "Classic" tạo một destination nếu nó chưa tồn tại, để các destination được phân giải theo tên được cung cấp.
