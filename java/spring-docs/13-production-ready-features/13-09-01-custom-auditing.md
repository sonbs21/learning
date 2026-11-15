# 13.9.1. Auditing tùy chỉnh (Custom Auditing)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.auditing.custom>

Để tùy chỉnh các event bảo mật được publish, bạn có thể cung cấp implementation riêng của `AbstractAuthenticationAuditListener` và `AbstractAuthorizationAuditListener`.

Bạn cũng có thể dùng các dịch vụ audit cho các event nghiệp vụ của riêng mình. Để làm vậy, hãy inject bean `AuditEventRepository` vào các component của bạn và dùng nó trực tiếp, hoặc publish một `AuditApplicationEvent` bằng `ApplicationEventPublisher` của Spring (bằng cách hiện thực `ApplicationEventPublisherAware`).
