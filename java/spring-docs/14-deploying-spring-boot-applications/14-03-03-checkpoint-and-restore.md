# 14.3.3. Checkpoint và Restore với JVM (Checkpoint and Restore With the JVM)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#deployment.efficient.checkpoint-restore>

[Coordinated Restore at Checkpoint](https://wiki.openjdk.org/display/crac/Main) (CRaC) là một project OpenJDK định nghĩa một Java API mới cho phép bạn tạo checkpoint và khôi phục (restore) một ứng dụng trên JVM HotSpot. Nó dựa trên [CRIU](https://github.com/checkpoint-restore/criu), một project hiện thực chức năng checkpoint/restore trên Linux.

Nguyên tắc như sau: bạn khởi động ứng dụng gần như bình thường nhưng với một phiên bản JDK có bật CRaC như [Bellsoft Liberica JDK with CRaC](https://bell-sw.com/pages/downloads/?package=jdk-crac) hoặc [Azul Zulu JDK with CRaC](https://www.azul.com/downloads/?package=jdk-crac#zulu). Sau đó, tại một thời điểm nào đó, có thể là sau một số khối lượng công việc giúp "làm nóng" JVM bằng cách thực thi tất cả các đường code phổ biến, bạn kích hoạt một checkpoint bằng một lời gọi API, một lệnh `jcmd`, một HTTP endpoint, hoặc một cơ chế khác.

Một biểu diễn bộ nhớ của JVM đang chạy, bao gồm cả trạng thái "đã được làm nóng" của nó, sau đó được serialize xuống đĩa, cho phép khôi phục nhanh chóng vào một thời điểm sau, có thể trên một máy khác có hệ điều hành và kiến trúc CPU tương tự. Tiến trình được khôi phục giữ lại tất cả các khả năng của JVM HotSpot, bao gồm cả các tối ưu JIT tiếp theo tại runtime.

Dựa trên nền tảng do Spring Framework cung cấp, Spring Boot cung cấp hỗ trợ cho việc tạo checkpoint và khôi phục ứng dụng, đồng thời quản lý sẵn vòng đời của các tài nguyên như socket, file và thread pool [trong một phạm vi hạn chế](https://github.com/spring-projects/spring-lifecycle-smoke-tests/blob/main/STATUS.adoc). Việc quản lý vòng đời bổ sung được mong đợi cho các dependency khác và có thể cho code ứng dụng xử lý các tài nguyên như vậy.

Bạn có thể tìm thêm chi tiết về hai chế độ được hỗ trợ ("checkpoint/restore theo yêu cầu của một ứng dụng đang chạy" và "checkpoint/restore tự động khi khởi động"), cách bật hỗ trợ checkpoint và restore, và một số hướng dẫn trong [tài liệu hỗ trợ JVM Checkpoint Restore của Spring Framework](https://docs.spring.io/spring-framework/reference/6.1/integration/checkpoint-restore.html).
