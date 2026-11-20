import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.IntStream;

public class CommonPool {
    public static void main(String[] args) {
        System.out.println("Số lõi CPU: " + Runtime.getRuntime().availableProcessors());
        System.out.println("Độ song song của common pool: " + ForkJoinPool.commonPool().getParallelism());

        Set<String> tenThread = ConcurrentHashMap.newKeySet();
        long t0 = System.currentTimeMillis();
        IntStream.range(0, 64).parallel().forEach(i -> {
            tenThread.add(Thread.currentThread().getName());
            try { Thread.sleep(500); } catch (InterruptedException e) { }   // giả lập gọi API chậm
        });
        long ms = System.currentTimeMillis() - t0;
        System.out.println("64 việc x 500ms chạy hết: " + ms + " ms, dùng " + tenThread.size() + " thread");
        tenThread.stream().sorted().limit(3).forEach(t -> System.out.println("  " + t));
    }
}
