# 7.10. Hỗ trợ Docker Compose (Docker Compose Support)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.docker-compose>

Docker Compose là một công nghệ phổ biến có thể dùng để định nghĩa và quản lý nhiều container cho các service mà ứng dụng của bạn cần. Một file `compose.yml` thường được tạo bên cạnh ứng dụng để định nghĩa và cấu hình các service container.

Một quy trình làm việc điển hình với Docker Compose là chạy `docker compose up`, làm việc với ứng dụng đang kết nối tới các service đã khởi động, rồi chạy `docker compose down` khi bạn làm xong.

Module `spring-boot-docker-compose` có thể được thêm vào project để cung cấp hỗ trợ làm việc với container bằng Docker Compose. Hãy thêm dependency của module vào bản build, như trong các đoạn sau cho Maven và Gradle:

**Maven**

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-docker-compose</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

**Gradle**

```gradle
dependencies {
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
}
```

Khi module này được thêm làm dependency, Spring Boot sẽ làm những việc sau:

- Tìm kiếm `compose.yml` và các tên file compose phổ biến khác trong thư mục ứng dụng
- Gọi `docker compose up` với `compose.yml` đã tìm thấy
- Tạo các bean service connection cho mỗi container được hỗ trợ
- Gọi `docker compose stop` khi ứng dụng tắt

Nếu các service Docker Compose đã chạy sẵn khi khởi động ứng dụng, Spring Boot sẽ chỉ tạo các bean service connection cho mỗi container được hỗ trợ. Nó sẽ không gọi lại `docker compose up` và sẽ không gọi `docker compose stop` khi ứng dụng tắt.

> **LƯU Ý (NOTE):** Theo mặc định, hỗ trợ Docker Compose của Spring Boot bị vô hiệu hóa khi chạy test. Để bật nó, hãy đặt `spring.docker.compose.skip.in-tests` thành `false`.

## 7.10.1. Điều kiện tiên quyết (Prerequisites)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.docker-compose.prerequisites>

Bạn cần có các ứng dụng CLI `docker` và `docker compose` (hoặc `docker-compose`) trên path. Phiên bản Docker Compose tối thiểu được hỗ trợ là 2.2.0.

## 7.10.2. Service Connection (Service Connections)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.docker-compose.service-connections>

Một service connection là một kết nối tới bất kỳ remote service nào. Auto-configuration của Spring Boot có thể sử dụng các chi tiết của một service connection và dùng chúng để thiết lập kết nối tới một remote service. Khi làm vậy, các chi tiết kết nối được ưu tiên hơn mọi configuration property liên quan đến kết nối.

Khi dùng hỗ trợ Docker Compose của Spring Boot, các service connection được thiết lập tới cổng được container ánh xạ ra.

> **LƯU Ý (NOTE):** Docker compose thường được dùng theo cách mà các cổng bên trong container được ánh xạ tới các cổng tạm thời (ephemeral port) trên máy của bạn. Ví dụ, một Postgres server có thể chạy bên trong container dùng cổng 5432 nhưng được ánh xạ tới một cổng hoàn toàn khác ở máy cục bộ. Service connection sẽ luôn phát hiện và dùng cổng đã được ánh xạ ở máy cục bộ.

Các service connection được thiết lập bằng cách dùng tên image của container. Các service connection sau hiện được hỗ trợ:

| Connection Details | Khớp với |
|---|---|
| `ActiveMQConnectionDetails` | Container có tên "symptoma/activemq" |
| `CassandraConnectionDetails` | Container có tên "cassandra" |
| `ElasticsearchConnectionDetails` | Container có tên "elasticsearch" |
| `JdbcConnectionDetails` | Container có tên "gvenzl/oracle-free", "gvenzl/oracle-xe", "mariadb", "mssql/server", "mysql", hoặc "postgres" |
| `MongoConnectionDetails` | Container có tên "mongo" |
| `Neo4jConnectionDetails` | Container có tên "neo4j" |
| `OtlpMetricsConnectionDetails` | Container có tên "otel/opentelemetry-collector-contrib" |
| `OtlpTracingConnectionDetails` | Container có tên "otel/opentelemetry-collector-contrib" |
| `PulsarConnectionDetails` | Container có tên "apachepulsar/pulsar" |
| `R2dbcConnectionDetails` | Container có tên "gvenzl/oracle-free", "gvenzl/oracle-xe", "mariadb", "mssql/server", "mysql", hoặc "postgres" |
| `RabbitConnectionDetails` | Container có tên "rabbitmq" |
| `RedisConnectionDetails` | Container có tên "redis" |
| `ZipkinConnectionDetails` | Container có tên "openzipkin/zipkin". |

## 7.10.3. Image tùy chỉnh (Custom Images)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.docker-compose.custom-images>

Đôi khi bạn có thể cần dùng phiên bản image của riêng mình để cung cấp một service. Bạn có thể dùng bất kỳ image tùy chỉnh nào miễn là nó hoạt động theo cùng cách như image tiêu chuẩn. Cụ thể, mọi biến môi trường mà image tiêu chuẩn hỗ trợ cũng phải được dùng trong image tùy chỉnh của bạn.

Nếu image của bạn dùng một tên khác, bạn có thể dùng một label trong file `compose.yml` để Spring Boot có thể cung cấp service connection. Hãy dùng một label tên là `org.springframework.boot.service-connection` để cung cấp tên service.

