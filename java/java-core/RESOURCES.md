# Java Core Resources

## Knowledge

- [Spec: The Java Language Specification, Java SE 21 (JLS)](https://docs.oracle.com/javase/specs/jls/se21/html/index.html)
  Nguồn chuẩn tuyệt đối cho *ngữ nghĩa ngôn ngữ*. Dùng cho: kiểu dữ liệu (§4), class và method (§8), biểu thức và `==` (§15), memory model (§17).
- [Spec: The Java Virtual Machine Specification, Java SE 21 (JVMS)](https://docs.oracle.com/javase/specs/jvms/se21/html/index.html)
  Nguồn chuẩn cho *cách JVM chạy*. Dùng cho: run-time data areas, stack và frame (§2.5–2.6), bytecode (§6), class loading (§5).
- [dev.java — Learn (Oracle)](https://dev.java/learn/)
  Tutorial chính thức, cập nhật theo Java hiện đại. Dùng cho: giải thích dễ đọc trước khi đọc spec; công cụ JDK như [`javap`](https://dev.java/learn/jvm/tools/core/javap/).
- [JVM Anatomy Quarks — Aleksey Shipilëv](https://shipilev.net/jvm/anatomy-quarks/)
  Các bài ngắn, rất sâu, của một kỹ sư HotSpot. Dùng cho: object layout, escape analysis, GC và JIT khi đã vững nền.
- Book: *Effective Java* (3rd ed.) — Joshua Bloch
  Best practice gắn với bản chất. Dùng cho: `equals`/`hashCode`, immutability, generics.
- Book: *Java Concurrency in Practice* — Brian Goetz et al.
  Sách kinh điển về concurrency. Dùng cho: thread, `synchronized`, `volatile`, happens-before.
- [Baeldung](https://www.baeldung.com/)
  Bài viết thực hành, đôi chỗ nông. Dùng cho: ví dụ code nhanh. Luôn đối chiếu lại với JLS/JVMS.

## Wisdom (Communities)

- [r/javahelp](https://www.reddit.com/r/javahelp/)
  Subreddit kiểm duyệt chặt, chuyên hỏi đáp Java. Dùng cho: hỏi khi code chạy "sai" so với điều mình tưởng.
- [Stack Overflow — tag `java`](https://stackoverflow.com/questions/tagged/java)
  Dùng cho: tìm các câu hỏi kinh điển (vd. "Is Java pass-by-reference or pass-by-value?") và đọc các câu trả lời được vote cao.

## Gaps
- Chưa có nguồn tiếng Việt đủ tin cậy; tạm dùng nguồn tiếng Anh ở trên.
