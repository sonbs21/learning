# 10.4.10. Các property Pulsar bổ sung (Additional Pulsar Properties)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.pulsar.additional-properties>

Các property được auto-configuration hỗ trợ được trình bày trong phần ["Integration Properties"](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.application-properties.integration) của Phụ lục. Lưu ý rằng, phần lớn các property này (dạng gạch nối hoặc camelCase) ánh xạ trực tiếp tới các configuration property của Apache Pulsar. Xem tài liệu Apache Pulsar để biết chi tiết.

Chỉ một tập con các property được Pulsar hỗ trợ là có sẵn trực tiếp thông qua class `PulsarProperties`. Nếu bạn muốn tinh chỉnh các component được tự động cấu hình với các property bổ sung không được hỗ trợ trực tiếp, bạn có thể dùng customizer được hỗ trợ bởi từng component đã nói ở trên.
