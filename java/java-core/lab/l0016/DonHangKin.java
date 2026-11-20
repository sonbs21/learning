import java.util.ArrayList;
import java.util.List;

public class DonHangKin {
    static class DonHang {
        private final List<String> monHang;
        DonHang(List<String> monHang) { this.monHang = List.copyOf(monHang); }  // chép ra bản không sửa được
        List<String> getMonHang() { return monHang; }
    }

    public static void main(String[] args) {
        List<String> ds = new ArrayList<>(List.of("sách", "bút"));
        DonHang don = new DonHang(ds);
        ds.add("laptop");
        System.out.println("1. Sau khi sửa ds:   " + don.getMonHang());
        try {
            don.getMonHang().clear();
        } catch (UnsupportedOperationException e) {
            System.out.println("2. getter.clear() bị chặn: " + e);
        }
    }
}
