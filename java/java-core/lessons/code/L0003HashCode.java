// Bài 3 — chạy bằng: java lessons/code/L0003HashCode.java
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class L0003HashCode {

    // Override equals nhưng QUÊN hashCode
    static class BadPoint {
        final int x, y;
        BadPoint(int x, int y) { this.x = x; this.y = y; }
        @Override public boolean equals(Object o) {
            return o instanceof BadPoint p && p.x == x && p.y == y;
        }
    }

    // Đúng hợp đồng: equals và hashCode dùng CÙNG các field
    static class GoodPoint {
        final int x, y;
        GoodPoint(int x, int y) { this.x = x; this.y = y; }
        @Override public boolean equals(Object o) {
            return o instanceof GoodPoint p && p.x == x && p.y == y;
        }
        @Override public int hashCode() { return Objects.hash(x, y); }
    }

    // record tự sinh equals/hashCode/toString theo các component
    record Point(int x, int y) {}

    // Key có field thay đổi được (mutable)
    static class MutableKey {
        int id;
        MutableKey(int id) { this.id = id; }
        @Override public boolean equals(Object o) { return o instanceof MutableKey k && k.id == id; }
        @Override public int hashCode() { return Integer.hashCode(id); }
    }

    public static void main(String[] args) {
        Set<BadPoint> bad = new HashSet<>();
        bad.add(new BadPoint(1, 2));
        System.out.println("1. Bad  contains(1,2): " + bad.contains(new BadPoint(1, 2)));
        bad.add(new BadPoint(1, 2));
        System.out.println("2. Bad  size sau 2 lần add: " + bad.size());

        Set<GoodPoint> good = new HashSet<>();
        good.add(new GoodPoint(1, 2));
        System.out.println("3. Good contains(1,2): " + good.contains(new GoodPoint(1, 2)));

        Set<Point> rec = new HashSet<>();
        rec.add(new Point(1, 2));
        System.out.println("4. record contains   : " + rec.contains(new Point(1, 2)));

        Set<MutableKey> set = new HashSet<>();
        MutableKey k = new MutableKey(1);
        set.add(k);
        k.id = 99;                                   // đổi field SAU khi đã bỏ vào set
        System.out.println("5. contains(k) chính nó: " + set.contains(k));
        System.out.println("6. size                : " + set.size());

        System.out.println("7. \"Aa\".hashCode() = " + "Aa".hashCode() + ", \"BB\".hashCode() = " + "BB".hashCode());
    }
}
