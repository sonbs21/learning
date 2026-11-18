public class Immutable {
    public static void main(String[] args) {
        String s = "hello";
        s.toUpperCase();
        System.out.println("1. s = " + s);

        String t = s.toUpperCase();
        System.out.println("2. t = " + t);
        System.out.println("3. s = " + s);
    }
}
