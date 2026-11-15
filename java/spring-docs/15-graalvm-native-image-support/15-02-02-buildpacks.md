# 15.2.2. Build native image bằng Buildpacks (Building a Native Image Using Buildpacks)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.developing-your-first-application.buildpacks>

Spring Boot có hỗ trợ buildpack cho native image trực tiếp cho cả Maven và Gradle. Điều này có nghĩa là bạn chỉ cần gõ một lệnh duy nhất và nhanh chóng có được một image hợp lý trong Docker daemon đang chạy ở máy cục bộ. Image kết quả không chứa JVM, thay vào đó native image được biên dịch tĩnh. Điều này dẫn đến các image nhỏ hơn.

> **LƯU Ý (NOTE):** Builder được dùng cho các image là `paketobuildpacks/builder-jammy-tiny:latest`. Nó có dung lượng nhỏ và giảm bề mặt tấn công (attack surface), nhưng bạn cũng có thể dùng `paketobuildpacks/builder-jammy-base:latest` hoặc `paketobuildpacks/builder-jammy-full:latest` để có thêm công cụ trong image nếu cần.

## Yêu cầu hệ thống (System Requirements)

Docker cần được cài đặt. Xem [Get Docker](https://docs.docker.com/installation/#installation) để biết thêm chi tiết. [Cấu hình nó để cho phép user không phải root](https://docs.docker.com/engine/install/linux-postinstall/#manage-docker-as-a-non-root-user) nếu bạn đang dùng Linux.

> **LƯU Ý (NOTE):** Bạn có thể chạy `docker run hello-world` (không có `sudo`) để kiểm tra Docker daemon có thể truy cập như mong đợi hay không. Xem tài liệu plugin Spring Boot [Maven](https://docs.spring.io/spring-boot/docs/3.2.5/maven-plugin/reference/htmlsingle//#build-image-docker-daemon) hoặc [Gradle](https://docs.spring.io/spring-boot/docs/3.2.5/gradle-plugin/reference/htmlsingle//#build-image-docker-daemon) để biết thêm chi tiết.

> **MẸO (TIP):** Trên macOS, khuyến nghị tăng bộ nhớ cấp cho Docker lên ít nhất `8GB`, và có thể thêm CPU nữa. Xem [câu trả lời Stack Overflow](https://stackoverflow.com/questions/44533319/how-to-assign-more-memory-to-docker-container/44533437#44533437) này để biết thêm chi tiết. Trên Microsoft Windows, hãy đảm bảo bật [Docker WSL 2 backend](https://docs.docker.com/docker-for-windows/wsl/) để có hiệu năng tốt hơn.

## Sử dụng Maven (Using Maven)

Để build một container native image bằng Maven, bạn nên đảm bảo file `pom.xml` dùng `spring-boot-starter-parent` và `org.graalvm.buildtools:native-maven-plugin`. Bạn nên có một phần `<parent>` trông như thế này:

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
</parent>
```

Ngoài ra, bạn nên có nội dung sau trong phần `<build> <plugins>`:

```xml
<plugin>
    <groupId>org.graalvm.buildtools</groupId>
    <artifactId>native-maven-plugin</artifactId>
</plugin>
```

`spring-boot-starter-parent` khai báo một profile `native` cấu hình các execution cần chạy để tạo native image. Bạn có thể kích hoạt profile bằng cờ `-P` trên dòng lệnh.

> **MẸO (TIP):** Nếu bạn không muốn dùng `spring-boot-starter-parent`, bạn sẽ cần cấu hình các execution cho goal `process-aot` từ plugin của Spring Boot và goal `add-reachability-metadata` từ plugin Native Build Tools.

Để build image, bạn có thể chạy goal `spring-boot:build-image` với profile `native` được kích hoạt:

```shell
$ mvn -Pnative spring-boot:build-image
```

## Sử dụng Gradle (Using Gradle)

Plugin Spring Boot Gradle tự động cấu hình các task AOT khi plugin GraalVM Native Image được áp dụng. Bạn nên kiểm tra rằng bản build Gradle của bạn chứa một khối `plugins` bao gồm `org.graalvm.buildtools.native`.

Miễn là plugin `org.graalvm.buildtools.native` được áp dụng, task `bootBuildImage` sẽ sinh ra một native image thay vì một image JVM. Bạn có thể chạy task bằng:

```shell
$ gradle bootBuildImage
```

## Chạy ví dụ (Running the example)

Sau khi bạn đã chạy lệnh build phù hợp, một Docker image sẽ có sẵn. Bạn có thể khởi động ứng dụng bằng `docker run`:

```shell
$ docker run --rm -p 8080:8080 docker.io/library/myproject:0.0.1-SNAPSHOT
```

Bạn sẽ thấy output tương tự như sau:

```shell
.   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::  (v3.2.5)
....... . . .
....... . . . (log output here)
....... . . .
........ Started MyApplication in 0.08 seconds (process running for 0.095)
```

> **LƯU Ý (NOTE):** Thời gian khởi động khác nhau giữa các máy, nhưng nó sẽ nhanh hơn nhiều so với một ứng dụng Spring Boot chạy trên JVM.

Nếu bạn mở trình duyệt web tới [localhost:8080](http://localhost:8080), bạn sẽ thấy output sau:

```
Hello World!
```

Để thoát ứng dụng một cách nhẹ nhàng (gracefully), hãy nhấn `ctrl-c`.
