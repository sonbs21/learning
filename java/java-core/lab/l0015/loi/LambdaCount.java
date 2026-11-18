import java.util.List;

public class LambdaCount {
    public static void main(String[] args) {
        int dem = 0;
        List.of("a", "b", "c").forEach(s -> dem++);
        System.out.println(dem);
    }
}
