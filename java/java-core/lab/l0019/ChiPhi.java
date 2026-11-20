public class ChiPhi {
    static class LoiNhe extends RuntimeException {
        LoiNhe() { super("nhẹ", null, false, false); }    // không chụp stack trace
    }

    static Object giu;                                     // để JIT không bỏ việc tạo object
    static Object taoSau(int doSau, boolean coStack) {
        if (doSau > 0) return taoSau(doSau - 1, coStack);
        return coStack ? new RuntimeException("nặng") : new LoiNhe();
    }

    static long doNs(int doSau, boolean coStack) {
        int n = 100_000;
        for (int i = 0; i < n; i++) giu = taoSau(doSau, coStack);     // làm nóng (bài 8)
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) giu = taoSau(doSau, coStack);
        return (System.nanoTime() - t0) / n;
    }

    public static void main(String[] args) {
        System.out.println("Có stack trace, sâu 10 tầng : " + doNs(10, true) + " ns/lần");
        System.out.println("Có stack trace, sâu 200 tầng: " + doNs(200, true) + " ns/lần");
        System.out.println("Không stack trace, sâu 200  : " + doNs(200, false) + " ns/lần");
        System.out.println("Tạo object thường, sâu 200  : (để so sánh) " + doObj() + " ns/lần");
    }
    static long doObj() {
        int n = 100_000; long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) giu = sauObj(200);
        return (System.nanoTime() - t0) / n;
    }
    static Object sauObj(int d) { return d > 0 ? sauObj(d - 1) : new Object(); }
}
