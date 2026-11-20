import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Pool {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger done = new AtomicInteger();
        long start = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(8);   // 8 thread dùng đi dùng lại
        for (int i = 0; i < 10_000; i++) {
            pool.submit(done::incrementAndGet);                    // giao việc cho pool
        }
        pool.shutdown();                                           // không nhận việc mới nữa
        pool.awaitTermination(10, TimeUnit.SECONDS);               // chờ làm xong việc đã giao
        System.out.println("Thread pool 8 thread:   " + done.get() + " việc, "
                + (System.nanoTime() - start) / 1_000_000 + " ms");
    }
}
