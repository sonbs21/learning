import java.util.ArrayList;
import java.util.List;

public class Challenge {
    static List<Runnable> suKien = new ArrayList<>();        // "bus" sự kiện sống mãi

    static class TrangSanPham {
        byte[] html = new byte[1_000_000];                    // mỗi trang nặng 1 MB
        int maSanPham;
        TrangSanPham(int ma) { maSanPham = ma; }

        void dangKy() {
            // TODO: listener này làm rò rỉ bộ nhớ. Sửa CHỈ phần đăng ký này.
            suKien.add(new Runnable() {
                public void run() { System.out.println("Sản phẩm " + maSanPham + " vừa đổi giá"); }
            });
        }
    }

    public static void main(String[] args) {
        try {
            for (int i = 1; i <= 1000; i++) new TrangSanPham(i).dangKy();
            System.out.println("Đăng ký 1000 trang, không sao");
            suKien.get(999).run();
        } catch (OutOfMemoryError e) {
            int n = suKien.size(); suKien = null;
            System.out.println("Hết bộ nhớ sau " + n + " trang");
        }
    }
}
