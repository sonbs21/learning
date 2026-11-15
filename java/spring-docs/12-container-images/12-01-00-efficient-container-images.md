# 12.1. Container image hiệu quả (Efficient Container Images)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#container-images.efficient-images>

Có thể dễ dàng đóng gói một uber jar Spring Boot thành một docker image. Tuy nhiên, có nhiều nhược điểm khi sao chép và chạy uber jar nguyên trạng trong docker image. Luôn có một lượng chi phí phát sinh (overhead) nhất định khi chạy một uber jar mà không giải nén nó, và trong một môi trường container hóa, điều này có thể dễ nhận thấy. Vấn đề khác là việc đặt code ứng dụng và tất cả các dependency của nó vào một layer duy nhất trong Docker image là chưa tối ưu. Vì bạn có thể biên dịch lại code thường xuyên hơn so với việc nâng cấp phiên bản Spring Boot mà bạn dùng, thường tốt hơn nếu tách biệt mọi thứ thêm một chút. Nếu bạn đặt các file jar vào layer trước các class của ứng dụng, Docker thường chỉ cần thay đổi layer dưới cùng và có thể lấy các layer khác từ cache của nó.
