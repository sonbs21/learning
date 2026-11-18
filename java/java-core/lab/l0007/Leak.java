import java.util.ArrayList;
import java.util.List;

public class Leak {
    static List<byte[]> cache = new ArrayList<>();   // static: sống suốt chương trình

    public static void main(String[] args) {
        int i = 0;
        try {
            while (true) {
                cache.add(new byte[10_000]);          // ~10 KB, bỏ vào cache, không bao giờ xoá
                i++;
                if (i % 1000 == 0) System.out.println("Đang giữ khoảng " + i / 100 + " MB");
            }
        } catch (OutOfMemoryError e) {
            cache = null;                             // thả ra để còn chỗ in thông báo
            System.out.println("Sập sau " + i + " object: " + e);
        }
    }
}
