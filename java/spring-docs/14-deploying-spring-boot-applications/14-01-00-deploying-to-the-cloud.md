# 14.1. Triển khai lên Cloud (Deploying to the Cloud)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#deployment.cloud>

Các jar thực thi của Spring Boot được làm sẵn cho hầu hết các nhà cung cấp PaaS (Platform-as-a-Service) cloud phổ biến. Các nhà cung cấp này có xu hướng yêu cầu bạn "tự mang container của mình" ("bring your own container"). Họ quản lý các tiến trình ứng dụng (không riêng ứng dụng Java), vì vậy họ cần một lớp trung gian để điều hợp ứng dụng *của bạn* với khái niệm tiến trình đang chạy của *cloud*.

Hai nhà cung cấp cloud phổ biến, Heroku và Cloud Foundry, dùng cách tiếp cận "buildpack". Buildpack bọc code đã triển khai của bạn trong bất cứ thứ gì cần thiết để *khởi động* ứng dụng. Đó có thể là một JDK và một lời gọi `java`, một web server nhúng, hoặc một application server đầy đủ. Một buildpack có thể cắm thêm (pluggable), nhưng lý tưởng nhất là bạn nên có thể dùng nó với càng ít tùy chỉnh càng tốt. Điều này giảm phạm vi chức năng nằm ngoài tầm kiểm soát của bạn. Nó giảm thiểu sự khác biệt giữa môi trường phát triển và production.

Lý tưởng nhất, ứng dụng của bạn, giống như một jar thực thi Spring Boot, có mọi thứ nó cần để chạy được đóng gói bên trong nó.

Trong phần này, chúng ta xem xét những gì cần thiết để đưa [ứng dụng mà chúng ta đã phát triển](https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#getting-started.first-application) trong phần "Bắt đầu" lên chạy trên Cloud.
