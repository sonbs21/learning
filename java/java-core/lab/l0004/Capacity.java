public class Capacity {
    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder();
        System.out.println("Ban đầu: dài " + sb.length() + ", sức chứa " + sb.capacity());
        int cu = sb.capacity();
        for (int i = 0; i < 100; i++) {
            StringBuilder ketQua = sb.append('x');
            if (ketQua != sb) System.out.println("append trả về object khác!");
            if (sb.capacity() != cu) {
                System.out.println("Đầy ở độ dài " + (sb.length() - 1) + " → sức chứa mới " + sb.capacity());
                cu = sb.capacity();
            }
        }
        System.out.println("append luôn trả về chính sb: " + (sb.append("!") == sb));
    }
}
