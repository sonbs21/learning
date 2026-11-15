# 18.3.11. Cấu hình access log (Configure Access Logging)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.configure-access-logs>

Access log có thể được cấu hình cho Tomcat, Undertow, và Jetty thông qua các namespace tương ứng của chúng.

Chẳng hạn, các thiết lập sau ghi log truy cập trên Tomcat với một [pattern tùy chỉnh](https://tomcat.apache.org/tomcat-10.1-doc/config/valve.html#Access_Logging).

**Properties**

```properties
server.tomcat.basedir=my-tomcat
server.tomcat.accesslog.enabled=true
server.tomcat.accesslog.pattern=%t %a %r %s (%D microseconds)
```

**Yaml**

```yaml
server:
  tomcat:
    basedir: "my-tomcat"
    accesslog:
      enabled: true
      pattern: "%t %a %r %s (%D microseconds)"
```

> **LƯU Ý (NOTE):** Vị trí mặc định cho log là một thư mục `logs` tương đối với thư mục gốc (base directory) của Tomcat. Theo mặc định, thư mục `logs` là một thư mục tạm, vì vậy bạn có thể muốn cố định thư mục gốc của Tomcat hoặc dùng một đường dẫn tuyệt đối cho log. Trong ví dụ trên, log có sẵn trong `my-tomcat/logs` tương đối với thư mục làm việc của ứng dụng.

Access log cho Undertow có thể được cấu hình theo cách tương tự, như trong ví dụ sau:

**Properties**

```properties
server.undertow.accesslog.enabled=true
server.undertow.accesslog.pattern=%t %a %r %s (%D milliseconds)
server.undertow.options.server.record-request-start-time=true
```

**Yaml**

```yaml
server:
  undertow:
    accesslog:
      enabled: true
      pattern: "%t %a %r %s (%D milliseconds)"
    options:
      server:
        record-request-start-time: true
```

Lưu ý rằng, ngoài việc bật access log và cấu hình pattern của nó, việc ghi lại thời điểm bắt đầu request cũng đã được bật. Điều này là bắt buộc khi đưa thời gian phản hồi (`%D`) vào pattern access log. Log được lưu trong một thư mục `logs` tương đối với thư mục làm việc của ứng dụng. Bạn có thể tùy chỉnh vị trí này bằng cách đặt property `server.undertow.accesslog.dir`.

Cuối cùng, access log cho Jetty cũng có thể được cấu hình như sau:

**Properties**

```properties
server.jetty.accesslog.enabled=true
server.jetty.accesslog.filename=/var/log/jetty-access.log
```

**Yaml**

```yaml
server:
  jetty:
    accesslog:
      enabled: true
      filename: "/var/log/jetty-access.log"
```

Theo mặc định, log được chuyển hướng tới `System.err`. Để biết thêm chi tiết, xem tài liệu của Jetty.
