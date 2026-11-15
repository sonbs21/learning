# 12.2. Dockerfile (Dockerfiles)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#container-images.dockerfiles>

Mặc dù có thể chuyển một uber jar Spring Boot thành docker image chỉ với vài dòng trong Dockerfile, chúng ta sẽ dùng [tính năng phân lớp](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#container-images.efficient-images.layering) để tạo một docker image được tối ưu. Khi bạn tạo một jar chứa file chỉ mục layer, jar `spring-boot-jarmode-layertools` sẽ được thêm làm dependency vào jar của bạn. Với jar này trên classpath, bạn có thể khởi chạy ứng dụng ở một chế độ đặc biệt, cho phép code khởi động chạy một thứ hoàn toàn khác với ứng dụng, ví dụ, một thứ giải nén các layer.

> **THẬN TRỌNG (CAUTION):** Chế độ `layertools` không thể được dùng với một [file nén Spring Boot thực thi đầy đủ (fully executable)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#deployment.installing) có chứa launch script. Hãy tắt cấu hình launch script khi build một file jar dự định dùng với `layertools`.

Đây là cách bạn có thể khởi chạy jar với jar mode `layertools`:

```shell
$ java -Djarmode=layertools -jar my-app.jar
```

Lệnh này sẽ cho ra output sau:

```
Usage:
  java -Djarmode=layertools -jar my-app.jar

Available commands:
  list     List layers from the jar that can be extracted
  extract  Extracts layers from the jar for image creation
  help     Help about any command
```

Lệnh `extract` có thể được dùng để dễ dàng chia ứng dụng thành các layer để thêm vào dockerfile. Đây là một ví dụ về Dockerfile dùng `jarmode`.

```dockerfile
FROM eclipse-temurin:17-jre as builder
WORKDIR application
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=layertools -jar application.jar extract

FROM eclipse-temurin:17-jre
WORKDIR application
COPY --from=builder application/dependencies/ ./
COPY --from=builder application/spring-boot-loader/ ./
COPY --from=builder application/snapshot-dependencies/ ./
COPY --from=builder application/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

Giả sử `Dockerfile` ở trên nằm trong thư mục hiện tại, docker image của bạn có thể được build bằng `docker build .`, hoặc tùy chọn chỉ định đường dẫn tới jar ứng dụng, như trong ví dụ sau:

```shell
$ docker build --build-arg JAR_FILE=path/to/myapp.jar .
```

Đây là một dockerfile nhiều giai đoạn (multi-stage). Giai đoạn builder giải nén các thư mục cần thiết cho sau này. Mỗi lệnh `COPY` liên quan đến các layer được jarmode giải nén.

Tất nhiên, một Dockerfile có thể được viết mà không cần dùng jarmode. Bạn có thể dùng một số kết hợp giữa `unzip` và `mv` để di chuyển các thứ vào đúng layer, nhưng jarmode đơn giản hóa điều đó.
