import java.util.HashMap;
import java.util.Map;

public class MapRace {
    public static void main(String[] args) throws InterruptedException {
        Map<Integer, Integer> map = new HashMap<>();
        Thread t1 = new Thread(() -> { for (int i = 0;      i < 50_000;  i++) map.put(i, i); });
        Thread t2 = new Thread(() -> { for (int i = 50_000; i < 100_000; i++) map.put(i, i); });
        t1.start(); t2.start();
        t1.join();  t2.join();
        System.out.println("Mong đợi: 100000 phần tử, thực tế: " + map.size());
    }
}
