# Notes

- **2026-11-15 — Người học phản hồi: bài 1–3 "đọc rất khó hiểu".** Nguyên nhân: quá nhiều thuật ngữ, bytecode, trích dẫn spec, nhiều ý trong một bài. Từ giờ:
  - Mở bằng ví dụ đời thường, chưa dùng thuật ngữ. Thuật ngữ chỉ đến sau, mỗi lần một cái.
  - Mỗi bài MỘT ý chính, code ngắn (≤ 15 dòng).
  - Bytecode/source JDK/spec đưa vào mục "Đào sâu (không bắt buộc)" thu gọn cuối bài.
  - Câu ngắn, ít chú thích chen ngang.
  - **Lần 2 (cùng ngày): người học muốn "chi tiết, dễ hiểu hơn"**, tức là giải thích TỪNG BƯỚC, KHÔNG nhảy cóc, chứ không phải viết ngắn hơn. Mỗi lệnh có khung "Bạn sẽ thấy" (`.expect`). Mọi thuật ngữ được giải thích bằng lời thường ngay lần đầu xuất hiện. Dùng các component `.analogy`, `.expect`, `.remember`, `details.deep` trong `assets/style.css`.
  - Bài 1 đã viết lại theo phong cách mới (mẫu chuẩn). Bài 2–3 vẫn là phong cách cũ: chờ người học xác nhận rồi viết lại.

- Ngôn ngữ: tiếng Việt, giữ thuật ngữ tiếng Anh (bytecode, heap, class loader...).
- Người học muốn đủ 3 hướng: phỏng vấn + nền Spring, debug production, hiểu sâu JVM.
- Mỗi bài: HTML + code chạy thật trong `./lab/lNNNN/` + quiz. Mọi output in trong bài phải được chạy thật trên JDK 21 của máy.
- Assets lấy từ khoá `../java-spring/assets/` (đổi màu accent sang cam cà phê) để hai khoá đồng bộ.
- Môi trường có hook bắt trình bày facts trước lệnh Bash đầu tiên và trước khi tạo file mới.

- 2026-11-15: đã soạn bài 1–2. Người học CHƯA gửi kết quả quiz/thử thách nào → chưa có learning record về hiểu biết. Bài 2 hỏi người học giải thích String vs StringBuilder: dùng câu trả lời đó để viết learning record.
- Component `.mem` (sơ đồ stack/heap) trong `assets/style.css`: dùng lại cho bài `==`/equals, String pool, GC.
- Mỗi bài mở đầu bằng callout "Ôn bài trước" (spaced retrieval) và quiz có ≥1 câu xen kẽ kiến thức bài cũ.

## Lộ trình dự kiến (sửa theo learning records)
1. Từ source đến bytecode (javac, javap, class loading) ✅ đã soạn
2. Bộ nhớ JVM: stack frame vs heap → pass-by-value ✅ đã soạn (2026-11-15)
3. `==` vs `equals`, `hashCode` contract ✅ đã soạn (2026-11-15); bài 3 kết bằng câu hỏi `"hi" == "hi"` → dẫn sang bài 4
4. String pool, immutability, autoboxing cache ✅ đã soạn (2026-11-15) theo phong cách mới (có giải thích lại reference/== ngay trong bài vì bài 2–3 chưa viết lại)
5. Class loading sâu: loading/linking/init, `static` chạy khi nào ✅ đã soạn (2026-11-17), phong cách mới. ClassLoader hierarchy CHƯA dạy → để dành cho bài reflection/Spring.
6. Interpreter vs JIT ✅ đã soạn (2026-11-17): đo warm-up (~270µs → ~22µs), -Xint, BadBenchmark (thử thách tự viết code)
7. GC: generations, G1, đọc GC log ✅ đã soạn (2026-11-17): reachability (bóng bay), Temp vs Leak với -Xmx32m, đọc GC log, heap dump. Lưu ý kỹ thuật: dùng object 10 KB (1 MB bị humongous), gán vào static để JIT không loại bỏ cấp phát (escape analysis), bắt OOM rồi null cache để in được lỗi.
8. HashMap/ArrayList bên trong — ArrayList ✅ đã soạn (2026-11-17): grow 10→15→22 (reflection + --add-opens), remove(0) chậm ~190x, contains List vs Set, thử thách ArrayDeque. HashMap bên trong ✅ bài 9 (2026-11-17): Peek.java (reflection, `--add-opens=` dạng 1 từ vì zsh), chi/dung collision tự nhiên ngăn 5, Aa/BB, resize 13/25, thứ tự HashMap thay đổi, newHashMap, thử thách tự tính ngăn 1/17/33. Bài 9 giải thích lại equals/hashCode bằng phong cách mới, nên bài 3 cũ bớt quan trọng.
9. Exception: chi phí, stack trace
10. Thread, JMM, `volatile`, `synchronized`, thread dump — race condition ✅ bài 10 (2026-11-17): Race (~105k/200k), bytecode count++ (javap -c -p, lambda), FixSync, FixAtomic, MapRace (HashMap mất ~25%) → ConcurrentHashMap; liên hệ Spring singleton. Deadlock + thread dump ✅ bài 11 (2026-11-17): Deadlock.java (thread có tên), 2 terminal, jps + jcmd Thread.print (jstack là "experimental"), đọc BLOCKED/locked/waiting to lock, "Found one Java-level deadlock", thử thách lock ordering. **Còn lại:** volatile/visibility/JMM.
11. Reflection, dynamic proxy, annotation → cầu nối sang Spring ✅ bài 12 (2026-11-17): @Tx tự viết, TxProxy (InvocationHandler), 3 trường hợp (proxy / real / self-invocation), thử thách bỏ @Retention(RUNTIME), thử thách mở (sửa để case 3 có Tx). **Đã đi hết lộ trình ban đầu (bài 2–3 cũ chưa viết lại).**
12. volatile/JMM ✅ bài 13 (2026-11-17): StopFlag treo (timeout exit 124), -Xint chạy được (JIT hoisting), thread dump RUNNABLE + CPU 100% (đối lập deadlock), volatile sửa, VolatileCount vẫn mất (~132k), bảng happens-before trích JLS, thử thách AtomicBoolean. Lưu ý kỹ thuật: dùng `timeout 5` để chạy chương trình treo.
13. Thread pool ✅ bài 14 (2026-11-17): NewThreadEachTask (~340ms) vs Pool (~20ms), WhoDoesIt, FutureDemo (KHÔNG đặt tên class là `Future` — trùng java.util.concurrent.Future), ForgotShutdown (exit 124, dump WAITING ở LinkedBlockingQueue.take), Tomcat 200, Đào sâu virtual thread (211ms vs 5021ms). Người học chưa chọn hướng tiếp theo; các lựa chọn: viết lại bài 2–3, ôn tập tổng hợp, exception, generics, CompletableFuture.
14. `final` ✅ bài 15 (2026-11-17, người học yêu cầu): FinalList, loi/ (4 file cố tình lỗi biên dịch: ReassignFinal, LambdaCount, OverrideFinal, ExtendString), DonHangHo (final field vẫn bị sửa) → DonHangKin (List.copyOf), JLS §4.12.4/§17.5, thử thách record + compact constructor. Lưu ý zsh: URL có dấu ngoặc phải đặt trong nháy đơn. Cần hỏi người học bước tiếp: JMM/volatile, viết lại bài 2–3, exception, generics, hay ôn tập tổng hợp.
