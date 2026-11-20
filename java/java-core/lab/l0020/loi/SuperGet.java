import java.util.List;

public class SuperGet {
    static void thu(List<? super Integer> ds) {
        ds.add(1);              // ghi: được
        Integer x = ds.get(0);  // đọc ra Integer: ?
    }
}
