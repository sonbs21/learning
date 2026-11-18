import java.util.HashMap;

public class Challenge {
    public static void main(String[] args) throws Exception {
        HashMap<Integer, String> map = new HashMap<>();
        map.put(1, "một");
        map.put(17, "mười bảy");
        map.put(33, "ba mươi ba");
        map.put(5, "năm");
        Peek.show(map);
    }
}
