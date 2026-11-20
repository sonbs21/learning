public class Challenge {
    record R(String ten) implements AutoCloseable {
        R { System.out.println("mở " + ten); }
        public void close() { System.out.println("đóng " + ten); }
    }

    static int dem() {
        int x = 1;
        try {
            return x;
        } finally {
            x = 99;
            System.out.println("finally: x = " + x);
        }
    }

    static void doiLoi() {
        try {
            throw new RuntimeException("A");
        } catch (RuntimeException e) {
            throw new IllegalStateException("B");
        } finally {
            System.out.println("finally của doiLoi");
        }
    }

    public static void main(String[] args) {
        System.out.println("dem() = " + dem());
        try { doiLoi(); } catch (Exception e) { System.out.println("bắt: " + e.getMessage()); }
        try (R a = new R("1"); R b = new R("2")) {
            System.out.println("thân try");
        }
    }
}
