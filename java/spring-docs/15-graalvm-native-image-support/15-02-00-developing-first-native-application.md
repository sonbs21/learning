# 15.2. Phát triển ứng dụng GraalVM Native đầu tiên (Developing Your First GraalVM Native Application)

> Nguồn: <https://docs.spring.io/spring-boot/docs/3.2.5/reference/htmlsingle/#native-image.developing-your-first-application>

Giờ chúng ta đã có cái nhìn tổng quan tốt về GraalVM Native Image và cách engine ahead-of-time của Spring hoạt động, chúng ta có thể xem cách tạo một ứng dụng.

Có hai cách chính để build một ứng dụng native image Spring Boot:

- Dùng hỗ trợ của Spring Boot cho Cloud Native Buildpacks để sinh một container nhẹ chứa một file thực thi native.
- Dùng GraalVM Native Build Tools để sinh một file thực thi native.

> **MẸO (TIP):** Cách dễ nhất để bắt đầu một project Spring Boot native mới là truy cập [start.spring.io](https://start.spring.io), thêm dependency "GraalVM Native Support" và sinh project. File `HELP.md` đi kèm sẽ cung cấp các gợi ý để bắt đầu.
