# 13.3.2. Tùy chỉnh cổng của management server (Customizing the Management Server Port)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.monitoring.customizing-management-server-port>

Expose các management endpoint bằng cổng HTTP mặc định là một lựa chọn hợp lý cho các triển khai trên cloud. Tuy nhiên, nếu ứng dụng của bạn chạy bên trong data center của riêng bạn, bạn có thể muốn expose endpoint bằng một cổng HTTP khác.

Bạn có thể đặt property `management.server.port` để thay đổi cổng HTTP, như ví dụ sau cho thấy:

**Properties**

```properties
management.server.port=8081
```

**Yaml**

```yaml
management:
  server:
    port: 8081
```

> **LƯU Ý (NOTE):** Trên Cloud Foundry, theo mặc định, ứng dụng chỉ nhận request trên cổng 8080 cho cả định tuyến HTTP và TCP. Nếu bạn muốn dùng một cổng management tùy chỉnh trên Cloud Foundry, bạn cần thiết lập tường minh các route của ứng dụng để chuyển tiếp traffic tới cổng tùy chỉnh.
