import java.util.ArrayList;
import java.util.List;

public class ListNumber {
    public static void main(String[] args) {
        List<Integer> soNguyen = new ArrayList<>();
        List<Number> so = soNguyen;         // Integer là Number, vậy List<Integer> là List<Number>?
    }
}
