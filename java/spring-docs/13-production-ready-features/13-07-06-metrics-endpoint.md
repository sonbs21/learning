# 13.7.6. Endpoint metrics (Metrics Endpoint)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.metrics.endpoint>

Spring Boot cung cấp một endpoint `metrics` mà bạn có thể dùng cho mục đích chẩn đoán để xem các metric được ứng dụng thu thập. Endpoint này không khả dụng theo mặc định và phải được expose. Xem [expose endpoint](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#actuator.endpoints.exposing) để biết thêm chi tiết.

Truy cập `/actuator/metrics` sẽ hiển thị danh sách các tên meter có sẵn. Bạn có thể đi sâu để xem thông tin về một meter cụ thể bằng cách cung cấp tên của nó làm selector — ví dụ, `/actuator/metrics/jvm.memory.max`.

> **MẸO (TIP):** Tên bạn dùng ở đây phải khớp với tên được dùng trong code, chứ không phải tên sau khi đã được chuẩn hóa theo quy ước đặt tên cho một hệ thống giám sát mà nó được gửi tới. Nói cách khác, nếu `jvm.memory.max` xuất hiện là `jvm_memory_max` trong Prometheus do quy ước đặt tên snake case của nó, bạn vẫn nên dùng `jvm.memory.max` làm selector khi xem meter trong endpoint `metrics`.

Bạn cũng có thể thêm bất kỳ số lượng query parameter `tag=KEY:VALUE` nào vào cuối URL để đi sâu theo chiều vào một meter — ví dụ, `/actuator/metrics/jvm.memory.max?tag=area:nonheap`.

> **MẸO (TIP):** Các phép đo được báo cáo là *tổng* thống kê của tất cả các meter khớp với tên meter và mọi tag đã được áp dụng. Trong ví dụ trên, thống kê `Value` được trả về là tổng dung lượng bộ nhớ tối đa của các vùng "Code Cache", "Compressed Class Space", và "Metaspace" của heap. Nếu bạn chỉ muốn xem kích thước tối đa cho "Metaspace", bạn có thể thêm một `tag=id:Metaspace` — tức là, `/actuator/metrics/jvm.memory.max?tag=area:nonheap&tag=id:Metaspace`.
