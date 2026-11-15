# 10.5.1. Auto-configuration cho RSocket Strategies (RSocket Strategies Auto-configuration)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.rsocket.strategies-auto-configuration>

Spring Boot tự động cấu hình một bean `RSocketStrategies` cung cấp toàn bộ hạ tầng cần thiết để mã hóa và giải mã payload RSocket. Theo mặc định, auto-configuration sẽ cố gắng cấu hình những thứ sau (theo thứ tự):

1. Codec [CBOR](https://cbor.io/) với Jackson
2. Codec JSON với Jackson

Starter `spring-boot-starter-rsocket` cung cấp cả hai dependency. Xem [phần hỗ trợ Jackson](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.json.jackson) để biết thêm về các khả năng tùy chỉnh.

Lập trình viên có thể tùy chỉnh component `RSocketStrategies` bằng cách tạo các bean hiện thực interface `RSocketStrategiesCustomizer`. Lưu ý rằng `@Order` của chúng là quan trọng, vì nó xác định thứ tự của các codec.
