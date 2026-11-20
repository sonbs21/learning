public class TaiNguyen {
    static class KetNoi implements AutoCloseable {
        final String ten; final boolean dongLoi;
        KetNoi(String ten, boolean dongLoi) { this.ten = ten; this.dongLoi = dongLoi; System.out.println("  mở " + ten); }
        @Override public void close() {
            System.out.println("  đóng " + ten);
            if (dongLoi) throw new IllegalStateException("lỗi khi đóng " + ten);
        }
    }

    static void kieuCu() {
        KetNoi k = new KetNoi("DB", true);
        try {
            throw new RuntimeException("lỗi khi truy vấn");      // lỗi gốc
        } finally {
            k.close();                                          // close cũng lỗi → che mất lỗi gốc
        }
    }

    static void kieuMoi() {
        try (KetNoi db = new KetNoi("DB", true); KetNoi file = new KetNoi("File", false)) {
            throw new RuntimeException("lỗi khi truy vấn");
        }
    }

    public static void main(String[] args) {
        System.out.println("--- try/finally kiểu cũ:");
        try { kieuCu(); } catch (Exception e) {
            System.out.println("Bắt được: " + e.getMessage());
        }
        System.out.println("--- try-with-resources:");
        try { kieuMoi(); } catch (Exception e) {
            System.out.println("Bắt được: " + e.getMessage());
            for (Throwable s : e.getSuppressed()) System.out.println("  kèm theo (suppressed): " + s.getMessage());
        }
    }
}
