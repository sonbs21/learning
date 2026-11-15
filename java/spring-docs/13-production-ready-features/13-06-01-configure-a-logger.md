# 13.6.1. Cấu hình một logger (Configure a Logger)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.loggers.configure>

Để cấu hình một logger nhất định, hãy `POST` một entity một phần tới URI của resource, như ví dụ sau cho thấy:

```json
{
    "configuredLevel": "DEBUG"
}
```

> **MẸO (TIP):** Để "reset" mức cụ thể của logger (và dùng cấu hình mặc định thay thế), bạn có thể truyền giá trị `null` làm `configuredLevel`.
