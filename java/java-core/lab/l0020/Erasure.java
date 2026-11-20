import java.util.ArrayList;
import java.util.List;

public class Erasure {
    public static void main(String[] args) {
        List<String> chu = new ArrayList<>();
        List<Integer> so = new ArrayList<>();
        System.out.println("Cùng một class lúc chạy? " + (chu.getClass() == so.getClass()));
        System.out.println("Class thật: " + chu.getClass().getName());

        chu.add("cà phê");
        String s = chu.get(0);              // không cần ép kiểu?
        System.out.println(s);
    }
}
