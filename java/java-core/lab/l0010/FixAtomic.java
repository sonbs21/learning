import java.util.concurrent.atomic.AtomicInteger;

public class FixAtomic {
    static AtomicInteger count = new AtomicInteger();

    public static void main(String[] args) throws InterruptedException {
        Runnable task = () -> {
            for (int i = 0; i < 100_000; i++) {
                count.incrementAndGet();   // tăng 1, làm trọn vẹn trong một bước
            }
        };
        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start(); t2.start();
        t1.join();  t2.join();
        System.out.println("Mong đợi: 200000, thực tế: " + count.get());
    }
}
