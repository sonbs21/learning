import java.util.*;
public class Const {
    record K(int v) { @Override public int hashCode() { return 42; } }
    record G(int v) {}
    static long time(java.util.function.IntFunction<Object> f) {
        Set<Object> s = new HashSet<>(); long t = System.nanoTime();
        for (int i = 0; i < 20_000; i++) s.add(f.apply(i));
        for (int i = 0; i < 20_000; i++) s.contains(f.apply(i));
        return (System.nanoTime() - t) / 1_000_000;
    }
    public static void main(String[] a) {
        Set<K> s = new HashSet<>(List.of(new K(1), new K(2)));
        System.out.println("contains(new K(2)) = " + s.contains(new K(2)) + ", size = " + s.size());
        System.out.println("hash tốt: " + time(G::new) + " ms, hash = 42: " + time(K::new) + " ms");
    }
}
