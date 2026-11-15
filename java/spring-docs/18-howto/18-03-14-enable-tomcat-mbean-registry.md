# 18.3.14. Bật MBean Registry của Tomcat (Enable Tomcat's MBean Registry)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.enable-tomcat-mbean-registry>

MBean registry của embedded Tomcat bị vô hiệu hóa theo mặc định. Điều này giảm thiểu lượng bộ nhớ Tomcat chiếm dụng. Nếu bạn muốn dùng các MBean của Tomcat, ví dụ để Micrometer có thể dùng chúng để expose metric, bạn phải dùng property `server.tomcat.mbeanregistry.enabled` để làm điều đó, như trong ví dụ sau:

**Properties**

```properties
server.tomcat.mbeanregistry.enabled=true
```

**Yaml**

```yaml
server:
  tomcat:
    mbeanregistry:
      enabled: true
```
