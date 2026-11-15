# 13.2.5. Che giấu các giá trị nhạy cảm (Sanitize Sensitive Values)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.sanitization>

Thông tin được trả về bởi các endpoint `/env`, `/configprops` và `/quartz` có thể nhạy cảm, vì vậy theo mặc định các giá trị luôn được che giấu hoàn toàn (thay bằng `******`).

Các giá trị chỉ có thể được xem ở dạng không bị che giấu khi:

- Property `show-values` đã được đặt thành một giá trị khác `NEVER`
- Không có bean [`SanitizingFunction`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.actuator.customizing-sanitization) tùy chỉnh nào được áp dụng

Property `show-values` có thể được cấu hình cho các endpoint có thể che giấu thành một trong các giá trị sau:

- `NEVER` - các giá trị luôn được che giấu hoàn toàn (thay bằng `******`)
- `ALWAYS` - các giá trị được hiển thị cho tất cả người dùng (miễn là không có bean `SanitizingFunction` nào được áp dụng)
- `WHEN_AUTHORIZED` - các giá trị chỉ được hiển thị cho người dùng được phân quyền (miễn là không có bean `SanitizingFunction` nào được áp dụng)

Với các HTTP endpoint, một người dùng được coi là được phân quyền nếu họ đã xác thực và có các role được cấu hình bởi property roles của endpoint. Theo mặc định, bất kỳ người dùng đã xác thực nào cũng được phân quyền.

Với các JMX endpoint, tất cả người dùng luôn được phân quyền.

Ví dụ sau cho phép tất cả người dùng có role `admin` xem các giá trị từ endpoint `/env` ở dạng gốc. Người dùng không được phân quyền, hoặc người dùng không có role `admin`, sẽ chỉ thấy các giá trị đã bị che giấu.

**Properties**

```properties
management.endpoint.env.show-values=WHEN_AUTHORIZED
management.endpoint.env.roles=admin
```

**Yaml**

```yaml
management:
  endpoint:
    env:
      show-values: WHEN_AUTHORIZED
      roles: "admin"
```

> **LƯU Ý (NOTE):** Ví dụ này giả định rằng chưa có bean [`SanitizingFunction`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.actuator.customizing-sanitization) nào được định nghĩa.
