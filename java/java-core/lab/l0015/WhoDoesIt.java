import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WhoDoesIt {
    public static void main(String[] args) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        for (int i = 1; i <= 6; i++) {
            int viec = i;
            pool.submit(() -> System.out.println("Việc " + viec + " do " + Thread.currentThread().getName() + " làm"));
        }
        pool.shutdown();
    }
}
