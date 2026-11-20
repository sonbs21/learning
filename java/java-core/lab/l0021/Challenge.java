import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class Challenge {
    public static void main(String[] args) {
        List<String> tu = IntStream.range(0, 200_000).mapToObj(i -> "x".repeat(i % 5 + 1)).toList();

        // TODO: đếm số từ theo độ dài. Code dưới đây SAI khi chạy parallel. Sửa lại.
        Map<Integer, Integer> dem = new HashMap<>();
        tu.parallelStream().forEach(w -> dem.merge(w.length(), 1, Integer::sum));

        System.out.println(dem + "  (mỗi độ dài đúng phải là 40000)");
    }
}
