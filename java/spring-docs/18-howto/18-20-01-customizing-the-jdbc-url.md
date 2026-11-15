# 18.20.1. Tùy chỉnh JDBC URL (Customizing the JDBC URL)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.docker-compose.jdbc-url>

Khi dùng `JdbcConnectionDetails` với Docker Compose, các tham số của JDBC URL có thể được tùy chỉnh bằng cách áp dụng label `org.springframework.boot.jdbc.parameters` cho service. Ví dụ:

```yaml
services:
  postgres:
    image: 'postgres:15.3'
    environment:
      - 'POSTGRES_USER=myuser'
      - 'POSTGRES_PASSWORD=secret'
      - 'POSTGRES_DB=mydb'
    ports:
      - '5432:5432'
    labels:
      org.springframework.boot.jdbc.parameters: 'ssl=true&sslmode=require'
```

Với file Docker Compose này, JDBC URL được dùng là `jdbc:postgresql://127.0.0.1:5432/mydb?ssl=true&sslmode=require`.
