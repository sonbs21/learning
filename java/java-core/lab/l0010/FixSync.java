public class FixSync {
    static int count = 0;
    static final Object lock = new Object();

    public static void main(String[] args) throws InterruptedException {
        Runnable task = () -> {
            for (int i = 0; i < 100_000; i++) {
                synchronized (lock) {    // mỗi lúc chỉ MỘT thread được vào
                    count++;
                }
            }
        };
        Thread t1 = new Thread(task);
        Thread t2 = new Thread(task);
        t1.start(); t2.start();
        t1.join();  t2.join();
        System.out.println("Mong đợi: 200000, thực tế: " + count);
    }
}
