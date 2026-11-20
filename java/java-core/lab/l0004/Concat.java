public class Concat {
    static final int N = 100_000;

    public static void main(String[] args) {
        long t0 = System.nanoTime();
        String s = "";
        for (int i = 0; i < N; i++) s += "x";           // mỗi vòng: tạo String MỚI, chép lại toàn bộ
        long t1 = System.nanoTime();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < N; i++) sb.append("x");     // viết thêm vào cùng một object
        String s2 = sb.toString();
        long t2 = System.nanoTime();

        System.out.println("String +=      : " + (t1 - t0) / 1_000_000 + " ms, dài " + s.length());
        System.out.println("StringBuilder  : " + (t2 - t1) / 1_000_000 + " ms, dài " + s2.length());
    }
}
