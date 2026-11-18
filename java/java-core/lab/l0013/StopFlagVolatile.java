public class StopFlagVolatile {
    static volatile boolean running = true;  // khác duy nhất: thêm volatile

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            long count = 0;
            while (running) {
                count++;
            }
            System.out.println("worker: thấy cờ tắt, dừng lại sau " + count + " vòng");
        }, "worker");

        worker.start();
        Thread.sleep(500);
        running = false;
        System.out.println("main: đã tắt cờ, chờ worker dừng...");
        worker.join();
        System.out.println("main: xong!");
    }
}
