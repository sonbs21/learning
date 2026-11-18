import java.util.ArrayList;
import java.util.List;

public class RemoveSpeed {
    static List<Integer> fill(int n) {
        List<Integer> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(i);
        return list;
    }

    public static void main(String[] args) {
        int n = 200_000;

        List<Integer> a = fill(n);
        long t1 = System.nanoTime();
        while (!a.isEmpty()) a.remove(a.size() - 1);      // xoá từ CUỐI
        System.out.println("Xoá từ cuối : " + (System.nanoTime() - t1) / 1_000_000 + " ms");

        List<Integer> b = fill(n);
        long t2 = System.nanoTime();
        while (!b.isEmpty()) b.remove(0);                 // xoá từ ĐẦU
        System.out.println("Xoá từ đầu  : " + (System.nanoTime() - t2) / 1_000_000 + " ms");
    }
}
