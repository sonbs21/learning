import java.util.List;
import java.util.stream.Stream;

public class Lazy {
    public static void main(String[] args) {
        List<Integer> so = List.of(1, 2, 3, 4, 5, 6);

        System.out.println("--- Chỉ khai báo, chưa có thao tác cuối:");
        Stream<Integer> s = so.stream()
            .filter(x -> { System.out.println("  filter " + x); return x % 2 == 0; })
            .map(x -> { System.out.println("  map " + x); return x * 10; });
        System.out.println("(chưa in gì ở trên)");

        System.out.println("--- Gọi findFirst():");
        System.out.println("Kết quả: " + s.findFirst().get());

        System.out.println("--- Dùng lại stream đã dùng:");
        try { s.count(); } catch (IllegalStateException e) { System.out.println(e); }
    }
}
