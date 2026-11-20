public class MatStack {
    static int doDai(String s) { return s.length(); }       // s = null → NullPointerException

    public static void main(String[] args) {
        for (int i = 1; i <= 1_000_000; i++) {
            try {
                doDai(null);
            } catch (NullPointerException e) {
                if (i == 1 || e.getStackTrace().length == 0) {
                    System.out.println("Lần " + i + ": message = " + e.getMessage());
                    System.out.println("    stack trace dài " + e.getStackTrace().length + " dòng");
                    if (i > 1) return;
                }
            }
        }
        System.out.println("Chạy hết 1 000 000 lần, stack trace vẫn đầy đủ");
    }
}
