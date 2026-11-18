public class Challenge {
    static void a(int[] arr)          { arr[0] = 10; arr = new int[]{20}; arr[0] = 30; }
    static void b(StringBuilder sb)   { sb.append(" world"); sb = new StringBuilder("bye"); }
    static void c(String s)           { s = s + "!"; }

    public static void main(String[] args) {
        int[] arr = {1};
        a(arr);
        StringBuilder sb = new StringBuilder("hello");
        b(sb);
        String s = "hi";
        c(s);
        System.out.println(arr[0] + " | " + sb + " | " + s);
    }
}
