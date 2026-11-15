# 10.2.3. Gửi message tới một Stream (Sending a Message To A Stream)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.amqp.sending-stream>

Để gửi một message tới một stream cụ thể, hãy chỉ định tên của stream, như trong ví dụ sau:

**Properties**

```properties
spring.rabbitmq.stream.name=my-stream
```

**Yaml**

```yaml
spring:
  rabbitmq:
    stream:
      name: "my-stream"
```

Nếu có bean `MessageConverter`, `StreamMessageConverter`, hoặc `ProducerCustomizer` được định nghĩa, nó sẽ được tự động gắn với `RabbitStreamTemplate` được tự động cấu hình.

Nếu bạn cần tạo thêm các instance `RabbitStreamTemplate` hoặc nếu bạn muốn ghi đè cái mặc định, Spring Boot cung cấp một bean `RabbitStreamTemplateConfigurer` mà bạn có thể dùng để khởi tạo một `RabbitStreamTemplate` với các thiết lập giống như các factory được auto-configuration sử dụng.
