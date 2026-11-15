# 10.4.2. Kết nối tới Pulsar theo kiểu reactive (Connecting to Pulsar Reactively)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.pulsar.connecting-reactive>

Khi auto-configuration Reactive được kích hoạt, Spring Boot sẽ tự động cấu hình và đăng ký một bean `ReactivePulsarClient`.

`ReactivePulsarClient` điều hợp (adapt) một instance của `PulsarClient` đã mô tả ở trên. Vì vậy, hãy làm theo phần trước để cấu hình `PulsarClient` được `ReactivePulsarClient` sử dụng.
