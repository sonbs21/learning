# 13.4. Giám sát và quản lý qua JMX (Monitoring and Management over JMX)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.jmx>

Java Management Extensions (JMX) cung cấp một cơ chế tiêu chuẩn để giám sát và quản lý ứng dụng. Theo mặc định, tính năng này không được bật. Bạn có thể bật nó bằng cách đặt configuration property `spring.jmx.enabled` thành `true`. Spring Boot expose `MBeanServer` phù hợp nhất dưới dạng một bean với ID là `mbeanServer`. Bất kỳ bean nào của bạn được đánh các annotation Spring JMX (`@ManagedResource`, `@ManagedAttribute`, hoặc `@ManagedOperation`) đều được expose tới nó.

Nếu nền tảng của bạn cung cấp một `MBeanServer` tiêu chuẩn, Spring Boot sẽ dùng nó và mặc định dùng `MBeanServer` của VM, nếu cần. Nếu tất cả đều thất bại, một `MBeanServer` mới sẽ được tạo.

Xem class [`JmxAutoConfiguration`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/jmx/JmxAutoConfiguration.java) để biết thêm chi tiết.

Theo mặc định, Spring Boot cũng expose các management endpoint dưới dạng JMX MBean bên dưới domain `org.springframework.boot`. Để kiểm soát hoàn toàn việc đăng ký endpoint trong domain JMX, hãy cân nhắc đăng ký implementation `EndpointObjectNameFactory` của riêng bạn.
