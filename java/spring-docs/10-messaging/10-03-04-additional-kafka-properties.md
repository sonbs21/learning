# 10.3.4. Các property Kafka bổ sung (Additional Kafka Properties)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.kafka.additional-properties>

Các property được auto configuration hỗ trợ được trình bày trong phần ["Integration Properties"](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.application-properties.integration) của Phụ lục. Lưu ý rằng, phần lớn các property này (dạng gạch nối hoặc camelCase) ánh xạ trực tiếp tới các property có dấu chấm của Apache Kafka. Xem tài liệu Apache Kafka để biết chi tiết.

Các property không chứa loại client (`producer`, `consumer`, `admin`, hoặc `streams`) trong tên được coi là chung và áp dụng cho tất cả các client. Hầu hết các property chung này có thể được ghi đè cho một hoặc nhiều loại client, nếu cần.

Apache Kafka chỉ định các property với mức độ quan trọng HIGH, MEDIUM, hoặc LOW. Auto-configuration của Spring Boot hỗ trợ tất cả các property có mức độ quan trọng HIGH, một số property MEDIUM và LOW được chọn lọc, và mọi property không có giá trị mặc định.

Chỉ một tập con các property được Kafka hỗ trợ là có sẵn trực tiếp thông qua class `KafkaProperties`. Nếu bạn muốn cấu hình từng loại client với các property bổ sung không được hỗ trợ trực tiếp, hãy dùng các property sau:

**Properties**

```properties
spring.kafka.properties[prop.one]=first
spring.kafka.admin.properties[prop.two]=second
spring.kafka.consumer.properties[prop.three]=third
spring.kafka.producer.properties[prop.four]=fourth
spring.kafka.streams.properties[prop.five]=fifth
```

**Yaml**

```yaml
spring:
  kafka:
    properties:
      "[prop.one]": "first"
    admin:
      properties:
        "[prop.two]": "second"
    consumer:
      properties:
        "[prop.three]": "third"
    producer:
      properties:
        "[prop.four]": "fourth"
    streams:
      properties:
        "[prop.five]": "fifth"
```

Thiết lập này đặt property Kafka chung `prop.one` thành `first` (áp dụng cho producer, consumer, admin, và stream), property admin `prop.two` thành `second`, property consumer `prop.three` thành `third`, property producer `prop.four` thành `fourth` và property stream `prop.five` thành `fifth`.

Bạn cũng có thể cấu hình `JsonDeserializer` của Spring Kafka như sau:

**Properties**

```properties
spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer
spring.kafka.consumer.properties[spring.json.value.default.type]=com.example.Invoice
spring.kafka.consumer.properties[spring.json.trusted.packages]=com.example.main,com.example.another
```

**Yaml**

```yaml
spring:
  kafka:
    consumer:
      value-deserializer: "org.springframework.kafka.support.serializer.JsonDeserializer"
      properties:
        "[spring.json.value.default.type]": "com.example.Invoice"
        "[spring.json.trusted.packages]": "com.example.main,com.example.another"
```

Tương tự, bạn có thể tắt hành vi mặc định của `JsonSerializer` là gửi thông tin kiểu trong header:

**Properties**

```properties
spring.kafka.producer.value-serializer=org.springframework.kafka.support.serializer.JsonSerializer
spring.kafka.producer.properties[spring.json.add.type.headers]=false
```

**Yaml**

```yaml
spring:
  kafka:
    producer:
      value-serializer: "org.springframework.kafka.support.serializer.JsonSerializer"
      properties:
        "[spring.json.add.type.headers]": false
```

> **QUAN TRỌNG (IMPORTANT):** Các property được đặt theo cách này sẽ ghi đè mọi mục cấu hình mà Spring Boot hỗ trợ tường minh.
