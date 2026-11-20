public class Finally {
    static String laySo() {
        try {
            System.out.println("  try: chuẩn bị return");
            return "từ try";
        } finally {
            System.out.println("  finally: vẫn chạy dù try đã return");
        }
    }

    @SuppressWarnings("finally")
    static String nuotLoi() {
        try {
            throw new IllegalStateException("lỗi quan trọng");
        } finally {
            return "finally return đè lên";      // KHÔNG BAO GIỜ làm thế này
        }
    }

    public static void main(String[] args) {
        System.out.println("Kết quả: " + laySo());
        System.out.println("Kết quả: " + nuotLoi());
    }
}
