# Mission: Java core đến tận bản chất JVM

## Why
Trở thành backend Java engineer giải thích được "Java chạy thế nào và tại sao": tự tin khi phỏng vấn, debug được lỗi production (memory leak, deadlock, chậm, OOM), và có nền vững để hiểu Spring (proxy, reflection, class loading).

## Success looks like
- Giải thích được từ `.java` → bytecode → class loading → interpreter/JIT, và chứng minh bằng `javap`, `-Xlog`.
- Vẽ được bộ nhớ JVM (stack, heap, metaspace) và dự đoán object nằm đâu, khi nào bị GC thu.
- Trả lời đúng các câu bẫy: pass-by-value, `==` vs `equals`, `hashCode` contract, String pool, autoboxing.
- Hiểu HashMap/ArrayList từ bên trong, chọn đúng collection theo độ phức tạp.
- Đọc được thread dump / heap dump / GC log; giải thích Java Memory Model, `volatile`, `synchronized`, happens-before.
- Hiểu reflection, dynamic proxy, annotation — đủ để thấy Spring "làm phép" thế nào.

## Constraints
- Đã viết được Java (class, interface, collection, OOP cơ bản) nhưng chưa hiểu sâu bên dưới.
- Mỗi bài: HTML ngắn + code Java chạy thật trong `./lab/` + quiz.
- Môi trường: OpenJDK 21 (Ubuntu), dùng công cụ JDK sẵn có (`javac`, `javap`, `jcmd`, `jstack`...).

## Out of scope (hiện tại)
- Spring (có workspace riêng `../java-spring/`).
- GUI (Swing/JavaFX), Java EE cũ.
- Viết JVM/compiler riêng.
