import java.util.List;

public class ExtendsAdd {
    static void thu(List<? extends Number> ds) {
        Number n = ds.get(0);   // đọc: được
        ds.add(1);              // ghi: ?
    }
}
