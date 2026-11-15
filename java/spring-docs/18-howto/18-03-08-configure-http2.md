# 18.3.8. Cấu hình HTTP/2 (Configure HTTP/2)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.configure-http2>

Bạn có thể bật hỗ trợ HTTP/2 trong ứng dụng Spring Boot với property cấu hình `server.http2.enabled`. Cả `h2` (HTTP/2 qua TLS) và `h2c` (HTTP/2 qua TCP) đều được hỗ trợ. Để dùng `h2`, SSL cũng phải được bật. Khi SSL không được bật, `h2c` sẽ được dùng. Ví dụ, bạn có thể muốn dùng `h2c` khi ứng dụng của bạn [chạy sau một proxy server](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.webserver.use-behind-a-proxy-server) thực hiện TLS termination.

## HTTP/2 với Tomcat (HTTP/2 With Tomcat)

Spring Boot mặc định đi kèm Tomcat 10.1.x, vốn hỗ trợ sẵn `h2c` và `h2`. Ngoài ra, bạn có thể dùng `libtcnative` để hỗ trợ `h2` nếu thư viện này và các dependency của nó đã được cài đặt trên hệ điều hành máy chủ.

Thư mục thư viện phải được đưa vào library path của JVM, nếu chưa có. Bạn có thể làm vậy với một tham số JVM như `-Djava.library.path=/usr/local/opt/tomcat-native/lib`. Thêm thông tin về điều này trong [tài liệu chính thức của Tomcat](https://tomcat.apache.org/tomcat-10.1-doc/apr.html).

## HTTP/2 với Jetty (HTTP/2 With Jetty)

Để hỗ trợ HTTP/2, Jetty cần thêm dependency `org.eclipse.jetty.http2:jetty-http2-server`. Để dùng `h2c` thì không cần dependency nào khác. Để dùng `h2`, bạn cũng cần chọn một trong các dependency sau, tùy thuộc vào cách triển khai của bạn:

- `org.eclipse.jetty:jetty-alpn-java-server` để dùng hỗ trợ có sẵn trong JDK
- `org.eclipse.jetty:jetty-alpn-conscrypt-server` và [thư viện Conscrypt](https://www.conscrypt.org/)

## HTTP/2 với Reactor Netty (HTTP/2 With Reactor Netty)

`spring-boot-webflux-starter` mặc định dùng Reactor Netty làm server. Reactor Netty hỗ trợ sẵn `h2c` và `h2`. Để có hiệu năng runtime tối ưu, server này cũng hỗ trợ `h2` với các thư viện native. Để bật điều đó, ứng dụng của bạn cần có thêm một dependency.

Spring Boot quản lý phiên bản cho "uber jar" `io.netty:netty-tcnative-boringssl-static`, chứa các thư viện native cho tất cả các nền tảng. Developer có thể chọn chỉ import các dependency cần thiết bằng cách dùng classifier (xem [tài liệu chính thức của Netty](https://netty.io/wiki/forked-tomcat-native.html)).

## HTTP/2 với Undertow (HTTP/2 With Undertow)

Undertow hỗ trợ sẵn `h2c` và `h2`.
