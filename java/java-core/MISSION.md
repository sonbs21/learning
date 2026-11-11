# Mission: Java Core — hiểu tới bản chất

## Why
Mình dưới 1 năm kinh nghiệm Java và muốn hiểu *bên dưới chạy thế nào*, không chỉ cách dùng. Mục tiêu là vượt được vòng phỏng vấn kỹ thuật Java backend, đồng thời debug và đọc hiểu code thật (Spring, thư viện) tự tin hơn.

## Success looks like
- Giải thích được bằng lời và sơ đồ chuyện gì xảy ra trong bộ nhớ (stack, heap) khi chạy một đoạn code bất kỳ
- Trả lời sâu, có dẫn chứng, các câu trong `../core/phong-van.md`: primitive và reference, `==` và `equals`, HashMap, String, pass-by-value, JVM memory/GC, thread và `synchronized`/`volatile`
- Đoán đúng output của các câu đố Java "bẫy" trước khi chạy
- Đọc được bytecode cơ bản (`javap -c`) để kiểm chứng điều mình hiểu

## Constraints
- Không có deadline, học lâu dài và đào sâu
- Hình thức: giải thích bản chất, kèm quiz và code chạy thử (JDK 21 có sẵn trên máy)
- Học bằng tiếng Việt, thuật ngữ giữ tiếng Anh

## Out of scope
- Spring/framework (có thư mục `../spring` riêng); chỉ nhắc tới khi cần minh hoạ ứng dụng
- Tuning GC chuyên sâu cho production, khi chưa vững nền tảng
