public class Temp {
    static byte[] last;   // chỉ giữ object MỚI NHẤT

    public static void main(String[] args) {
        for (int i = 1; i <= 100_000; i++) {
            last = new byte[10_000];   // ~10 KB; object cũ không còn ai giữ → thành rác
        }
        System.out.println("Đã tạo 100 000 object (~1 000 MB). Xong, không lỗi!");
    }
}
