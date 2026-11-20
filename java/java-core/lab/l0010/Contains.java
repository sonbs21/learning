import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Contains {
    public static void main(String[] args) {
        int n = 100_000;
        List<Integer> list = new ArrayList<>();
        Set<Integer> set = new HashSet<>();
        for (int i = 0; i < n; i++) { list.add(i); set.add(i); }

        int found = 0;
        long t1 = System.nanoTime();
        for (int i = 0; i < 10_000; i++) if (list.contains(n - 1 - i)) found++;
        System.out.println("ArrayList.contains x10 000 : " + (System.nanoTime() - t1) / 1_000_000 + " ms");

        long t2 = System.nanoTime();
        for (int i = 0; i < 10_000; i++) if (set.contains(n - 1 - i)) found++;
        System.out.println("HashSet.contains   x10 000 : " + (System.nanoTime() - t2) / 1_000_000 + " ms");
        System.out.println("(tìm thấy " + found + ")");
    }
}
