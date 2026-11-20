import java.util.ArrayList;
import java.util.List;

public class LenLut {
    @SuppressWarnings("unchecked")
    static void themBay(List ds) { ds.add(42); }        // nhận raw List, nhét Integer vào

    public static void main(String[] args) {
        List<String> chu = new ArrayList<>();
        themBay(chu);                                    // lúc chạy không ai kiểm tra
        System.out.println("Kích thước: " + chu.size());
        Object o = chu.get(0);                           // lấy ra dạng Object: chưa sao
        System.out.println("Lấy ra được: " + o);
        String s = chu.get(0);                           // compiler chèn ép kiểu String: nổ
    }
}
