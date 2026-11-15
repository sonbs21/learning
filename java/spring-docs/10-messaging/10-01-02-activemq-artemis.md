# 10.1.2. Hỗ trợ ActiveMQ Artemis (ActiveMQ Artemis Support)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.jms.artemis>

Spring Boot có thể tự động cấu hình một `ConnectionFactory` khi phát hiện [ActiveMQ Artemis](https://activemq.apache.org/components/artemis/) có trên classpath. Nếu có broker, một broker nhúng sẽ được tự động khởi động và cấu hình (trừ khi property mode đã được đặt tường minh). Các mode được hỗ trợ là `embedded` (để làm rõ rằng cần một broker nhúng và một lỗi nên xảy ra nếu broker không có trên classpath) và `native` (để kết nối tới một broker bằng giao thức transport `netty`). Khi mode sau được cấu hình, Spring Boot cấu hình một `ConnectionFactory` kết nối tới một broker chạy trên máy cục bộ với các thiết lập mặc định.

> **LƯU Ý (NOTE):** Nếu bạn dùng `spring-boot-starter-artemis`, các dependency cần thiết để kết nối tới một instance ActiveMQ Artemis hiện có được cung cấp, cũng như hạ tầng Spring để tích hợp với JMS. Thêm `org.apache.activemq:artemis-jakarta-server` vào ứng dụng cho phép bạn dùng mode nhúng.

Cấu hình ActiveMQ Artemis được kiểm soát bởi các configuration property bên ngoài trong `spring.artemis.*`. Ví dụ, bạn có thể khai báo phần sau trong `application.properties`:

**Properties**

```properties
spring.artemis.mode=native
spring.artemis.broker-url=tcp://192.168.1.210:9876
spring.artemis.user=admin
spring.artemis.password=secret
```

**Yaml**

```yaml
spring:
  artemis:
    mode: native
    broker-url: "tcp://192.168.1.210:9876"
    user: "admin"
    password: "secret"
```

Khi nhúng broker, bạn có thể chọn có bật tính bền vững (persistence) hay không và liệt kê các destination nên được tạo sẵn. Chúng có thể được chỉ định dưới dạng danh sách phân tách bằng dấu phẩy để tạo chúng với các tùy chọn mặc định, hoặc bạn có thể định nghĩa (các) bean kiểu `org.apache.activemq.artemis.jms.server.config.JMSQueueConfiguration` hoặc `org.apache.activemq.artemis.jms.server.config.TopicConfiguration`, lần lượt cho cấu hình queue và topic nâng cao.

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
spring.artemis.pool.enabled=true
spring.artemis.pool.max-connections=50
```

**Yaml**

```yaml
spring:
  artemis:
    pool:
      enabled: true
      max-connections: 50
```

Xem [`ArtemisProperties`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/jms/artemis/ArtemisProperties.java) để biết thêm các tùy chọn được hỗ trợ.

Không có tra cứu JNDI nào liên quan, và các destination được phân giải theo tên của chúng, bằng thuộc tính `name` trong cấu hình ActiveMQ Artemis hoặc các tên được cung cấp thông qua cấu hình.
