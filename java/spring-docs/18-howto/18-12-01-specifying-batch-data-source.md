# 18.12.1. Chỉ định data source cho batch (Specifying a Batch Data Source)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#howto.batch.specifying-a-data-source>

Theo mặc định, các ứng dụng batch cần một `DataSource` để lưu trữ chi tiết các job. Spring Batch mặc định mong đợi một `DataSource` duy nhất. Để nó dùng một `DataSource` khác với `DataSource` chính của ứng dụng, hãy khai báo một bean `DataSource`, annotate phương thức `@Bean` của nó với `@BatchDataSource`. Nếu bạn làm vậy và muốn có hai data source, hãy nhớ đánh dấu cái còn lại là `@Primary`. Để kiểm soát nhiều hơn, hãy thêm `@EnableBatchProcessing` vào một trong các class `@Configuration` của bạn hoặc kế thừa `DefaultBatchConfiguration`. Xem Javadoc của [`@EnableBatchProcessing`](https://docs.spring.io/spring-batch/docs/5.1.1/org/springframework/batch/core/configuration/annotation/EnableBatchProcessing.html) và [`DefaultBatchConfiguration`](https://docs.spring.io/spring-batch/docs/5.1.1/org/springframework/batch/core/configuration/support/DefaultBatchConfiguration.html) để biết thêm chi tiết.

Để biết thêm thông tin về Spring Batch, xem [trang dự án Spring Batch](https://spring.io/projects/spring-batch).
