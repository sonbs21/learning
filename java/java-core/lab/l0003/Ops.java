public class Ops {
    static boolean refEq(Object a, Object b) { return a == b; }
    static boolean intEq(int a, int b)       { return a == b; }
    static boolean callEq(Object a, Object b){ return a.equals(b); }
}
