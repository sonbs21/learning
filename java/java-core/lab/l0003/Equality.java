import java.util.*;

public class Equality {
    // 1. Không override gì: dùng Object.equals / Object.hashCode
    static class P1 { int x, y; P1(int x, int y) { this.x = x; this.y = y; } }

    // 2. Chỉ override equals (LỖI kinh điển)
    static class P2 {
        int x, y; P2(int x, int y) { this.x = x; this.y = y; }
        @Override public boolean equals(Object o) {
            return o instanceof P2 p && p.x == x && p.y == y;
        }
    }

    // 3. Override cả equals và hashCode
    static class P3 {
        int x, y; P3(int x, int y) { this.x = x; this.y = y; }
        @Override public boolean equals(Object o) {
            return o instanceof P3 p && p.x == x && p.y == y;
        }
        @Override public int hashCode() { return Objects.hash(x, y); }
    }

    // 4. record: compiler tự sinh equals/hashCode/toString
    record P4(int x, int y) {}

    public static void main(String[] args) {
        P1 a1 = new P1(1, 2), b1 = new P1(1, 2);
        System.out.println("P1: ==" + (a1 == b1) + " equals=" + a1.equals(b1));

        P2 a2 = new P2(1, 2), b2 = new P2(1, 2);
        Set<P2> s2 = new HashSet<>(List.of(a2));
        System.out.println("P2: ==" + (a2 == b2) + " equals=" + a2.equals(b2)
                + " set.contains=" + s2.contains(b2));

        P3 a3 = new P3(1, 2), b3 = new P3(1, 2);
        Set<P3> s3 = new HashSet<>(List.of(a3));
        System.out.println("P3: ==" + (a3 == b3) + " equals=" + a3.equals(b3)
                + " set.contains=" + s3.contains(b3));

        P4 a4 = new P4(1, 2), b4 = new P4(1, 2);
        System.out.println("P4: ==" + (a4 == b4) + " equals=" + a4.equals(b4)
                + " set.contains=" + new HashSet<>(List.of(a4)).contains(b4));
    }
}
