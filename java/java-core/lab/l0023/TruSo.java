import java.util.ArrayList;
import java.util.List;

public class TruSo {
    public static void main(String[] args) {
        List<Integer> so = new ArrayList<>(List.of(5, Integer.MIN_VALUE, -10, Integer.MAX_VALUE));
        so.sort((a, b) -> a - b);                     // "mẹo" trừ hai số
        System.out.println("a - b             : " + so);
        so.sort(Integer::compare);
        System.out.println("Integer.compare   : " + so);
        System.out.println("MIN_VALUE - 5 = " + (Integer.MIN_VALUE - 5));
    }
}
