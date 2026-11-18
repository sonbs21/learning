public class Depth {
    static int depth = 0;
    static void dive() { depth++; dive(); }
    public static void main(String[] args) {
        try { dive(); } catch (StackOverflowError e) {
            System.out.println("StackOverflowError ở độ sâu " + depth);
        }
    }
}
