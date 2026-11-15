# 15.2.3. Build native image bằng Native Build Tools (Building a Native Image using Native Build Tools)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.developing-your-first-application.native-build-tools>

Nếu bạn muốn sinh trực tiếp một file thực thi native mà không dùng Docker, bạn có thể dùng GraalVM Native Build Tools. Native Build Tools là các plugin do GraalVM cung cấp cho cả Maven và Gradle. Bạn có thể dùng chúng để thực hiện nhiều tác vụ GraalVM khác nhau, bao gồm việc sinh native image.

## Điều kiện tiên quyết (Prerequisites)

Để build một native image bằng Native Build Tools, bạn sẽ cần một bản phân phối GraalVM trên máy. Bạn có thể tải nó thủ công trên [trang Liberica Native Image Kit](https://bell-sw.com/pages/downloads/native-image-kit/#/nik-22-17), hoặc bạn có thể dùng một trình quản lý tải xuống như SDKMAN!.

### Linux và macOS (Linux and macOS)

Để cài đặt trình biên dịch native image trên macOS hoặc Linux, chúng tôi khuyên dùng SDKMAN!. Hãy lấy SDKMAN! từ [sdkman.io](https://sdkman.io) và cài đặt bản phân phối Liberica GraalVM bằng các lệnh sau:

```shell
$ sdk install java 22.3.r17-nik
$ sdk use java 22.3.r17-nik
```

Kiểm tra rằng phiên bản đúng đã được cấu hình bằng cách xem output của `java -version`:

```shell
$ java -version
openjdk version "17.0.5" 2022-10-18 LTS
OpenJDK Runtime Environment GraalVM 22.3.0 (build 17.0.5+8-LTS)
OpenJDK 64-Bit Server VM GraalVM 22.3.0 (build 17.0.5+8-LTS, mixed mode)
```

### Windows

Trên Windows, hãy làm theo [hướng dẫn này](https://medium.com/graalvm/using-graalvm-and-native-image-on-windows-10-9954dc071311) để cài đặt [GraalVM](https://www.graalvm.org/downloads/) hoặc [Liberica Native Image Kit](https://bell-sw.com/pages/downloads/native-image-kit/#/nik-22-17) phiên bản 22.3, Visual Studio Build Tools và Windows SDK. Do [giới hạn độ dài tối đa của dòng lệnh trên Windows](https://docs.microsoft.com/en-US/troubleshoot/windows-client/shell-experience/command-line-string-limitation), hãy đảm bảo dùng x64 Native Tools Command Prompt thay vì dòng lệnh Windows thông thường để chạy các plugin Maven hoặc Gradle.

## Sử dụng Maven (Using Maven)

Cũng như với [hỗ trợ buildpack](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.developing-your-first-application.buildpacks.maven), bạn cần đảm bảo đang dùng `spring-boot-starter-parent` để kế thừa profile `native` và plugin `org.graalvm.buildtools:native-maven-plugin` được sử dụng.

Với profile `native` được kích hoạt, bạn có thể gọi goal `native:compile` để kích hoạt việc biên dịch `native-image`:

```shell
$ mvn -Pnative native:compile
```

File thực thi native image có thể được tìm thấy trong thư mục `target`.

## Sử dụng Gradle (Using Gradle)

Khi plugin Native Build Tools Gradle được áp dụng cho project, plugin Spring Boot Gradle sẽ tự động kích hoạt engine Spring AOT. Các dependency giữa các task được cấu hình tự động, vì vậy bạn chỉ cần chạy task tiêu chuẩn `nativeCompile` để sinh một native image:

```shell
$ gradle nativeCompile
```

File thực thi native image có thể được tìm thấy trong thư mục `build/native/nativeCompile`.

## Chạy ví dụ (Running the Example)

Đến đây, ứng dụng của bạn đã có thể hoạt động. Giờ bạn có thể khởi động ứng dụng bằng cách chạy nó trực tiếp:

**Maven**

```shell
$ target/myproject
```

**Gradle**

```shell
$ build/native/nativeCompile/myproject
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
