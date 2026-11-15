# 18.12.2. Chạy các job Spring Batch lúc khởi động (Running Spring Batch Jobs on Startup)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.batch.running-jobs-on-startup>

Auto-configuration của Spring Batch được bật bằng cách thêm `spring-boot-starter-batch` vào classpath của ứng dụng.

Nếu tìm thấy một bean `Job` duy nhất trong application context, nó sẽ được thực thi lúc khởi động (xem [`JobLauncherApplicationRunner`](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/batch/JobLauncherApplicationRunner.java) để biết chi tiết). Nếu tìm thấy nhiều bean `Job`, job cần thực thi phải được chỉ định bằng `spring.batch.job.name`.

Để vô hiệu hóa việc chạy một `Job` được tìm thấy trong application context, hãy đặt `spring.batch.job.enabled` thành `false`.

Xem [BatchAutoConfiguration](https://github.com/spring-projects/spring-boot/tree/v3.2.5/spring-boot-project/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/batch/BatchAutoConfiguration.java) để biết thêm chi tiết.
