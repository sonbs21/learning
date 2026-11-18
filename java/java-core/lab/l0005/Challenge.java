public class Challenge {
    public static void main(String[] args) {
        System.out.println("1. tạo mảng");
        Box[] boxes = new Box[3];
        System.out.println("2. đọc hằng số");
        System.out.println("   MAX = " + Box.MAX);
        System.out.println("3. đọc biến static thường");
        System.out.println("   count = " + Box.count);
    }
}

class Box {
    static final int MAX = 10;   // hằng số
    static int count = 5;        // biến static thường
    static { System.out.println("   >>> [static] Box được khởi tạo"); }
}
