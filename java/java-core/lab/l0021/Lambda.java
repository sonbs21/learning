import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.*;

public class Lambda {
    @FunctionalInterface
    interface TinhGia { long tinh(long giaGoc); }      // đúng 1 method abstract

    public static void main(String[] args) {
        List<String> mon = new ArrayList<>(List.of("trà sữa", "cà phê", "nước cam"));

        // Cách cũ: class ẩn danh
        mon.sort(new Comparator<String>() {
            @Override public int compare(String a, String b) { return a.length() - b.length(); }
        });
        System.out.println("Class ẩn danh: " + mon);

        // Cách mới: lambda, cùng ý nghĩa
        mon.sort((a, b) -> b.length() - a.length());
        System.out.println("Lambda:        " + mon);

        TinhGia giam10 = gia -> gia * 90 / 100;
        System.out.println("Giảm 10%: " + giam10.tinh(50_000));

        Predicate<String> coChuA = s -> s.contains("a");
        Function<String, Integer> doDai = String::length;     // method reference
        Supplier<String> chao = () -> "Xin chào";
        Consumer<String> in = System.out::println;
        System.out.println(coChuA.test("nước cam") + " " + doDai.apply("cà phê"));
        in.accept(chao.get());
    }
}
