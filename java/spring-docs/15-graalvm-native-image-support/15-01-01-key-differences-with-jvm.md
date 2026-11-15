# 15.1.1. Những khác biệt chính so với triển khai trên JVM (Key Differences with JVM Deployments)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.introducing-graalvm-native-images.key-differences-with-jvm-deployments>

Việc GraalVM Native Image được tạo ra ahead-of-time có nghĩa là có một số khác biệt chính giữa ứng dụng native và ứng dụng dựa trên JVM. Những khác biệt chính là:

- Phân tích tĩnh ứng dụng được thực hiện tại thời điểm build từ điểm vào `main`.
- Code không thể được tiếp cận khi native image được tạo ra sẽ bị loại bỏ và sẽ không là một phần của file thực thi.
- GraalVM không trực tiếp biết về các thành phần động trong code của bạn và phải được thông báo về reflection, resource, serialization, và dynamic proxy.
- Classpath của ứng dụng được cố định tại thời điểm build và không thể thay đổi.
- Không có việc nạp class lười (lazy class loading), mọi thứ đi kèm trong file thực thi sẽ được nạp vào bộ nhớ khi khởi động.
- Có một số hạn chế xung quanh một số khía cạnh của ứng dụng Java chưa được hỗ trợ đầy đủ.

Ngoài những khác biệt đó, Spring dùng một quá trình gọi là [xử lý Ahead-of-Time của Spring (Spring Ahead-of-Time processing)](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.introducing-graalvm-native-images.understanding-aot-processing), áp đặt thêm các hạn chế. Vui lòng đảm bảo đọc ít nhất phần đầu của mục tiếp theo để tìm hiểu về những hạn chế đó.

> **MẸO (TIP):** Phần [Native Image Compatibility Guide](https://www.graalvm.org/22.3/reference-manual/native-image/metadata/Compatibility/) trong tài liệu tham khảo GraalVM cung cấp thêm chi tiết về các hạn chế của GraalVM.
