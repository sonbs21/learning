# 10.2.1. Hỗ trợ RabbitMQ (RabbitMQ Support)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.amqp.rabbitmq>

[RabbitMQ](https://www.rabbitmq.com/) là một message broker nhẹ, đáng tin cậy, có khả năng mở rộng và di động, dựa trên giao thức AMQP. Spring dùng RabbitMQ để giao tiếp thông qua giao thức AMQP.

Cấu hình RabbitMQ được kiểm soát bởi các configuration property bên ngoài trong `spring.rabbitmq.*`. Ví dụ, bạn có thể khai báo phần sau trong `application.properties`:

**Properties**

```properties
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=admin
spring.rabbitmq.password=secret
```

**Yaml**

```yaml
spring:
  rabbitmq:
    host: "localhost"
    port: 5672
    username: "admin"
    password: "secret"
```

Ngoài ra, bạn có thể cấu hình cùng kết nối đó bằng thuộc tính `addresses`:

**Properties**

```properties
spring.rabbitmq.addresses=amqp://admin:secret@localhost
```

**Yaml**

```yaml
spring:
  rabbitmq:
    addresses: "amqp://admin:secret@localhost"
```

> **LƯU Ý (NOTE):** Khi chỉ định địa chỉ theo cách đó, các property `host` và `port` sẽ bị bỏ qua. Nếu địa chỉ dùng giao thức `amqps`, hỗ trợ SSL sẽ được bật tự động.

Xem [`RabbitProperties`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/amqp/RabbitProperties.java) để biết thêm các tùy chọn cấu hình dựa trên property được hỗ trợ. Để cấu hình các chi tiết cấp thấp hơn của `ConnectionFactory` RabbitMQ được Spring AMQP sử dụng, hãy định nghĩa một bean `ConnectionFactoryCustomizer`.

Nếu có bean `ConnectionNameStrategy` trong context, nó sẽ được tự động dùng để đặt tên cho các kết nối được tạo bởi `CachingConnectionFactory` được tự động cấu hình.

Để thực hiện một tùy chỉnh bổ sung, áp dụng cho toàn ứng dụng, lên `RabbitTemplate`, hãy dùng một bean `RabbitTemplateCustomizer`.

> **MẸO (TIP):** Xem [Understanding AMQP, the protocol used by RabbitMQ](https://spring.io/blog/2010/06/14/understanding-amqp-the-protocol-used-by-rabbitmq/) để biết thêm chi tiết.
