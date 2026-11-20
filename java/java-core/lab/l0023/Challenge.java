import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Challenge {
    record Don(String ma, boolean vip, long tong) { }

    public static void main(String[] args) {
        List<Don> ds = new ArrayList<>(List.of(
            new Don("D05", false, 120_000), new Don("D02", true, 80_000),
            new Don("D09", false, 300_000), new Don("D01", true, 80_000),
            new Don("D07", true, 250_000)));

        // TODO: sắp xếp: khách VIP lên trước; cùng nhóm thì tổng tiền GIẢM dần; bằng tiền thì mã TĂNG dần
        Comparator<Don> thuTu = Comparator.comparing(Don::ma);   // sửa dòng này

        ds.sort(thuTu);
        ds.forEach(d -> System.out.println(d.ma() + " vip=" + d.vip() + " tong=" + d.tong()));
    }
}
