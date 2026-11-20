public class StopFlag {
    static boolean running = true;          // cờ báo "tiếp tục chạy"

    public static void main(String[] args) throws InterruptedException {
        Thread worker = new Thread(() -> {
            long count = 0;
            while (running) {               // cứ chạy khi cờ còn bật
                count++;
            }
            System.out.println("worker: thấy cờ tắt, dừng lại sau " + count + " vòng");
        }, "worker");

        worker.start();
        Thread.sleep(500);                  // cho worker chạy nửa giây
        running = false;                    // main tắt cờ
        System.out.println("main: đã tắt cờ, chờ worker dừng...");
        worker.join();                      // chờ worker kết thúc
        System.out.println("main: xong!");
    }
}
