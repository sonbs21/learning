# 18.13.1. Thay đổi HTTP port hoặc địa chỉ của các Actuator endpoint (Change the HTTP Port or Address of the Actuator Endpoints)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.actuator.change-http-port-or-address>

Trong một ứng dụng độc lập, HTTP port của Actuator mặc định giống với HTTP port chính. Để ứng dụng lắng nghe trên một port khác, hãy đặt property bên ngoài: `management.server.port`. Để lắng nghe trên một địa chỉ mạng hoàn toàn khác (chẳng hạn như khi bạn có một mạng nội bộ cho việc quản trị và một mạng bên ngoài cho các ứng dụng người dùng), bạn cũng có thể đặt `management.server.address` thành một địa chỉ IP hợp lệ mà server có thể bind tới.

Để biết thêm chi tiết, xem mã nguồn [`ManagementServerProperties`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-actuator-autoconfigure/src/main/java/org/springframework/boot/actuate/autoconfigure/web/server/ManagementServerProperties.java) và "[Tùy chỉnh port của management server (Customizing the Management Server Port)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.monitoring.customizing-management-server-port)" trong phần "Các tính năng sẵn sàng cho production".
