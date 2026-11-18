import java.util.ArrayList;
import java.util.List;

public class DonHangHo {
    // Lớp "trông như bất biến": field private final, không có setter
    static class DonHang {
        private final List<String> monHang;
        DonHang(List<String> monHang) { this.monHang = monHang; }
        List<String> getMonHang() { return monHang; }
    }

    public static void main(String[] args) {
        List<String> ds = new ArrayList<>(List.of("sách", "bút"));
        DonHang don = new DonHang(ds);
        System.out.println("1. Đơn hàng lúc tạo: " + don.getMonHang());

        ds.add("laptop");                       // sửa list GỐC bên ngoài
        System.out.println("2. Sau khi sửa ds:   " + don.getMonHang());

        don.getMonHang().clear();               // sửa qua getter
        System.out.println("3. Sau getter.clear: " + don.getMonHang());
    }
}
