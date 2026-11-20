import java.util.ArrayList;
import java.util.List;

public class Wildcard {
    // ? extends Number: "một list của MỘT loại con nào đó của Number" -> chỉ ĐỌC ra
    static double tong(List<? extends Number> ds) {
        double t = 0;
        for (Number n : ds) t += n.doubleValue();
        return t;
    }
    // ? super Integer: "một list chứa được Integer" -> chỉ GHI vào
    static void them123(List<? super Integer> ds) {
        ds.add(1); ds.add(2); ds.add(3);
    }

    public static void main(String[] args) {
        System.out.println(tong(List.of(1, 2, 3)));          // List<Integer>
        System.out.println(tong(List.of(1.5, 2.5)));         // List<Double>

        List<Number> so = new ArrayList<>();
        List<Object> doVat = new ArrayList<>(List.of("ly"));
        them123(so);
        them123(doVat);
        System.out.println(so + " " + doVat);
    }
}
