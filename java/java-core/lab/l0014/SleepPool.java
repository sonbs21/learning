import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SleepPool {
    public static void main(String[] args) {
        AtomicInteger done = new AtomicInteger();
        long start = System.nanoTime();
        try (ExecutorService ex = Executors.newFixedThreadPool(200)) {
            for (int i = 0; i < 10_000; i++) {
                ex.submit(() -> {
                    try { Thread.sleep(100); } catch (InterruptedException e) { }
                    done.incrementAndGet();
                });
            }
        }
        System.out.println("Pool 200 thread: " + done.get() + " việc (mỗi việc chờ 100 ms), "
                + (System.nanoTime() - start) / 1_000_000 + " ms");
    }
}
