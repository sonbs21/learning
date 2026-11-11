# Notes

- Ngôn ngữ: tiếng Việt, giữ nguyên thuật ngữ tiếng Anh (bean, proxy, container...).
- **Người học yêu cầu (2026-10-30): dạy như người mới, từ đầu, KHÔNG đọc/tham chiếu các file khác ngoài workspace này** (ví dụ `../boot/`).
- Mỗi bài chỉ một ý; giải thích bằng ví dụ đời thường trước, thuật ngữ sau.
- Lab: `./lab/` — Maven, Spring Boot 3.5.16 (có sẵn trong `~/.m2`, chạy offline: `mvn -o -q spring-boot:run`).
- `lab/src/main/resources/application.properties` đang tắt web server (`web-application-type=none`). Bỏ dòng này khi tới bài REST API.
- 2026-10-30: đã soạn bài 1–6 theo yêu cầu (mọi output/lỗi trong bài đều được chạy thật trên 3.5.16). Người học CHƯA làm/nộp thử thách nào → chưa có learning record về hiểu biết. Bài 7 nên là: database (JPA + H2) hoặc xử lý lỗi/validation, tuỳ kết quả thử thách.
- `drafts/`: bài auto-configuration đã soạn & kiểm chứng; dùng lại khi người học đã vững bean/container.
- Môi trường có hook bắt trình bày facts trước lệnh Bash/xoá/tạo file mới. Trong zsh, `$CMD` không tách từ — dùng function.
