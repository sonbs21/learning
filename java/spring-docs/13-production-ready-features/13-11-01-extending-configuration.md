# 13.11.1. Mở rộng cấu hình (Extending Configuration)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.process-monitoring.configuration>

Trong file `META-INF/spring.factories`, bạn có thể kích hoạt (các) listener ghi file PID:

```
org.springframework.context.ApplicationListener=\
org.springframework.boot.context.ApplicationPidFileWriter,\
org.springframework.boot.web.context.WebServerPortFileWriter
```
