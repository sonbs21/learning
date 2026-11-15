# 18.15.1. Nạp lại nội dung tĩnh (Reload Static Content)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.hotswapping.reload-static-content>

Có một vài tùy chọn cho hot reloading. Cách tiếp cận được khuyến nghị là dùng [`spring-boot-devtools`](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools), vì nó cung cấp thêm các tính năng trong lúc phát triển, chẳng hạn như hỗ trợ khởi động lại ứng dụng nhanh và LiveReload cũng như cấu hình hợp lý cho thời gian phát triển (chẳng hạn như template caching). Devtools hoạt động bằng cách giám sát các thay đổi trên classpath. Điều này có nghĩa là các thay đổi tài nguyên tĩnh phải được "build" thì thay đổi mới có hiệu lực. Theo mặc định, điều này xảy ra tự động trong Eclipse khi bạn lưu thay đổi. Trong IntelliJ IDEA, lệnh Make Project sẽ kích hoạt quá trình build cần thiết. Do [các loại trừ khởi động lại mặc định (default restart exclusions)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#using.devtools.restart.excluding-resources), các thay đổi đối với tài nguyên tĩnh không kích hoạt việc khởi động lại ứng dụng. Tuy nhiên, chúng có kích hoạt live reload.

Ngoài ra, chạy trong một IDE (đặc biệt là khi bật debug) là một cách tốt để phát triển (tất cả các IDE hiện đại đều cho phép nạp lại tài nguyên tĩnh và thường cũng cho phép hot-swap các thay đổi class Java).

Cuối cùng, [các plugin Maven và Gradle](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#build-tool-plugins) có thể được cấu hình (xem property `addResources`) để hỗ trợ chạy từ dòng lệnh với việc nạp lại các file tĩnh trực tiếp từ mã nguồn. Bạn có thể dùng điều đó với một process biên dịch css/js bên ngoài nếu bạn đang viết code đó bằng các công cụ cấp cao hơn.
