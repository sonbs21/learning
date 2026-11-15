# 15.1. Giới thiệu GraalVM Native Image (Introducing GraalVM Native Images)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.introducing-graalvm-native-images>

GraalVM Native Image cung cấp một cách mới để triển khai và chạy ứng dụng Java. So với Java Virtual Machine, native image có thể chạy với dung lượng bộ nhớ sử dụng nhỏ hơn và thời gian khởi động nhanh hơn nhiều.

Chúng rất phù hợp với các ứng dụng được triển khai bằng container image và đặc biệt thú vị khi kết hợp với các nền tảng "Function as a service" (FaaS).

Không giống như các ứng dụng truyền thống được viết cho JVM, các ứng dụng GraalVM Native Image yêu cầu xử lý ahead-of-time để tạo ra một file thực thi. Việc xử lý ahead-of-time này bao gồm phân tích tĩnh code ứng dụng của bạn từ điểm vào (entry point) chính của nó.

Một GraalVM Native Image là một file thực thi hoàn chỉnh, đặc thù cho từng nền tảng. Bạn không cần phải đi kèm một Java Virtual Machine để chạy một native image.

> **MẸO (TIP):** Nếu bạn chỉ muốn bắt đầu và thử nghiệm với GraalVM, bạn có thể nhảy tới phần "[Phát triển ứng dụng GraalVM Native đầu tiên (Developing Your First GraalVM Native Application)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.developing-your-first-application)" và quay lại phần này sau.
