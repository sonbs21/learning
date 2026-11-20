import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ParallelRace {
    public static void main(String[] args) {
        List<Integer> ketQua = new ArrayList<>();
        try {
            IntStream.range(0, 100_000).parallel().forEach(ketQua::add);   // ghi vào list dùng chung
            System.out.println("forEach + add: " + ketQua.size() + " phần tử (đúng phải là 100000)");
        } catch (Exception e) { System.out.println("forEach + add: lỗi " + e); }

        List<Integer> dung = IntStream.range(0, 100_000).parallel().boxed().collect(Collectors.toList());
        System.out.println("collect:       " + dung.size() + " phần tử");

        System.out.print("Thứ tự forEach: ");
        IntStream.range(0, 10).parallel().forEach(i -> System.out.print(i + " "));
        System.out.print("\nforEachOrdered: ");
        IntStream.range(0, 10).parallel().forEachOrdered(i -> System.out.print(i + " "));
        System.out.println();
    }
}
