public class Challenge {
    public static void main(String[] args) {
        String a = "hi";
        String h = "h";
        System.out.println("1. " + (a == "h" + "i"));
        System.out.println("2. " + (a == h + "i"));
        Integer x = 127, y = 127;
        Integer m = 128, n = 128;
        System.out.println("3. " + (x == y));
        System.out.println("4. " + (m == n));
    }
}
