import java.util.ArrayList;
import java.util.List;

public class Leak {
    static List<Object> lichSu = new ArrayList<>();      // giữ lại "biên lai" mãi mãi

    static class DonHang {
        byte[] anhChupHoaDon = new byte[1_000_000];       // mỗi đơn hàng nặng 1 MB
        int ma;
        DonHang(int ma) { this.ma = ma; }

        class BienLaiInner { int ma() { return ma; } }    // inner: ngầm giữ cả DonHang
        static class BienLaiNested {                      // static nested: chỉ giữ cái nó cần
            final int ma;
            BienLaiNested(int ma) { this.ma = ma; }
        }
    }

    public static void main(String[] args) {
        boolean dungInner = args[0].equals("inner");
        try {
            for (int i = 1; i <= 1000; i++) {
                DonHang d = new DonHang(i);
                lichSu.add(dungInner ? d.new BienLaiInner() : new DonHang.BienLaiNested(d.ma));
                // d ra khỏi scope ở đây: chỉ còn biên lai được giữ lại
            }
            System.out.println(args[0] + ": giữ 1000 biên lai, không sao");
        } catch (OutOfMemoryError e) {
            int soLuong = lichSu.size();
            lichSu = null;
            System.out.println(args[0] + ": hết bộ nhớ sau " + soLuong + " biên lai: " + e.getMessage());
        }
    }
}
