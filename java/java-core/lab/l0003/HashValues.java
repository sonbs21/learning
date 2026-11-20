import java.util.Objects;

public class HashValues {
    // Chỉ override equals
    static class P2 {
        int x, y; P2(int x, int y) { this.x = x; this.y = y; }
        @Override public boolean equals(Object o) { return o instanceof P2 p && p.x == x && p.y == y; }
    }
    // Override cả equals và hashCode
    static class P3 {
        int x, y; P3(int x, int y) { this.x = x; this.y = y; }
        @Override public boolean equals(Object o) { return o instanceof P3 p && p.x == x && p.y == y; }
        @Override public int hashCode() { return Objects.hash(x, y); }
    }

    public static void main(String[] args) {
        P2 a2 = new P2(1, 2), b2 = new P2(1, 2);
        System.out.println("P2: equals=" + a2.equals(b2) + "  hashCode a=" + a2.hashCode() + "  b=" + b2.hashCode());
        P3 a3 = new P3(1, 2), b3 = new P3(1, 2);
        System.out.println("P3: equals=" + a3.equals(b3) + "  hashCode a=" + a3.hashCode() + "  b=" + b3.hashCode());
    }
}
