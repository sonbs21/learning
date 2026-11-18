import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ForgotShutdown {
    public static void main(String[] args) {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        pool.submit(() -> System.out.println("việc đã làm xong"));
        System.out.println("main: hết hàm main rồi!");
        // QUÊN pool.shutdown()
    }
}
