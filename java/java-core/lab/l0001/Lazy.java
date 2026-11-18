public class Lazy {
    public static void main(String[] args) {
        if (args.length > 0) {
            new Helper().hi();
        }
        System.out.println("xong");
    }
}
class Helper { void hi() { System.out.println("hi"); } }
