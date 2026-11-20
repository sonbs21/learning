public class Challenge {
    static String in(Object o)  { return "Object"; }
    static String in(String s)  { return "String"; }
    static String in(long n)    { return "long"; }

    public static void main(String[] args) {
        Object chu = "xin chào";
        System.out.println("1: " + in(5));
        System.out.println("2: " + in(null));
        System.out.println("3: " + in(chu));
        System.out.println("4: " + in('a'));
        System.out.println("5: " + in(Integer.valueOf(5)));
    }
}
