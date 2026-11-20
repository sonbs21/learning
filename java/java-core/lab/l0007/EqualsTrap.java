import java.util.HashSet;
import java.util.Set;

public class EqualsTrap {
    static class Diem {
        final int x, y;
        Diem(int x, int y) { this.x = x; this.y = y; }

        // Nhầm: tham số là Diem chứ không phải Object
        public boolean equals(Diem o) { return x == o.x && y == o.y; }
        @Override public int hashCode() { return 31 * x + y; }
    }

    public static void main(String[] args) {
        Diem a = new Diem(1, 2), b = new Diem(1, 2);
        Object bAnDi = b;
        System.out.println("a.equals(b)     = " + a.equals(b));
        System.out.println("a.equals(bAnDi) = " + a.equals(bAnDi));
        Set<Diem> set = new HashSet<>();
        set.add(a);
        System.out.println("set.contains(b) = " + set.contains(b));
    }
}
