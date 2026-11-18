public class Pool {
    public static void main(String[] args) {
        String a = "hi";
        String b = "hi";
        String c = new String("hi");

        System.out.println("1. a == b      : " + (a == b));
        System.out.println("2. a == c      : " + (a == c));
        System.out.println("3. a.equals(c) : " + a.equals(c));
        System.out.println("4. a == c.intern() : " + (a == c.intern()));
    }
}
