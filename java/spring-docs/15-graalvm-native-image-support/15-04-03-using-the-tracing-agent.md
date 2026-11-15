# 15.4.3. Sử dụng Tracing Agent (Using the Tracing Agent)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.advanced.using-the-tracing-agent>

[Tracing agent](https://www.graalvm.org/22.3/reference-manual/native-image/metadata/AutomaticMetadataCollection) của GraalVM native image cho phép bạn chặn bắt việc sử dụng reflection, resource hoặc proxy trên JVM để sinh ra các gợi ý liên quan. Spring sẽ tự động sinh hầu hết các gợi ý này, nhưng tracing agent có thể được dùng để nhanh chóng xác định các mục còn thiếu.

Khi dùng agent để sinh gợi ý cho một native image, có một vài cách tiếp cận:

- Khởi chạy ứng dụng trực tiếp và sử dụng nó.
- Chạy các test của ứng dụng để sử dụng ứng dụng.

Tùy chọn đầu tiên thú vị để xác định các gợi ý còn thiếu khi một thư viện hoặc một mẫu không được Spring nhận ra.

Tùy chọn thứ hai nghe có vẻ hấp dẫn hơn cho một thiết lập có thể lặp lại, nhưng theo mặc định các gợi ý được sinh ra sẽ bao gồm mọi thứ mà hạ tầng test cần. Một số trong số này sẽ không cần thiết khi ứng dụng chạy thật. Để giải quyết vấn đề này, agent hỗ trợ một file access-filter khiến một số dữ liệu nhất định bị loại trừ khỏi output được sinh ra.

## Khởi chạy ứng dụng trực tiếp (Launch the Application Directly)

Dùng lệnh sau để khởi chạy ứng dụng với native image tracing agent được gắn vào:

```shell
$ java -Dspring.aot.enabled=true \
    -agentlib:native-image-agent=config-output-dir=/path/to/config-dir/ \
    -jar target/myproject-0.0.1-SNAPSHOT.jar
```

Giờ bạn có thể sử dụng các đường code mà bạn muốn có gợi ý, rồi dừng ứng dụng bằng `ctrl-c`.

Khi ứng dụng tắt, native image tracing agent sẽ ghi các file gợi ý vào thư mục output cấu hình đã cho. Bạn có thể kiểm tra thủ công các file này, hoặc dùng chúng làm đầu vào cho quá trình build native image. Để dùng chúng làm đầu vào, hãy sao chép chúng vào thư mục `src/main/resources/META-INF/native-image/`. Lần tiếp theo bạn build native image, GraalVM sẽ xem xét các file này.

Có các tùy chọn nâng cao hơn có thể được đặt trên native image tracing agent, ví dụ lọc các gợi ý được ghi lại theo class gọi, v.v. Để đọc thêm, vui lòng xem [tài liệu chính thức](https://www.graalvm.org/22.3/reference-manual/native-image/metadata/AutomaticMetadataCollection).
