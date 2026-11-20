public class Overload {
    static int tong(int a, int b)           { return a + b; }
    static double tong(double a, double b)  { return a + b; }
    static int tong(int a, int b, int c)    { return a + b + c; }

    public static void main(String[] args) {
        System.out.println(tong(1, 2));        // (int, int)
        System.out.println(tong(1.5, 2.5));    // (double, double)
        System.out.println(tong(1, 2, 3));     // (int, int, int)
    }
}
