public class VolatileCount {
    static volatile int count = 0;           // volatile có cứu được count++ không?

    public static void main(String[] args) throws InterruptedException {
        Runnable task = () -> { for (int i = 0; i < 100_000; i++) count++; };
        Thread t1 = new Thread(task), t2 = new Thread(task);
        t1.start(); t2.start();
        t1.join();  t2.join();
        System.out.println("Mong đợi: 200000, thực tế: " + count);
    }
}
