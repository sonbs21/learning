# 18.13.3. Tùy chỉnh việc làm sạch dữ liệu (Customizing Sanitization)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.actuator.customizing-sanitization>

Để kiểm soát việc làm sạch (sanitization), hãy định nghĩa một bean `SanitizingFunction`. `SanitizableData` mà hàm được gọi với nó cung cấp quyền truy cập vào key và value cũng như `PropertySource` mà chúng xuất phát từ đó. Điều này cho phép bạn, ví dụ, làm sạch mọi giá trị đến từ một property source cụ thể. Mỗi `SanitizingFunction` được gọi theo thứ tự cho đến khi có một hàm thay đổi giá trị của dữ liệu có thể làm sạch.
