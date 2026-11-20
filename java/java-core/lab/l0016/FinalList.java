import java.util.ArrayList;
import java.util.List;

public class FinalList {
    public static void main(String[] args) {
        final List<String> gioHang = new ArrayList<>();
        gioHang.add("sách");          // sửa NỘI DUNG object → được phép
        gioHang.add("bút");
        gioHang.remove("sách");
        System.out.println("gioHang = " + gioHang);
        // gioHang = new ArrayList<>(); // gán biến sang object khác → lỗi biên dịch
    }
}
