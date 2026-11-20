import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class FutureDemo {
    static long tinhTong(int den) {                 // một việc "nặng" có kết quả trả về
        long s = 0;
        for (int i = 1; i <= den; i++) s += i;
        return s;
    }

    public static void main(String[] args) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        Future<Long> f1 = pool.submit(() -> tinhTong(1_000_000));
        Future<Long> f2 = pool.submit(() -> tinhTong(2_000_000));
        System.out.println("main: đã giao 2 việc, làm việc khác trong lúc chờ...");
        System.out.println("Kết quả 1 = " + f1.get());   // get(): chờ tới khi có kết quả
        System.out.println("Kết quả 2 = " + f2.get());
        pool.shutdown();
    }
}
