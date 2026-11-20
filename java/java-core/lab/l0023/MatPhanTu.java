import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

public class MatPhanTu {
    record Mon(String ten, int gia) { }

    public static void main(String[] args) {
        Set<Mon> hash = new HashSet<>();
        Set<Mon> tree = new TreeSet<>(Comparator.comparingInt(Mon::gia));    // chỉ so theo giá
        for (Set<Mon> s : java.util.List.of(hash, tree)) {
            s.add(new Mon("bạc xỉu", 30000));
            s.add(new Mon("americano", 30000));
            s.add(new Mon("cà phê", 25000));
        }
        System.out.println("HashSet: " + hash.size() + " món " + hash);
        System.out.println("TreeSet: " + tree.size() + " món " + tree);
    }
}
