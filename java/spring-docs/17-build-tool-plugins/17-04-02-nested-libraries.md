# 17.4.2. Thư viện lồng nhau (Nested Libraries)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#build-tool-plugins.other-build-systems.nested-libraries>

Khi đóng gói lại một file nén, bạn có thể đưa vào các tham chiếu tới các file dependency bằng interface `org.springframework.boot.loader.tools.Libraries`. Chúng tôi không cung cấp bất kỳ implementation cụ thể nào của `Libraries` ở đây vì chúng thường đặc thù cho từng hệ thống build.

Nếu file nén của bạn đã bao gồm các thư viện, bạn có thể dùng `Libraries.NONE`.
