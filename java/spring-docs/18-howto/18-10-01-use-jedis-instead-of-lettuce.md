# 18.10.1. Dùng Jedis thay cho Lettuce (Use Jedis Instead of Lettuce)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.nosql.jedis-instead-of-lettuce>

Theo mặc định, Spring Boot starter (`spring-boot-starter-data-redis`) dùng [Lettuce](https://github.com/lettuce-io/lettuce-core/). Bạn cần loại bỏ dependency đó và bao gồm dependency [Jedis](https://github.com/xetorthio/jedis/) thay thế. Spring Boot quản lý cả hai dependency này, cho phép bạn chuyển sang Jedis mà không cần chỉ định phiên bản.

Ví dụ sau cho thấy cách thực hiện điều này trong Maven:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
    <exclusions>
        <exclusion>
            <groupId>io.lettuce</groupId>
            <artifactId>lettuce-core</artifactId>
        </exclusion>
    </exclusions>
</dependency>
<dependency>
    <groupId>redis.clients</groupId>
    <artifactId>jedis</artifactId>
</dependency>
```

Ví dụ sau cho thấy cách thực hiện điều này trong Gradle:

```gradle
dependencies {
    implementation('org.springframework.boot:spring-boot-starter-data-redis') {
        exclude group: 'io.lettuce', module: 'lettuce-core'
    }
    implementation 'redis.clients:jedis'
    // ...
}
```
