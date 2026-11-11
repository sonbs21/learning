// Bài 6 — reachability, GC và memory leak.
// Chạy kèm log GC, heap nhỏ để thấy GC hoạt động:
//   java -Xmx64m -Xlog:gc lessons/code/L0006Gc.java
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class L0006Gc {

    // "Cache" static sống suốt đời chương trình: static field là một GC root
    static final List<byte[]> CACHE = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        // 1. Object không còn ai trỏ tới => đủ điều kiện bị thu hồi
        Object strong = new Object();
        WeakReference<Object> weak = new WeakReference<>(strong);
        System.out.println("1. trước khi bỏ tham chiếu: weak.get() != null ? " + (weak.get() != null));
        strong = null;                // bỏ mũi tên duy nhất
        System.gc();                  // chỉ là GỢI Ý, JVM không bắt buộc chạy GC
        Thread.sleep(100);
        System.out.println("2. sau strong=null + gc   : weak.get() != null ? " + (weak.get() != null));

        // 2. Rất nhiều object chết trẻ => young GC dọn rẻ
        long sum = 0;
        for (int i = 0; i < 2_000_000; i++) {
            byte[] tmp = new byte[100];   // chết ngay sau vòng lặp
            sum += tmp.length;
        }
        System.out.println("3. đã cấp phát " + sum / 1_000_000 + " MB rác ngắn hạn, chương trình vẫn sống");

        // 3. Memory leak kiểu Java: object vẫn reachable từ static field
        try {
            for (int i = 0; ; i++) {
                CACHE.add(new byte[1_000_000]);   // 1 MB mỗi lần, không bao giờ xoá
            }
        } catch (OutOfMemoryError e) {
            int leaked = CACHE.size();
            CACHE.clear();                         // cứu chương trình để in kết quả
            System.out.println("4. OutOfMemoryError sau khi giữ ~" + leaked + " MB trong CACHE");
        }
    }
}
