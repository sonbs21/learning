import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class Virtual {
    public static void main(String[] args) {
        AtomicInteger done = new AtomicInteger();
        long start = System.nanoTime();
        try (ExecutorService ex = Executors.newVirtualThreadPerTaskExecutor()) {   // Java 21
            for (int i = 0; i < 10_000; i++) {
                ex.submit(() -> {
                    try { Thread.sleep(100); } catch (InterruptedException e) { }  // giả lập chờ I/O
                    done.incrementAndGet();
                });
            }
        }   // try-with-resources: tự đóng và chờ mọi việc xong
        System.out.println("Virtual threads: " + done.get() + " việc (mỗi việc chờ 100 ms), "
                + (System.nanoTime() - start) / 1_000_000 + " ms");
    }
}
