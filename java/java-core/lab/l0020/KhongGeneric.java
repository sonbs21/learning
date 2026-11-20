import java.util.ArrayList;
import java.util.List;

public class KhongGeneric {
    public static void main(String[] args) {
        List ds = new ArrayList();          // "raw type": hộp không dán nhãn
        ds.add("cà phê");
        ds.add(42);                         // compiler không cản
        for (Object o : ds) {
            String s = (String) o;          // tự ép kiểu lấy ra
            System.out.println(s.toUpperCase());
        }
    }
}
