public class Deadlock {
    static final Object taiKhoanA = new Object();
    static final Object taiKhoanB = new Object();

    static void nghi() {
        try { Thread.sleep(100); } catch (InterruptedException e) { }
    }

    public static void main(String[] args) {
        Thread t1 = new Thread(() -> {
            synchronized (taiKhoanA) {
                System.out.println("chuyen-A-sang-B: đã khoá A, chờ khoá B...");
                nghi();
                synchronized (taiKhoanB) {
                    System.out.println("chuyen-A-sang-B: xong");
                }
            }
        }, "chuyen-A-sang-B");

        Thread t2 = new Thread(() -> {
            synchronized (taiKhoanB) {
                System.out.println("chuyen-B-sang-A: đã khoá B, chờ khoá A...");
                nghi();
                synchronized (taiKhoanA) {
                    System.out.println("chuyen-B-sang-A: xong");
                }
            }
        }, "chuyen-B-sang-A");

        t1.start();
        t2.start();
        System.out.println("PID của chương trình: " + ProcessHandle.current().pid());
    }
}