Ví dụ:

```yaml
services:
  redis:
    image: 'mycompany/mycustomredis:7.0'
    ports:
      - '6379'
    labels:
      org.springframework.boot.service-connection: redis
```

## 7.10.4. Bỏ qua các container cụ thể (Skipping Specific Containers)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.docker-compose.skipping>

Nếu bạn có một container image được định nghĩa trong `compose.yml` mà bạn không muốn kết nối với ứng dụng, bạn có thể dùng một label để bỏ qua nó. Bất kỳ container nào được gắn label `org.springframework.boot.ignore` sẽ bị Spring Boot bỏ qua.

Ví dụ:

```yaml
services:
  redis:
    image: 'redis:7.0'
    ports:
      - '6379'
    labels:
      org.springframework.boot.ignore: true
```

## 7.10.5. Sử dụng một file Compose cụ thể (Using a Specific Compose File)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.docker-compose.specific-file>

Nếu file compose của bạn không nằm trong cùng thư mục với ứng dụng, hoặc nếu nó có tên khác, bạn có thể dùng `spring.docker.compose.file` trong `application.properties` hoặc `application.yaml` để trỏ tới một file khác. Property có thể được định nghĩa dưới dạng đường dẫn chính xác hoặc đường dẫn tương đối với ứng dụng.

Ví dụ:

**Properties**

```properties
spring.docker.compose.file=../my-compose.yml
```

**Yaml**

```yaml
spring:
  docker:
    compose:
      file: "../my-compose.yml"
```

## 7.10.6. Chờ container sẵn sàng (Waiting for Container Readiness)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.docker-compose.readiness>

Các container được Docker Compose khởi động có thể mất một khoảng thời gian để trở nên hoàn toàn sẵn sàng. Cách được khuyến nghị để kiểm tra sự sẵn sàng là thêm một phần `healthcheck` bên dưới định nghĩa service trong file `compose.yml`.

Vì việc bỏ qua cấu hình `healthcheck` trong các file `compose.yml` không phải là hiếm, Spring Boot cũng kiểm tra trực tiếp sự sẵn sàng của service. Theo mặc định, một container được coi là sẵn sàng khi có thể thiết lập kết nối TCP/IP tới cổng đã được ánh xạ của nó.

Bạn có thể vô hiệu hóa điều này cho từng container bằng cách thêm label `org.springframework.boot.readiness-check.tcp.disable` vào file `compose.yml`.

Ví dụ:

```yaml
services:
  redis:
    image: 'redis:7.0'
    ports:
      - '6379'
    labels:
      org.springframework.boot.readiness-check.tcp.disable: true
```

Bạn cũng có thể thay đổi các giá trị timeout trong file `application.properties` hoặc `application.yaml`:

**Properties**

```properties
spring.docker.compose.readiness.tcp.connect-timeout=10s
spring.docker.compose.readiness.tcp.read-timeout=5s
```

**Yaml**

```yaml
spring:
  docker:
    compose:
      readiness:
        tcp:
          connect-timeout: 10s
          read-timeout: 5s
```

Timeout tổng thể có thể được cấu hình bằng `spring.docker.compose.readiness.timeout`.

## 7.10.7. Kiểm soát vòng đời Docker Compose (Controlling the Docker Compose Lifecycle)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.docker-compose.lifecycle>

Theo mặc định, Spring Boot gọi `docker compose up` khi ứng dụng khởi động và `docker compose stop` khi nó tắt. Nếu bạn muốn quản lý vòng đời theo cách khác, bạn có thể dùng property `spring.docker.compose.lifecycle-management`.

Các giá trị sau được hỗ trợ:

- `none` - Không khởi động hay dừng Docker Compose
- `start-only` - Khởi động Docker Compose khi ứng dụng khởi động và để nó tiếp tục chạy
- `start-and-stop` - Khởi động Docker Compose khi ứng dụng khởi động và dừng nó khi JVM thoát

Ngoài ra, bạn có thể dùng property `spring.docker.compose.start.command` để thay đổi việc dùng `docker compose up` hay `docker compose start`. `spring.docker.compose.stop.command` cho phép bạn cấu hình việc dùng `docker compose down` hay `docker compose stop`.

Ví dụ sau cho thấy cách cấu hình quản lý vòng đời:

**Properties**

```properties
spring.docker.compose.lifecycle-management=start-and-stop
spring.docker.compose.start.command=start
spring.docker.compose.stop.command=down
spring.docker.compose.stop.timeout=1m
```

**Yaml**

```yaml
spring:
  docker:
    compose:
      lifecycle-management: start-and-stop
      start:
        command: start
      stop:
        command: down
        timeout: 1m
```

## 7.10.8. Kích hoạt profile Docker Compose (Activating Docker Compose Profiles)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#features.docker-compose.profiles>

Profile của Docker Compose tương tự như Spring profile ở chỗ chúng cho phép bạn điều chỉnh cấu hình Docker Compose cho các môi trường cụ thể. Nếu bạn muốn kích hoạt một profile Docker Compose cụ thể, bạn có thể dùng property `spring.docker.compose.profiles.active` trong file `application.properties` hoặc `application.yaml`:

**Properties**

```properties
spring.docker.compose.profiles.active=myprofile
```

**Yaml**

```yaml
spring:
  docker:
    compose:
      profiles:
        active: "myprofile"
```
