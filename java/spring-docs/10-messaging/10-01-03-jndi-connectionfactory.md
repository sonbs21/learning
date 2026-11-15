# 10.1.3. Sử dụng JNDI ConnectionFactory (Using a JNDI ConnectionFactory)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#messaging.jms.jndi>

Nếu bạn đang chạy ứng dụng trong một application server, Spring Boot cố gắng định vị một `ConnectionFactory` JMS bằng JNDI. Theo mặc định, các vị trí `java:/JmsXA` và `java:/XAConnectionFactory` được kiểm tra. Bạn có thể dùng property `spring.jms.jndi-name` nếu cần chỉ định một vị trí khác, như trong ví dụ sau:

**Properties**

```properties
spring.jms.jndi-name=java:/MyConnectionFactory
```

**Yaml**

```yaml
spring:
  jms:
    jndi-name: "java:/MyConnectionFactory"
```
