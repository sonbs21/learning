public class Race {
    static int count = 0;

    public static void main(String[] args) throws InterruptedException {
        Runnable task = () -> {
            for (int i = 0; i < 100_000; i++) {
                count++;
            }
        };
        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start(); t2.start();      // hai thread chạy CÙNG LÚC
        t1.join();  t2.join();       // chờ cả hai chạy xong
        System.out.println("Mong đợi: 200000, thực tế: " + count);
    }
}
