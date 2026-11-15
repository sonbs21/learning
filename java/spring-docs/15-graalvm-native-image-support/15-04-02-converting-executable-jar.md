# 15.4.2. Chuyển đổi một jar thực thi Spring Boot (Converting a Spring Boot Executable Jar)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.advanced.converting-executable-jars>

Có thể chuyển đổi một [jar thực thi](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#appendix.executable-jar) Spring Boot thành native image miễn là jar chứa các tài nguyên được AOT sinh ra. Điều này có thể hữu ích vì một số lý do, bao gồm:

- Bạn có thể giữ pipeline JVM thông thường và biến ứng dụng JVM thành native image trên nền tảng CI/CD của mình.
- Vì `native-image` [không hỗ trợ biên dịch chéo (cross-compilation)](https://github.com/oracle/graal/issues/407), bạn có thể giữ một artifact triển khai trung lập với hệ điều hành, sau đó chuyển đổi nó sang các kiến trúc hệ điều hành khác nhau.

Bạn có thể chuyển đổi một jar thực thi Spring Boot thành native image bằng Cloud Native Buildpacks, hoặc bằng công cụ `native-image` đi kèm GraalVM.

> **LƯU Ý (NOTE):** Jar thực thi của bạn phải bao gồm các tài nguyên được AOT sinh ra như các class được sinh ra và các file gợi ý JSON.

## Sử dụng Buildpacks (Using Buildpacks)

Các ứng dụng Spring Boot thường dùng Cloud Native Buildpacks thông qua các tích hợp Maven (`mvn spring-boot:build-image`) hoặc Gradle (`gradle bootBuildImage`). Tuy nhiên, bạn cũng có thể dùng [`pack`](https://buildpacks.io//docs/tools/pack/) để biến một jar thực thi Spring Boot đã được xử lý AOT thành một native container image.

Đầu tiên, hãy đảm bảo có một Docker daemon (xem [Get Docker](https://docs.docker.com/installation/#installation) để biết thêm chi tiết). [Cấu hình nó để cho phép user không phải root](https://docs.docker.com/engine/install/linux-postinstall/#manage-docker-as-a-non-root-user) nếu bạn đang dùng Linux.

Bạn cũng cần cài đặt `pack` bằng cách làm theo [hướng dẫn cài đặt trên buildpacks.io](https://buildpacks.io//docs/tools/pack/#install).

Giả sử một jar thực thi Spring Boot đã được xử lý AOT được build thành `myproject-0.0.1-SNAPSHOT.jar` nằm trong thư mục `target`, hãy chạy:

```shell
$ pack build --builder paketobuildpacks/builder-jammy-tiny \
    --path target/myproject-0.0.1-SNAPSHOT.jar \
    --env 'BP_NATIVE_IMAGE=true' \
    my-application:0.0.1-SNAPSHOT
```

> **LƯU Ý (NOTE):** Bạn không cần cài đặt GraalVM ở máy cục bộ để sinh image theo cách này.

Sau khi `pack` hoàn tất, bạn có thể khởi chạy ứng dụng bằng `docker run`:

```shell
$ docker run --rm -p 8080:8080 docker.io/library/myproject:0.0.1-SNAPSHOT
```

## Sử dụng GraalVM native-image (Using GraalVM native-image)

Một tùy chọn khác để biến một jar thực thi Spring Boot đã được xử lý AOT thành một file thực thi native là dùng công cụ `native-image` của GraalVM. Để cách này hoạt động, bạn sẽ cần một bản phân phối GraalVM trên máy. Bạn có thể tải nó thủ công trên [trang Liberica Native Image Kit](https://bell-sw.com/pages/downloads/native-image-kit/#/nik-22-17) hoặc bạn có thể dùng một trình quản lý tải xuống như SDKMAN!.

Giả sử một jar thực thi Spring Boot đã được xử lý AOT được build thành `myproject-0.0.1-SNAPSHOT.jar` nằm trong thư mục `target`, hãy chạy:

```shell
$ rm -rf target/native
$ mkdir -p target/native
$ cd target/native
$ jar -xvf ../myproject-0.0.1-SNAPSHOT.jar
$ native-image -H:Name=myproject @META-INF/native-image/argfile -cp .:BOOT-INF/classes:`find BOOT-INF/lib | tr '\n' ':'`
$ mv myproject ../
```

> **LƯU Ý (NOTE):** Các lệnh này hoạt động trên máy Linux hoặc macOS, nhưng bạn sẽ cần điều chỉnh chúng cho Windows.

> **MẸO (TIP):** `@META-INF/native-image/argfile` có thể không được đóng gói trong jar của bạn. Nó chỉ được đưa vào khi cần ghi đè reachability metadata.

> **CẢNH BÁO (WARNING):** Cờ `-cp` của `native-image` không chấp nhận wildcard. Bạn cần đảm bảo tất cả các jar đều được liệt kê (lệnh ở trên dùng `find` và `tr` để làm điều này).
