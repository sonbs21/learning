# Java Core Resources

## Knowledge

- [Spec: The Java Virtual Machine Specification, Java SE 21](https://docs.oracle.com/javase/specs/jvms/se21/html/index.html)
  Nguồn gốc của mọi thứ về JVM. Use for: class file format (ch.4), loading/linking/initialization (ch.5), bộ lệnh bytecode (ch.6), vùng nhớ runtime (§2.5).
- [Spec: The Java Language Specification, Java SE 21](https://docs.oracle.com/javase/specs/jls/se21/html/index.html)
  Định nghĩa chính thức của ngôn ngữ. Use for: câu bẫy ngữ nghĩa (pass-by-value, `==`, autoboxing), Java Memory Model (ch.17).
- [Oracle: Tài liệu lệnh `java`, `javac`, `javap` (JDK 21)](https://docs.oracle.com/en/java/javase/21/docs/specs/man/index.html)
  Use for: flag `-Xlog`, `-XX:...`, cách đọc output `javap`.
- [dev.java — Learn Java (Oracle)](https://dev.java/learn/)
  Tutorial chính thức, cập nhật. Use for: giải thích dễ đọc trước khi đọc spec.
- [Aleksey Shipilëv — JVM Anatomy Quarks](https://shipilev.net/jvm/anatomy-quarks/)
  Bài ngắn, đo đạc thật, do kỹ sư HotSpot viết. Use for: JIT, GC, object layout, các hiện tượng "lạ" của JVM.
- [Book: _Java Performance_ (2nd ed.) — Scott Oaks, O'Reilly](https://www.oreilly.com/library/view/java-performance-2nd/9781492056102/)
  Use for: GC, JIT, heap/thread dump, tuning thực tế.
- [Book: _Java Concurrency in Practice_ — Brian Goetz et al.](https://jcip.net/)
  Kinh điển về concurrency. Use for: thread safety, JMM, `volatile`, `synchronized`, executor.
- [Book: _Effective Java_ (3rd ed.) — Joshua Bloch](https://www.oreilly.com/library/view/effective-java-3rd/9780134686097/)
  Tác giả thiết kế Collections Framework. Use for: equals/hashCode (Item 10–11), immutability, generics, best practice API.
- [Source: OpenJDK jdk21u trên GitHub](https://github.com/openjdk/jdk21u/tree/master/src/java.base/share/classes)
  Source thật của thư viện chuẩn (máy này không có `src.zip`). Use for: trích nguyên văn `HashMap`, `Object`, `String`... khi giải thích "bên trong".
- [Oracle JDK 21 VM Guide: Tiered Compilation](https://docs.oracle.com/en/java/javase/21/vm/java-hotspot-virtual-machine-performance-enhancements.html#GUID-85BA7DE7-4AF9-47D9-BFCF-379230C66412)
  Tài liệu chính thức, ngắn. Use for: interpreter/C1/C2, warm-up.
- [Oracle JDK 21: GC Tuning Guide](https://docs.oracle.com/en/java/javase/21/gctuning/)
  Tài liệu chính thức về GC (G1, generations, tuning). Use for: mọi bài về GC.
- [Oracle JDK 21: Troubleshooting Memory Leaks](https://docs.oracle.com/en/java/javase/21/troubleshoot/troubleshooting-memory-leaks.html)
  Use for: OutOfMemoryError, heap dump, quy trình điều tra leak.
- [dev.java: The Collections Framework](https://dev.java/learn/api/collections-framework/)
  Loạt bài chính thức về List/Set/Map/Queue, dễ đọc. Use for: các bài collection.
- [Oracle Java Tutorial: Concurrency — Thread Interference / Synchronization](https://docs.oracle.com/javase/tutorial/essential/concurrency/interfere.html)
  Ngắn, dễ đọc, dùng đúng ví dụ `c++`. Use for: race condition, synchronized, intrinsic lock (nhập môn trước khi đọc JCiP).
- [Oracle JDK 21 Troubleshooting Guide: Diagnostic Tools](https://docs.oracle.com/en/java/javase/21/troubleshoot/diagnostic-tools.html)
  Use for: jcmd, jstack, thread dump, heap dump — quy trình debug production. Lưu ý: man page ghi jstack "experimental and unsupported", ưu tiên jcmd.
- [Spring Framework Reference: Proxying Mechanisms / Understanding AOP Proxies](https://docs.spring.io/spring-framework/reference/core/aop/proxying.html)
  Use for: proxy (JDK/CGLIB), self-invocation — cầu nối sang khoá Spring.
- [dev.java: Reflection](https://dev.java/learn/reflection/)
  Use for: reflection API, annotation lúc chạy.
- [JLS SE 21, Chapter 17.4: Memory Model](https://docs.oracle.com/javase/specs/jls/se21/html/jls-17.html#jls-17.4)
  Định nghĩa gốc happens-before (§17.4.5). Rất khó — chỉ để tra cứu/trích dẫn.
- [Aleksey Shipilëv — Close Encounters of The Java Memory Model Kind](https://shipilev.net/blog/2016/close-encounters-of-jmm-kind/)
  Nguồn đáng tin nhất về JMM ngoài spec. Use for: đào sâu sau khi đã nắm volatile/happens-before cơ bản.
- [JMH — Java Microbenchmark Harness (OpenJDK)](https://github.com/openjdk/jmh)
  Công cụ benchmark chuẩn. Use for: bất cứ khi nào cần đo hiệu năng nghiêm túc.
- [OpenJDK JEPs](https://openjdk.org/jeps/0)
  Use for: lý do thiết kế của từng tính năng (ví dụ JEP 280: string concat bằng `invokedynamic`).

## Wisdom (Communities)

- [r/java](https://www.reddit.com/r/java/)
  Moderation tốt, có kỹ sư OpenJDK tham gia. Use for: thảo luận thiết kế, tin tức JVM.
- [Stack Overflow — tag `java` / `jvm`](https://stackoverflow.com/questions/tagged/jvm)
  Use for: câu hỏi cụ thể, có code tái hiện.

## Gaps
- Chưa có nguồn tiếng Việt chất lượng cao, đáng tin cậy.
