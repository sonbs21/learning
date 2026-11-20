import java.util.concurrent.atomic.AtomicInteger;

public class NewThreadEachTask {
    public static void main(String[] args) throws InterruptedException {
        AtomicInteger done = new AtomicInteger();
        long start = System.nanoTime();
        Thread[] threads = new Thread[10_000];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(done::incrementAndGet);   // MỖI việc một thread mới
            threads[i].start();
        }
        for (Thread t : threads) t.join();
        System.out.println("Thread mới cho mỗi việc: " + done.get() + " việc, "
                + (System.nanoTime() - start) / 1_000_000 + " ms");
    }
}
