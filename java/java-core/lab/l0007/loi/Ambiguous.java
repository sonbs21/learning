public class Ambiguous {
    static String in(String s)  { return "String"; }
    static String in(Integer n) { return "Integer"; }
    public static void main(String[] args) { System.out.println(in(null)); }
}
