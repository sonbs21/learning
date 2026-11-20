import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SapXep {
    record Mon(String ten, int gia) implements Comparable<Mon> {
        @Override public int compareTo(Mon khac) { return Integer.compare(gia, khac.gia); }   // thứ tự "tự nhiên": theo giá
    }

    public static void main(String[] args) {
        List<Mon> menu = new ArrayList<>(List.of(
            new Mon("trà sữa", 35000), new Mon("bạc xỉu", 30000),
            new Mon("cà phê", 25000), new Mon("americano", 30000)));

        menu.sort(null);                                            // null = dùng compareTo
        System.out.println("Comparable (giá):        " + menu);

        menu.sort(Comparator.comparing(Mon::ten));                  // một cách sắp khác, không sửa class
        System.out.println("Comparator (tên):        " + menu);

        menu.sort(Comparator.comparingInt(Mon::gia).reversed().thenComparing(Mon::ten));
        System.out.println("Giá giảm dần, rồi theo tên: " + menu);
    }
}
