import java.util.HashMap;

public class Resize {
    public static void main(String[] args) throws Exception {
        HashMap<Integer, String> map = new HashMap<>();
        int last = -1;
        for (int i = 1; i <= 25; i++) {
            map.put(i, "v" + i);
            int n = Peek.table(map).length;
            if (n != last) {
                System.out.println("Sau khi put phần tử thứ " + i + " → số ngăn = " + n);
                last = n;
            }
        }
    }
}
