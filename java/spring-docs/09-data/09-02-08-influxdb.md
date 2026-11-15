# 9.2.8. InfluxDB

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#data.nosql.influxdb>

> **CẢNH BÁO (WARNING):** Auto-configuration cho InfluxDB đã bị deprecated và được lên kế hoạch loại bỏ trong Spring Boot 3.4 để ưu tiên [client Java InfluxDB mới](https://github.com/influxdata/influxdb-client-java), vốn cung cấp tích hợp Spring Boot riêng.

[InfluxDB](https://www.influxdata.com/) là một database chuỗi thời gian (time series) mã nguồn mở, được tối ưu cho việc lưu trữ và truy xuất dữ liệu chuỗi thời gian nhanh, có tính sẵn sàng cao, trong các lĩnh vực như giám sát vận hành, metrics ứng dụng, dữ liệu cảm biến Internet-of-Things, và phân tích thời gian thực.

## Kết nối tới InfluxDB (Connecting to InfluxDB)

Spring Boot tự động cấu hình một instance `InfluxDB`, với điều kiện client `influxdb-java` nằm trên classpath và URL của database được đặt bằng `spring.influx.url`.

Nếu kết nối tới InfluxDB yêu cầu user và password, bạn có thể đặt các property `spring.influx.user` và `spring.influx.password` tương ứng.

InfluxDB dựa vào OkHttp. Nếu bạn cần tinh chỉnh http client mà `InfluxDB` dùng ở phía sau, bạn có thể đăng ký một bean `InfluxDbOkHttpClientBuilderProvider`.

Nếu bạn cần kiểm soát nhiều hơn đối với cấu hình, hãy cân nhắc đăng ký một bean `InfluxDbCustomizer`.
