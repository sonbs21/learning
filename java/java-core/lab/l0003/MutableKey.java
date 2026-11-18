import java.util.*;

public class MutableKey {
    static class P {
        int x, y; P(int x, int y) { this.x = x; this.y = y; }
        @Override public boolean equals(Object o) { return o instanceof P p && p.x == x && p.y == y; }
        @Override public int hashCode() { return Objects.hash(x, y); }
    }
    public static void main(String[] args) {
        P p = new P(1, 2);
        Set<P> set = new HashSet<>();
        set.add(p);
        p.x = 100;                       // sửa key SAU khi đã cho vào set
        System.out.println("contains(p)   = " + set.contains(p));
        System.out.println("size          = " + set.size());
        System.out.println("iterate thấy p = " + set.iterator().next().equals(p));
    }
}
