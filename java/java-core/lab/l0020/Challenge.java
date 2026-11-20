import java.util.ArrayList;
import java.util.List;

public class Challenge {
    // TODO: sửa CHỈ chữ ký của method này để main biên dịch được
    static <T> void chep(List<T> nguon, List<T> dich) {
        for (T x : nguon) dich.add(x);
    }

    public static void main(String[] args) {
        List<Integer> soNguyen = List.of(1, 2, 3);
        List<Number> so = new ArrayList<>();
        List<Object> doVat = new ArrayList<>();
        chep(soNguyen, so);
        chep(soNguyen, doVat);
        System.out.println(so + " " + doVat);
    }
}
